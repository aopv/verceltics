package com.apoorvdarshan.verceltics.data.account

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import java.security.GeneralSecurityException
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreAccountCipherTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store by lazy { NoBackupAtomicFileStore(context, TEST_ACCOUNT_PATH) }

    @Before
    fun prepareIsolatedStorage() {
        store.delete()
        deleteTestKey()
    }

    @After
    fun cleanIsolatedStorage() {
        store.delete()
        deleteTestKey()
    }

    @Test
    fun encryptedAccountSurvivesCipherRecreationWithoutPlaintextAtRest() {
        val tokenText = "instrumentation-token-that-must-never-appear-on-disk"
        val account = VercelAccount(
            id = "instrumented-account",
            displayName = "Instrumented Account",
            email = "instrumented@example.com",
            token = SecretValue.of(tokenText),
            createdAtMillis = 1_000L,
            updatedAtMillis = 2_000L,
        )
        VercelAccountRepository(store, AndroidKeystoreAccountCipher(TEST_KEY_ALIAS)).save(account)

        val encryptedRecord = checkNotNull(store.read())
        val tokenBytes = tokenText.encodeToByteArray()
        try {
            assertFalse(encryptedRecord.containsSubsequence(tokenBytes))
        } finally {
            encryptedRecord.fill(0)
            tokenBytes.fill(0)
        }

        val restored = checkNotNull(
            VercelAccountRepository(
                store,
                AndroidKeystoreAccountCipher(TEST_KEY_ALIAS),
            ).load(),
        )
        assertEquals(account.id, restored.id)
        assertEquals(account.displayName, restored.displayName)
        assertEquals(account.email, restored.email)
        assertEquals(account.token, restored.token)
        assertEquals(account.createdAtMillis, restored.createdAtMillis)
        assertEquals(account.updatedAtMillis, restored.updatedAtMillis)
        assertTrue(keyStore().containsAlias(TEST_KEY_ALIAS))
    }

    @Test
    fun tamperedCiphertextAndWrongAssociatedDataAreRejected() {
        val cipher = AndroidKeystoreAccountCipher(TEST_KEY_ALIAS)
        val plaintext = "sensitive-account-payload".encodeToByteArray()
        val associatedData = "verceltics.test.aad".encodeToByteArray()
        val wrongAssociatedData = "verceltics.test.other-aad".encodeToByteArray()
        val sealed = cipher.encrypt(plaintext, associatedData)
        val initializationVector = sealed.initializationVector()
        val ciphertext = sealed.ciphertext()
        val tamperedCiphertext = ciphertext.copyOf().also { bytes ->
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
        }

        try {
            assertThrows(GeneralSecurityException::class.java) {
                cipher.decrypt(
                    SealedPayload(initializationVector, tamperedCiphertext),
                    associatedData,
                )
            }
            assertThrows(GeneralSecurityException::class.java) {
                cipher.decrypt(sealed, wrongAssociatedData)
            }
            val decrypted = cipher.decrypt(sealed, associatedData)
            try {
                assertArrayEquals(plaintext, decrypted)
            } finally {
                decrypted.fill(0)
            }
        } finally {
            plaintext.fill(0)
            associatedData.fill(0)
            wrongAssociatedData.fill(0)
            initializationVector.fill(0)
            ciphertext.fill(0)
            tamperedCiphertext.fill(0)
        }
    }

    private fun deleteTestKey() {
        keyStore().let { store ->
            if (store.containsAlias(TEST_KEY_ALIAS)) store.deleteEntry(TEST_KEY_ALIAS)
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun ByteArray.containsSubsequence(candidate: ByteArray): Boolean {
        if (candidate.isEmpty() || candidate.size > size) return false
        return (0..size - candidate.size).any { start ->
            candidate.indices.all { offset -> this[start + offset] == candidate[offset] }
        }
    }

    private companion object {
        const val TEST_KEY_ALIAS = "verceltics.test.account-storage.instrumented.v1"
        const val TEST_ACCOUNT_PATH = "accounts/tests/android-keystore-instrumented.account"
    }
}
