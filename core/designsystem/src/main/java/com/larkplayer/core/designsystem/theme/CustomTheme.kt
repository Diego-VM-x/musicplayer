package com.larkplayer.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.larkplayer.core.model.ColorItem

/**
 * Applies the user's custom color overrides to a Material3 ColorScheme.
 * Equivalent to the original app's `CustomTheme.ColorItem(mode, attr, value)`.
 *
 * Supported attrs: primary, onPrimary, secondary, accent (-> secondary),
 * background, surface, surfaceVariant, error.
 */
fun ColorScheme.withOverrides(overrides: List<ColorItem>, isDark: Boolean): ColorScheme {
    var scheme = this
    overrides.forEach { item ->
        val applies = item.mode == 2 || (item.mode == 1 && isDark) || (item.mode == 0 && !isDark)
        if (!applies) return@forEach
        val color = runCatching { Color(android.graphics.Color.parseColor(item.value)) }.getOrNull()
            ?: return@forEach
        scheme = when (item.attr.lowercase()) {
            "primary" -> scheme.copy(primary = color)
            "onprimary" -> scheme.copy(onPrimary = color)
            "secondary", "accent" -> scheme.copy(secondary = color)
            "background" -> scheme.copy(background = color)
            "surface" -> scheme.copy(surface = color)
            "surfacevariant" -> scheme.copy(surfaceVariant = color)
            "error" -> scheme.copy(error = color)
            else -> scheme
        }
    }
    return scheme
}
