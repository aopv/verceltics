package com.apoorvdarshan.verceltics.ui.pagespeed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apoorvdarshan.verceltics.data.account.SecretValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PageSpeedConnectionStatus {
    RESTORING,
    DISCONNECTED,
    CONNECTED,
    SAVED_UNAVAILABLE,
}

enum class PageSpeedOperation {
    RESTORING,
    CONNECTING,
    REFRESHING,
    DISCONNECTING,
}

data class PageSpeedUiState(
    val status: PageSpeedConnectionStatus = PageSpeedConnectionStatus.RESTORING,
    val dashboard: PageSpeedDashboardUi? = null,
    val savedSiteUrl: String? = null,
    val operation: PageSpeedOperation? = PageSpeedOperation.RESTORING,
    val error: String? = null,
    val notice: String? = null,
    val showDisconnectConfirmation: Boolean = false,
    val canDisconnect: Boolean = false,
) {
    val isBusy: Boolean
        get() = operation != null

    val isConnected: Boolean
        get() = status == PageSpeedConnectionStatus.CONNECTED ||
            status == PageSpeedConnectionStatus.SAVED_UNAVAILABLE
}

/**
 * Lifecycle owner for PageSpeed state. API keys are method arguments only and are never copied into
 * [uiState], SavedStateHandle, error strings, or logs.
 */
