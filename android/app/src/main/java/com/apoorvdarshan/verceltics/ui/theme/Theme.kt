package com.apoorvdarshan.verceltics.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.apoorvdarshan.verceltics.ui.screens.about.AboutAppearance

private val LightColors = lightColorScheme(
    primary = LightSignalFill,
    onPrimary = SignalForeground,
    primaryContainer = LightNavigationAccent,
    onPrimaryContainer = LightSurface,
    secondary = LightTextPrimary,
    onSecondary = LightSurface,
    secondaryContainer = LightSurface,
    onSecondaryContainer = LightTextPrimary,
    tertiary = LightSuccess,
    onTertiary = LightSurface,
    background = LightCanvas,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightRaised,
    onSurfaceVariant = LightTextSecondary,
    outline = Color.Black.copy(alpha = 0.94f),
    outlineVariant = Color.Black.copy(alpha = 0.25f),
    error = LightDanger,
    onError = LightSurface,
)

private val DarkColors = darkColorScheme(
    primary = DarkSignal,
    onPrimary = SignalForeground,
    primaryContainer = DarkSignal.copy(alpha = 0.38f),
    onPrimaryContainer = DarkTextPrimary,
    secondary = DarkTextPrimary,
    onSecondary = SignalForeground,
    secondaryContainer = DarkRaised,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = DarkSuccess,
    onTertiary = SignalForeground,
    background = DarkCanvas,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkRaised,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkTextPrimary.copy(alpha = 0.42f),
    outlineVariant = DarkTextPrimary.copy(alpha = 0.22f),
    error = DarkDanger,
    onError = SignalForeground,
)

private val VercelticsShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(19.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun VercelticsTheme(
    appearance: AboutAppearance,
    content: @Composable () -> Unit,
) {
    VercelticsTheme(
        darkTheme = appearance.resolveDarkTheme(isSystemInDarkTheme()),
        content = content,
    )
}

@Composable
fun VercelticsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        DisposableEffect(darkTheme) {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            onDispose { }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VercelticsTypography,
        shapes = VercelticsShapes,
        content = content,
    )
}

internal fun AboutAppearance.resolveDarkTheme(systemDarkTheme: Boolean): Boolean = when (this) {
    AboutAppearance.SYSTEM -> systemDarkTheme
    AboutAppearance.LIGHT -> false
    AboutAppearance.DARK -> true
}
