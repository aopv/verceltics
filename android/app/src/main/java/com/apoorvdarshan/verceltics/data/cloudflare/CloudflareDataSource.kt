package com.apoorvdarshan.verceltics.data.cloudflare

import com.apoorvdarshan.verceltics.data.account.SecretValue
import com.apoorvdarshan.verceltics.data.network.CancelableCall
import com.apoorvdarshan.verceltics.data.network.ResponseTooLargeException
import com.apoorvdarshan.verceltics.data.network.UnsafeRedirectException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Cancellable read-only dashboard orchestration with bounded, truthful section states. */
class CloudflareDataSource(
    private val api: CloudflareReadApi = CloudflareApi(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    fun newDashboardCall(
        token: SecretValue,
        preferredAccountId: String? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        maximumPages: Int = DEFAULT_MAXIMUM_PAGES,
        maximumItems: Int = DEFAULT_MAXIMUM_ITEMS,
    ): CancelableCall<CloudflareFetchResult> {
        require(pageSize in 1..CloudflareApi.MAXIMUM_PAGE_SIZE)
        require(maximumPages in 1..CloudflareApi.MAXIMUM_PAGE_NUMBER)
        require(maximumItems in pageSize..HARD_MAXIMUM_ITEMS)
        require(
            preferredAccountId == null ||
                preferredAccountId.isSafeCloudflareText(CF_MAX_ID_CHARACTERS),
        )
        return CloudflareDashboardCall(
            api = api,
            token = token,
            preferredAccountId = preferredAccountId,
            pageSize = pageSize,
            maximumPages = maximumPages,
            maximumItems = maximumItems,
            nowMillis = nowMillis,
        )
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 50
        const val DEFAULT_MAXIMUM_PAGES = 100
        const val DEFAULT_MAXIMUM_ITEMS = 20_000
        private const val HARD_MAXIMUM_ITEMS = 100_000
    }
}

private class CloudflareDashboardCall(
    private val api: CloudflareReadApi,
    private val token: SecretValue,
    private val preferredAccountId: String?,
    private val pageSize: Int,
    private val maximumPages: Int,
    private val maximumItems: Int,
    private val nowMillis: () -> Long,
) : TrackedCloudflareCall<CloudflareFetchResult>() {
    override fun executeTracked(): CloudflareFetchResult {
        val verification = try {
            executeChild(api.newVerifyTokenCall(token))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return CloudflareFetchResult.Failure(safeCloudflareFailure(error))
        }
        if (!verification.isActive) {
            return CloudflareFetchResult.Failure(
                CloudflareFailure(
                    CloudflareFailureKind.AUTHENTICATION,
                    "This Cloudflare API token is not active.",
                ),
            )
        }

        val failures = mutableListOf<CloudflareFailure>()
        val warnings = mutableListOf<String>()
        val accountsResult = collectPages(
            label = "account",
            childFactory = { page -> api.newAccountsPageCall(token, page, pageSize) },
            identity = CloudflareAccountSummary::id,
        )
        val accounts = accountsResult.itemsOrEmpty()
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        val accountsComplete = accountsResult is CloudflareCollectionResult.Complete
        accountsResult.failureOrNull()?.let { failure ->
            failures += failure
            warnings += "Cloudflare account inventory is incomplete: ${failure.message}"
        }

        val selectedAccountId = preferredAccountId
            ?.takeIf { preferred -> accounts.any { it.id == preferred } }
            ?: accounts.firstOrNull()?.id
        val inventory = selectedAccountId?.let { accountId ->
            loadAccountInventory(accountId, failures, warnings)
        }
        val profile = CloudflareProfile(
            id = verification.id ?: credentialFingerprint(token),
            displayName = accounts.firstOrNull()?.name ?: "Cloudflare API Token",
            tokenStatus = verification.status,
        )
        val snapshot = CloudflareSnapshot(
            profile = profile,
            accounts = accounts,
            selectedAccountId = selectedAccountId,
            selectedAccountInventory = inventory,
            accountsComplete = accountsComplete,
            fetchedAtMillis = nowMillis(),
            warnings = warnings.distinct(),
        )
        return if (snapshot.isComplete) {
            CloudflareFetchResult.Complete(snapshot)
        } else {
            CloudflareFetchResult.Partial(snapshot, failures.distinct())
        }
    }

    private fun loadAccountInventory(
        accountId: String,
        failures: MutableList<CloudflareFailure>,
        dashboardWarnings: MutableList<String>,
    ): CloudflareAccountInventory {
        val sectionWarnings = mutableListOf<String>()
        val zonesResult = collectPages(
            label = "zone",
            childFactory = { page -> api.newZonesPageCall(token, accountId, page, pageSize) },
            identity = CloudflareZone::id,
        )
        val pagesResult = collectPages(
            label = "Pages project",
            childFactory = { page -> api.newPagesProjectsPageCall(token, accountId, page, pageSize) },
            identity = CloudflarePagesProject::id,
        )
        val workersResult = loadWorkerScripts(accountId)

        fun record(section: String, result: CloudflareCollectionResult<*>) {
            result.failureOrNull()?.let { failure ->
                failures += failure
                val warning = "Cloudflare $section inventory is incomplete: ${failure.message}"
                sectionWarnings += warning
                dashboardWarnings += warning
            }
        }
        record("zone", zonesResult)
        record("Pages", pagesResult)
        record("Worker", workersResult)

        return CloudflareAccountInventory(
            accountId = accountId,
            zones = zonesResult.itemsOrEmpty().sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            ),
            pagesProjects = pagesResult.itemsOrEmpty().sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            ),
            workers = workersResult.itemsOrEmpty().sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.id },
            ),
            zonesComplete = zonesResult is CloudflareCollectionResult.Complete,
            pagesComplete = pagesResult is CloudflareCollectionResult.Complete,
            workersComplete = workersResult is CloudflareCollectionResult.Complete,
            warnings = sectionWarnings.distinct(),
        )
    }

    /** Workers scripts is a bounded SinglePage Cloudflare endpoint; it has no page controls. */
    private fun loadWorkerScripts(accountId: String): CloudflareCollectionResult<CloudflareWorkerScript> =
        try {
            CloudflareCollectionResult.Complete(executeChild(api.newWorkerScriptsCall(token, accountId)))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            CloudflareCollectionResult.Failure(safeCloudflareFailure(error))
        }

    private fun <T> collectPages(
        label: String,
        childFactory: (Int) -> CancelableCall<CloudflarePage<T>>,
        identity: (T) -> String,
    ): CloudflareCollectionResult<T> {
        val items = mutableListOf<T>()
        val seenIds = mutableSetOf<String>()
        var completedPages = 0
        for (requestedPage in 1..maximumPages) {
            throwIfCancelled()
            val response = try {
                executeChild(childFactory(requestedPage))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                return failedOrPartial(items, completedPages, safeCloudflareFailure(error))
            }
            if (response.page != null && response.page != requestedPage) {
                return failedOrPartial(
                    items,
                    completedPages,
                    invalidPaginationFailure("Cloudflare returned the wrong $label page."),
                )
            }
            completedPages += 1
            if (response.items.isEmpty()) return CloudflareCollectionResult.Complete(items.toList())
            if (response.totalPages != null && response.totalPages < requestedPage) {
                return failedOrPartial(
                    items,
                    completedPages - 1,
                    invalidPaginationFailure(
                        "Cloudflare returned inconsistent $label pagination metadata.",
                    ),
                )
            }
            val uniqueItems = response.items.filter { seenIds.add(identity(it)) }
            if (uniqueItems.isEmpty()) {
                return failedOrPartial(
                    items,
                    completedPages - 1,
                    invalidPaginationFailure(
                        "Cloudflare pagination repeated a $label page without new items.",
                    ),
                )
            }
            if (items.size + uniqueItems.size > maximumItems) {
                val available = (maximumItems - items.size).coerceAtLeast(0)
                items += uniqueItems.take(available)
                return failedOrPartial(
                    items,
                    completedPages,
                    invalidPaginationFailure(
                        "Cloudflare $label inventory exceeded the $maximumItems-item safety limit.",
                    ),
                )
            }
            items += uniqueItems

            val totalPages = response.totalPages
            if (totalPages != null && requestedPage >= totalPages) {
                return CloudflareCollectionResult.Complete(items.toList())
            }
            if (totalPages == null && response.items.size < pageSize) {
                return CloudflareCollectionResult.Complete(items.toList())
            }
        }
        return failedOrPartial(
            items,
            completedPages,
            invalidPaginationFailure(
                "Cloudflare $label pagination exceeded $maximumPages pages.",
            ),
        )
    }

    private fun credentialFingerprint(token: SecretValue): String {
        val tokenBytes = token.use { it.toByteArray(StandardCharsets.UTF_8) }
        val digest = try {
            MessageDigest.getInstance("SHA-256").digest(tokenBytes)
        } finally {
            tokenBytes.fill(0)
        }
        return try {
            digest.joinToString("") { "%02x".format(it) }.take(32)
        } finally {
            digest.fill(0)
        }
    }

    private fun <T> failedOrPartial(
        items: List<T>,
        completedPages: Int,
        failure: CloudflareFailure,
    ): CloudflareCollectionResult<T> = if (items.isEmpty() || completedPages == 0) {
        CloudflareCollectionResult.Failure(failure)
    } else {
        CloudflareCollectionResult.Partial(items.toList(), failure, completedPages)
    }
}

