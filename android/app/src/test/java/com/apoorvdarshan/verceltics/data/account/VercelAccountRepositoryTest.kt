package com.apoorvdarshan.verceltics.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.charset.StandardCharsets

class VercelAccountRepositoryTest {
    @Test
    fun savesEncryptedEnvelopeAndLoadsAccount() {
        val store = MemoryAtomicBytesStore()
        val repository = VercelAccountRepository(store, TestAccountCipher())
        val token = "sensitive-vercel-token"
        val account = testAccount(token)

        repository.save(account)

        val stored = checkNotNull(store.bytes)
        assertFalse(String(stored, StandardCharsets.UTF_8).contains(token))
        val loaded = checkNotNull(repository.load())
        assertEquals(account.id, loaded.id)
        assertEquals(account.token, loaded.token)
        assertEquals("vercel", loaded.providerId)
    }

    @Test
    fun missingAccountReturnsNullAndDeleteIsExplicit() {
        val store = MemoryAtomicBytesStore()
        val repository = VercelAccountRepository(store, TestAccountCipher())

        assertNull(repository.load())
        repository.save(testAccount("token"))
        repository.delete()
        assertNull(repository.load())
    }

    @Test
    fun corruptedDataIsSurfacedWithoutDeletion() {
        val store = MemoryAtomicBytesStore().apply { bytes = byteArrayOf(1, 2, 3) }
        val repository = VercelAccountRepository(store, TestAccountCipher())

        assertThrows(IllegalArgumentException::class.java) { repository.load() }
        assertEquals(3, store.bytes?.size)
    }

    private fun testAccount(token: String) = VercelAccount(
        id = "user_123",
        displayName = "Apoorv",
        email = "apoorv@example.com",
        token = SecretValue.of(token),
        createdAtMillis = 10L,
        updatedAtMillis = 11L,
    )

    private class MemoryAtomicBytesStore : AtomicBytesStore {
        var bytes: ByteArray? = null

        override fun read(): ByteArray? = bytes?.copyOf()

        override fun write(bytes: ByteArray) {
            this.bytes = bytes.copyOf()
        }

        override fun delete() {
            bytes = null
        }
    }

    /** Test-only reversible cipher; production always constructs AndroidKeystoreAccountCipher. */
    private class TestAccountCipher : AccountCipher {
        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): SealedPayload =
            SealedPayload(ByteArray(12) { 7 }, transform(plaintext, associatedData))

        override fun decrypt(payload: SealedPayload, associatedData: ByteArray): ByteArray =
            transform(payload.ciphertext(), associatedData)

        private fun transform(input: ByteArray, associatedData: ByteArray): ByteArray =
            ByteArray(input.size) { index ->
                (input[index].toInt() xor associatedData[index % associatedData.size].toInt()).toByte()
            }
    }
}
