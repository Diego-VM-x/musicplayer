package com.larkplayer.core.model

/**
 * Custom theme override — equivalent to the original app's
 * `CustomTheme.ColorItem(mode, attr, value)`.
 *
 * @param mode 0 = light, 1 = dark, 2 = both
 * @param attr semantic attribute name (e.g. "primary", "accent", "background")
 * @param value hex color string (e.g. "#FF6200EE")
 */
data class ColorItem(
    val mode: Int = 2,
    val attr: String,
    val value: String,
)

data class ThemeConfig(
    val useDynamicColor: Boolean = true,
    val themeMode: Int = 2, // 0 light, 1 dark, 2 system
    val overrides: List<ColorItem> = emptyList(),
)
