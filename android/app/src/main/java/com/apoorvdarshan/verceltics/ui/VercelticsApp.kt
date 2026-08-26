package com.apoorvdarshan.verceltics.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.core.content.edit
import com.apoorvdarshan.verceltics.domain.IntegrationCatalog
import com.apoorvdarshan.verceltics.domain.Workspace
import com.apoorvdarshan.verceltics.ui.components.AppNavigationDestination
import com.apoorvdarshan.verceltics.ui.components.AppNavigationDock
import com.apoorvdarshan.verceltics.ui.components.OffsetPanel
import com.apoorvdarshan.verceltics.ui.components.ProviderMark
import com.apoorvdarshan.verceltics.ui.components.StatusPill
import com.apoorvdarshan.verceltics.ui.pagespeed.PageSpeedCacheState
import com.apoorvdarshan.verceltics.ui.pagespeed.PageSpeedConnectionStatus
import com.apoorvdarshan.verceltics.ui.pagespeed.PageSpeedRoute
import com.apoorvdarshan.verceltics.ui.pagespeed.PageSpeedUiState
import com.apoorvdarshan.verceltics.ui.pagespeed.PageSpeedViewModel
import com.apoorvdarshan.verceltics.ui.screens.AboutScreen
import com.apoorvdarshan.verceltics.ui.screens.ProviderDetailScreen
import com.apoorvdarshan.verceltics.ui.screens.VercelWorkspaceScreen
import com.apoorvdarshan.verceltics.ui.screens.WorkspaceScreen
import com.apoorvdarshan.verceltics.ui.screens.about.AboutAppearance
import com.apoorvdarshan.verceltics.ui.screens.about.AboutScreenAction
import com.apoorvdarshan.verceltics.ui.screens.about.AboutScreenState
import com.apoorvdarshan.verceltics.ui.screens.about.AboutUpdateState
import com.apoorvdarshan.verceltics.ui.screens.about.currentAndroidAppVersion

private const val UI_PREFERENCES = "verceltics.ui"
private const val LAST_PRIMARY_WORKSPACE = "lastPrimaryWorkspace"
private const val PAGE_SPEED_PROVIDER_ID = "pageSpeed"

private enum class MainDestination(
    val id: String,
    val navigationDestination: AppNavigationDestination,
    val workspace: Workspace? = null,
) {
    HOSTING("hosting", AppNavigationDestination.HOSTING, Workspace.HOSTING),
    REGISTRARS("registrars", AppNavigationDestination.REGISTRARS, Workspace.REGISTRARS),
    SITES("sites", AppNavigationDestination.SITES, Workspace.SITES),
    ABOUT("about", AppNavigationDestination.ABOUT),
    ;

    companion object {
        fun fromId(id: String?): MainDestination = entries.firstOrNull { it.id == id } ?: HOSTING

        fun fromNavigation(destination: AppNavigationDestination): MainDestination = when (destination) {
            AppNavigationDestination.HOSTING -> HOSTING
            AppNavigationDestination.REGISTRARS -> REGISTRARS
            AppNavigationDestination.SITES -> SITES
            AppNavigationDestination.ABOUT -> ABOUT
        }

        fun fromWorkspace(workspace: Workspace): MainDestination = when (workspace) {
            Workspace.HOSTING -> HOSTING
            Workspace.REGISTRARS -> REGISTRARS
            Workspace.SITES -> SITES
        }
    }
}

/**
 * Native Android app shell matching the SwiftUI MainTabView hierarchy.
 *
 * Four destinations are persistent. Search is a contextual action that focuses the current or
 * last primary workspace; it is deliberately not a fifth destination and tab taps do not build a
 * synthetic back stack.
 */
