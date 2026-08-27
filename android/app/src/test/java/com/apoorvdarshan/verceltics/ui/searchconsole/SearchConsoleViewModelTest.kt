package com.apoorvdarshan.verceltics.ui.searchconsole

import androidx.lifecycle.SavedStateHandle
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
class SearchConsoleViewModelTest {
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
    fun missingOAuthConfigurationStaysTruthfullyPaused() = runTest(dispatcher) {
        val readiness = SearchConsoleOAuthReadinessUi.ConfigurationNeeded("Add Android OAuth config.")
        val gateway = FakeGateway(SearchConsoleRestoreUi.NotConnected, readiness)
        val viewModel = SearchConsoleViewModel(gateway, SavedStateHandle())
        advanceUntilIdle()

        viewModel.connect()
        advanceUntilIdle()

        assertEquals(SearchConsoleConnectionStatus.DISCONNECTED, viewModel.uiState.value.status)
        assertSame(readiness, viewModel.uiState.value.oauthReadiness)
        assertEquals(0, gateway.connectCalls)
    }

    @Test
    fun unexpectedRestoreFailureDoesNotInventSavedConnection() = runTest(dispatcher) {
        val gateway = FakeGateway(SearchConsoleRestoreUi.NotConnected).apply {
            restoreFailure = SearchConsoleUiException("Search Console storage could not be checked.")
        }
        val viewModel = SearchConsoleViewModel(gateway, SavedStateHandle())
        advanceUntilIdle()

        assertEquals(SearchConsoleConnectionStatus.DISCONNECTED, viewModel.uiState.value.status)
        assertFalse(viewModel.uiState.value.isConnected)
        assertNull(viewModel.uiState.value.savedAccount)
        assertEquals(
            "Search Console storage could not be checked.",
            viewModel.uiState.value.error,
        )
    }

    @Test
    fun cachedRestoreRefreshesOnlyOnceAfterForeground() = runTest(dispatcher) {
        val cached = DASHBOARD.copy(cacheState = SearchConsoleCacheState.CACHED_STALE)
        val gateway = FakeGateway(SearchConsoleRestoreUi.Available(cached))
        val viewModel = SearchConsoleViewModel(gateway, SavedStateHandle())
        viewModel.onForeground()
        advanceUntilIdle()

        assertEquals(1, gateway.restoreCalls)
        assertEquals(1, gateway.refreshCalls)
        assertEquals(SearchConsoleCacheState.LIVE, viewModel.uiState.value.dashboard?.cacheState)

        viewModel.onBackground()
        viewModel.onForeground()
        advanceUntilIdle()
        assertEquals(1, gateway.refreshCalls)
    }

    @Test
    fun propertySelectionRestoresAndKeepsDetailModesIndependent() = runTest(dispatcher) {
        val savedState = SavedStateHandle(
            mapOf(
                SearchConsoleViewModel.SELECTED_PROPERTY_URL to PROPERTY.siteUrl,
                SearchConsoleViewModel.SELECTED_SECTION to SearchConsoleDetailSection.SITEMAPS.name,
            ),
        )
        val gateway = FakeGateway(SearchConsoleRestoreUi.Available(DASHBOARD))
        val viewModel = SearchConsoleViewModel(gateway, savedState)
        advanceUntilIdle()

        assertEquals(PROPERTY.siteUrl, viewModel.uiState.value.selectedPropertyUrl)
        assertEquals(SearchConsoleDetailSection.SITEMAPS, viewModel.uiState.value.selectedSection)
        assertSame(WORKSPACE, viewModel.uiState.value.propertyWorkspace)

        viewModel.selectSection(SearchConsoleDetailSection.INSPECT)
        viewModel.updateInspectionUrl("https://example.com/article")
        viewModel.inspectUrl()
        advanceUntilIdle()

        assertEquals(SearchConsoleDetailSection.INSPECT, viewModel.uiState.value.selectedSection)
        assertSame(INSPECTION, viewModel.uiState.value.inspection)
        assertEquals(1, gateway.inspectCalls)
        assertEquals("https://example.com/article", gateway.lastInspectionUrl)
    }

    @Test
    fun propertySearchAndDisconnectDoNotLeaveStaleSelection() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val gateway = FakeGateway(SearchConsoleRestoreUi.Available(DASHBOARD))
        val viewModel = SearchConsoleViewModel(gateway, savedState)
        advanceUntilIdle()

        viewModel.updatePropertySearch("missing")
        assertTrue(viewModel.uiState.value.visibleProperties.isEmpty())
        viewModel.openProperty(PROPERTY.siteUrl)
        advanceUntilIdle()
        viewModel.requestDisconnectConfirmation()
        viewModel.confirmDisconnect()
        advanceUntilIdle()

