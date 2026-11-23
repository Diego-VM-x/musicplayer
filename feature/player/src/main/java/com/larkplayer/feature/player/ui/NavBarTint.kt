package com.larkplayer.feature.player.ui

import android.app.Activity
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

/**
 * Tints the Android navigation bar with the current playback colour while a
 * song is active, and restores plain black when nothing is playing.
 */
@Composable
fun NavBarTint(color: Color, active: Boolean) {
    val context = LocalContext.current
    DisposableEffect(active, color) {
        val window = (context as? Activity)?.window
        val previous = window?.navigationBarColor
        if (window != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            window.navigationBarColor = if (active) color.toArgb() else android.graphics.Color.BLACK
        }
        onDispose {
            if (window != null) {
                window.navigationBarColor = previous ?: android.graphics.Color.BLACK
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = true
                }
            }
        }
    }
}
