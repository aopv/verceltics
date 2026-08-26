package com.apoorvdarshan.verceltics.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

data class VercelAnalyticsUiState(
    val projectId: String? = null,
    val selectedRange: VercelAnalyticsRange = VercelAnalyticsRange.WEEK,
    val selectedEnvironment: VercelAnalyticsEnvironment = VercelAnalyticsEnvironment.PRODUCTION,
    val displayedRange: VercelAnalyticsRange? = null,
    val displayedEnvironment: VercelAnalyticsEnvironment? = null,
    val data: VercelAnalyticsDataUi? = null,
    val unavailableMessage: String? = null,
    val error: String? = null,
    val isLoading: Boolean = false,
    val lastUpdatedMillis: Long? = null,
) {
    val hasVisibleContent: Boolean
        get() = displayedRange != null
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
    private val _analyticsState = MutableStateFlow(VercelAnalyticsUiState())
    val analyticsState: StateFlow<VercelAnalyticsUiState> = _analyticsState.asStateFlow()

    private var restoreJob: Job? = null
    private var refreshJob: Job? = null
    private var mutationJob: Job? = null
    private var analyticsJob: Job? = null
    private var analyticsProject: VercelProjectUi? = null
    private var analyticsGeneration = 0L
    private val analyticsCache = mutableMapOf<AnalyticsCacheKey, CachedAnalytics>()

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
        closeProjectAnalytics()
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

    fun openProjectAnalytics(project: VercelProjectUi) {
        val current = _analyticsState.value
        if (
            analyticsProject?.id == project.id &&
            current.projectId == project.id &&
            (current.hasVisibleContent || current.isLoading)
        ) {
            return
        }
        analyticsProject = project
        _analyticsState.value = VercelAnalyticsUiState(
            projectId = project.id,
            selectedRange = current.selectedRange,
            selectedEnvironment = current.selectedEnvironment,
        )
        startAnalyticsLoad(forceRefresh = false, debounce = false)
    }

    fun closeProjectAnalytics() {
        analyticsJob?.cancel()
        analyticsJob = null
        analyticsGeneration += 1
        analyticsProject = null
        _analyticsState.value = VercelAnalyticsUiState(
            selectedRange = _analyticsState.value.selectedRange,
            selectedEnvironment = _analyticsState.value.selectedEnvironment,
        )
    }

    fun selectAnalyticsRange(range: VercelAnalyticsRange) {
        if (_analyticsState.value.selectedRange == range) return
        _analyticsState.update { it.copy(selectedRange = range, error = null) }
        startAnalyticsLoad(forceRefresh = false, debounce = true)
    }

    fun selectAnalyticsEnvironment(environment: VercelAnalyticsEnvironment) {
        if (_analyticsState.value.selectedEnvironment == environment) return
        _analyticsState.update { it.copy(selectedEnvironment = environment, error = null) }
        startAnalyticsLoad(forceRefresh = false, debounce = true)
    }

    fun refreshProjectAnalytics() {
        startAnalyticsLoad(forceRefresh = true, debounce = false)
    }

    private fun startAnalyticsLoad(forceRefresh: Boolean, debounce: Boolean) {
        val project = analyticsProject ?: return
        val selection = _analyticsState.value
        val range = selection.selectedRange
        val environment = selection.selectedEnvironment
        val key = AnalyticsCacheKey(project.id, project.teamId, range, environment)
        val now = System.currentTimeMillis()
        analyticsJob?.cancel()
        analyticsJob = null
        analyticsGeneration += 1
        val generation = analyticsGeneration
        val cached = analyticsCache[key]
        if (cached != null) {
            applyAnalyticsResult(
                projectId = project.id,
                range = range,
                environment = environment,
                result = cached.result,
                updatedAtMillis = cached.updatedAtMillis,
            )
            if (!forceRefresh && now - cached.updatedAtMillis < ANALYTICS_CACHE_LIFETIME_MILLIS) {
                return
            }
        }

        _analyticsState.update {
            it.copy(
                projectId = project.id,
                isLoading = true,
                error = null,
            )
        }
        analyticsJob = viewModelScope.launch {
            if (debounce) delay(ANALYTICS_SELECTION_DEBOUNCE_MILLIS)
            val result = try {
                gateway.loadProjectAnalytics(project, range, environment)
            } catch (error: CancellationException) {
                throw error
            }
            if (
                generation != analyticsGeneration ||
                analyticsProject?.id != project.id ||
                _analyticsState.value.selectedRange != range ||
                _analyticsState.value.selectedEnvironment != environment
            ) {
                return@launch
            }
            result.fold(
                onSuccess = { loaded ->
                    val updatedAt = System.currentTimeMillis()
                    analyticsCache[key] = CachedAnalytics(loaded, updatedAt)
                    applyAnalyticsResult(project.id, range, environment, loaded, updatedAt)
                },
                onFailure = { error ->
                    _analyticsState.update {
                        it.copy(
                            isLoading = false,
                            error = messageOf(error),
                        )
                    }
                },
            )
        }
    }

    private fun applyAnalyticsResult(
        projectId: String,
        range: VercelAnalyticsRange,
        environment: VercelAnalyticsEnvironment,
        result: VercelAnalyticsLoadUi,
        updatedAtMillis: Long,
    ) {
        _analyticsState.value = when (result) {
            is VercelAnalyticsLoadUi.Available -> _analyticsState.value.copy(
                projectId = projectId,
                displayedRange = range,
                displayedEnvironment = environment,
                data = result.data,
                unavailableMessage = null,
                error = null,
                isLoading = false,
                lastUpdatedMillis = updatedAtMillis,
            )

            is VercelAnalyticsLoadUi.Unavailable -> _analyticsState.value.copy(
                projectId = projectId,
                displayedRange = range,
                displayedEnvironment = environment,
                data = null,
                unavailableMessage = result.message,
                error = null,
                isLoading = false,
                lastUpdatedMillis = updatedAtMillis,
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

    private data class AnalyticsCacheKey(
        val projectId: String,
        val teamId: String?,
        val range: VercelAnalyticsRange,
        val environment: VercelAnalyticsEnvironment,
    )

    private data class CachedAnalytics(
        val result: VercelAnalyticsLoadUi,
        val updatedAtMillis: Long,
    )

    private companion object {
        const val ANALYTICS_CACHE_LIFETIME_MILLIS = 5 * 60 * 1_000L
        const val ANALYTICS_SELECTION_DEBOUNCE_MILLIS = 250L
    }
}