@Composable
fun VercelticsApp(
    vercelConnectionViewModel: VercelConnectionViewModel,
    pageSpeedViewModel: PageSpeedViewModel,
    aboutState: AboutScreenState = defaultAboutScreenState(),
    onAboutAction: (AboutScreenAction) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences(UI_PREFERENCES, Context.MODE_PRIVATE)
    }
    val restoredWorkspace = remember(preferences) {
        Workspace.entries.firstOrNull {
            it.id == preferences.getString(LAST_PRIMARY_WORKSPACE, Workspace.HOSTING.id)
        } ?: Workspace.HOSTING
    }
    var destinationId by rememberSaveable {
        mutableStateOf(MainDestination.fromWorkspace(restoredWorkspace).id)
    }
    var lastWorkspaceId by rememberSaveable { mutableStateOf(restoredWorkspace.id) }
    var providerId by rememberSaveable { mutableStateOf<String?>(null) }
    var hostingSearchRequestId by rememberSaveable { mutableIntStateOf(0) }
    var hostingRefreshRequestId by rememberSaveable { mutableIntStateOf(0) }
    var hostingSearchAvailable by rememberSaveable { mutableStateOf(false) }
    val connectionState by vercelConnectionViewModel.uiState.collectAsStateWithLifecycle()
    val pageSpeedState by pageSpeedViewModel.uiState.collectAsStateWithLifecycle()
    val destination = MainDestination.fromId(destinationId)
    val provider = providerId?.let(IntegrationCatalog::provider)
    val destinationState = rememberSaveableStateHolder()
    val haptic = LocalHapticFeedback.current

    fun selectDestination(selected: MainDestination) {
        providerId = null
        destinationId = selected.id
        selected.workspace?.let { workspace ->
            lastWorkspaceId = workspace.id
            preferences.edit { putString(LAST_PRIMARY_WORKSPACE, workspace.id) }
        }
    }

    fun requestSearch() {
        val preferredWorkspace = destination.workspace
            ?: Workspace.entries.firstOrNull { it.id == lastWorkspaceId }
            ?: Workspace.HOSTING
        val workspace = if (hostingSearchAvailable) Workspace.HOSTING else preferredWorkspace
        selectDestination(MainDestination.fromWorkspace(workspace))
        if (workspace == Workspace.HOSTING && hostingSearchAvailable) {
            hostingSearchRequestId += 1
        }
    }

    LaunchedEffect(connectionState.isSearchAvailable) {
        hostingSearchAvailable = connectionState.isSearchAvailable
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hostingRefreshRequestId += 1
    }

    BackHandler(enabled = provider != null && provider.id != PAGE_SPEED_PROVIDER_ID) {
        providerId = null
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (provider == null) {
                AppNavigationDock(
                    selectedDestination = destination.navigationDestination,
                    onDestinationSelected = { selected ->
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        selectDestination(MainDestination.fromNavigation(selected))
                    },
                    onSearch = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        requestSearch()
                    },
                )
            }
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = contentPadding.calculateBottomPadding())
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        if (provider == null) {
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        } else {
                            WindowInsetsSides.Top +
                                WindowInsetsSides.Horizontal +
                                WindowInsetsSides.Bottom
                        },
                    ),
                )
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (provider != null) {
                if (provider.id == PAGE_SPEED_PROVIDER_ID) {
                    PageSpeedRoute(
                        viewModel = pageSpeedViewModel,
                        onBack = { providerId = null },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ProviderDetailScreen(
                        provider = provider,
                        vercelConnectionViewModel = vercelConnectionViewModel,
                        onBack = { providerId = null },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                destinationState.SaveableStateProvider(destination.id) {
                    when (destination) {
                        MainDestination.HOSTING -> VercelWorkspaceScreen(
                            vercelConnectionViewModel = vercelConnectionViewModel,
                            searchRequestId = hostingSearchRequestId,
                            refreshRequestId = hostingRefreshRequestId,
                            onConnectProvider = { providerId = it.id },
                            onSearchAvailabilityChanged = { hostingSearchAvailable = it },
                            modifier = Modifier.fillMaxSize(),
                        )

                        MainDestination.REGISTRARS -> WorkspaceScreen(
                            workspace = Workspace.REGISTRARS,
                            onConnectProvider = { providerId = it.id },
                            onAccountAction = {},
                            modifier = Modifier.fillMaxSize(),
                        )

                        MainDestination.SITES -> WorkspaceScreen(
                            workspace = Workspace.SITES,
                            onConnectProvider = { providerId = it.id },
                            onAccountAction = {},
                            modifier = Modifier.fillMaxSize(),
                            connectedContent = if (pageSpeedState.isConnected) {
                                {
                                    PageSpeedConnectionCard(
                                        state = pageSpeedState,
                                        onClick = { providerId = PAGE_SPEED_PROVIDER_ID },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            } else {
                                null
                            },
                        )

                        MainDestination.ABOUT -> AboutScreen(
                            state = aboutState,
                            onAction = onAboutAction,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageSpeedConnectionCard(
    state: PageSpeedUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val provider = remember { checkNotNull(IntegrationCatalog.provider(PAGE_SPEED_PROVIDER_ID)) }
    val accent = Color(provider.accentColor)
    val haptic = LocalHapticFeedback.current
    val subtitle = state.dashboard?.siteUrl
        ?: state.savedSiteUrl
        ?: state.error
        ?: "Saved connection"
    val status = when (state.status) {
        PageSpeedConnectionStatus.CONNECTED -> when (state.dashboard?.cacheState) {
            PageSpeedCacheState.LIVE -> "Live"
            PageSpeedCacheState.CACHED_FRESH -> "Saved"
            PageSpeedCacheState.CACHED_STALE -> "Stale"
            null -> "Saved"
        }
        PageSpeedConnectionStatus.SAVED_UNAVAILABLE -> "Attention"
        PageSpeedConnectionStatus.RESTORING -> "Restoring"
        PageSpeedConnectionStatus.DISCONNECTED -> "Disconnected"
    }
    val statusColor = if (state.status == PageSpeedConnectionStatus.SAVED_UNAVAILABLE) {
        MaterialTheme.colorScheme.error
    } else {
        accent
    }

    OffsetPanel(
        modifier = modifier.heightIn(min = 88.dp),
        color = MaterialTheme.colorScheme.surface,
        borderColor = accent,
        shadowColor = accent,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            onClick()
        },
        testTag = "workspace.sites.pageSpeedConnection",
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProviderMark(provider = provider, size = 46.dp)
            Spacer(Modifier.width(13.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = provider.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            StatusPill(text = status, color = statusColor)
        }
    }
}

private fun defaultAboutScreenState() = AboutScreenState(
    version = currentAndroidAppVersion(),
    appearance = AboutAppearance.SYSTEM,
    update = AboutUpdateState.NotConfigured,
)
