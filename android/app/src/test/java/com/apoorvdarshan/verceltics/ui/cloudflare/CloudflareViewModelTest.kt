package com.apoorvdarshan.verceltics.ui.cloudflare

import androidx.lifecycle.SavedStateHandle
import com.apoorvdarshan.verceltics.data.account.SecretValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloudflareViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun cachedRestoreIsOfflineFirstAndRefreshesOnceAfterForeground() = runTest(dispatcher) {
        val cached = dashboard(cacheState = CloudflareCacheState.CACHED_STALE)
        val gateway = FakeGateway(CloudflareRestoreUi.Available(cached))
        val viewModel = CloudflareViewModel(gateway, SavedStateHandle())

        advanceUntilIdle()
        assertEquals(0, gateway.refreshCalls)
        assertEquals(cached, viewModel.uiState.value.dashboard)

        viewModel.onForeground()
        advanceUntilIdle()
        assertEquals(1, gateway.refreshCalls)
        viewModel.onBackground()
        viewModel.onForeground()
        advanceUntilIdle()
        assertEquals(1, gateway.refreshCalls)
    }

    @Test
    fun tokenNeverEntersUiStateAndSecureWindowEndsAfterConnect() = runTest(dispatcher) {
        val rawToken = "never-publish-cloudflare-token"
        val gateway = FakeGateway(CloudflareRestoreUi.NotConnected)
        val viewModel = CloudflareViewModel(gateway, SavedStateHandle())
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.requiresSecureWindow)
        viewModel.setRouteVisible(true)
        assertTrue(viewModel.uiState.value.requiresSecureWindow)

        viewModel.connect(SecretValue.of(rawToken))
        advanceUntilIdle()

        assertEquals(rawToken, gateway.lastToken?.use { it })
        assertFalse(viewModel.uiState.value.toString().contains(rawToken))
        assertEquals(CloudflareConnectionStatus.CONNECTED, viewModel.uiState.value.status)
        assertFalse(viewModel.uiState.value.requiresSecureWindow)
    }

    @Test
    fun accountSelectionRefreshesPreferredInventoryAndClosesResource() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val gateway = FakeGateway(CloudflareRestoreUi.Available(dashboard()))
        val viewModel = CloudflareViewModel(gateway, handle)
        advanceUntilIdle()

        viewModel.openResource(CloudflareResourceKind.ZONE, "zone-primary")
        assertEquals("zone-primary", viewModel.uiState.value.selectedResource?.id)

        viewModel.selectAccount("account-secondary")
        advanceUntilIdle()

        assertEquals("account-secondary", gateway.lastPreferredAccountId)
        assertEquals("account-secondary", viewModel.uiState.value.dashboard?.selectedAccountId)
        assertNull(viewModel.uiState.value.selectedResource)
        assertNull(handle.get<String>(CloudflareViewModel.SELECTED_RESOURCE_ID))
    }

    @Test
    fun resourceSelectionSurvivesViewModelRecreationOnlyWhileResourceExists() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val gateway = FakeGateway(CloudflareRestoreUi.Available(dashboard()))
        val first = CloudflareViewModel(gateway, handle)
        advanceUntilIdle()
        first.openResource(CloudflareResourceKind.WORKER, "worker-primary")

        val recreated = CloudflareViewModel(gateway, handle)
        advanceUntilIdle()

        assertEquals(CloudflareResourceKind.WORKER, recreated.uiState.value.selectedResource?.kind)
        assertEquals("worker-primary", recreated.uiState.value.selectedResource?.id)
    }

    private class FakeGateway(
        private val restored: CloudflareRestoreUi,
    ) : CloudflareUiGateway {
        var refreshCalls = 0
        var lastPreferredAccountId: String? = null
        var lastToken: SecretValue? = null

        override suspend fun restore(): Result<CloudflareRestoreUi> = Result.success(restored)

        override suspend fun connect(apiToken: SecretValue): Result<CloudflareDashboardUi> {
            lastToken = apiToken
            return Result.success(dashboard())
        }

        override suspend fun refresh(preferredAccountId: String?): Result<CloudflareDashboardUi> {
            refreshCalls += 1
            lastPreferredAccountId = preferredAccountId
            return Result.success(dashboard(preferredAccountId ?: "account-primary"))
        }

        override suspend fun disconnect(): Result<Unit> = Result.success(Unit)
    }
}

private fun dashboard(
    selectedAccountId: String = "account-primary",
    cacheState: CloudflareCacheState = CloudflareCacheState.LIVE,
): CloudflareDashboardUi {
    val primary = selectedAccountId == "account-primary"
    val inventory = CloudflareInventoryUi(
        accountId = selectedAccountId,
        zones = listOf(
            CloudflareZoneUi(
                id = if (primary) "zone-primary" else "zone-secondary",
                name = if (primary) "primary.example" else "secondary.example",
                status = "active",
                type = "full",
                paused = false,
                accountName = if (primary) "Primary" else "Secondary",
                planName = "Free",
            ),
        ),
        pagesProjects = emptyList(),
        workers = if (primary) {
            listOf(CloudflareWorkerUi("worker-primary", null, null, listOf("fetch"), false, true))
        } else {
            emptyList()
        },
        loadedZoneCount = 1,
        loadedPagesProjectCount = 0,
        loadedWorkerCount = if (primary) 1 else 0,
        zonesComplete = true,
        pagesComplete = true,
        workersComplete = true,
        zonesTruncatedForDisplay = false,
        pagesTruncatedForDisplay = false,
        workersTruncatedForDisplay = false,
        warnings = emptyList(),
    )
    return CloudflareDashboardUi(
        profile = CloudflareProfileUi("profile", "Cloudflare token", "active"),
        accounts = listOf(
            CloudflareAccountUi("account-primary", "Primary", "standard"),
            CloudflareAccountUi("account-secondary", "Secondary", "standard"),
        ),
        loadedAccountCount = 2,
        accountsComplete = true,
        accountsTruncatedForDisplay = false,
        selectedAccountId = selectedAccountId,
        inventory = inventory,
        warnings = emptyList(),
        fetchedAtMillis = 1_700_000_000_000,
        cacheState = cacheState,
    )
}
