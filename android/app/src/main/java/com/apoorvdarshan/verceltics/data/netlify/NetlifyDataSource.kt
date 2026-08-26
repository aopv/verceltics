package com.apoorvdarshan.verceltics.data.netlify

import com.apoorvdarshan.verceltics.data.account.SecretValue
import com.apoorvdarshan.verceltics.data.network.CancelableCall
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Cancellable Netlify orchestration with bounded pagination and explicit partial-data states.
 * It performs no writes and owns no Android/UI lifecycle.
 */
class NetlifyDataSource(
    private val api: NetlifyReadApi = NetlifyApi(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    fun newSnapshotCall(
        token: SecretValue,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        maximumPages: Int = DEFAULT_MAXIMUM_PAGES,
    ): CancelableCall<NetlifyFetchResult> {
        requirePagination(pageSize, maximumPages)
        return NetlifySnapshotCall(api, token, pageSize, maximumPages, nowMillis)
    }

    fun newDeploymentsCall(
        token: SecretValue,
        siteId: String,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        maximumPages: Int = DEFAULT_MAXIMUM_PAGES,
    ): CancelableCall<NetlifyCollectionResult<NetlifyDeployment>> {
        requirePagination(pageSize, maximumPages)
        return NetlifyPagedCollectionCall(
            pageSize = pageSize,
            maximumPages = maximumPages,
            pageCall = { page -> api.newListDeploymentsPageCall(token, siteId, page, pageSize) },
            identity = NetlifyDeployment::id,
            collectionLabel = "deploys",
        )
    }

    fun newBuildsCall(
        token: SecretValue,
        siteId: String,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        maximumPages: Int = DEFAULT_MAXIMUM_PAGES,
    ): CancelableCall<NetlifyCollectionResult<NetlifyBuild>> {
        requirePagination(pageSize, maximumPages)
        return NetlifyPagedCollectionCall(
            pageSize = pageSize,
            maximumPages = maximumPages,
            pageCall = { page -> api.newListBuildsPageCall(token, siteId, page, pageSize) },
            identity = NetlifyBuild::id,
            collectionLabel = "builds",
        )
    }

    fun newSiteDetailsCall(
        token: SecretValue,
        siteId: String,
    ): CancelableCall<NetlifyResourceResult<NetlifySiteDetails>> = NetlifySingleResourceCall {
        api.newSiteDetailsCall(token, siteId)
    }

    fun newBuildCall(
        token: SecretValue,
        buildId: String,
    ): CancelableCall<NetlifyResourceResult<NetlifyBuild>> = NetlifySingleResourceCall {
        api.newBuildCall(token, buildId)
    }

    private fun requirePagination(pageSize: Int, maximumPages: Int) {
        require(pageSize in 1..NetlifyApi.MAXIMUM_PAGE_SIZE) { "Invalid Netlify page size." }
        require(maximumPages in 1..NetlifyApi.MAXIMUM_PAGE_NUMBER) {
            "Invalid Netlify pagination limit."
        }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE: Int = 100
        const val DEFAULT_MAXIMUM_PAGES: Int = 200
    }
}

private class NetlifySnapshotCall(
    private val api: NetlifyReadApi,
    private val token: SecretValue,
    private val pageSize: Int,
    private val maximumPages: Int,
    private val nowMillis: () -> Long,
) : TrackedNetlifyCall<NetlifyFetchResult>() {
    override fun executeTracked(): NetlifyFetchResult {
        val profile = try {
            executeChild(api.newValidatePersonalTokenCall(token))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return NetlifyFetchResult.Failure(safeFailure(error))
        }

        val sites = mutableListOf<NetlifySite>()
        val seenIds = mutableSetOf<String>()
        for (page in 1..maximumPages) {
            throwIfCancelled()
            val pageItems = try {
                executeChild(api.newListSitesPageCall(token, page, pageSize))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                return partial(profile, sites, safeFailure(error))
            }
            if (pageItems.isEmpty()) return complete(profile, sites)
            val uniqueItems = pageItems.filter { seenIds.add(it.id) }
            if (uniqueItems.isEmpty()) {
                return partial(profile, sites, repeatedPageFailure("sites"))
            }
            sites += uniqueItems
            if (pageItems.size < pageSize) return complete(profile, sites)
        }
        return partial(profile, sites, pageLimitFailure("sites", maximumPages))
    }

    private fun complete(profile: NetlifyProfile, sites: List<NetlifySite>): NetlifyFetchResult =
        NetlifyFetchResult.Complete(
            NetlifySnapshot(
                profile = profile,
                sites = sites.toList(),
                fetchedAtMillis = nowMillis(),
                sitesComplete = true,
                warnings = emptyList(),
            ),
        )

    private fun partial(
        profile: NetlifyProfile,
        sites: List<NetlifySite>,
        failure: NetlifyFailure,
    ): NetlifyFetchResult = NetlifyFetchResult.Partial(
        snapshot = NetlifySnapshot(
            profile = profile,
            sites = sites.toList(),
            fetchedAtMillis = nowMillis(),
            sitesComplete = false,
            warnings = listOf(
                "The Netlify site list is incomplete: ${failure.message}"
                    .take(MAX_WARNING_CHARACTERS),
            ),
        ),
        failure = failure,
    )
}

private class NetlifyPagedCollectionCall<T>(
    private val pageSize: Int,
    private val maximumPages: Int,
    private val pageCall: (Int) -> CancelableCall<List<T>>,
    private val identity: (T) -> String,
    private val collectionLabel: String,
) : TrackedNetlifyCall<NetlifyCollectionResult<T>>() {
    override fun executeTracked(): NetlifyCollectionResult<T> {
        val items = mutableListOf<T>()
        val seenIds = mutableSetOf<String>()
        var completedPages = 0
        for (page in 1..maximumPages) {
            throwIfCancelled()
            val pageItems = try {
                executeChild(pageCall(page))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                return failedOrPartial(items, completedPages, safeFailure(error))
            }
            completedPages += 1
            if (pageItems.isEmpty()) return NetlifyCollectionResult.Complete(items.toList())
            val uniqueItems = pageItems.filter { seenIds.add(identity(it)) }
            if (uniqueItems.isEmpty()) {
                return failedOrPartial(items, completedPages - 1, repeatedPageFailure(collectionLabel))
            }
            items += uniqueItems
            if (pageItems.size < pageSize) return NetlifyCollectionResult.Complete(items.toList())
        }
        return failedOrPartial(items, completedPages, pageLimitFailure(collectionLabel, maximumPages))
    }

    private fun failedOrPartial(
        items: List<T>,
        completedPages: Int,
        failure: NetlifyFailure,
    ): NetlifyCollectionResult<T> = if (items.isEmpty() || completedPages == 0) {
        NetlifyCollectionResult.Failure(failure)
    } else {
        NetlifyCollectionResult.Partial(items.toList(), failure, completedPages)
    }
}

private class NetlifySingleResourceCall<T>(
    private val childFactory: () -> CancelableCall<T>,
) : TrackedNetlifyCall<NetlifyResourceResult<T>>() {
    override fun executeTracked(): NetlifyResourceResult<T> = try {
        NetlifyResourceResult.Complete(executeChild(childFactory()))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        NetlifyResourceResult.Failure(safeFailure(error))
    }
}

private abstract class TrackedNetlifyCall<T> : CancelableCall<T> {
    private val started = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    private val activeChild = AtomicReference<CancelableCall<*>?>()

    final override fun execute(): T {
        check(started.compareAndSet(false, true)) { "A Netlify call can only execute once." }
        throwIfCancelled()
        return executeTracked()
    }

    protected abstract fun executeTracked(): T

    final override fun cancel() {
        cancelled.set(true)
        activeChild.getAndSet(null)?.cancel()
    }

    protected fun <V> executeChild(call: CancelableCall<V>): V {
        throwIfCancelled()
        activeChild.set(call)
        if (cancelled.get()) {
            activeChild.compareAndSet(call, null)
            call.cancel()
            throw CancellationException("The Netlify request was cancelled.")
        }
        return try {
            val value = call.execute()
            throwIfCancelled()
            value
        } finally {
            activeChild.compareAndSet(call, null)
        }
    }

    protected fun throwIfCancelled() {
        if (cancelled.get()) throw CancellationException("The Netlify request was cancelled.")
    }
}

private fun safeFailure(error: Exception): NetlifyFailure = when (error) {
    is NetlifyApiException -> error.failure
    is NetlifyResponseFormatException -> NetlifyFailure(
        NetlifyFailureKind.INVALID_RESPONSE,
        error.message ?: "Netlify returned an invalid response.",
    )
    is IOException -> NetlifyFailure(
        NetlifyFailureKind.NETWORK,
        "Netlify could not be reached. Check your connection and try again.",
    )
    is IllegalArgumentException -> NetlifyFailure(
        NetlifyFailureKind.CONFIGURATION,
        "The Netlify request configuration is invalid.",
    )
    else -> NetlifyFailure(
        NetlifyFailureKind.INVALID_RESPONSE,
        "Netlify returned an invalid response.",
    )
}

private fun repeatedPageFailure(label: String): NetlifyFailure = NetlifyFailure(
    NetlifyFailureKind.INVALID_RESPONSE,
    "Netlify pagination repeated a $label page without returning new items.",
)

private fun pageLimitFailure(label: String, maximumPages: Int): NetlifyFailure = NetlifyFailure(
    NetlifyFailureKind.INVALID_RESPONSE,
    "Netlify $label pagination exceeded $maximumPages pages.",
)
