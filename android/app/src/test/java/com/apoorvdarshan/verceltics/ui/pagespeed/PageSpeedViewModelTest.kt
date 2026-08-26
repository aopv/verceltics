package com.apoorvdarshan.verceltics.ui.pagespeed

import com.apoorvdarshan.verceltics.data.account.SecretValue
import com.apoorvdarshan.verceltics.data.pagespeed.PageSpeedMetricUnit
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PageSpeedViewModelTest {
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
    fun cachedRestoreKeepsStaleAuditVisibleWithoutNetworkRefresh() = runTest(dispatcher) {
        val cached = DASHBOARD.copy(cacheState = PageSpeedCacheState.CACHED_STALE)
        val gateway = FakeGateway(PageSpeedRestoreUi.Available(cached))
        val viewModel = PageSpeedViewModel(gateway)

        advanceUntilIdle()

        assertEquals(1, gateway.restoreCalls)
        assertEquals(0, gateway.refreshCalls)
        assertEquals(PageSpeedConnectionStatus.CONNECTED, viewModel.uiState.value.status)
        assertSame(cached, viewModel.uiState.value.dashboard)
        assertTrue(viewModel.uiState.value.isConnected)
    }

    @Test
    fun apiKeyIsAnEphemeralArgumentAndNeverAppearsInUiState() = runTest(dispatcher) {
        val secretText = "never-expose-google-api-key"
        val gateway = FakeGateway(PageSpeedRestoreUi.NotConnected)
        val viewModel = PageSpeedViewModel(gateway)
        advanceUntilIdle()

        viewModel.connect("https://example.com", SecretValue.of(secretText))
        advanceUntilIdle()

        assertEquals(1, gateway.connectCalls)
        assertEquals("https://example.com", gateway.lastSiteUrl)
        assertEquals(secretText, gateway.lastApiKey?.use { it })
        assertFalse(viewModel.uiState.value.toString().contains(secretText))
        assertEquals(PageSpeedConnectionStatus.CONNECTED, viewModel.uiState.value.status)
    }

    @Test
    fun failedRefreshPreservesCachedDashboardAndUsesSafeError() = runTest(dispatcher) {
        val cached = DASHBOARD.copy(cacheState = PageSpeedCacheState.CACHED_FRESH)
        val gateway = FakeGateway(PageSpeedRestoreUi.Available(cached)).apply {
            refreshResult = Result.failure(IOException("raw-network-detail"))
        }
        val viewModel = PageSpeedViewModel(gateway)
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertSame(cached, viewModel.uiState.value.dashboard)
        assertEquals("Showing the last saved result.", viewModel.uiState.value.notice)
        assertEquals(
            "PageSpeed & CrUX could not complete this request.",
            viewModel.uiState.value.error,
        )
        assertFalse(viewModel.uiState.value.toString().contains("raw-network-detail"))
    }

    @Test
    fun cancelStopsInFlightConnectAndDoesNotPublishLateResult() = runTest(dispatcher) {
        val gateway = FakeGateway(PageSpeedRestoreUi.NotConnected).apply {
            connectRelease = CompletableDeferred()
        }
        val viewModel = PageSpeedViewModel(gateway)
        advanceUntilIdle()

        viewModel.connect("https://example.com", SecretValue.of("key"))
        runCurrent()
        gateway.connectStarted.await()
        viewModel.cancelOperation()
        runCurrent()

        assertTrue(gateway.connectCancelled.await())
        assertEquals(PageSpeedConnectionStatus.DISCONNECTED, viewModel.uiState.value.status)
        assertEquals("Request cancelled.", viewModel.uiState.value.notice)
        assertNull(viewModel.uiState.value.dashboard)
    }

    @Test
    fun disconnectRequiresExplicitConfirmation() = runTest(dispatcher) {
        val gateway = FakeGateway(PageSpeedRestoreUi.Available(DASHBOARD))
        val viewModel = PageSpeedViewModel(gateway)
        advanceUntilIdle()

        viewModel.requestDisconnectConfirmation()
        runCurrent()
        assertTrue(viewModel.uiState.value.showDisconnectConfirmation)
        assertEquals(0, gateway.disconnectCalls)

        viewModel.dismissDisconnectConfirmation()
        assertFalse(viewModel.uiState.value.showDisconnectConfirmation)
        assertEquals(0, gateway.disconnectCalls)

        viewModel.requestDisconnectConfirmation()
        viewModel.confirmDisconnect()
        advanceUntilIdle()

        assertEquals(1, gateway.disconnectCalls)
        assertEquals(PageSpeedConnectionStatus.DISCONNECTED, viewModel.uiState.value.status)
        assertFalse(viewModel.uiState.value.isConnected)
    }

    @Test
    fun savedConnectionWithoutSnapshotRemainsConnectedAndRecoverable() = runTest(dispatcher) {
        val gateway = FakeGateway(
            PageSpeedRestoreUi.SavedWithoutSnapshot("https://example.com"),
        )
        val viewModel = PageSpeedViewModel(gateway)

        advanceUntilIdle()

        assertEquals(PageSpeedConnectionStatus.SAVED_UNAVAILABLE, viewModel.uiState.value.status)
        assertEquals("https://example.com", viewModel.uiState.value.savedSiteUrl)
        assertTrue(viewModel.uiState.value.isConnected)
        assertTrue(viewModel.uiState.value.canDisconnect)
    }

    private class FakeGateway(
        private val restored: PageSpeedRestoreUi,
    ) : PageSpeedUiGateway {
        var restoreCalls = 0
        var connectCalls = 0
        var refreshCalls = 0
        var disconnectCalls = 0
        var lastApiKey: SecretValue? = null
        var lastSiteUrl: String? = null
        var connectStarted = CompletableDeferred<Unit>()
        var connectRelease = CompletableDeferred(Unit)
        var connectCancelled = CompletableDeferred<Boolean>()
        var refreshResult: Result<PageSpeedDashboardUi> = Result.success(DASHBOARD)

        override suspend fun restore(): Result<PageSpeedRestoreUi> {
            restoreCalls += 1
            return Result.success(restored)
        }

        override suspend fun connect(
            apiKey: SecretValue,
            siteUrl: String,
        ): Result<PageSpeedDashboardUi> {
            connectCalls += 1
            lastApiKey = apiKey
            lastSiteUrl = siteUrl
            connectStarted.complete(Unit)
            return try {
                connectRelease.await()
                Result.success(DASHBOARD)
            } finally {
                if (!connectCancelled.isCompleted) {
                    connectCancelled.complete(!connectRelease.isCompleted)
                }
            }
        }

        override suspend fun refresh(): Result<PageSpeedDashboardUi> {
            refreshCalls += 1
            return refreshResult
        }

        override suspend fun disconnect(): Result<Unit> {
            disconnectCalls += 1
            return Result.success(Unit)
        }
    }

    private companion object {
        val DASHBOARD = PageSpeedDashboardUi(
            siteUrl = "https://example.com",
            siteName = "example.com",
            status = "Good",
            metrics = listOf(
                PageSpeedMetricUi(
                    key = "pagespeed.mobile.performance",
                    label = "Mobile Performance",
                    value = 96.0,
                    unit = PageSpeedMetricUnit.SCORE,
                    formattedValue = "96",
                ),
            ),
            fetchedAtMillis = 42L,
            sources = PageSpeedSourcesUi(
                mobile = PageSpeedSourceUiState.AVAILABLE,
                desktop = PageSpeedSourceUiState.AVAILABLE,
                crux = PageSpeedSourceUiState.AVAILABLE,
            ),
            warnings = emptyList(),
            cacheState = PageSpeedCacheState.LIVE,
        )
    }
}
