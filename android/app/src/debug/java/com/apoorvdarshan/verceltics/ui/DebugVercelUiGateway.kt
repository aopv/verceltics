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

    override suspend fun disconnect(): Result<Unit> = Result.success(
        DebugVercelGatewayController.disconnect(),
    )
}
