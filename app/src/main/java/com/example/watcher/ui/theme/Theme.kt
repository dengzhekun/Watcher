package com.example.watcher.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Ink950,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkTextPrimary,
    secondary = DarkSecondary,
    onSecondary = Ink950,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = DarkTertiary,
    onTertiary = Ink950,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkTextPrimary,
    background = DarkCanvas,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceRaised,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    error = AlertRed,
    onError = White,
    errorContainer = ColorSchemeTokens.darkErrorContainer,
    onErrorContainer = DarkTextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricBlue,
    onPrimary = White,
    primaryContainer = BlueWash,
    onPrimaryContainer = BlueInk,
    secondary = SignalGreen,
    onSecondary = White,
    secondaryContainer = SignalGreenContainer,
    onSecondaryContainer = SignalGreen,
    tertiary = StoryBlue,
    onTertiary = White,
    tertiaryContainer = StoryBlueContainer,
    onTertiaryContainer = BlueInk,
    background = Cloud50,
    onBackground = Ink950,
    surface = White,
    onSurface = Ink950,
    surfaceVariant = Cloud150,
    onSurfaceVariant = Ink700,
    outline = Ink300,
    error = AlertRed,
    onError = White,
    errorContainer = AlertRedContainer,
    onErrorContainer = AlertRed
)

data class WatcherExtendedColors(
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val signalGlow: Color,
    val primaryGradient: Brush,
    val glassOverlay: Color
)

val LocalWatcherExtendedColors = staticCompositionLocalOf {
    WatcherExtendedColors(
        surfaceContainerLow = Cloud100,
        surfaceContainer = Cloud150,
        surfaceContainerHigh = Cloud200,
        surfaceContainerHighest = Cloud250,
        signalGlow = SignalGreenFixed,
        primaryGradient = Brush.linearGradient(listOf(ElectricBlue, ElectricBlueContainer)),
        glassOverlay = White.copy(alpha = 0.72f)
    )
}

@Composable
fun WatcherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) {
        WatcherExtendedColors(
            surfaceContainerLow = DarkSurface,
            surfaceContainer = DarkSurfaceRaised,
            surfaceContainerHigh = DarkSurfaceHighest,
            surfaceContainerHighest = DarkOutline,
            signalGlow = DarkSecondary,
            primaryGradient = Brush.linearGradient(listOf(DarkPrimary, DarkPrimaryContainer)),
            glassOverlay = DarkSurface.copy(alpha = 0.72f)
        )
    } else {
        WatcherExtendedColors(
            surfaceContainerLow = Cloud100,
            surfaceContainer = Cloud150,
            surfaceContainerHigh = Cloud200,
            surfaceContainerHighest = Cloud250,
            signalGlow = SignalGreenFixed,
            primaryGradient = Brush.linearGradient(listOf(ElectricBlue, ElectricBlueContainer)),
            glassOverlay = White.copy(alpha = 0.72f)
        )
    }
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            val useDarkStatusBarIcons = colorScheme.background.luminance() > 0.5f

            window.statusBarColor = Transparent.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.navigationBarColor = Transparent.toArgb()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }

            insetsController.isAppearanceLightStatusBars = useDarkStatusBarIcons
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                insetsController.isAppearanceLightNavigationBars = useDarkStatusBarIcons
            }
        }
    }

    CompositionLocalProvider(LocalWatcherExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

private object ColorSchemeTokens {
    val darkErrorContainer = Color(0xFF5A2A2B)
}