        assertEquals(SearchConsoleConnectionStatus.DISCONNECTED, viewModel.uiState.value.status)
        assertNull(viewModel.uiState.value.selectedPropertyUrl)
        assertNull(savedState.get<String>(SearchConsoleViewModel.SELECTED_PROPERTY_URL))
        assertEquals(1, gateway.disconnectCalls)
    }

    @Test
    fun cancelAuthorizationReconcilesDurableResultWithoutObservableSecrets() = runTest(dispatcher) {
        val gateway = FakeGateway(SearchConsoleRestoreUi.NotConnected).apply {
            connectRelease = CompletableDeferred()
        }
        val viewModel = SearchConsoleViewModel(gateway, SavedStateHandle())
        advanceUntilIdle()
        viewModel.setRouteVisible(true)

        viewModel.connect()
        runCurrent()
        assertTrue(viewModel.uiState.value.requiresSecureWindow)
        gateway.connectStarted.await()
        viewModel.cancelOperation()
        advanceUntilIdle()

        assertEquals(SearchConsoleConnectionStatus.DISCONNECTED, viewModel.uiState.value.status)
        assertEquals("Google authorization cancelled.", viewModel.uiState.value.notice)
        assertFalse(viewModel.uiState.value.requiresSecureWindow)
        assertFalse(viewModel.uiState.value.toString().contains("authorization-code"))
    }

    @Test
    fun performanceQueryIsForwardedAndUpdatesOnlyPerformanceResource() = runTest(dispatcher) {
        val gateway = FakeGateway(SearchConsoleRestoreUi.Available(DASHBOARD))
        val viewModel = SearchConsoleViewModel(gateway, SavedStateHandle())
        advanceUntilIdle()
        viewModel.openProperty(PROPERTY.siteUrl)
        advanceUntilIdle()

        val query = viewModel.uiState.value.performanceQuery.copy(
            searchType = SearchConsoleSearchTypeUi.IMAGE,
            dimensions = listOf(SearchConsoleDimensionUi.PAGE, SearchConsoleDimensionUi.COUNTRY),
            sortField = SearchConsoleSortFieldUi.IMPRESSIONS,
        )
        viewModel.applyPerformanceQuery(query)
        advanceUntilIdle()

        assertEquals(query, gateway.lastPerformanceQuery)
        assertEquals(query, viewModel.uiState.value.performanceQuery)
        assertSame(WORKSPACE.sitemaps, viewModel.uiState.value.propertyWorkspace?.sitemaps)
        assertFalse(viewModel.uiState.value.isLoadingPerformance)
    }

    @Test
    fun contextualSearchClosesDetailFocusesOnceAndExplainsUnavailableSearch() = runTest(dispatcher) {
        val savedState = SavedStateHandle(
            mapOf(SearchConsoleViewModel.SELECTED_PROPERTY_URL to PROPERTY.siteUrl),
        )
        val gateway = FakeGateway(SearchConsoleRestoreUi.Available(DASHBOARD))
        val viewModel = SearchConsoleViewModel(gateway, savedState)
        advanceUntilIdle()

        viewModel.handleSearchRequest(9)
        assertNull(viewModel.uiState.value.selectedPropertyUrl)
        assertTrue(viewModel.uiState.value.shouldFocusPropertySearch)
        viewModel.acknowledgePropertySearchFocus()
        viewModel.handleSearchRequest(9)
        assertFalse(viewModel.uiState.value.shouldFocusPropertySearch)

        val unavailableGateway =
            FakeGateway(SearchConsoleRestoreUi.SavedUnavailable("Secure storage is locked."))
        val unavailable = SearchConsoleViewModel(unavailableGateway, SavedStateHandle())
        advanceUntilIdle()
        unavailable.handleSearchRequest(3)
        assertTrue(unavailable.uiState.value.shouldFocusPropertySearch)
        assertTrue(unavailable.uiState.value.notice.orEmpty().contains("Refresh the saved Google"))

        unavailable.refresh()
        advanceUntilIdle()
        assertEquals(SearchConsoleConnectionStatus.CONNECTED, unavailable.uiState.value.status)
        assertTrue(unavailable.uiState.value.shouldFocusPropertySearch)
        assertEquals(1, unavailableGateway.refreshCalls)

        val disconnected = SearchConsoleViewModel(
            FakeGateway(SearchConsoleRestoreUi.NotConnected),
            SavedStateHandle(),
        )
        advanceUntilIdle()
        disconnected.handleSearchRequest(4)
        assertFalse(disconnected.uiState.value.isConnected)
        assertFalse(disconnected.uiState.value.shouldFocusPropertySearch)
        assertEquals(
            "Connect Google Search Console to search verified properties.",
            disconnected.uiState.value.notice,
        )
    }

    private class FakeGateway(
        var restored: SearchConsoleRestoreUi,
        override val oauthReadiness: SearchConsoleOAuthReadinessUi = SearchConsoleOAuthReadinessUi.Ready,
    ) : SearchConsoleUiGateway {
        var restoreCalls = 0
        var connectCalls = 0
        var refreshCalls = 0
        var inspectCalls = 0
        var disconnectCalls = 0
        var lastInspectionUrl: String? = null
        var lastPerformanceQuery: SearchConsolePerformanceQueryUi? = null
        var connectStarted = CompletableDeferred<Unit>()
        var connectRelease = CompletableDeferred<Unit>().apply { complete(Unit) }
        var restoreFailure: Throwable? = null

        override suspend fun restore(): Result<SearchConsoleRestoreUi> {
            restoreCalls += 1
            val failure = restoreFailure
            return if (failure != null) Result.failure(failure) else Result.success(restored)
        }

        override suspend fun connect(): Result<SearchConsoleDashboardUi> {
            connectCalls += 1
            connectStarted.complete(Unit)
            connectRelease.await()
            restored = SearchConsoleRestoreUi.Available(DASHBOARD)
            return Result.success(DASHBOARD)
        }

        override suspend fun refresh(): Result<SearchConsoleDashboardUi> {
            refreshCalls += 1
            return Result.success(DASHBOARD.copy(cacheState = SearchConsoleCacheState.LIVE))
        }

        override suspend fun loadProperty(
            property: SearchConsolePropertyUi,
            performanceQuery: SearchConsolePerformanceQueryUi,
        ): Result<SearchConsolePropertyWorkspaceUi> = Result.success(WORKSPACE)

        override suspend fun loadPerformance(
            siteUrl: String,
            query: SearchConsolePerformanceQueryUi,
        ): Result<SearchConsoleResourceUi<SearchConsolePerformanceUi>> {
            lastPerformanceQuery = query
            return Result.success(WORKSPACE.performance)
        }

        override suspend fun inspect(
            siteUrl: String,
            inspectionUrl: String,
        ): Result<SearchConsoleInspectionUi> {
            inspectCalls += 1
            lastInspectionUrl = inspectionUrl
            return Result.success(INSPECTION)
        }

        override suspend fun disconnect(): Result<Unit> {
            disconnectCalls += 1
            restored = SearchConsoleRestoreUi.NotConnected
            return Result.success(Unit)
        }
    }

    companion object {
        private val PROPERTY = SearchConsolePropertyUi("sc-domain:example.com", "example.com", "Owner")
        private val DASHBOARD = SearchConsoleDashboardUi(
            account = SearchConsoleAccountUi("subject", "owner@example.com"),
            properties = listOf(PROPERTY),
            loadedPropertyCount = 1,
            providerInventoryComplete = true,
            inventoryTruncatedForDisplay = false,
            warnings = emptyList(),
            fetchedAtMillis = 42,
            cacheState = SearchConsoleCacheState.CACHED_FRESH,
        )
        private val WORKSPACE = SearchConsolePropertyWorkspaceUi(
            property = PROPERTY,
            performance = SearchConsoleResourceUi.Available(
                SearchConsolePerformanceUi(
                    clicks = 1.0,
                    impressions = 10.0,
                    ctr = 0.1,
                    position = 2.0,
                    timeline = emptyList(),
                    breakdownRows = emptyList(),
                    loadedBreakdownRowCount = 0,
                    hasPreviousPage = false,
                    hasNextPage = false,
                    firstIncompleteDate = null,
                    firstIncompleteHour = null,
                ),
            ),
            sitemaps = SearchConsoleResourceUi.Available(emptyList()),
        )
        private val INSPECTION = SearchConsoleInspectionUi(
            inspectionResultLink = null,
            verdict = "PASS",
            coverageState = "Indexed",
            indexingState = "ALLOWED",
            robotsTxtState = "ALLOWED",
            pageFetchState = "SUCCESSFUL",
            lastCrawlTime = null,
            googleCanonical = null,
            userCanonical = null,
            crawledAs = null,
            sitemaps = emptyList(),
            referringUrls = emptyList(),
            ampVerdict = null,
            mobileVerdict = "PASS",
            richResultsVerdict = null,
            issues = emptyList(),
        )
    }
}
