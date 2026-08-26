package com.apoorvdarshan.verceltics.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class VercelConnectionStatus {
    RESTORING,
    DISCONNECTED,
    CONNECTED,
    SAVED_UNAVAILABLE,
}

enum class VercelConnectionMutation {
    CONNECTING,
    DISCONNECTING,
}

data class VercelConnectionUiState(
    val status: VercelConnectionStatus = VercelConnectionStatus.RESTORING,
    val dashboard: VercelDashboardUi? = null,
    val savedAccount: VercelAccountUi? = null,
    val error: String? = null,
    val isRefreshing: Boolean = false,
    val mutation: VercelConnectionMutation? = null,
) {
    val isBusy: Boolean
        get() = status == VercelConnectionStatus.RESTORING || isRefreshing || mutation != null

    val isSearchAvailable: Boolean
        get() = status == VercelConnectionStatus.CONNECTED && dashboard != null
}

/**
 * Activity-scoped owner for Vercel connection state and provider operations.
 *
 * The ViewModel survives navigation and configuration changes. A single mutex serializes gateway
 * work; refresh may be skipped while a credential mutation is active, but it never cancels that
 * mutation. Connect and disconnect may cancel a read-only refresh before taking the mutex.
 */
class VercelConnectionViewModel(
    private val gateway: VercelUiGateway,
) : ViewModel() {
    private val operationMutex = Mutex()
    private val _uiState = MutableStateFlow(VercelConnectionUiState())
    val uiState: StateFlow<VercelConnectionUiState> = _uiState.asStateFlow()

    private var restoreJob: Job? = null
    private var refreshJob: Job? = null
    private var mutationJob: Job? = null

    init {
        restore()
    }

    fun restore() {
        if (mutationJob?.isActive == true) return
        refreshJob?.cancel()
        restoreJob?.cancel()
        restoreJob = viewModelScope.launch {
            operationMutex.withLock {
                if (mutationJob?.isActive == true) return@withLock
                _uiState.value = VercelConnectionUiState()
                gateway.restore().fold(
                    onSuccess = ::applyRestore,
                    onFailure = { error ->
                        _uiState.value = VercelConnectionUiState(
                            status = VercelConnectionStatus.SAVED_UNAVAILABLE,
                            error = messageOf(error),
                        )
                    },
                )
            }
        }
    }

    fun refresh() {
        if (
            mutationJob?.isActive == true ||
            restoreJob?.isActive == true ||
            refreshJob?.isActive == true ||
            _uiState.value.status == VercelConnectionStatus.DISCONNECTED
        ) {
            return
        }
        refreshJob = viewModelScope.launch {
            operationMutex.withLock {
                if (mutationJob?.isActive == true) return@withLock
                _uiState.update { it.copy(isRefreshing = true, error = null) }
                gateway.refresh().fold(
                    onSuccess = { dashboard ->
                        _uiState.value = connectedState(dashboard)
                    },
                    onFailure = { error ->
                        _uiState.update { current ->
                            if (current.dashboard != null) {
                                current.copy(
                                    status = VercelConnectionStatus.CONNECTED,
                                    isRefreshing = false,
                                    error = messageOf(error),
                                )
                            } else {
                                current.copy(
                                    status = VercelConnectionStatus.SAVED_UNAVAILABLE,
                                    isRefreshing = false,
                                    error = messageOf(error),
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    fun connect(personalToken: String) {
        if (personalToken.isBlank()) {
            _uiState.update { it.copy(error = "Enter a Vercel personal access token.") }
            return
        }
        launchMutation(VercelConnectionMutation.CONNECTING) {
            gateway.connect(personalToken).fold(
                onSuccess = { dashboard ->
                    _uiState.value = connectedState(
                        dashboard = dashboard,
                        mutation = VercelConnectionMutation.CONNECTING,
                    )
                },
                onFailure = { error ->
                    _uiState.update { it.copy(error = messageOf(error)) }
                },
            )
        }
    }

    fun disconnect() {
        launchMutation(VercelConnectionMutation.DISCONNECTING) {
            gateway.disconnect().fold(
                onSuccess = {
                    _uiState.value = VercelConnectionUiState(
                        status = VercelConnectionStatus.DISCONNECTED,
                        mutation = VercelConnectionMutation.DISCONNECTING,
                    )
                },
                onFailure = { error ->
                    _uiState.update { it.copy(error = messageOf(error)) }
                },
            )
        }
    }

    private fun launchMutation(
        mutation: VercelConnectionMutation,
        operation: suspend () -> Unit,
    ) {
        if (mutationJob?.isActive == true) return
        refreshJob?.cancel()
        restoreJob?.cancel()
        mutationJob = viewModelScope.launch {
            operationMutex.withLock {
                _uiState.update {
                    it.copy(
                        mutation = mutation,
                        isRefreshing = false,
                        error = null,
                    )
                }
                try {
                    operation()
                } finally {
                    _uiState.update { it.copy(mutation = null) }
                }
            }
        }
    }

    private fun applyRestore(restored: VercelRestoreUi) {
        _uiState.value = when (restored) {
            VercelRestoreUi.NoSavedAccount -> VercelConnectionUiState(
                status = VercelConnectionStatus.DISCONNECTED,
            )

            is VercelRestoreUi.Available -> connectedState(restored.dashboard)
            is VercelRestoreUi.DashboardUnavailable -> VercelConnectionUiState(
                status = VercelConnectionStatus.SAVED_UNAVAILABLE,
                savedAccount = restored.account,
                error = messageOf(restored.error),
            )
        }
    }

    private fun connectedState(
        dashboard: VercelDashboardUi,
        mutation: VercelConnectionMutation? = null,
    ): VercelConnectionUiState = VercelConnectionUiState(
        status = VercelConnectionStatus.CONNECTED,
        dashboard = dashboard,
        savedAccount = dashboard.account,
        mutation = mutation,
    )

    private fun messageOf(error: Throwable): String =
        error.message?.takeIf(String::isNotBlank) ?: "The Vercel request could not be completed."

    class Factory(
        private val gateway: VercelUiGateway,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(VercelConnectionViewModel::class.java)) {
                "Unsupported ViewModel class: ${modelClass.name}"
            }
            return VercelConnectionViewModel(gateway) as T
        }
    }
}