class PageSpeedViewModel(
    private val gateway: PageSpeedUiGateway,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PageSpeedUiState())
    val uiState: StateFlow<PageSpeedUiState> = _uiState.asStateFlow()

    private var operationJob: Job? = null
    private var generation: Long = 0

    init {
        restore()
    }

    fun restore() {
        launchExclusive(PageSpeedOperation.RESTORING) { currentGeneration ->
            _uiState.value = PageSpeedUiState()
            gateway.restore().fold(
                onSuccess = { restored ->
                    if (isCurrent(currentGeneration)) applyRestore(restored)
                },
                onFailure = { error ->
                    if (isCurrent(currentGeneration)) {
                        _uiState.value = PageSpeedUiState(
                            status = PageSpeedConnectionStatus.SAVED_UNAVAILABLE,
                            operation = null,
                            error = safeMessage(error),
                            canDisconnect = true,
                        )
                    }
                },
            )
        }
    }

    fun connect(siteUrl: String, apiKey: SecretValue) {
        if (siteUrl.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Enter a complete HTTPS site URL.")
            return
        }
        launchExclusive(PageSpeedOperation.CONNECTING) { currentGeneration ->
            val previous = _uiState.value
            _uiState.value = previous.copy(
                operation = PageSpeedOperation.CONNECTING,
                error = null,
                notice = null,
                showDisconnectConfirmation = false,
            )
            gateway.connect(apiKey, siteUrl.trim()).fold(
                onSuccess = { dashboard ->
                    if (isCurrent(currentGeneration)) applyDashboard(dashboard)
                },
                onFailure = { error ->
                    if (isCurrent(currentGeneration)) {
                        _uiState.value = previous.copy(
                            operation = null,
                            error = safeMessage(error),
                            notice = null,
                            showDisconnectConfirmation = false,
                        )
                    }
                },
            )
        }
    }

    fun refresh() {
        val current = _uiState.value
        if (current.status == PageSpeedConnectionStatus.DISCONNECTED || current.isBusy) return
        launchExclusive(PageSpeedOperation.REFRESHING) { currentGeneration ->
            val visible = _uiState.value
            _uiState.value = visible.copy(
                operation = PageSpeedOperation.REFRESHING,
                error = null,
                notice = null,
            )
            gateway.refresh().fold(
                onSuccess = { dashboard ->
                    if (isCurrent(currentGeneration)) applyDashboard(dashboard)
                },
                onFailure = { error ->
                    if (isCurrent(currentGeneration)) {
                        _uiState.value = visible.copy(
                            operation = null,
                            error = safeMessage(error),
                            notice = "Showing the last saved result.",
                        )
                    }
                },
            )
        }
    }

    fun cancelOperation() {
        val operation = _uiState.value.operation
        if (operation != PageSpeedOperation.CONNECTING && operation != PageSpeedOperation.REFRESHING) {
            return
        }
        generation += 1
        operationJob?.cancel()
        operationJob = null
        _uiState.value = _uiState.value.copy(
            operation = null,
            error = null,
            notice = "Request cancelled.",
        )
    }

    fun requestDisconnectConfirmation() {
        if (_uiState.value.canDisconnect && !_uiState.value.isBusy) {
            _uiState.value = _uiState.value.copy(showDisconnectConfirmation = true)
        }
    }

    fun dismissDisconnectConfirmation() {
        _uiState.value = _uiState.value.copy(showDisconnectConfirmation = false)
    }

    fun confirmDisconnect() {
        if (!_uiState.value.canDisconnect) return
        launchExclusive(PageSpeedOperation.DISCONNECTING) { currentGeneration ->
            val previous = _uiState.value
            _uiState.value = previous.copy(
                operation = PageSpeedOperation.DISCONNECTING,
                error = null,
                notice = null,
                showDisconnectConfirmation = false,
            )
            gateway.disconnect().fold(
                onSuccess = {
                    if (isCurrent(currentGeneration)) {
                        _uiState.value = PageSpeedUiState(
                            status = PageSpeedConnectionStatus.DISCONNECTED,
                            operation = null,
                        )
                    }
                },
                onFailure = { error ->
                    if (isCurrent(currentGeneration)) {
                        _uiState.value = previous.copy(
                            operation = null,
                            error = safeMessage(error),
                            notice = null,
                            showDisconnectConfirmation = false,
                        )
                    }
                },
            )
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(error = null, notice = null)
    }

    private fun applyRestore(restored: PageSpeedRestoreUi) {
        _uiState.value = when (restored) {
            PageSpeedRestoreUi.NotConnected -> PageSpeedUiState(
                status = PageSpeedConnectionStatus.DISCONNECTED,
                operation = null,
            )
            is PageSpeedRestoreUi.Available -> PageSpeedUiState(
                status = PageSpeedConnectionStatus.CONNECTED,
                dashboard = restored.dashboard,
                savedSiteUrl = restored.dashboard.siteUrl,
                operation = null,
                canDisconnect = true,
            )
            is PageSpeedRestoreUi.SavedWithoutSnapshot -> PageSpeedUiState(
                status = PageSpeedConnectionStatus.SAVED_UNAVAILABLE,
                savedSiteUrl = restored.siteUrl,
                operation = null,
                notice = "This connection has no saved audit yet. Refresh when you are online.",
                canDisconnect = true,
            )
            is PageSpeedRestoreUi.SavedUnavailable -> PageSpeedUiState(
                status = PageSpeedConnectionStatus.SAVED_UNAVAILABLE,
                operation = null,
                error = restored.message,
                canDisconnect = restored.canDisconnect,
            )
        }
    }

    private fun applyDashboard(dashboard: PageSpeedDashboardUi) {
        _uiState.value = PageSpeedUiState(
            status = PageSpeedConnectionStatus.CONNECTED,
            dashboard = dashboard,
            savedSiteUrl = dashboard.siteUrl,
            operation = null,
            canDisconnect = true,
        )
    }

    private fun launchExclusive(
        operation: PageSpeedOperation,
        block: suspend (generation: Long) -> Unit,
    ) {
        generation += 1
        val currentGeneration = generation
        operationJob?.cancel()
        operationJob = viewModelScope.launch {
            try {
                block(currentGeneration)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(currentGeneration)) {
                    _uiState.value = _uiState.value.copy(
                        operation = null,
                        error = safeMessage(error),
                    )
                }
            }
        }
        if (operation == PageSpeedOperation.RESTORING) {
            _uiState.value = _uiState.value.copy(operation = PageSpeedOperation.RESTORING)
        }
    }

    private fun isCurrent(value: Long): Boolean = generation == value

    private fun safeMessage(error: Throwable): String =
        (error as? PageSpeedUiException)?.message
            ?: "PageSpeed & CrUX could not complete this request."

    override fun onCleared() {
        generation += 1
        operationJob?.cancel()
        operationJob = null
        super.onCleared()
    }

    class Factory(
        private val gateway: PageSpeedUiGateway,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PageSpeedViewModel::class.java)) {
                "Unsupported PageSpeed ViewModel class."
            }
            return PageSpeedViewModel(gateway) as T
        }
    }
}
