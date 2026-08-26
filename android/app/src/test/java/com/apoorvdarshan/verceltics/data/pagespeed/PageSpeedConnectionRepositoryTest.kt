package com.apoorvdarshan.verceltics.data.pagespeed

import com.apoorvdarshan.verceltics.data.account.AccountCipher
import com.apoorvdarshan.verceltics.data.account.AtomicBytesStore
import com.apoorvdarshan.verceltics.data.account.SealedPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class PageSpeedConnectionRepositoryTest {
    @Test
    fun encryptedProviderRecordRoundTripsCredentialAndOfflineSnapshot() {
        val store = MemoryAtomicBytesStore()
        val repository = PageSpeedConnectionRepository(store, TestAccountCipher())
        val connection = testConnection(apiKey = "sensitive-google-key")

        repository.save(connection)

        val encrypted = checkNotNull(store.bytes)
        assertFalse(String(encrypted, StandardCharsets.UTF_8).contains("sensitive-google-key"))
        val loaded = checkNotNull(repository.load())
        assertEquals(connection.id, loaded.id)
        assertEquals(connection.credentials.siteUrl, loaded.credentials.siteUrl)
        assertEquals(connection.credentials.apiKey, loaded.credentials.apiKey)
        assertEquals(connection.cachedSnapshot, loaded.cachedSnapshot)
        assertFalse(loaded.toString().contains("sensitive-google-key"))
    }

    @Test
    fun recordUsesProviderSpecificSlotAndAuthenticatedContext() {
        assertEquals("accounts/pagespeed-crux-api-key.account", PageSpeedConnectionRepository.ACCOUNT_PATH)
        assertEquals(
            "verceltics.account-envelope.v1:pagespeed-crux",
            PageSpeedConnectionRepository.ASSOCIATED_DATA,
        )
        assertFalse(PageSpeedConnectionRepository.ACCOUNT_PATH.contains("vercel-personal-token"))
        assertFalse(PageSpeedConnectionRepository.ASSOCIATED_DATA.endsWith(":vercel"))
    }

    @Test
    fun missingReturnsNullAndCorruptionIsSurfacedWithoutDeletion() {
        val store = MemoryAtomicBytesStore()
        val repository = PageSpeedConnectionRepository(store, TestAccountCipher())
        assertNull(repository.load())

        store.bytes = byteArrayOf(1, 2, 3)
        assertThrows(IllegalArgumentException::class.java) { repository.load() }
        assertEquals(3, store.bytes?.size)
    }

    @Test
    fun restoreIsOfflineAndKeepsLastSnapshotWhenRefreshFails() {
        val store = MemoryAtomicBytesStore()
        val repository = PageSpeedConnectionRepository(store, TestAccountCipher())
        val original = testConnection(apiKey = "key")
        repository.save(original)
        val connectionStore = PageSpeedConnectionStore(repository, nowMillis = { 2_000_000L })

        val restored = connectionStore.restore() as PageSpeedRestoreResult.Restored
        assertEquals(original.id, restored.connectionId)
        assertEquals(original.cachedSnapshot, restored.cachedSnapshot)
        assertTrue(restored.cacheIsStale)

        val persisted = connectionStore.persistRefreshResult(
            PageSpeedFetchResult.Failure(
                PageSpeedFailure(PageSpeedFailureKind.NETWORK, "Provider unavailable."),
            ),
        )
        assertFalse(persisted)
        assertEquals(original.cachedSnapshot, repository.load()?.cachedSnapshot)
    }

    @Test
    fun unreadableSavedRecordIsNotMisreportedAsDisconnected() {
        val store = MemoryAtomicBytesStore().apply { bytes = byteArrayOf(9, 8, 7) }
        val connectionStore = PageSpeedConnectionStore(
            PageSpeedConnectionRepository(store, TestAccountCipher()),
        )

        assertEquals(
            PageSpeedRestoreResult.Unavailable(PageSpeedRestoreProblem.SAVED_RECORD_UNREADABLE),
            connectionStore.restore(),
        )
        assertEquals(3, store.bytes?.size)
    }

    @Test
    fun cancelledCommitRollbackCannotDeleteANewerEncryptedRevision() {
        val repository = PageSpeedConnectionRepository(MemoryAtomicBytesStore(), TestAccountCipher())
        val connectionStore = PageSpeedConnectionStore(repository, nowMillis = { 1_000L })
        val first = testConnection(apiKey = "first-key")
        val second = testConnection(apiKey = "second-key")

        val firstCommit = connectionStore.saveValidatedConnection(
            first.credentials,
            PageSpeedFetchResult.Complete(checkNotNull(first.cachedSnapshot)),
        )
        connectionStore.acceptValidatedConnection(firstCommit)
        val secondCommit = connectionStore.saveValidatedConnection(
            second.credentials,
            PageSpeedFetchResult.Complete(checkNotNull(second.cachedSnapshot)),
        )

        assertFalse(connectionStore.rollbackValidatedConnection(firstCommit))
        assertEquals(second.credentials.apiKey, repository.load()?.credentials?.apiKey)
        assertTrue(connectionStore.rollbackValidatedConnection(secondCommit))
        assertEquals(first.credentials.apiKey, repository.load()?.credentials?.apiKey)
    }

    @Test
    fun acceptedCommitMakesALateCancellationRollbackANoOp() {
        val repository = PageSpeedConnectionRepository(MemoryAtomicBytesStore(), TestAccountCipher())
        val connectionStore = PageSpeedConnectionStore(repository, nowMillis = { 1_000L })
        val connection = testConnection(apiKey = "accepted-key")
        val commit = connectionStore.saveValidatedConnection(
            connection.credentials,
            PageSpeedFetchResult.Complete(checkNotNull(connection.cachedSnapshot)),
        )

        connectionStore.acceptValidatedConnection(commit)

        assertFalse(connectionStore.rollbackValidatedConnection(commit))
        assertEquals(connection.credentials.apiKey, repository.load()?.credentials?.apiKey)
        assertEquals(connection.cachedSnapshot, repository.load()?.cachedSnapshot)
    }

    private fun testConnection(apiKey: String): PageSpeedStoredConnection {
        val credentials = PageSpeedCredentials.create(apiKey, "https://example.com/path")
        val snapshot = PageSpeedSnapshot(
            siteUrl = credentials.siteUrl,
            siteName = "example.com",
            status = "Good",
            metrics = listOf(
                PageSpeedMetric(
                    key = "pagespeed.mobile.performance",
                    label = "Mobile Performance",
                    value = 96.0,
                    unit = PageSpeedMetricUnit.SCORE,
                    formattedValue = "96",
                ),
            ),
            fetchedAtMillis = 100L,
            availability = PageSpeedSourceAvailability(
                desktop = PageSpeedSourceState.UNAVAILABLE,
                crux = PageSpeedSourceState.AVAILABLE,
            ),
            warnings = listOf("Desktop PageSpeed data is unavailable: temporary."),
        )
        return PageSpeedStoredConnection(
            id = "pagespeed_123",
            credentials = credentials,
            createdAtMillis = 10L,
            updatedAtMillis = 100L,
            cachedSnapshot = snapshot,
        )
    }

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

    private class TestAccountCipher : AccountCipher {
        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): SealedPayload =
            SealedPayload(ByteArray(12) { 4 }, transform(plaintext, associatedData))

        override fun decrypt(payload: SealedPayload, associatedData: ByteArray): ByteArray =
            transform(payload.ciphertext(), associatedData)

        private fun transform(input: ByteArray, associatedData: ByteArray): ByteArray =
            ByteArray(input.size) { index ->
                (input[index].toInt() xor associatedData[index % associatedData.size].toInt()).toByte()
            }
    }
}
