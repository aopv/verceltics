package com.apoorvdarshan.verceltics.ui

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred

enum class DebugVercelScenario {
    DISCONNECTED,
    CONNECTED,
    OFFLINE_SAVED,
}

/** Deterministic in-process provider backend used only by debug instrumentation hosts. */
object DebugVercelGatewayController {
    @Volatile
    var scenario: DebugVercelScenario = DebugVercelScenario.DISCONNECTED
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

    @Volatile
    private var connectStarted = CompletableDeferred<Unit>()

    @Volatile
    private var connectRelease = CompletableDeferred<Unit>().apply { complete(Unit) }

    @Synchronized
    fun configure(
        scenario: DebugVercelScenario,
        blockConnect: Boolean = false,
    ) {
        this.scenario = scenario
        connectCalls = 0
        refreshCalls = 0
        disconnectCalls = 0
        connectStarted = CompletableDeferred()
        connectRelease = CompletableDeferred<Unit>().also { gate ->
            if (!blockConnect) gate.complete(Unit)
        }
    }

    fun isConnectStarted(): Boolean = connectStarted.isCompleted

    fun releaseConnect() {
        connectRelease.complete(Unit)
    }

    internal suspend fun connect(): VercelDashboardUi {
        connectCalls += 1
        connectStarted.complete(Unit)
        connectRelease.await()
        scenario = DebugVercelScenario.CONNECTED
        return dashboard
    }

    internal fun refresh(): Result<VercelDashboardUi> {
        refreshCalls += 1
        return when (scenario) {
            DebugVercelScenario.CONNECTED -> Result.success(dashboard)
            DebugVercelScenario.OFFLINE_SAVED -> Result.failure(IOException(OFFLINE_MESSAGE))
            DebugVercelScenario.DISCONNECTED -> Result.failure(
                IllegalStateException("Connect a Vercel account first."),
            )
        }
    }

    internal fun disconnect() {
        disconnectCalls += 1
        scenario = DebugVercelScenario.DISCONNECTED
    }

    val dashboard = VercelDashboardUi(
        account = VercelAccountUi(
            displayName = "Apoorv Test",
            email = "apoorv@example.com",
        ),
        projects = listOf(
            VercelProjectUi(
                id = "test-project",
                name = "verceltics.app",
                framework = "Next.js",
                updatedAtMillis = 1_700_000_000_000,
            ),
        ),
    )

    const val OFFLINE_MESSAGE = "Saved account is offline for this test."
}

class DebugVercelUiGateway : VercelUiGateway {
    override suspend fun restore(): Result<VercelRestoreUi> = Result.success(
        when (DebugVercelGatewayController.scenario) {
            DebugVercelScenario.DISCONNECTED -> VercelRestoreUi.NoSavedAccount
            DebugVercelScenario.CONNECTED -> VercelRestoreUi.Available(
                DebugVercelGatewayController.dashboard,
            )

            DebugVercelScenario.OFFLINE_SAVED -> VercelRestoreUi.DashboardUnavailable(
                account = DebugVercelGatewayController.dashboard.account,
                error = IOException(DebugVercelGatewayController.OFFLINE_MESSAGE),
            )
        },
    )

    override suspend fun connect(personalToken: String): Result<VercelDashboardUi> =
        Result.success(DebugVercelGatewayController.connect())

    override suspend fun refresh(): Result<VercelDashboardUi> =
        DebugVercelGatewayController.refresh()

    override suspend fun loadProjectAnalytics(
        project: VercelProjectUi,
        range: VercelAnalyticsRange,
        environment: VercelAnalyticsEnvironment,
    ): Result<VercelAnalyticsLoadUi> = Result.success(
        VercelAnalyticsLoadUi.Available(
            VercelAnalyticsDataUi(
                overview = VercelAnalyticsOverviewUi(
                    pageViews = 12_806,
                    visitors = 2_104,
                    bounceRate = 42.0,
                ),
                previousOverview = VercelAnalyticsOverviewUi(
                    pageViews = 11_920,
                    visitors = 1_970,
                    bounceRate = 45.0,
                ),
                timeseries = listOf(
                    VercelAnalyticsPointUi("2026-08-21", 1_320, 240),
                    VercelAnalyticsPointUi("2026-08-22", 2_410, 390),
                    VercelAnalyticsPointUi("2026-08-23", 1_860, 310),
                    VercelAnalyticsPointUi("2026-08-24", 2_920, 480),
                    VercelAnalyticsPointUi("2026-08-25", 2_150, 360),
                    VercelAnalyticsPointUi("2026-08-26", 2_146, 324),
                ),
                pages = listOf(
                    VercelAnalyticsBreakdownUi("/", 7_840, 1_380),
                    VercelAnalyticsBreakdownUi("/pricing", 2_946, 510),
                ),
                referrers = listOf(
                    VercelAnalyticsBreakdownUi("google.com", 5_760, 980),
                    VercelAnalyticsBreakdownUi("", 4_040, 720),
                ),
                countries = listOf(
                    VercelAnalyticsBreakdownUi("IN", 5_500, 920),
                    VercelAnalyticsBreakdownUi("US", 3_920, 640),
                ),
            ),
        ),
    )

    override suspend fun disconnect(): Result<Unit> = Result.success(
        DebugVercelGatewayController.disconnect(),
    )
}
