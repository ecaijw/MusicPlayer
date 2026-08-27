package com.localplayer.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = OnPrimaryLight,
    primaryContainer = PurpleContainerLight,
    onPrimaryContainer = PurplePrimary,
    secondary = PurplePrimary,
    onSecondary = OnPrimaryLight,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceContainer = PlayerBarLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OnSurfaceVariantLight,
    outlineVariant = OutlineVariantLight
)

private val DarkColors = darkColorScheme(
    primary = PurplePrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PurpleContainerDark,
    onPrimaryContainer = PurplePrimaryDark,
    secondary = PurplePrimary,
    onSecondary = OnPrimaryLight,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceContainer = PlayerBarDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OnSurfaceVariantDark,
    outlineVariant = OutlineVariantDark
)

@Composable
fun LocalPlayerTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
