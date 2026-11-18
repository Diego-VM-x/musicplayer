package com.larkplayer.core.designsystem.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.larkplayer.core.model.ThemeConfig

private val DarkColors = darkColorScheme(
    primary = ApplePalette.Blue,
    secondary = ApplePalette.Purple,
    tertiary = ApplePalette.Pink,
    background = ApplePalette.Background,
    surface = ApplePalette.Surface,
    surfaceVariant = ApplePalette.SurfaceElevated,
)
private val LightColors = lightColorScheme(
    primary = ApplePalette.Blue,
    secondary = ApplePalette.Purple,
    tertiary = ApplePalette.Pink,
)

@Composable
fun LarkTheme(
    config: ThemeConfig = ThemeConfig(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = when (config.themeMode) {
        0 -> false
        1 -> true
        else -> systemDark
    }
    val dynamic = config.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val base = when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val accent = Color(config.accentColor.toInt())
    val themed = if (dynamic) base else base.copy(primary = accent, secondary = accent)
    val colors = themed.withOverrides(config.overrides, dark)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Keep the status bar black in dark mode — Lark never tints it.
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
        }
    }

    MaterialTheme(colorScheme = colors, content = content)
}
