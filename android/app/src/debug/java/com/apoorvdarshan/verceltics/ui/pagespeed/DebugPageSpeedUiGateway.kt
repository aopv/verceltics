package com.apoorvdarshan.verceltics.ui.pagespeed

import com.apoorvdarshan.verceltics.data.account.SecretValue
import com.apoorvdarshan.verceltics.data.pagespeed.PageSpeedMetricUnit

enum class DebugPageSpeedScenario {
    DISCONNECTED,
    CONNECTED,
    OFFLINE_SAVED,
}

/** Deterministic, in-process PageSpeed backend used only by the debug instrumentation host. */
object DebugPageSpeedGatewayController {
    @Volatile
    var scenario: DebugPageSpeedScenario = DebugPageSpeedScenario.DISCONNECTED
        private set

    @Volatile
    var restoreCalls: Int = 0
        private set

    @Volatile
    var connectCalls: Int = 0
        private set

    @Volatile
    var refreshCalls: Int = 0
        private set

    @Volatile
    var disconnectCalls: Int = 0
        private set

    @Synchronized
    fun configure(scenario: DebugPageSpeedScenario) {
        this.scenario = scenario
        restoreCalls = 0
        connectCalls = 0
        refreshCalls = 0
        disconnectCalls = 0
    }

    internal fun restored(): PageSpeedRestoreUi {
        restoreCalls += 1
        return when (scenario) {
            DebugPageSpeedScenario.DISCONNECTED -> PageSpeedRestoreUi.NotConnected
            DebugPageSpeedScenario.CONNECTED -> PageSpeedRestoreUi.Available(cachedDashboard)
            DebugPageSpeedScenario.OFFLINE_SAVED -> PageSpeedRestoreUi.Available(
                cachedDashboard.copy(cacheState = PageSpeedCacheState.CACHED_STALE),
            )
        }
    }

    internal fun connected(siteUrl: String): PageSpeedDashboardUi {
        connectCalls += 1
        scenario = DebugPageSpeedScenario.CONNECTED
        return liveDashboard.copy(
            siteUrl = siteUrl,
            siteName = siteUrl.substringAfter("://").substringBefore('/'),
        )
    }

    internal fun refreshed(): Result<PageSpeedDashboardUi> {
        refreshCalls += 1
        return when (scenario) {
            DebugPageSpeedScenario.CONNECTED -> Result.success(liveDashboard)
            DebugPageSpeedScenario.OFFLINE_SAVED -> Result.failure(
                PageSpeedUiException("The test provider is offline. The saved audit remains available."),
            )
            DebugPageSpeedScenario.DISCONNECTED -> Result.failure(
                PageSpeedUiException("Connect PageSpeed & CrUX first."),
            )
        }
    }

    internal fun disconnected() {
        disconnectCalls += 1
        scenario = DebugPageSpeedScenario.DISCONNECTED
    }

    private val liveDashboard = PageSpeedDashboardUi(
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
            PageSpeedMetricUi(
                key = "pagespeed.desktop.performance",
                label = "Desktop Performance",
                value = 92.0,
                unit = PageSpeedMetricUnit.SCORE,
                formattedValue = "92",
            ),
            PageSpeedMetricUi(
                key = "crux.largest_contentful_paint",
                label = "LCP (Page field p75)",
                value = 1_480.0,
                unit = PageSpeedMetricUnit.MILLISECONDS,
                formattedValue = "1.48 s",
            ),
        ),
        fetchedAtMillis = 1_700_000_000_000,
        sources = PageSpeedSourcesUi(
            mobile = PageSpeedSourceUiState.AVAILABLE,
            desktop = PageSpeedSourceUiState.AVAILABLE,
            crux = PageSpeedSourceUiState.AVAILABLE,
        ),
        warnings = emptyList(),
        cacheState = PageSpeedCacheState.LIVE,
    )

    private val cachedDashboard = liveDashboard.copy(cacheState = PageSpeedCacheState.CACHED_FRESH)
}

class DebugPageSpeedUiGateway : PageSpeedUiGateway {
    override suspend fun restore(): Result<PageSpeedRestoreUi> =
        Result.success(DebugPageSpeedGatewayController.restored())

    override suspend fun connect(
        apiKey: SecretValue,
        siteUrl: String,
    ): Result<PageSpeedDashboardUi> {
        apiKey.use { require(it.isNotBlank()) }
        return Result.success(DebugPageSpeedGatewayController.connected(siteUrl))
    }

    override suspend fun refresh(): Result<PageSpeedDashboardUi> =
        DebugPageSpeedGatewayController.refreshed()

    override suspend fun disconnect(): Result<Unit> = Result.success(
        DebugPageSpeedGatewayController.disconnected(),
    )
}
