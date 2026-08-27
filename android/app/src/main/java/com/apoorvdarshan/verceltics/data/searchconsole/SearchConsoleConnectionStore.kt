package com.apoorvdarshan.verceltics.data.searchconsole

/** Offline restore and race-safe persistence policy for the encrypted Search Console slot. */
class SearchConsoleConnectionStore(
    private val repository: SearchConsoleConnectionRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    fun restore(): SearchConsoleRestoreResult = try {
        val connection = repository.load() ?: return SearchConsoleRestoreResult.NotConnected
        val now = nowMillis()
        SearchConsoleRestoreResult.Restored(
            id = connection.id,
            subject = connection.credential.subject,
            email = connection.credential.email,
            cachedSnapshot = connection.cachedSnapshot,
            cacheIsStale = connection.cachedSnapshot?.let {
                now - it.fetchedAtMillis > CACHE_STALE_AFTER_MILLIS
            } ?: false,
            credentialNeedsRefresh = connection.credential.needsRefresh(now),
        )
    } catch (_: SecurityException) {
        SearchConsoleRestoreResult.Unavailable(
            SearchConsoleRestoreProblem.SECURE_STORAGE_UNAVAILABLE,
        )
    } catch (_: Exception) {
        SearchConsoleRestoreResult.Unavailable(SearchConsoleRestoreProblem.SAVED_RECORD_UNREADABLE)
    }

    internal fun saveValidatedConnection(
        credential: SearchConsoleOAuthCredential,
        result: SearchConsoleFetchResult<SearchConsoleSnapshot>,
    ): SearchConsoleRecordCommit {
        val liveSnapshot = when (result) {
            is SearchConsoleFetchResult.Complete -> result.value
            is SearchConsoleFetchResult.Partial -> result.value
            is SearchConsoleFetchResult.Failure -> throw IllegalArgumentException(
                "A failed Search Console validation cannot be saved.",
            )
        }
        // A corrupt or unavailable prior record is never treated as an empty slot and overwritten.
        val current = repository.load()
        val id = credential.subject ?: credential.email ?: stableCredentialId(credential)
        val boundedLive = boundedSnapshot(liveSnapshot)
        val cache = current
            ?.takeIf { it.id == id }
            ?.cachedSnapshot
            ?.let { mergeCache(it, boundedLive) }
            ?: boundedLive
        val now = nowMillis()
        return repository.saveWithRevision(
            SearchConsoleStoredConnection(
                id = id,
                credential = credential,
                createdAtMillis = current?.takeIf { it.id == id }?.createdAtMillis ?: now,
                updatedAtMillis = now,
                cachedSnapshot = cache,
            ),
        )
    }

    internal fun acceptValidatedConnection(commit: SearchConsoleRecordCommit) = repository.accept(commit)

    internal fun rollbackValidatedConnection(commit: SearchConsoleRecordCommit): Boolean =
        repository.rollbackIfRevisionMatches(commit)

    /** CAS-update after token refresh; stale work cannot resurrect a disconnected account. */
    internal fun persistRefreshedCredential(
        expected: SearchConsoleVersionedConnection,
        credential: SearchConsoleOAuthCredential,
    ): Boolean = repository.saveIfRevisionMatches(
        expected.revision,
        expected.connection.copyWith(
            credential = credential,
            updatedAtMillis = nowMillis(),
        ),
    )

    /** CAS-update for an inventory refresh. Failures never erase a usable offline cache. */
    fun persistSnapshotRefresh(result: SearchConsoleFetchResult<SearchConsoleSnapshot>): Boolean {
        val versioned = try {
            repository.loadWithRevision()
        } catch (_: Exception) {
            return false
        } ?: return false
        val snapshot = when (result) {
            is SearchConsoleFetchResult.Complete -> boundedSnapshot(result.value)
            is SearchConsoleFetchResult.Partial -> boundedSnapshot(result.value)
            is SearchConsoleFetchResult.Failure -> return false
        }
        val existing = versioned.connection.cachedSnapshot
        if (existing?.propertiesComplete == true && !snapshot.propertiesComplete) return false
        val cache = existing?.let { mergeCache(it, snapshot) } ?: snapshot
        return repository.saveIfRevisionMatches(
            versioned.revision,
            versioned.connection.copyWith(
                updatedAtMillis = nowMillis(),
                cachedSnapshot = cache,
            ),
        )
    }

    fun disconnect() = repository.delete()

    private fun boundedSnapshot(snapshot: SearchConsoleSnapshot): SearchConsoleSnapshot {
        val properties = snapshot.properties
            .filter { property ->
                property.siteUrl.toByteArray(Charsets.UTF_8).size <=
                    MAX_SEARCH_CONSOLE_STORED_STRING_BYTES
            }
            .take(MAX_CACHED_PROPERTIES)
        val modified = properties.size != snapshot.properties.size
        if (!modified) return snapshot.copy(properties = properties)
        val warning =
            "The offline Search Console property cache is intentionally bounded; refresh online for the complete list."
        return SearchConsoleSnapshot(
            properties = properties,
            fetchedAtMillis = snapshot.fetchedAtMillis,
            propertiesComplete = false,
            warnings = (snapshot.warnings.take(MAX_WARNINGS - 1) + warning).distinct(),
        )
    }

    private fun mergeCache(
        existing: SearchConsoleSnapshot,
        candidate: SearchConsoleSnapshot,
    ): SearchConsoleSnapshot {
        if (candidate.propertiesComplete) return candidate
        if (existing.propertiesComplete) return existing

        val propertiesByUrl = linkedMapOf<String, SearchConsoleProperty>()
        existing.properties.forEach { propertiesByUrl[it.siteUrl] = it }
        // New observations replace stale details without changing the stable cached order.
        candidate.properties.forEach { propertiesByUrl[it.siteUrl] = it }
        val warnings = (candidate.warnings + existing.warnings)
            .distinct()
            .take(MAX_WARNINGS)
        return boundedSnapshot(
            SearchConsoleSnapshot(
                properties = propertiesByUrl.values.toList(),
                fetchedAtMillis = maxOf(existing.fetchedAtMillis, candidate.fetchedAtMillis),
                propertiesComplete = false,
                warnings = warnings,
            ),
        )
    }

    private fun stableCredentialId(credential: SearchConsoleOAuthCredential): String {
        val bytes = (credential.refreshToken ?: credential.accessToken).utf8Bytes()
        return try {
            java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                .take(8)
                .joinToString("") { "%02x".format(it) }
        } finally {
            bytes.fill(0)
        }
    }

    companion object {
        const val MAX_CACHED_PROPERTIES = 25
        const val CACHE_STALE_AFTER_MILLIS = 6 * 60 * 60 * 1_000L
    }
}

private fun SearchConsoleStoredConnection.copyWith(
    credential: SearchConsoleOAuthCredential = this.credential,
    updatedAtMillis: Long = this.updatedAtMillis,
    cachedSnapshot: SearchConsoleSnapshot? = this.cachedSnapshot,
) = SearchConsoleStoredConnection(id, credential, createdAtMillis, updatedAtMillis, cachedSnapshot)
