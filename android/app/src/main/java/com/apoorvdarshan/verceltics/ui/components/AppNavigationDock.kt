package com.apoorvdarshan.verceltics.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The four persistent workspaces in the app's primary navigation.
 *
 * Search deliberately is not a destination: it acts on the current workspace and is exposed as
 * a separate action by [AppNavigationDock], matching the native iOS navigation hierarchy.
 */
enum class AppNavigationDestination(
    val id: String,
    val label: String,
    val compactLabel: String,
    val icon: ImageVector,
) {
    HOSTING("hosting", "Hosting", "Host", Icons.Rounded.Storage),
    REGISTRARS("registrars", "Registrars", "Domains", Icons.Rounded.Language),
    SITES("sites", "Sites", "Sites", Icons.Rounded.QueryStats),
    ABOUT("about", "About", "About", Icons.Rounded.Info),
}

/**
 * Native Compose counterpart to the iOS custom Liquid Glass navigation dock.
 *
 * Android uses translucent Material surfaces, elevation, a branded tint, and a hard offset outline
 * to echo the same visual language without pretending to expose Apple's Liquid Glass API. Haptic
 * feedback intentionally belongs to [onDestinationSelected] and [onSearch], so the app shell can
 * coordinate feedback with the navigation state change.
 */
@Composable
fun AppNavigationDock(
    selectedDestination: AppNavigationDestination,
    onDestinationSelected: (AppNavigationDestination) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    showsAboutBadge: Boolean = false,
) {
    val fontScale = LocalDensity.current.fontScale
    val labelScaleFactor = if (fontScale > 1.2f) 1.2f / fontScale else 1f
    val dockHeight = (62f * fontScale).coerceIn(62f, 76f).dp
    val searchWidth = (60f * fontScale).coerceIn(60f, 72f).dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            // This pointer target owns the complete dock rectangle, including safe-area padding.
            // It only consumes events that none of the controls claimed, preventing click-through.
            .consumeUnclaimedPointerInput()
            .navigationBarsPadding()
            .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 4.dp)
            .testTag("mainNavigation.dock")
            .semantics { isTraversalGroup = true },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            GlassDockSurface(
                modifier = Modifier
                    .weight(1f)
                    .height(dockHeight + DockShadowYOffset),
                shape = DockShape,
                tintStrength = 0.12f,
                testTag = "mainNavigation.primary",
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppNavigationDestination.entries.forEach { destination ->
                        NavigationDestinationButton(
                            destination = destination,
                            isSelected = selectedDestination == destination,
                            showsBadge = showsAboutBadge &&
                                destination == AppNavigationDestination.ABOUT,
                            labelScaleFactor = labelScaleFactor,
                            onClick = { onDestinationSelected(destination) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            SearchDockButton(
                onClick = onSearch,
                labelScaleFactor = labelScaleFactor,
                modifier = Modifier
                    .width(searchWidth + DockShadowXOffset)
                    .height(dockHeight + DockShadowYOffset),
            )
        }
    }
}

@Composable
private fun NavigationDestinationButton(
    destination: AppNavigationDestination,
    isSelected: Boolean,
    showsBadge: Boolean,
    labelScaleFactor: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = 700f, dampingRatio = 0.82f),
        label = "${destination.id} dock press",
    )
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) colors.primary else Color.Transparent,
        label = "${destination.id} dock container",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) colors.onPrimary else colors.onSurface,
        label = "${destination.id} dock content",
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .scale(scale),
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = SelectedShadowXOffset, y = SelectedShadowYOffset)
                    .background(HardShadowColor, SelectedShape),
            )
        }

        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxSize()
                .testTag("mainNavigation.${destination.id}")
                .semantics {
                    contentDescription = destination.label
                    role = Role.Tab
                    selected = isSelected
                },
            shape = SelectedShape,
            color = containerColor,
            contentColor = contentColor,
            border = if (isSelected) BorderStroke(2.dp, colors.outline) else null,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            interactionSource = interactionSource,
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 2.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                    )
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = MaterialTheme.typography.labelSmall.fontSize * labelScaleFactor,
                            lineHeight = MaterialTheme.typography.labelSmall.lineHeight * labelScaleFactor,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (showsBadge) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 3.dp, end = 5.dp)
                            .size(9.dp)
                            .background(colors.error, CircleShape)
                            .border(1.dp, colors.outline, CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchDockButton(
    onClick: () -> Unit,
    labelScaleFactor: Float,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = 700f, dampingRatio = 0.82f),
        label = "search dock press",
    )

    GlassDockSurface(
        modifier = modifier.scale(scale),
        shape = DockShape,
        tintStrength = 0.28f,
        testTag = "mainNavigation.searchSurface",
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxSize()
                .testTag("mainNavigation.search")
                .semantics {
                    contentDescription = "Search current workspace"
                    role = Role.Button
                },
            color = Color.Transparent,
            contentColor = colors.onSurface,
            shape = DockShape,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            interactionSource = interactionSource,
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 7.dp)
                        .width(26.dp)
                        .height(3.dp)
                        .background(colors.primary),
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 3.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(23.dp),
                    )
                    Text(
                        text = "Search",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = MaterialTheme.typography.labelSmall.fontSize * labelScaleFactor,
                            lineHeight = MaterialTheme.typography.labelSmall.lineHeight * labelScaleFactor,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassDockSurface(
    shape: Shape,
    tintStrength: Float,
    testTag: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.surface.luminance() < 0.5f
    val glassAlpha = if (isDark) 0.84f else 0.88f
    val outlineAlpha = if (isDark) 0.34f else 0.92f
    val shadowAlpha = if (isDark) 0.56f else 0.82f

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = DockShadowXOffset, bottom = DockShadowYOffset)
                .offset(x = DockShadowXOffset, y = DockShadowYOffset)
                .border(3.dp, colors.outline.copy(alpha = shadowAlpha), shape),
        )
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = DockShadowXOffset, bottom = DockShadowYOffset)
                .testTag(testTag),
            color = colors.surface.copy(alpha = glassAlpha),
            contentColor = colors.onSurface,
            shape = shape,
            border = BorderStroke(1.5.dp, colors.outline.copy(alpha = outlineAlpha)),
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.primary.copy(alpha = tintStrength)),
            ) {
                content()
            }
        }
    }
}

/** Consume only pointer gestures that no child control claimed. */
private fun Modifier.consumeUnclaimedPointerInput(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(
            requireUnconsumed = true,
            pass = PointerEventPass.Final,
        )
        down.consume()

        do {
            val event = awaitPointerEvent(PointerEventPass.Final)
            event.changes.forEach { change ->
                if (!change.isConsumed) change.consume()
            }
        } while (event.changes.any { it.pressed })
    }
}

private val DockShape = RoundedCornerShape(19.dp)
private val SelectedShape = RoundedCornerShape(14.dp)
private val DockShadowXOffset: Dp = 3.dp
private val DockShadowYOffset: Dp = 4.dp
private val SelectedShadowXOffset: Dp = 2.dp
private val SelectedShadowYOffset: Dp = 3.dp
private val HardShadowColor = Color.Black.copy(alpha = 0.80f)
