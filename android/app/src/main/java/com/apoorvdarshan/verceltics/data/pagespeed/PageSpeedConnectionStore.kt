package com.apoorvdarshan.verceltics.data.pagespeed

import java.util.UUID

/** Opaque rollback handle for one validated encrypted persistence transaction. */
class PageSpeedConnectionCommit internal constructor(
    val connectionId: String,
    internal val recordCommit: PageSpeedRecordCommit,
) {
    override fun toString(): String =
        "PageSpeedConnectionCommit(connectionId=$connectionId, recordCommit=<redacted>)"
}

/** Coordinates persistence without turning restore into a network-dependent operation. */
class PageSpeedConnectionStore(
    private val repository: PageSpeedConnectionRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    fun restore(): PageSpeedRestoreResult = try {
        val connection = repository.load() ?: return PageSpeedRestoreResult.NotConnected
        val snapshot = connection.cachedSnapshot
        PageSpeedRestoreResult.Restored(
            connectionId = connection.id,
            siteUrl = connection.credentials.siteUrl,
            cachedSnapshot = snapshot,
            cacheIsStale = snapshot == null || nowMillis() - snapshot.fetchedAtMillis >= CACHE_LIFETIME_MILLIS,
        )
    } catch (_: SecurityException) {
        PageSpeedRestoreResult.Unavailable(PageSpeedRestoreProblem.SECURE_STORAGE_UNAVAILABLE)
    } catch (_: Exception) {
        PageSpeedRestoreResult.Unavailable(PageSpeedRestoreProblem.SAVED_RECORD_UNREADABLE)
    }

    /** Internal backend access for an explicit refresh; UI restore state never receives the key. */
    internal fun loadForRefresh(): PageSpeedStoredConnection? = repository.load()

    fun saveValidatedConnection(
        credentials: PageSpeedCredentials,
        result: PageSpeedFetchResult,
    ): PageSpeedConnectionCommit {
        val snapshot = when (result) {
            is PageSpeedFetchResult.Complete -> result.snapshot
            is PageSpeedFetchResult.Partial -> result.snapshot
            is PageSpeedFetchResult.Failure -> error("A failed PageSpeed validation cannot be saved.")
        }
        require(snapshot.siteUrl == credentials.siteUrl) {
            "The PageSpeed validation belongs to a different site."
        }
        val now = nowMillis()
        val existing = repository.load()
        val connection = PageSpeedStoredConnection(
            id = existing?.takeIf { it.credentials.siteUrl == credentials.siteUrl }?.id
                ?: UUID.randomUUID().toString(),
            credentials = credentials,
            createdAtMillis = existing
                ?.takeIf { it.credentials.siteUrl == credentials.siteUrl }
                ?.createdAtMillis
                ?: now,
            updatedAtMillis = now,
            cachedSnapshot = snapshot,
        )
        val recordCommit = repository.saveWithRevision(connection)
        return PageSpeedConnectionCommit(
            connectionId = connection.id,
            recordCommit = recordCommit,
        )
    }

    /** Accepts a successful replacement and immediately releases its encrypted rollback copy. */
    fun acceptValidatedConnection(commit: PageSpeedConnectionCommit) =
        repository.accept(commit.recordCommit)

    /**
     * Compensates a cancelled connect only while its exact encrypted revision is still current.
     * A later connect or refresh is never removed by this rollback.
     */
    fun rollbackValidatedConnection(commit: PageSpeedConnectionCommit): Boolean =
        repository.rollbackIfRevisionMatches(commit.recordCommit)

    /** A failed refresh never destroys the saved credential or last truthful snapshot. */
    fun persistRefreshResult(result: PageSpeedFetchResult): Boolean {
        val snapshot = when (result) {
            is PageSpeedFetchResult.Complete -> result.snapshot
            is PageSpeedFetchResult.Partial -> result.snapshot
            is PageSpeedFetchResult.Failure -> return false
        }
        val existing = repository.load() ?: return false
        require(existing.credentials.siteUrl == snapshot.siteUrl) {
            "The PageSpeed refresh belongs to a different site."
        }
        repository.save(
            existing.copy(
                updatedAtMillis = nowMillis(),
                cachedSnapshot = snapshot,
            ),
        )
        return true
    }

    fun disconnect() = repository.delete()

    companion object {
        const val CACHE_LIFETIME_MILLIS: Long = 30 * 60 * 1_000L
    }
}
