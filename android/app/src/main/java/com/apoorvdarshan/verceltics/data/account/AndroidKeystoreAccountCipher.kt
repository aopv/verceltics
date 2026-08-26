package com.apoorvdarshan.verceltics.data.account

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-256-GCM account encryption backed by a non-exportable AndroidKeyStore key. */
class AndroidKeystoreAccountCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : AccountCipher {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): SealedPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(associatedData)
        return SealedPayload(
            initializationVector = cipher.iv,
            ciphertext = cipher.doFinal(plaintext),
        )
    }

    override fun decrypt(payload: SealedPayload, associatedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val initializationVector = payload.initializationVector()
        val ciphertext = payload.ciphertext()
        return try {
            cipher.init(
                Cipher.DECRYPT_MODE,
                existingKey(),
                GCMParameterSpec(GCM_TAG_BITS, initializationVector),
            )
            cipher.updateAAD(associatedData)
            cipher.doFinal(ciphertext)
        } finally {
            initializationVector.fill(0)
            ciphertext.fill(0)
        }
    }

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        existingKeyOrNull()?.let { return it }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun existingKey(): SecretKey = existingKeyOrNull()
        ?: throw IllegalStateException("The account encryption key is unavailable.")

    private fun existingKeyOrNull(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_BITS = 128
        const val DEFAULT_KEY_ALIAS: String = "verceltics.account-storage.v1"
    }
}
