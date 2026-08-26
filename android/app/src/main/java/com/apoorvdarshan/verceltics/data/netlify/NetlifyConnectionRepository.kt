package com.apoorvdarshan.verceltics.data.netlify

import android.content.Context
import com.apoorvdarshan.verceltics.data.account.AccountCipher
import com.apoorvdarshan.verceltics.data.account.AccountEnvelopeCodec
import com.apoorvdarshan.verceltics.data.account.AndroidKeystoreAccountCipher
import com.apoorvdarshan.verceltics.data.account.AtomicBytesStore
import com.apoorvdarshan.verceltics.data.account.NoBackupAtomicFileStore
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** In-memory identity for one encrypted Netlify record revision. */
internal class NetlifyRecordRevision private constructor(
    private val digest: ByteArray,
) {
    fun matches(envelope: ByteArray): Boolean {
        val candidate = MessageDigest.getInstance(DIGEST_ALGORITHM).digest(envelope)
        return try {
            MessageDigest.isEqual(digest, candidate)
        } finally {
            candidate.fill(0)
        }
    }

    override fun toString(): String = "NetlifyRecordRevision(<redacted>)"

    companion object {
        private const val DIGEST_ALGORITHM = "SHA-256"

        fun from(envelope: ByteArray): NetlifyRecordRevision = NetlifyRecordRevision(
            MessageDigest.getInstance(DIGEST_ALGORITHM).digest(envelope),
        )
    }
}

/** Opaque encrypted rollback state for one pending Netlify replacement. */
internal class NetlifyRecordCommit(
    val revision: NetlifyRecordRevision,
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
    fun claimRollback(): NetlifyRollbackEnvelope? {
        if (state != State.PENDING) return null
        state = State.ROLLBACK_CLAIMED
        return NetlifyRollbackEnvelope(rollbackEnvelope).also { rollbackEnvelope = null }
    }

    override fun toString(): String = "NetlifyRecordCommit(<redacted>)"

    private enum class State {
        PENDING,
        ACCEPTED,
        ROLLBACK_CLAIMED,
    }
}

internal class NetlifyRollbackEnvelope(val bytes: ByteArray?) {
    override fun toString(): String = "NetlifyRollbackEnvelope(<redacted>)"
}

internal data class NetlifyVersionedConnection(
    val connection: NetlifyStoredConnection,
    val revision: NetlifyRecordRevision,
)

/** Atomic encrypted Netlify storage in its own no-backup path, key alias, and AAD domain. */
class NetlifyConnectionRepository(
    private val store: AtomicBytesStore,
    private val cipher: AccountCipher,
) {
    private var pendingCommit: NetlifyRecordCommit? = null

    @Synchronized
    fun load(): NetlifyStoredConnection? = loadWithRevision()?.connection

    @Synchronized
    internal fun loadWithRevision(): NetlifyVersionedConnection? {
        val envelopeBytes = store.read() ?: return null
        val associatedData = ASSOCIATED_DATA.toByteArray(StandardCharsets.UTF_8)
        var plaintext: ByteArray? = null
        return try {
            val revision = NetlifyRecordRevision.from(envelopeBytes)
            val sealedPayload = AccountEnvelopeCodec.decode(envelopeBytes)
            plaintext = cipher.decrypt(sealedPayload, associatedData)
            NetlifyVersionedConnection(
                connection = NetlifyConnectionPayloadCodec.decode(plaintext),
                revision = revision,
            )
        } finally {
            envelopeBytes.fill(0)
            associatedData.fill(0)
            plaintext?.fill(0)
        }
    }

    @Synchronized
    fun save(connection: NetlifyStoredConnection) {
        check(pendingCommit == null) { "A Netlify connection replacement is already pending." }
        val envelope = encryptedEnvelope(connection)
        try {
            store.write(envelope)
        } finally {
            envelope.fill(0)
        }
    }

    @Synchronized
    internal fun saveWithRevision(connection: NetlifyStoredConnection): NetlifyRecordCommit {
        check(pendingCommit == null) { "A Netlify connection replacement is already pending." }
        require(connection.account.providerId == NetlifyAccount.PROVIDER_ID) {
            "Wrong account provider for the Netlify storage slot."
        }
        var previousEnvelope = store.read()
        var envelope: ByteArray? = null
        return try {
            envelope = encryptedEnvelope(connection)
            store.write(envelope)
            NetlifyRecordCommit(
                revision = NetlifyRecordRevision.from(envelope),
                previousEnvelope = previousEnvelope,
            ).also { commit ->
                pendingCommit = commit
                previousEnvelope = null
            }
        } finally {
            envelope?.fill(0)
            previousEnvelope?.fill(0)
        }
    }

    /** Compare-and-swap used by refreshes so stale work cannot resurrect or replace a new record. */
    @Synchronized
    internal fun saveIfRevisionMatches(
        expectedRevision: NetlifyRecordRevision,
        connection: NetlifyStoredConnection,
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

    /** Releases the prior encrypted record once the caller accepts the replacement. */
    @Synchronized
    internal fun accept(commit: NetlifyRecordCommit) {
        if (pendingCommit === commit) pendingCommit = null
        commit.accept()?.fill(0)
    }

    /** Restores the prior envelope only while this exact replacement remains current. */
    @Synchronized
    internal fun rollbackIfRevisionMatches(commit: NetlifyRecordCommit): Boolean {
        if (pendingCommit !== commit) return false
        val rollback = commit.claimRollback() ?: return false
        pendingCommit = null
        val previousEnvelope = rollback.bytes
        val currentEnvelope = store.read()
        return try {
            if (currentEnvelope == null || !commit.revision.matches(currentEnvelope)) {
                false
            } else {
                if (previousEnvelope == null) {
                    store.delete()
                } else {
                    store.write(previousEnvelope)
                }
                true
            }
        } finally {
            currentEnvelope?.fill(0)
            previousEnvelope?.fill(0)
        }
    }

    /** Only an explicit user disconnect flow should erase this slot. */
    @Synchronized
    fun delete() {
        pendingCommit?.accept()?.fill(0)
        pendingCommit = null
        store.delete()
    }

    private fun encryptedEnvelope(connection: NetlifyStoredConnection): ByteArray {
        require(connection.account.providerId == NetlifyAccount.PROVIDER_ID) {
            "Wrong account provider for the Netlify storage slot."
        }
        val plaintext = NetlifyConnectionPayloadCodec.encode(connection)
        val associatedData = ASSOCIATED_DATA.toByteArray(StandardCharsets.UTF_8)
        return try {
            AccountEnvelopeCodec.encode(cipher.encrypt(plaintext, associatedData))
        } finally {
            plaintext.fill(0)
            associatedData.fill(0)
        }
    }

    companion object {
        internal const val ASSOCIATED_DATA = "verceltics.account-envelope.v1:netlify-personal-token"
        internal const val ACCOUNT_PATH = "accounts/netlify-personal-token.account"
        internal const val KEY_ALIAS = "verceltics.account-storage.netlify.v1"

        fun create(context: Context): NetlifyConnectionRepository = NetlifyConnectionRepository(
            store = NoBackupAtomicFileStore(context, ACCOUNT_PATH),
            cipher = AndroidKeystoreAccountCipher(keyAlias = KEY_ALIAS),
        )
    }
}
