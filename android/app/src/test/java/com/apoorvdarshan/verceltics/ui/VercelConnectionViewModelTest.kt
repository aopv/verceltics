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
    }
}
