package com.apoorvdarshan.verceltics.data.netlify

import com.apoorvdarshan.verceltics.data.account.SecretValue
import com.apoorvdarshan.verceltics.data.network.CancelableCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CancellationException

class NetlifyDataSourceTest {
    @Test
    fun snapshotValidatesThenCollectsEveryBoundedPage() {
        val api = FakeApi(
            sitePage = { page ->
                when (page) {
                    1 -> valueCall(listOf(site("one"), site("two")))
                    2 -> valueCall(listOf(site("three")))
                    else -> error("Unexpected page $page")
                }
            },
        )

        val result = NetlifyDataSource(api, nowMillis = { 42L })
            .newSnapshotCall(TOKEN, pageSize = 2, maximumPages = 5)
            .execute() as NetlifyFetchResult.Complete

        assertEquals(listOf(1, 2), api.requestedSitePages)
        assertEquals(listOf("one", "two", "three"), result.snapshot.sites.map { it.id })
        assertEquals(42L, result.snapshot.fetchedAtMillis)
        assertTrue(result.snapshot.sitesComplete)
    }

    @Test
    fun laterSitePageFailureReturnsTruthfulPartialSnapshot() {
        val api = FakeApi(
            sitePage = { page ->
                if (page == 1) valueCall(listOf(site("one"), site("two")))
                else throwingCall(IOException("raw socket detail must not surface"))
            },
        )

        val result = NetlifyDataSource(api)
            .newSnapshotCall(TOKEN, pageSize = 2, maximumPages = 5)
            .execute() as NetlifyFetchResult.Partial

        assertEquals(2, result.snapshot.sites.size)
        assertEquals(NetlifyFailureKind.NETWORK, result.failure.kind)
        assertTrue(result.snapshot.warnings.single().contains("incomplete"))
        assertTrue(result.snapshot.warnings.none { it.contains("socket detail") })
    }

    @Test
    fun failedValidationDoesNotAttemptSitesOrProduceEmptySuccess() {
        val api = FakeApi(
            validation = throwingCall(
                NetlifyApiException(
                    NetlifyFailure(
                        NetlifyFailureKind.AUTHENTICATION,
                        "Netlify rejected this personal token.",
                        401,
                    ),
                    "unauthorized",
                ),
            ),
        )

        val result = NetlifyDataSource(api).newSnapshotCall(TOKEN).execute()

        assertEquals(NetlifyFailureKind.AUTHENTICATION, (result as NetlifyFetchResult.Failure).failure.kind)
        assertTrue(api.requestedSitePages.isEmpty())
    }

    @Test
    fun deploymentPagingPreservesLoadedDataAndReportsLaterFailure() {
        val api = FakeApi(
            deploymentPage = { page ->
                if (page == 1) {
                    valueCall(listOf(deployment("one"), deployment("two")))
                } else {
                    throwingCall(IOException("offline"))
                }
            },
        )

        val result = NetlifyDataSource(api)
            .newDeploymentsCall(TOKEN, "site-123", pageSize = 2, maximumPages = 4)
            .execute() as NetlifyCollectionResult.Partial

        assertEquals(2, result.items.size)
        assertEquals(1, result.completedPages)
        assertEquals(NetlifyFailureKind.NETWORK, result.failure.kind)
    }

    @Test
    fun repeatedFullPageAndMaximumPageGuardNeverClaimCompleteness() {
        val repeated = NetlifyDataSource(
            FakeApi(sitePage = { valueCall(listOf(site("one"), site("two"))) }),
        ).newSnapshotCall(TOKEN, pageSize = 2, maximumPages = 3).execute()
            as NetlifyFetchResult.Partial
        assertEquals(NetlifyFailureKind.INVALID_RESPONSE, repeated.failure.kind)
        assertTrue(repeated.failure.message.contains("repeated"))

        val limited = NetlifyDataSource(
            FakeApi(sitePage = { page -> valueCall(listOf(site("$page-a"), site("$page-b"))) }),
        ).newSnapshotCall(TOKEN, pageSize = 2, maximumPages = 2).execute()
            as NetlifyFetchResult.Partial
        assertEquals(4, limited.snapshot.sites.size)
        assertTrue(limited.failure.message.contains("exceeded 2 pages"))
    }

    @Test
    fun cancellationBeforeExecutionDoesNotStartProviderWork() {
        val api = FakeApi()
        val call = NetlifyDataSource(api).newSnapshotCall(TOKEN)
        call.cancel()

        assertThrows(CancellationException::class.java, call::execute)
        assertEquals(0, api.validationExecutions)
    }

    private class FakeApi(
        private val validation: CancelableCall<NetlifyProfile> = valueCall(PROFILE),
        private val sitePage: (Int) -> CancelableCall<List<NetlifySite>> = { valueCall(emptyList()) },
        private val deploymentPage: (Int) -> CancelableCall<List<NetlifyDeployment>> =
            { valueCall(emptyList()) },
    ) : NetlifyReadApi {
        val requestedSitePages = mutableListOf<Int>()
        var validationExecutions = 0

        override fun newValidatePersonalTokenCall(token: SecretValue): CancelableCall<NetlifyProfile> =
            object : CancelableCall<NetlifyProfile> {
                override fun execute(): NetlifyProfile {
                    validationExecutions += 1
                    return validation.execute()
                }

                override fun cancel() = validation.cancel()
            }

        override fun newListSitesPageCall(
            token: SecretValue,
            page: Int,
            perPage: Int,
        ): CancelableCall<List<NetlifySite>> {
            requestedSitePages += page
            return sitePage(page)
        }

        override fun newSiteDetailsCall(
            token: SecretValue,
            siteId: String,
        ): CancelableCall<NetlifySiteDetails> = valueCall(
            NetlifySiteDetails(site(siteId), emptyList(), null, null),
        )

        override fun newListDeploymentsPageCall(
            token: SecretValue,
            siteId: String,
            page: Int,
            perPage: Int,
        ): CancelableCall<List<NetlifyDeployment>> = deploymentPage(page)

        override fun newListBuildsPageCall(
            token: SecretValue,
            siteId: String,
            page: Int,
            perPage: Int,
        ): CancelableCall<List<NetlifyBuild>> = valueCall(emptyList())

        override fun newBuildCall(token: SecretValue, buildId: String): CancelableCall<NetlifyBuild> =
            valueCall(NetlifyBuild(buildId, null, null, null, null, null))
    }

    companion object {
        private val TOKEN = SecretValue.of("netlify-secret")
        private val PROFILE = NetlifyProfile("user-123", "Apoorv", "a@example.com", null)

        private fun site(id: String) = NetlifySite(id, "Site $id", null, null, null, null, null)

        private fun deployment(id: String) = NetlifyDeployment(
            id, "Deploy $id", "ready", null, null, null, null,
        )

        private fun <T> valueCall(value: T): CancelableCall<T> = object : CancelableCall<T> {
            override fun execute(): T = value
            override fun cancel() = Unit
        }

        private fun <T> throwingCall(error: Exception): CancelableCall<T> = object : CancelableCall<T> {
            override fun execute(): T = throw error
            override fun cancel() = Unit
        }
    }
}
