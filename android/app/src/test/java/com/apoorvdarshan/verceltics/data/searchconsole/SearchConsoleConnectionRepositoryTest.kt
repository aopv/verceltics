package com.apoorvdarshan.verceltics.data.searchconsole

import com.apoorvdarshan.verceltics.data.account.AccountCipher
import com.apoorvdarshan.verceltics.data.account.AtomicBytesStore
import com.apoorvdarshan.verceltics.data.account.SealedPayload
import com.apoorvdarshan.verceltics.data.account.SecretValue
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class SearchConsoleConnectionRepositoryTest {
    @Test
    fun encryptedCredentialAndBoundedOfflineInventoryRoundTripWithoutPrintingTokens() {
        val store = MemoryStore()
        val repository = SearchConsoleConnectionRepository(store, TestCipher())
        val connection = connection("access-secret", "refresh-secret", snapshot())

        repository.save(connection)

        val envelope = checkNotNull(store.bytes)
        val rendered = String(envelope, StandardCharsets.UTF_8)
        assertFalse(rendered.contains("access-secret"))
        assertFalse(rendered.contains("refresh-secret"))
        val loaded = checkNotNull(repository.load())
        assertEquals(SecretValue.of("access-secret"), loaded.credential.accessToken)
        assertEquals(SecretValue.of("refresh-secret"), loaded.credential.refreshToken)
        assertEquals(connection.cachedSnapshot, loaded.cachedSnapshot)
        assertFalse(loaded.toString().contains("access-secret"))
        assertFalse(loaded.credential.toString().contains("refresh-secret"))
    }

    @Test
    fun providerUsesDedicatedNoBackupPathKeyAndAad() {
        assertEquals(
            "accounts/google-search-console-oauth.account",
            SearchConsoleConnectionRepository.ACCOUNT_PATH,
        )
        assertEquals(
            "verceltics.account-envelope.v1:google-search-console-oauth",
            SearchConsoleConnectionRepository.ASSOCIATED_DATA,
        )
        assertEquals(
            "verceltics.account-storage.google-search-console.v1",
            SearchConsoleConnectionRepository.KEY_ALIAS,
        )
        assertFalse(SearchConsoleConnectionRepository.ACCOUNT_PATH.contains("vercel-personal"))
    }

    @Test
    fun cancelledEmptySaveDeletesOnlyItsRevisionAndCancelledReplacementRestoresExactEnvelope() {
        val store = MemoryStore()
        val repository = SearchConsoleConnectionRepository(store, TestCipher())
        val emptyCommit = repository.saveWithRevision(connection("a1", "r1", snapshot()))
        assertTrue(repository.rollbackIfRevisionMatches(emptyCommit))
        assertNull(store.bytes)

        repository.save(connection("a2", "r2", snapshot(1)))
        val prior = checkNotNull(store.bytes).copyOf()
        val replacement = repository.saveWithRevision(connection("a3", "r3", snapshot(2)))
        assertTrue(repository.rollbackIfRevisionMatches(replacement))
        assertArrayEquals(prior, store.bytes)
        assertEquals(SecretValue.of("a2"), repository.load()?.credential?.accessToken)
        prior.fill(0)
    }

    @Test
    fun acceptedSaveCannotBeRolledBackAndStaleCasCannotOverwriteNewerRecord() {
        val repository = SearchConsoleConnectionRepository(MemoryStore(), TestCipher())
        repository.save(connection("base", "refresh", snapshot()))
        val stale = checkNotNull(repository.loadWithRevision())
        val commit = repository.saveWithRevision(connection("accepted", "refresh", snapshot(2)))
        repository.accept(commit)

        assertFalse(repository.rollbackIfRevisionMatches(commit))
        assertFalse(
            repository.saveIfRevisionMatches(
                stale.revision,
                connection("stale", "refresh", snapshot(3)),
            ),
        )
        assertEquals(SecretValue.of("accepted"), repository.load()?.credential?.accessToken)
    }

    @Test
    fun connectionStoreBoundsCacheAndNeverReplacesCompleteCacheWithPartialData() {
        val repository = SearchConsoleConnectionRepository(MemoryStore(), TestCipher())
        val store = SearchConsoleConnectionStore(repository, nowMillis = { 500L })
        val full = snapshot(SearchConsoleConnectionStore.MAX_CACHED_PROPERTIES + 3)
        val commit = store.saveValidatedConnection(
            credential("access", "refresh"),
            SearchConsoleFetchResult.Complete(full),
        )
        store.acceptValidatedConnection(commit)
        val cached = checkNotNull(repository.load()?.cachedSnapshot)
        assertEquals(SearchConsoleConnectionStore.MAX_CACHED_PROPERTIES, cached.properties.size)
        assertFalse(cached.propertiesComplete)
        assertTrue(cached.warnings.single().contains("bounded"))

        val complete = snapshot(2)
        repository.save(connection("access", "refresh", complete))
        val partial = SearchConsoleSnapshot(
            listOf(property(0)), 700L, false, listOf("Property list is incomplete."),
        )
        assertFalse(
            store.persistSnapshotRefresh(
                checkNotNull(store.loadForRefresh()),
                SearchConsoleFetchResult.Partial(partial, failure()),
            ),
        )
        assertEquals(complete, repository.load()?.cachedSnapshot)
    }

    @Test
    fun longPropertyIdentifierPersistsAndRestoresWithoutMutation() {
        val repository = SearchConsoleConnectionRepository(MemoryStore(), TestCipher())
        val store = SearchConsoleConnectionStore(repository, nowMillis = { 500L })
        val longSiteUrl = "https://example.com/" + "long-path-segment/".repeat(250)
        assertTrue(longSiteUrl.length > 2_048)
        val snapshot = SearchConsoleSnapshot(
            properties = listOf(SearchConsoleProperty(longSiteUrl, "siteOwner")),
            fetchedAtMillis = 400L,
            propertiesComplete = true,
            warnings = emptyList(),
        )

        val commit = store.saveValidatedConnection(
            credential("access", "refresh"),
            SearchConsoleFetchResult.Complete(snapshot),
        )
        store.acceptValidatedConnection(commit)

        val restored = store.restore() as SearchConsoleRestoreResult.Restored
        assertEquals(longSiteUrl, restored.cachedSnapshot?.properties?.single()?.siteUrl)
        assertTrue(checkNotNull(restored.cachedSnapshot).propertiesComplete)
    }

    @Test
    fun identifierTooLargeForEncryptedPayloadIsOmittedRatherThanRewritten() {
        val repository = SearchConsoleConnectionRepository(MemoryStore(), TestCipher())
        val store = SearchConsoleConnectionStore(repository, nowMillis = { 500L })
        val oversizedSiteUrl = "sc-domain:" + "€".repeat(6_000)
        assertTrue(oversizedSiteUrl.length <= MAX_URL_CHARACTERS)
        assertTrue(
            oversizedSiteUrl.toByteArray(Charsets.UTF_8).size >
                MAX_SEARCH_CONSOLE_STORED_STRING_BYTES,
        )
        val snapshot = SearchConsoleSnapshot(
            properties = listOf(SearchConsoleProperty(oversizedSiteUrl, "siteOwner")),
            fetchedAtMillis = 400L,
            propertiesComplete = true,
            warnings = emptyList(),
        )

        val commit = store.saveValidatedConnection(
            credential("access", "refresh"),
            SearchConsoleFetchResult.Complete(snapshot),
        )
        store.acceptValidatedConnection(commit)

        val restored = store.restore() as SearchConsoleRestoreResult.Restored
        val cached = checkNotNull(restored.cachedSnapshot)
        assertTrue(cached.properties.isEmpty())
        assertFalse(cached.propertiesComplete)
        assertTrue(cached.warnings.single().contains("bounded"))
    }

    @Test
    fun sameAccountPartialReconnectMergesRatherThanShrinkingOfflineCache() {
        val repository = SearchConsoleConnectionRepository(MemoryStore(), TestCipher())
        val store = SearchConsoleConnectionStore(repository, nowMillis = { 800L })
        val existing = partialSnapshot(
            listOf(property(0), property(1), property(2)),
            fetchedAtMillis = 400L,
            warning = "Earlier property inventory was incomplete.",
        )
        repository.save(connection("old-access", "refresh", existing))
        val refreshedProperty = property(1).copy(permissionLevel = "siteRestrictedUser")
        val candidate = partialSnapshot(
            listOf(refreshedProperty, property(3)),
            fetchedAtMillis = 700L,
            warning = "Latest property inventory was incomplete.",
        )

        val commit = store.saveValidatedConnection(
            credential("new-access", "refresh"),
            SearchConsoleFetchResult.Partial(candidate, failure()),
        )
        store.acceptValidatedConnection(commit)

        val cached = checkNotNull(repository.load()?.cachedSnapshot)
        assertEquals(
            listOf(
                "sc-domain:example0.com",
                "sc-domain:example1.com",
                "sc-domain:example2.com",
                "sc-domain:example3.com",
            ),
            cached.properties.map(SearchConsoleProperty::siteUrl),
        )
        assertEquals("siteRestrictedUser", cached.properties[1].permissionLevel)
        assertEquals(700L, cached.fetchedAtMillis)
        assertFalse(cached.propertiesComplete)
    }

    @Test
    fun partialRefreshPreservesFullerSameAccountPartialCache() {
        val repository = SearchConsoleConnectionRepository(MemoryStore(), TestCipher())
        val store = SearchConsoleConnectionStore(repository, nowMillis = { 900L })
        val existing = partialSnapshot(
            listOf(property(0), property(1), property(2)),
            fetchedAtMillis = 400L,
            warning = "Earlier property inventory was incomplete.",
        )
        repository.save(connection("access", "refresh", existing))
        val candidate = partialSnapshot(
            listOf(property(1).copy(permissionLevel = "siteFullUser")),
            fetchedAtMillis = 700L,
            warning = "Latest property inventory was incomplete.",
        )

        assertTrue(
            store.persistSnapshotRefresh(
                checkNotNull(store.loadForRefresh()),
                SearchConsoleFetchResult.Partial(candidate, failure()),
            ),
        )

        val cached = checkNotNull(repository.load()?.cachedSnapshot)
        assertEquals(existing.properties.map(SearchConsoleProperty::siteUrl), cached.properties.map(SearchConsoleProperty::siteUrl))
        assertEquals("siteFullUser", cached.properties[1].permissionLevel)
        assertEquals(700L, cached.fetchedAtMillis)
        assertEquals(2, cached.warnings.size)
    }

    @Test
    fun snapshotRefreshCannotCrossIntoReplacementAccount() {
        val repository = SearchConsoleConnectionRepository(MemoryStore(), TestCipher())
        val store = SearchConsoleConnectionStore(repository, nowMillis = { 950L })
        repository.save(connection("account-a-access", "refresh-a", snapshot()))
        val accountA = checkNotNull(store.loadForRefresh())
        val accountB = SearchConsoleStoredConnection(
            "subject-2",
            credential("account-b-access", "refresh-b", subject = "subject-2"),
            20L,
            900L,
            snapshot(2),
        )
        repository.save(accountB)

        assertFalse(
            store.persistSnapshotRefresh(
                accountA,
                SearchConsoleFetchResult.Complete(snapshot(4).copy(fetchedAtMillis = 950L)),
            ),
        )
        assertEquals("subject-2", repository.load()?.id)
        assertEquals(2, repository.load()?.cachedSnapshot?.properties?.size)
    }

    private fun credential(
        access: String,
        refresh: String?,
        subject: String = "subject-1",
    ) = SearchConsoleOAuthCredential(
        SecretValue.of(access),
        refresh?.let(SecretValue::of),
        "Bearer",
        SearchConsoleOAuthCredential.REQUIRED_SCOPES,
        1_000_000L,
        subject,
        "apoorv@example.com",
    )

    private fun connection(access: String, refresh: String?, snapshot: SearchConsoleSnapshot?) =
        SearchConsoleStoredConnection(
            "subject-1", credential(access, refresh), 10L, 100L, snapshot,
        )

    private fun snapshot(count: Int = 1) = SearchConsoleSnapshot(
        List(count) { property(it) }, 100L, true, emptyList(),
    )

    private fun partialSnapshot(
        properties: List<SearchConsoleProperty>,
        fetchedAtMillis: Long,
        warning: String,
    ) = SearchConsoleSnapshot(properties, fetchedAtMillis, false, listOf(warning))

    private fun property(index: Int) = SearchConsoleProperty(
        "sc-domain:example$index.com",
        "siteOwner",
    )

    private fun failure() = SearchConsoleFailure(
        SearchConsoleFailureKind.NETWORK,
        "Google Search Console could not be reached.",
    )

    private class MemoryStore : AtomicBytesStore {
        var bytes: ByteArray? = null
        override fun read(): ByteArray? = bytes?.copyOf()
        override fun write(bytes: ByteArray) { this.bytes = bytes.copyOf() }
        override fun delete() { bytes = null }
    }

    private class TestCipher : AccountCipher {
        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): SealedPayload =
            SealedPayload(ByteArray(12) { 9 }, transform(plaintext, associatedData))

        override fun decrypt(payload: SealedPayload, associatedData: ByteArray): ByteArray =
            transform(payload.ciphertext(), associatedData)

        private fun transform(input: ByteArray, aad: ByteArray): ByteArray = ByteArray(input.size) {
            (input[it].toInt() xor aad[it % aad.size].toInt()).toByte()
        }
    }
}
