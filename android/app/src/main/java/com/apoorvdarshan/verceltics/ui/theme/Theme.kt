package com.apoorvdarshan.verceltics.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Orange,
    onPrimary = Ink,
    primaryContainer = OrangeSoft,
    onPrimaryContainer = Ink,
    secondary = Ink,
    onSecondary = Paper,
    secondaryContainer = Paper,
    onSecondaryContainer = Ink,
    tertiary = Lime,
    onTertiary = Ink,
    background = Cream,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0EADF),
    onSurfaceVariant = MutedInk,
    outline = Ink,
    outlineVariant = Color(0xFFB8B0A2),
    error = Color(0xFFCF372D),
    onError = Paper,
)

private val DarkColors = darkColorScheme(
    primary = OrangeSoft,
    onPrimary = Ink,
    primaryContainer = Color(0xFF5C2A0F),
    onPrimaryContainer = DarkText,
    secondary = DarkText,
    onSecondary = Ink,
    secondaryContainer = DarkRaised,
    onSecondaryContainer = DarkText,
    tertiary = Lime,
    onTertiary = Ink,
    background = DarkCanvas,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkRaised,
    onSurfaceVariant = DarkMuted,
    outline = DarkStroke,
    outlineVariant = Color(0xFF6F675B),
    error = Color(0xFFFF766C),
    onError = Ink,
)

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
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
            onDispose { }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VercelticsTypography,
        content = content,
    )
}
