package com.apoorvdarshan.verceltics.data.netlify

import com.apoorvdarshan.verceltics.data.account.SecretValue

/** Opaque handle for accepting or compensating one validated encrypted replacement. */
class NetlifyConnectionCommit internal constructor(
    val accountId: String,
    internal val recordCommit: NetlifyRecordCommit,
) {
    override fun toString(): String =
        "NetlifyConnectionCommit(accountId=$accountId, recordCommit=<redacted>)"
}

/** Coordinates encrypted persistence while keeping restore strictly offline. */
class NetlifyConnectionStore(
    private val repository: NetlifyConnectionRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    fun restore(): NetlifyRestoreResult = try {
        val connection = repository.load() ?: return NetlifyRestoreResult.NotConnected
        val snapshot = connection.cachedSnapshot
        NetlifyRestoreResult.Restored(
            profile = connection.account.profile(),
            cachedSnapshot = snapshot,
            cacheIsStale = snapshot == null ||
                nowMillis() - snapshot.fetchedAtMillis >= CACHE_LIFETIME_MILLIS,
        )
    } catch (_: SecurityException) {
        NetlifyRestoreResult.Unavailable(NetlifyRestoreProblem.SECURE_STORAGE_UNAVAILABLE)
    } catch (_: Exception) {
        NetlifyRestoreResult.Unavailable(NetlifyRestoreProblem.SAVED_RECORD_UNREADABLE)
    }

    /** Internal backend access for refresh. UI-facing restore never receives this token. */
    internal fun loadForRefresh(): NetlifyStoredConnection? = repository.load()

    fun saveValidatedConnection(
        token: SecretValue,
        result: NetlifyFetchResult,
    ): NetlifyConnectionCommit {
        val snapshot = result.snapshotOrThrow()
        val now = nowMillis()
        val existing = repository.load()
        val sameAccount = existing?.takeIf { it.account.id == snapshot.profile.id }
        val candidateCache = snapshot.forOfflineCache()
        val preservedCache = if (result is NetlifyFetchResult.Partial) {
            sameAccount?.cachedSnapshot
                ?.takeIf { it.isFullerThan(candidateCache) }
                ?.copy(profile = snapshot.profile)
        } else {
            null
        }
        val connection = NetlifyStoredConnection(
            account = NetlifyAccount(
                id = snapshot.profile.id,
                displayName = snapshot.profile.displayName,
                email = snapshot.profile.email,
                avatarUrl = snapshot.profile.avatarUrl,
                personalToken = token,
                createdAtMillis = sameAccount?.account?.createdAtMillis ?: now,
                updatedAtMillis = now,
            ),
            cachedSnapshot = preservedCache ?: candidateCache,
        )
        return NetlifyConnectionCommit(
            accountId = connection.account.id,
            recordCommit = repository.saveWithRevision(connection),
        )
    }

    /** Accepts a completed connection flow and releases its encrypted rollback copy. */
    fun acceptValidatedConnection(commit: NetlifyConnectionCommit) =
        repository.accept(commit.recordCommit)

    /**
     * Compensates cancellation only while this commit's exact encrypted revision is current. A
     * newer connection, refresh, or disconnect is never overwritten by a late rollback.
     */
    fun rollbackValidatedConnection(commit: NetlifyConnectionCommit): Boolean =
        repository.rollbackIfRevisionMatches(commit.recordCommit)

    /**
     * A failed refresh never destroys saved credentials or cached inventory. A partial refresh is
     * cached only when no prior inventory exists, so an incomplete page cannot replace a fuller
     * last-known snapshot.
     */
    fun persistRefreshResult(result: NetlifyFetchResult): Boolean {
        val versioned = repository.loadWithRevision() ?: return false
        val existing = versioned.connection
        val snapshot = when (result) {
            is NetlifyFetchResult.Complete -> result.snapshot
            is NetlifyFetchResult.Partial -> {
                if (existing.cachedSnapshot != null) return false
                result.snapshot
            }
            is NetlifyFetchResult.Failure -> return false
        }
        require(snapshot.profile.id == existing.account.id) {
            "The Netlify refresh belongs to a different account."
        }
        return repository.saveIfRevisionMatches(
            expectedRevision = versioned.revision,
            connection = NetlifyStoredConnection(
                account = NetlifyAccount(
                    id = existing.account.id,
                    displayName = snapshot.profile.displayName,
                    email = snapshot.profile.email,
                    avatarUrl = snapshot.profile.avatarUrl,
                    personalToken = existing.account.personalToken,
                    createdAtMillis = existing.account.createdAtMillis,
                    updatedAtMillis = nowMillis(),
                ),
                cachedSnapshot = snapshot.forOfflineCache(),
            ),
        )
    }

    fun disconnect() = repository.delete()

    private fun NetlifyFetchResult.snapshotOrThrow(): NetlifySnapshot = when (this) {
        is NetlifyFetchResult.Complete -> snapshot
        is NetlifyFetchResult.Partial -> snapshot
        is NetlifyFetchResult.Failure -> error("A failed Netlify validation cannot be saved.")
    }

    private fun NetlifySnapshot.forOfflineCache(): NetlifySnapshot {
        var modified = sites.size > MAX_CACHED_SITES
        val cachedSites = sites.take(MAX_CACHED_SITES).map { site ->
            val cached = site.copy(
                id = site.id.take(CACHED_ID_CHARACTERS),
                name = site.name.take(CACHED_NAME_CHARACTERS),
                subtitle = site.subtitle?.take(CACHED_SUBTITLE_CHARACTERS),
                url = site.url?.takeIf { it.length <= CACHED_URL_CHARACTERS },
                status = site.status?.take(CACHED_STATUS_CHARACTERS),
                adminUrl = site.adminUrl?.takeIf { it.length <= CACHED_URL_CHARACTERS },
            )
            if (cached != site) modified = true
            cached
        }
        if (!modified) return copy(sites = cachedSites)

        val cacheWarning =
            "The offline Netlify cache is intentionally bounded; refresh online for the complete site data."
        return copy(
            sites = cachedSites,
            sitesComplete = false,
            warnings = (warnings + cacheWarning).distinct(),
        )
    }

    private fun NetlifySnapshot.isFullerThan(candidate: NetlifySnapshot): Boolean =
        sites.size > candidate.sites.size ||
            (sitesComplete && sites.size == candidate.sites.size)

    companion object {
        const val CACHE_LIFETIME_MILLIS: Long = 15 * 60 * 1_000L
        internal const val MAX_CACHED_SITES = 25
        private const val CACHED_ID_CHARACTERS = 256
        private const val CACHED_NAME_CHARACTERS = 256
        private const val CACHED_SUBTITLE_CHARACTERS = 512
        private const val CACHED_URL_CHARACTERS = 2_048
        private const val CACHED_STATUS_CHARACTERS = 128
    }
}
