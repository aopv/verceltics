package com.apoorvdarshan.verceltics.data.pagespeed

import android.content.Context
import com.apoorvdarshan.verceltics.data.account.AccountCipher
import com.apoorvdarshan.verceltics.data.account.AccountEnvelopeCodec
import com.apoorvdarshan.verceltics.data.account.AndroidKeystoreAccountCipher
import com.apoorvdarshan.verceltics.data.account.AtomicBytesStore
import com.apoorvdarshan.verceltics.data.account.NoBackupAtomicFileStore
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** In-memory identity for one encrypted record version. It never changes the on-disk envelope. */
internal class PageSpeedRecordRevision private constructor(
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

    override fun toString(): String = "PageSpeedRecordRevision(<redacted>)"

    companion object {
        private const val DIGEST_ALGORITHM = "SHA-256"

        fun from(envelope: ByteArray): PageSpeedRecordRevision = PageSpeedRecordRevision(
            MessageDigest.getInstance(DIGEST_ALGORITHM).digest(envelope),
        )
    }
}

/**
 * Rollback material for one encrypted replacement. The previous envelope remains encrypted and is
 * released as soon as the connect is accepted or compensated.
 */
internal class PageSpeedRecordCommit(
    val revision: PageSpeedRecordRevision,
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
    fun claimRollback(): PageSpeedRollbackEnvelope? {
        if (state != State.PENDING) return null
        state = State.ROLLBACK_CLAIMED
        return PageSpeedRollbackEnvelope(rollbackEnvelope).also { rollbackEnvelope = null }
    }

    override fun toString(): String = "PageSpeedRecordCommit(<redacted>)"

    private enum class State {
        PENDING,
        ACCEPTED,
        ROLLBACK_CLAIMED,
    }
}

internal class PageSpeedRollbackEnvelope(val bytes: ByteArray?) {
    override fun toString(): String = "PageSpeedRollbackEnvelope(<redacted>)"
}

/**
 * Atomic encrypted PageSpeed storage in a provider-specific no-backup slot.
 *
 * It deliberately has a different file and authenticated associated-data value from Vercel. A
 * corrupt/unreadable record is surfaced and is never treated as an absent account or deleted.
 */
class PageSpeedConnectionRepository(
    private val store: AtomicBytesStore,
    private val cipher: AccountCipher,
) {
    @Synchronized
    fun load(): PageSpeedStoredConnection? {
        val envelopeBytes = store.read() ?: return null
        val associatedData = ASSOCIATED_DATA.toByteArray(StandardCharsets.UTF_8)
        var plaintext: ByteArray? = null
        return try {
            val sealedPayload = AccountEnvelopeCodec.decode(envelopeBytes)
            plaintext = cipher.decrypt(sealedPayload, associatedData)
            PageSpeedConnectionPayloadCodec.decode(plaintext)
        } finally {
            envelopeBytes.fill(0)
            associatedData.fill(0)
            plaintext?.fill(0)
        }
    }

    @Synchronized
    fun save(connection: PageSpeedStoredConnection) {
        accept(saveWithRevision(connection))
    }

    @Synchronized
    internal fun saveWithRevision(connection: PageSpeedStoredConnection): PageSpeedRecordCommit {
        var previousEnvelope = store.read()
        val associatedData = ASSOCIATED_DATA.toByteArray(StandardCharsets.UTF_8)
        var plaintext: ByteArray? = null
        var envelope: ByteArray? = null
        return try {
            val encoded = PageSpeedConnectionPayloadCodec.encode(connection)
            plaintext = encoded
            envelope = AccountEnvelopeCodec.encode(cipher.encrypt(encoded, associatedData))
            store.write(envelope)
            PageSpeedRecordCommit(
                revision = PageSpeedRecordRevision.from(envelope),
                previousEnvelope = previousEnvelope,
            ).also {
                previousEnvelope = null
            }
        } finally {
            plaintext?.fill(0)
            associatedData.fill(0)
            envelope?.fill(0)
            previousEnvelope?.fill(0)
        }
    }

    /** Releases the encrypted previous record once the replacement is accepted. */
    @Synchronized
    internal fun accept(commit: PageSpeedRecordCommit) {
        commit.accept()?.fill(0)
    }

    /** Restores the previous envelope only if the cancelled replacement is still current. */
    @Synchronized
    internal fun rollbackIfRevisionMatches(commit: PageSpeedRecordCommit): Boolean {
        val rollback = commit.claimRollback() ?: return false
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

    /** Only an explicit user disconnect flow should call this. */
    @Synchronized
    fun delete() = store.delete()

    companion object {
        internal const val ASSOCIATED_DATA = "verceltics.account-envelope.v1:pagespeed-crux"
        internal const val ACCOUNT_PATH = "accounts/pagespeed-crux-api-key.account"

        fun create(context: Context): PageSpeedConnectionRepository =
            PageSpeedConnectionRepository(
                store = NoBackupAtomicFileStore(context, ACCOUNT_PATH),
                // Reuse the existing non-exportable key without changing its alias or Vercel data.
                cipher = AndroidKeystoreAccountCipher(),
            )
    }
}
