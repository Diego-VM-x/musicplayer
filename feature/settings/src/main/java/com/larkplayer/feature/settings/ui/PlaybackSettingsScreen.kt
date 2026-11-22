package com.larkplayer.feature.settings.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.feature.settings.SettingsViewModel

/** "Ajustes de reproducción" — same rows, order and slider as Lark. */
@Composable
fun PlaybackSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val accent = MaterialTheme.colorScheme.primary
    val context = LocalContext.current

    SettingsPage(title = "Ajustes de reproducción", onBack = onBack) {
        PlayToggleRow(
            icon = Icons.Rounded.Headphones,
            title = "Reproduce junto con otras aplicaciones",
            checked = playback.playWithOthers,
            onCheckedChange = viewModel::setPlayWithOthers,
            accent = accent,
        )

        Spacer(Modifier.height(26.dp))

        PlayToggleRow(
            icon = Icons.Rounded.Headphones,
            title = "Reproducir en segundo plano",
            subtitle = "La música sigue al salir de la app o apagar la pantalla",
            checked = playback.backgroundPlayback,
            onCheckedChange = { enabled ->
                viewModel.setBackgroundPlayback(enabled)
                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    runCatching {
                        val pm = context.getSystemService(PowerManager::class.java)
                        if (pm?.isIgnoringBatteryOptimizations(context.packageName) == false) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        }
                    }
                }
            },
            accent = accent,
        )

        Spacer(Modifier.height(26.dp))

        PlayToggleRow(
            icon = Icons.Rounded.GraphicEq,
            title = "Reproducción sin pausas",
            subtitle = "Elimina cualquier espacio o pausa que pueda producirse entre pistas",
            checked = playback.gapless,
            onCheckedChange = viewModel::setGapless,
            accent = accent,
        )

        Spacer(Modifier.height(26.dp))

        PlayToggleRow(
            icon = Icons.Rounded.ShowChart,
            title = "Crossfade",
            subtitle = "Mezcla las canciones superponiendo salida y entrada",
            checked = playback.crossfade,
            onCheckedChange = viewModel::setCrossfade,
            accent = accent,
        )

        Spacer(Modifier.height(14.dp))
        Text(
            "${playback.crossfadeSeconds}s",
            color = ApplePalette.TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Slider(
            value = playback.crossfadeSeconds.toFloat(),
            onValueChange = { viewModel.setCrossfadeSeconds(it.toInt()) },
            valueRange = 1f..12f,
            steps = 10,
            enabled = playback.crossfade,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.18f),
                disabledThumbColor = accent.copy(alpha = 0.45f),
                disabledActiveTrackColor = accent.copy(alpha = 0.45f),
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        )
    }
}
