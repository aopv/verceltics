package com.apoorvdarshan.verceltics.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apoorvdarshan.verceltics.domain.IntegrationCatalog
import com.apoorvdarshan.verceltics.domain.Workspace
import com.apoorvdarshan.verceltics.ui.screens.AboutScreen
import com.apoorvdarshan.verceltics.ui.screens.CatalogScreen
import com.apoorvdarshan.verceltics.ui.screens.ProviderDetailScreen
import com.apoorvdarshan.verceltics.ui.screens.SearchScreen

private enum class BottomDestination(
    val id: String,
    val label: String,
    val compactLabel: String,
    val icon: ImageVector,
    val workspace: Workspace? = null,
) {
    HOSTING("hosting", "Hosting", "Host", Icons.Rounded.Storage, Workspace.HOSTING),
    REGISTRARS("registrars", "Registrars", "Domain", Icons.Rounded.Language, Workspace.REGISTRARS),
    SITES("sites", "Sites", "Sites", Icons.Rounded.QueryStats, Workspace.SITES),
    ABOUT("about", "About", "Info", Icons.Rounded.Info),
    SEARCH("search", "Search", "Search", Icons.Rounded.Search),
}

@Composable
fun VercelticsApp(
    vercelGateway: VercelUiGateway,
    modifier: Modifier = Modifier,
) {
    var destinationId by rememberSaveable { mutableStateOf(BottomDestination.HOSTING.id) }
    var destinationHistory by rememberSaveable {
        mutableStateOf(listOf(BottomDestination.HOSTING.id))
    }
    var providerId by rememberSaveable { mutableStateOf<String?>(null) }
    val destination = BottomDestination.entries.firstOrNull { it.id == destinationId }
        ?: BottomDestination.HOSTING
    val provider = providerId?.let(IntegrationCatalog::provider)
    val haptic = LocalHapticFeedback.current
    val destinationState = rememberSaveableStateHolder()

    BackHandler(enabled = provider != null || destinationHistory.size > 1) {
        if (provider != null) {
            providerId = null
        } else {
            destinationHistory = destinationHistory.dropLast(1)
            destinationId = destinationHistory.lastOrNull() ?: BottomDestination.HOSTING.id
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            ControlDock(
                selected = destination,
                onSelect = { selected ->
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    providerId = null
                    if (selected.id != destinationId) {
                        destinationHistory = appendDestinationHistory(
                            history = destinationHistory,
                            selectedId = selected.id,
                        )
                        destinationId = selected.id
                    }
                },
            )
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = contentPadding.calculateBottomPadding())
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                    ),
                )
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (provider != null) {
                ProviderDetailScreen(
                    provider = provider,
                    vercelGateway = vercelGateway,
                    onBack = { providerId = null },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                destinationState.SaveableStateProvider(destination.id) {
                    when (destination) {
                        BottomDestination.HOSTING,
                        BottomDestination.REGISTRARS,
                        BottomDestination.SITES,
                        -> CatalogScreen(
                            workspace = requireNotNull(destination.workspace),
                            onProviderClick = { providerId = it.id },
                            modifier = Modifier.fillMaxSize(),
                        )

                        BottomDestination.ABOUT -> AboutScreen(Modifier.fillMaxSize())
                        BottomDestination.SEARCH -> SearchScreen(
                            onProviderClick = { providerId = it.id },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

private const val MAX_DESTINATION_HISTORY = 12

internal fun appendDestinationHistory(
    history: List<String>,
    selectedId: String,
): List<String> {
    val updatedHistory = history + selectedId
    if (updatedHistory.size <= MAX_DESTINATION_HISTORY) return updatedHistory

    val retainedTail = updatedHistory.takeLast(MAX_DESTINATION_HISTORY - 1)
    return if (retainedTail.firstOrNull() == BottomDestination.HOSTING.id) {
        retainedTail
    } else {
        listOf(BottomDestination.HOSTING.id) + retainedTail
    }
}

@Composable
private fun ControlDock(
    selected: BottomDestination,
    onSelect: (BottomDestination) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 14.dp,
        tonalElevation = 0.dp,
        shape = RectangleShape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            HorizontalDivider(thickness = 3.dp, color = MaterialTheme.colorScheme.outline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 76.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomDestination.entries.forEachIndexed { index, destination ->
                    if (index > 0) {
                        Spacer(
                            Modifier
                                .width(1.dp)
                                .heightIn(min = 76.dp)
                                .background(MaterialTheme.colorScheme.outline)
                        )
                    }
                    DockItem(
                        destination = destination,
                        isSelected = destination == selected,
                        onClick = { onSelect(destination) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DockItem(
    destination: BottomDestination,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val useCompactLabel = LocalDensity.current.fontScale >= 1.5f
    val container = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val content = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 76.dp)
            .testTag("nav.${destination.id}")
            .semantics {
                contentDescription = destination.label
                role = Role.Tab
                selected = isSelected
            },
        color = container,
        contentColor = content,
        shape = RectangleShape,
        tonalElevation = 0.dp,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.outline) else null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = null,
                modifier = Modifier.height(27.dp),
            )
            Text(
                text = if (useCompactLabel) destination.compactLabel else destination.label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
