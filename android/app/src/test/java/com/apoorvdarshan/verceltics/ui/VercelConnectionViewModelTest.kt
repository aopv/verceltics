package com.apoorvdarshan.verceltics.ui

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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VercelConnectionViewModelTest {
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
    fun refreshCannotCancelOrOvertakeConnectMutation() = runTest(dispatcher) {
        val gateway = FakeGateway(restoreResult = VercelRestoreUi.NoSavedAccount)
        gateway.connectRelease = CompletableDeferred()
        val viewModel = VercelConnectionViewModel(gateway)
        advanceUntilIdle()

        viewModel.connect("test-token")
        runCurrent()
        gateway.connectStarted.await()

        viewModel.refresh()
        gateway.connectRelease.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, gateway.connectCalls)
        assertEquals(0, gateway.refreshCalls)
        assertEquals(VercelConnectionStatus.CONNECTED, viewModel.uiState.value.status)
        assertNull(viewModel.uiState.value.mutation)
    }

    @Test
    fun disconnectCompletesWhileRefreshRequestIsIgnored() = runTest(dispatcher) {
        val gateway = FakeGateway(
            restoreResult = VercelRestoreUi.Available(TEST_DASHBOARD),
        )
        gateway.disconnectRelease = CompletableDeferred()
        val viewModel = VercelConnectionViewModel(gateway)
        advanceUntilIdle()

        viewModel.disconnect()
        runCurrent()
        gateway.disconnectStarted.await()

        viewModel.refresh()
        gateway.disconnectRelease.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, gateway.disconnectCalls)
        assertEquals(0, gateway.refreshCalls)
        assertEquals(VercelConnectionStatus.DISCONNECTED, viewModel.uiState.value.status)
        assertNull(viewModel.uiState.value.dashboard)
    }

    @Test
    fun offlineSavedAccountNeverBecomesDisconnected() = runTest(dispatcher) {
        val gateway = FakeGateway(
            restoreResult = VercelRestoreUi.DashboardUnavailable(
                account = TEST_DASHBOARD.account,
                error = IOException("offline"),
            ),
        )
        val viewModel = VercelConnectionViewModel(gateway)
        advanceUntilIdle()

        assertEquals(VercelConnectionStatus.SAVED_UNAVAILABLE, viewModel.uiState.value.status)
        assertEquals(TEST_DASHBOARD.account, viewModel.uiState.value.savedAccount)
        assertEquals("offline", viewModel.uiState.value.error)
    }

    @Test
    fun projectAnalyticsLoadsRealGatewayResultAndTracksSelection() = runTest(dispatcher) {
        val gateway = FakeGateway(VercelRestoreUi.Available(TEST_DASHBOARD))
        val viewModel = VercelConnectionViewModel(gateway)
        advanceUntilIdle()

        viewModel.openProjectAnalytics(TEST_DASHBOARD.projects.single())
        advanceUntilIdle()

        assertEquals(1, gateway.analyticsCalls)
        assertEquals(VercelAnalyticsRange.WEEK, gateway.lastAnalyticsRange)
        assertEquals(VercelAnalyticsEnvironment.PRODUCTION, gateway.lastAnalyticsEnvironment)
        assertEquals(12_806L, viewModel.analyticsState.value.data?.overview?.pageViews)
        assertEquals(VercelAnalyticsRange.WEEK, viewModel.analyticsState.value.displayedRange)

        viewModel.selectAnalyticsRange(VercelAnalyticsRange.MONTH)
        advanceUntilIdle()

        assertEquals(2, gateway.analyticsCalls)
        assertEquals(VercelAnalyticsRange.MONTH, gateway.lastAnalyticsRange)
        assertEquals(VercelAnalyticsRange.MONTH, viewModel.analyticsState.value.displayedRange)
    }

    @Test
    fun failedRefreshKeepsLastSuccessfulAnalyticsVisible() = runTest(dispatcher) {
        val gateway = FakeGateway(VercelRestoreUi.Available(TEST_DASHBOARD))
        val viewModel = VercelConnectionViewModel(gateway)
        advanceUntilIdle()
        viewModel.openProjectAnalytics(TEST_DASHBOARD.projects.single())
        advanceUntilIdle()
        assertNotNull(viewModel.analyticsState.value.data)

        gateway.analyticsResult = Result.failure(IOException("analytics offline"))
        viewModel.refreshProjectAnalytics()
        advanceUntilIdle()

        assertEquals("analytics offline", viewModel.analyticsState.value.error)
        assertNotNull(viewModel.analyticsState.value.data)
        assertEquals(VercelAnalyticsRange.WEEK, viewModel.analyticsState.value.displayedRange)
    }

    @Test
    fun unavailableAnalyticsIsTruthfulAndContainsNoPlaceholderData() = runTest(dispatcher) {
        val gateway = FakeGateway(VercelRestoreUi.Available(TEST_DASHBOARD))
        gateway.analyticsResult = Result.success(
            VercelAnalyticsLoadUi.Unavailable("Web Analytics is not enabled."),
        )
        val viewModel = VercelConnectionViewModel(gateway)
        advanceUntilIdle()

        viewModel.openProjectAnalytics(TEST_DASHBOARD.projects.single())
        advanceUntilIdle()

        assertNull(viewModel.analyticsState.value.data)
        assertEquals("Web Analytics is not enabled.", viewModel.analyticsState.value.unavailableMessage)
        assertEquals(VercelAnalyticsRange.WEEK, viewModel.analyticsState.value.displayedRange)
    }

    @Test
    fun changingAnalyticsSelectionCancelsOlderRequestAndOnlyAppliesLatest() = runTest(dispatcher) {
        val gateway = FakeGateway(VercelRestoreUi.Available(TEST_DASHBOARD))
        gateway.analyticsRelease = CompletableDeferred()
        val viewModel = VercelConnectionViewModel(gateway)
        advanceUntilIdle()

        viewModel.openProjectAnalytics(TEST_DASHBOARD.projects.single())
        runCurrent()
        viewModel.selectAnalyticsRange(VercelAnalyticsRange.MONTH)
        runCurrent()
        gateway.analyticsRelease?.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, gateway.analyticsCalls)
        assertEquals(VercelAnalyticsRange.MONTH, viewModel.analyticsState.value.displayedRange)
        assertEquals(VercelAnalyticsRange.MONTH, gateway.lastAnalyticsRange)
    }

    private class FakeGateway(
        private val restoreResult: VercelRestoreUi,
    ) : VercelUiGateway {
        var connectCalls = 0
        var refreshCalls = 0
        var disconnectCalls = 0
        var connectStarted = CompletableDeferred<Unit>()
        var connectRelease = CompletableDeferred(Unit)
        var disconnectStarted = CompletableDeferred<Unit>()
        var disconnectRelease = CompletableDeferred(Unit)
        var analyticsCalls = 0
        var lastAnalyticsRange: VercelAnalyticsRange? = null
        var lastAnalyticsEnvironment: VercelAnalyticsEnvironment? = null
        var analyticsResult: Result<VercelAnalyticsLoadUi> = Result.success(TEST_ANALYTICS)
        var analyticsRelease: CompletableDeferred<Unit>? = null

        override suspend fun restore(): Result<VercelRestoreUi> = Result.success(restoreResult)

        override suspend fun connect(personalToken: String): Result<VercelDashboardUi> {
            connectCalls += 1
            connectStarted.complete(Unit)
            connectRelease.await()
            return Result.success(TEST_DASHBOARD)
        }

        override suspend fun refresh(): Result<VercelDashboardUi> {
            refreshCalls += 1
            return Result.success(TEST_DASHBOARD)
        }

        override suspend fun loadProjectAnalytics(
            project: VercelProjectUi,
            range: VercelAnalyticsRange,
            environment: VercelAnalyticsEnvironment,
        ): Result<VercelAnalyticsLoadUi> {
            analyticsCalls += 1
            lastAnalyticsRange = range
            lastAnalyticsEnvironment = environment
            analyticsRelease?.await()
            return analyticsResult
        }

        override suspend fun disconnect(): Result<Unit> {
            disconnectCalls += 1
            disconnectStarted.complete(Unit)
            disconnectRelease.await()
            return Result.success(Unit)
        }
    }

    private companion object {
        val TEST_DASHBOARD = VercelDashboardUi(
            account = VercelAccountUi("Apoorv Test", "apoorv@example.com"),
            projects = listOf(VercelProjectUi("project", "verceltics", "Next.js", null)),
        )
        val TEST_ANALYTICS = VercelAnalyticsLoadUi.Available(
            VercelAnalyticsDataUi(
                overview = VercelAnalyticsOverviewUi(12_806, 2_104, 42.0),
                previousOverview = VercelAnalyticsOverviewUi(11_000, 1_900, 44.0),
                timeseries = listOf(VercelAnalyticsPointUi("2026-08-27", 12_806, 2_104)),
                pages = listOf(VercelAnalyticsBreakdownUi("/", 8_000, 1_200)),
                referrers = emptyList(),
                countries = emptyList(),
            ),
        )
    }
}