private abstract class TrackedCloudflareCall<T> : CancelableCall<T> {
    private val started = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    private val activeChild = AtomicReference<CancelableCall<*>?>()

    final override fun execute(): T {
        check(started.compareAndSet(false, true)) { "A Cloudflare call can only execute once." }
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
            throw CancellationException("The Cloudflare request was cancelled.")
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
        if (cancelled.get()) throw CancellationException("The Cloudflare request was cancelled.")
    }
}

private fun <T> CloudflareCollectionResult<T>.itemsOrEmpty(): List<T> = when (this) {
    is CloudflareCollectionResult.Complete -> items
    is CloudflareCollectionResult.Partial -> items
    is CloudflareCollectionResult.Failure -> emptyList()
}

private fun CloudflareCollectionResult<*>.failureOrNull(): CloudflareFailure? = when (this) {
    is CloudflareCollectionResult.Complete -> null
    is CloudflareCollectionResult.Partial -> failure
    is CloudflareCollectionResult.Failure -> failure
}

private fun safeCloudflareFailure(error: Exception): CloudflareFailure = when (error) {
    is CloudflareApiException -> error.failure
    is CloudflareResponseFormatException -> CloudflareFailure(
        CloudflareFailureKind.INVALID_RESPONSE,
        "Cloudflare returned data the app could not parse.",
    )
    is ResponseTooLargeException -> CloudflareFailure(
        CloudflareFailureKind.INVALID_RESPONSE,
        "Cloudflare returned more data than the app can process safely.",
    )
    is UnsafeRedirectException -> CloudflareFailure(
        CloudflareFailureKind.INVALID_RESPONSE,
        "Cloudflare returned an unsafe redirect.",
    )
    is IOException -> CloudflareFailure(
        CloudflareFailureKind.NETWORK,
        "Cloudflare could not be reached. Check your connection and try again.",
    )
    is IllegalArgumentException -> CloudflareFailure(
        CloudflareFailureKind.CONFIGURATION,
        "The Cloudflare request configuration is invalid.",
    )
    else -> CloudflareFailure(
        CloudflareFailureKind.INVALID_RESPONSE,
        "Cloudflare returned an invalid response.",
    )
}

private fun invalidPaginationFailure(message: String) = CloudflareFailure(
    CloudflareFailureKind.INVALID_RESPONSE,
    message,
)
