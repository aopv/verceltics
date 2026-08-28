package com.apoorvdarshan.verceltics.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.apoorvdarshan.verceltics.ui.screens.about.AboutAppearance

private val LightColors = lightColorScheme(
    primary = LightSignal,
    onPrimary = LightOnSignal,
    primaryContainer = LightSignal.copy(alpha = 0.12f),
    onPrimaryContainer = LightSignal,
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
    outline = Color.Black.copy(alpha = 0.095f),
    outlineVariant = Color.Black.copy(alpha = 0.06f),
    error = LightDanger,
    onError = LightSurface,
)

private val DarkColors = darkColorScheme(
    primary = DarkSignal,
    onPrimary = DarkOnSignal,
    primaryContainer = DarkSignal.copy(alpha = 0.15f),
    onPrimaryContainer = DarkTextPrimary,
    secondary = DarkTextPrimary,
    onSecondary = DarkCanvas,
    secondaryContainer = DarkRaised,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = DarkSuccess,
    onTertiary = DarkCanvas,
    background = DarkCanvas,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkRaised,
    onSurfaceVariant = DarkTextSecondary,
    outline = Color.White.copy(alpha = 0.10f),
    outlineVariant = Color.White.copy(alpha = 0.055f),
    error = DarkDanger,
    onError = DarkCanvas,
)

private val VercelticsShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(13.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/** The resolved app appearance, including an explicit in-app Light or Dark override. */
val LocalVercelticsDarkTheme = staticCompositionLocalOf { false }

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

    CompositionLocalProvider(LocalVercelticsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VercelticsTypography,
            shapes = VercelticsShapes,
            content = content,
        )
    }
}

internal fun AboutAppearance.resolveDarkTheme(systemDarkTheme: Boolean): Boolean = when (this) {
    AboutAppearance.SYSTEM -> systemDarkTheme
    AboutAppearance.LIGHT -> false
    AboutAppearance.DARK -> true
}
