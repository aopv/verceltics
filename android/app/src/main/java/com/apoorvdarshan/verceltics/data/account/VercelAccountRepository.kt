package com.apoorvdarshan.verceltics.data.account

import android.content.Context
import java.nio.charset.StandardCharsets

/**
 * Atomic encrypted persistence for the Vercel account slot.
 *
 * A read or decryption failure is surfaced and never silently replaced or deleted.
 */
class VercelAccountRepository(
    private val store: AtomicBytesStore,
    private val cipher: AccountCipher,
) {
    @Synchronized
    fun load(): VercelAccount? {
        val envelopeBytes = store.read() ?: return null
        val associatedData = ASSOCIATED_DATA.toByteArray(StandardCharsets.UTF_8)
        var plaintext: ByteArray? = null
        return try {
            val sealedPayload = AccountEnvelopeCodec.decode(envelopeBytes)
            plaintext = cipher.decrypt(sealedPayload, associatedData)
            VercelAccountPayloadCodec.decode(plaintext)
        } finally {
            envelopeBytes.fill(0)
            associatedData.fill(0)
            plaintext?.fill(0)
        }
    }

    @Synchronized
    fun save(account: VercelAccount) {
        require(account.providerId == VercelAccount.PROVIDER_ID) { "Wrong account provider." }
        val plaintext = VercelAccountPayloadCodec.encode(account)
        val associatedData = ASSOCIATED_DATA.toByteArray(StandardCharsets.UTF_8)
        var envelope: ByteArray? = null
        try {
            envelope = AccountEnvelopeCodec.encode(cipher.encrypt(plaintext, associatedData))
            store.write(envelope)
        } finally {
            plaintext.fill(0)
            associatedData.fill(0)
            envelope?.fill(0)
        }
    }

    /** Call only after an explicit user disconnect action. */
    @Synchronized
    fun delete() = store.delete()

    companion object {
        private const val ASSOCIATED_DATA = "verceltics.account-envelope.v1:vercel"
        private const val ACCOUNT_PATH = "accounts/vercel-personal-token.account"

        fun create(context: Context): VercelAccountRepository = VercelAccountRepository(
            store = NoBackupAtomicFileStore(context, ACCOUNT_PATH),
            cipher = AndroidKeystoreAccountCipher(),
        )
    }
}
