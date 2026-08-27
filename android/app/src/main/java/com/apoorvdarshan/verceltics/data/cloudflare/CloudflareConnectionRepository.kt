package com.apoorvdarshan.verceltics.data.cloudflare

import android.content.Context
import com.apoorvdarshan.verceltics.data.account.AccountCipher
import com.apoorvdarshan.verceltics.data.account.AccountEnvelopeCodec
import com.apoorvdarshan.verceltics.data.account.AndroidKeystoreAccountCipher
import com.apoorvdarshan.verceltics.data.account.AtomicBytesStore
import com.apoorvdarshan.verceltics.data.account.NoBackupAtomicFileStore
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal class CloudflareRecordRevision private constructor(private val digest: ByteArray) {
    fun matches(envelope: ByteArray): Boolean {
        val candidate = MessageDigest.getInstance(DIGEST_ALGORITHM).digest(envelope)
        return try {
            MessageDigest.isEqual(digest, candidate)
        } finally {
            candidate.fill(0)
        }
    }

    override fun toString(): String = "CloudflareRecordRevision(<redacted>)"

    companion object {
        private const val DIGEST_ALGORITHM = "SHA-256"

        fun from(envelope: ByteArray): CloudflareRecordRevision = CloudflareRecordRevision(
            MessageDigest.getInstance(DIGEST_ALGORITHM).digest(envelope),
        )
    }
}

internal class CloudflareRecordCommit(
    val revision: CloudflareRecordRevision,
    previousEnvelope: ByteArray?,
) {
    private var state = State.PENDING
    private var rollbackEnvelope: ByteArray? = previousEnvelope

    @Synchronized
    fun accept(): ByteArray? {
        if (state != State.PENDING) return null
        state = State.ACCEPTED
        return rollbackEnvelope.also { rollbackEnvelope = null }
    }

    @Synchronized
    fun claimRollback(): CloudflareRollbackEnvelope? {
        if (state != State.PENDING) return null
        state = State.ROLLBACK_CLAIMED
        return CloudflareRollbackEnvelope(rollbackEnvelope).also { rollbackEnvelope = null }
    }

    override fun toString(): String = "CloudflareRecordCommit(<redacted>)"

    private enum class State { PENDING, ACCEPTED, ROLLBACK_CLAIMED }
}

internal class CloudflareRollbackEnvelope(val bytes: ByteArray?) {
    override fun toString(): String = "CloudflareRollbackEnvelope(<redacted>)"
}

internal data class CloudflareVersionedConnection(
    val connection: CloudflareStoredConnection,
    val revision: CloudflareRecordRevision,
)

/** Atomic encrypted Cloudflare storage in a provider-specific no-backup slot and AAD domain. */
class CloudflareConnectionRepository(
    private val store: AtomicBytesStore,
    private val cipher: AccountCipher,
) {
    private var pendingCommit: CloudflareRecordCommit? = null

    @Synchronized
    fun load(): CloudflareStoredConnection? = loadWithRevision()?.connection

    @Synchronized
    internal fun loadWithRevision(): CloudflareVersionedConnection? {
        val envelope = store.read() ?: return null
        val associatedData = ASSOCIATED_DATA.toByteArray(StandardCharsets.UTF_8)
        var plaintext: ByteArray? = null
        return try {
            val revision = CloudflareRecordRevision.from(envelope)
            val sealedPayload = AccountEnvelopeCodec.decode(envelope)
            plaintext = cipher.decrypt(sealedPayload, associatedData)
            CloudflareVersionedConnection(
                connection = CloudflareConnectionPayloadCodec.decode(plaintext),
                revision = revision,
            )
        } finally {
            envelope.fill(0)
            associatedData.fill(0)
            plaintext?.fill(0)
        }
    }

    @Synchronized
    fun save(connection: CloudflareStoredConnection) {
        check(pendingCommit == null) { "A Cloudflare connection replacement is already pending." }
        val envelope = encryptedEnvelope(connection)
        try {
            store.write(envelope)
        } finally {
            envelope.fill(0)
        }
    }

    @Synchronized
    internal fun saveWithRevision(connection: CloudflareStoredConnection): CloudflareRecordCommit {
        check(pendingCommit == null) { "A Cloudflare connection replacement is already pending." }
        var previousEnvelope = store.read()
        var replacementEnvelope: ByteArray? = null
        return try {
            replacementEnvelope = encryptedEnvelope(connection)
            store.write(replacementEnvelope)
            CloudflareRecordCommit(
                revision = CloudflareRecordRevision.from(replacementEnvelope),
                previousEnvelope = previousEnvelope,
            ).also { commit ->
                pendingCommit = commit
                previousEnvelope = null
            }
        } finally {
            replacementEnvelope?.fill(0)
            previousEnvelope?.fill(0)
        }
    }

    /** Compare-and-swap prevents stale refreshes from resurrecting or overwriting a record. */
    @Synchronized
    internal fun saveIfRevisionMatches(
        expectedRevision: CloudflareRecordRevision,
        connection: CloudflareStoredConnection,
    ): Boolean {
        if (pendingCommit != null) return false
        val currentEnvelope = store.read() ?: return false
        var replacementEnvelope: ByteArray? = null
        return try {
            if (!expectedRevision.matches(currentEnvelope)) return false
            replacementEnvelope = encryptedEnvelope(connection)
            store.write(replacementEnvelope)
            true
        } finally {
            currentEnvelope.fill(0)
            replacementEnvelope?.fill(0)
        }
    }

    @Synchronized
    internal fun accept(commit: CloudflareRecordCommit) {
        if (pendingCommit === commit) pendingCommit = null
        commit.accept()?.fill(0)
    }

    @Synchronized
    internal fun rollbackIfRevisionMatches(commit: CloudflareRecordCommit): Boolean {
        if (pendingCommit !== commit) return false
        val rollback = commit.claimRollback() ?: return false
        pendingCommit = null
        val previousEnvelope = rollback.bytes
        val currentEnvelope = store.read()
        return try {
            if (currentEnvelope == null || !commit.revision.matches(currentEnvelope)) {
                false
            } else {
                if (previousEnvelope == null) store.delete() else store.write(previousEnvelope)
                true
            }
        } finally {
            currentEnvelope?.fill(0)
            previousEnvelope?.fill(0)
        }
    }

    @Synchronized
    fun delete() {
        pendingCommit?.accept()?.fill(0)
        pendingCommit = null
        store.delete()
    }

    private fun encryptedEnvelope(connection: CloudflareStoredConnection): ByteArray {
        require(connection.account.providerId == CloudflareAccount.PROVIDER_ID)
        val plaintext = CloudflareConnectionPayloadCodec.encode(connection)
        val associatedData = ASSOCIATED_DATA.toByteArray(StandardCharsets.UTF_8)
        return try {
            AccountEnvelopeCodec.encode(cipher.encrypt(plaintext, associatedData))
        } finally {
            plaintext.fill(0)
            associatedData.fill(0)
        }
    }

    companion object {
        internal const val ASSOCIATED_DATA = "verceltics.account-envelope.v1:cloudflare-api-token"
        internal const val ACCOUNT_PATH = "accounts/cloudflare-api-token.account"
        internal const val KEY_ALIAS = "verceltics.account-storage.cloudflare.v1"

        fun create(context: Context): CloudflareConnectionRepository = CloudflareConnectionRepository(
            store = NoBackupAtomicFileStore(context, ACCOUNT_PATH),
            cipher = AndroidKeystoreAccountCipher(keyAlias = KEY_ALIAS),
        )
    }
}
