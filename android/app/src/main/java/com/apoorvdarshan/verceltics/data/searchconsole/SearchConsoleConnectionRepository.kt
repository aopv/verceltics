package com.apoorvdarshan.verceltics.data.searchconsole

import android.content.Context
import com.apoorvdarshan.verceltics.data.account.AccountCipher
import com.apoorvdarshan.verceltics.data.account.AccountEnvelopeCodec
import com.apoorvdarshan.verceltics.data.account.AndroidKeystoreAccountCipher
import com.apoorvdarshan.verceltics.data.account.AtomicBytesStore
import com.apoorvdarshan.verceltics.data.account.NoBackupAtomicFileStore
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal class SearchConsoleRecordRevision private constructor(private val digest: ByteArray) {
    fun matches(envelope: ByteArray): Boolean {
        val candidate = MessageDigest.getInstance("SHA-256").digest(envelope)
        return try {
            MessageDigest.isEqual(digest, candidate)
        } finally {
            candidate.fill(0)
        }
    }

    override fun toString(): String = "SearchConsoleRecordRevision(<redacted>)"

    companion object {
        fun from(envelope: ByteArray) = SearchConsoleRecordRevision(
            MessageDigest.getInstance("SHA-256").digest(envelope),
        )
    }
}

internal class SearchConsoleRecordCommit(
    val revision: SearchConsoleRecordRevision,
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
    fun claimRollback(): SearchConsoleRollbackEnvelope? {
        if (state != State.PENDING) return null
        state = State.ROLLBACK_CLAIMED
        return SearchConsoleRollbackEnvelope(rollbackEnvelope).also { rollbackEnvelope = null }
    }

    override fun toString(): String = "SearchConsoleRecordCommit(<redacted>)"

    private enum class State { PENDING, ACCEPTED, ROLLBACK_CLAIMED }
}

internal class SearchConsoleRollbackEnvelope(val bytes: ByteArray?) {
    override fun toString(): String = "SearchConsoleRollbackEnvelope(<redacted>)"
}

internal data class SearchConsoleVersionedConnection(
    val connection: SearchConsoleStoredConnection,
    val revision: SearchConsoleRecordRevision,
)

/** Atomic encrypted Google Search Console storage in a provider-specific no-backup slot. */
class SearchConsoleConnectionRepository(
    private val store: AtomicBytesStore,
    private val cipher: AccountCipher,
) {
    private var pendingCommit: SearchConsoleRecordCommit? = null

    @Synchronized
    fun load(): SearchConsoleStoredConnection? = loadWithRevision()?.connection

    @Synchronized
    internal fun loadWithRevision(): SearchConsoleVersionedConnection? {
        val envelope = store.read() ?: return null
        val aad = ASSOCIATED_DATA.toByteArray(StandardCharsets.UTF_8)
        var plaintext: ByteArray? = null
        return try {
            val revision = SearchConsoleRecordRevision.from(envelope)
            plaintext = cipher.decrypt(AccountEnvelopeCodec.decode(envelope), aad)
            SearchConsoleVersionedConnection(
                SearchConsoleConnectionPayloadCodec.decode(plaintext),
                revision,
            )
        } finally {
            envelope.fill(0)
            aad.fill(0)
            plaintext?.fill(0)
        }
    }

    @Synchronized
    fun save(connection: SearchConsoleStoredConnection) {
        check(pendingCommit == null) { "A Search Console replacement is already pending." }
        val envelope = encryptedEnvelope(connection)
        try {
            store.write(envelope)
        } finally {
            envelope.fill(0)
        }
    }

    @Synchronized
    internal fun saveWithRevision(connection: SearchConsoleStoredConnection): SearchConsoleRecordCommit {
        check(pendingCommit == null) { "A Search Console replacement is already pending." }
        var previousEnvelope = store.read()
        var envelope: ByteArray? = null
        return try {
            envelope = encryptedEnvelope(connection)
            store.write(envelope)
            SearchConsoleRecordCommit(
                SearchConsoleRecordRevision.from(envelope),
                previousEnvelope,
            ).also {
                pendingCommit = it
                previousEnvelope = null
            }
        } finally {
            envelope?.fill(0)
            previousEnvelope?.fill(0)
        }
    }

    @Synchronized
    internal fun saveIfRevisionMatches(
        expected: SearchConsoleRecordRevision,
        connection: SearchConsoleStoredConnection,
    ): Boolean {
        if (pendingCommit != null) return false
        val current = store.read() ?: return false
        var replacement: ByteArray? = null
        return try {
            if (!expected.matches(current)) return false
            replacement = encryptedEnvelope(connection)
            store.write(replacement)
            true
        } finally {
            current.fill(0)
            replacement?.fill(0)
        }
    }

    @Synchronized
    internal fun accept(commit: SearchConsoleRecordCommit) {
        if (pendingCommit === commit) pendingCommit = null
        commit.accept()?.fill(0)
    }

    @Synchronized
    internal fun rollbackIfRevisionMatches(commit: SearchConsoleRecordCommit): Boolean {
        if (pendingCommit !== commit) return false
        val rollback = commit.claimRollback() ?: return false
        pendingCommit = null
        val previous = rollback.bytes
        val current = store.read()
        return try {
            if (current == null || !commit.revision.matches(current)) {
                false
            } else {
                if (previous == null) store.delete() else store.write(previous)
                true
            }
        } finally {
            current?.fill(0)
            previous?.fill(0)
        }
    }

    @Synchronized
    fun delete() {
        pendingCommit?.accept()?.fill(0)
        pendingCommit = null
        store.delete()
    }

    private fun encryptedEnvelope(connection: SearchConsoleStoredConnection): ByteArray {
        val plaintext = SearchConsoleConnectionPayloadCodec.encode(connection)
        val aad = ASSOCIATED_DATA.toByteArray(StandardCharsets.UTF_8)
        return try {
            AccountEnvelopeCodec.encode(cipher.encrypt(plaintext, aad))
        } finally {
            plaintext.fill(0)
            aad.fill(0)
        }
    }

    companion object {
        internal const val ASSOCIATED_DATA =
            "verceltics.account-envelope.v1:google-search-console-oauth"
        internal const val ACCOUNT_PATH = "accounts/google-search-console-oauth.account"
        internal const val KEY_ALIAS = "verceltics.account-storage.google-search-console.v1"

        fun create(context: Context) = SearchConsoleConnectionRepository(
            NoBackupAtomicFileStore(context, ACCOUNT_PATH),
            AndroidKeystoreAccountCipher(keyAlias = KEY_ALIAS),
        )
    }
}
