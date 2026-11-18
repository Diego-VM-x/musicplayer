package com.larkplayer.feature.settings.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.larkplayer.core.designsystem.component.LarkCard
import com.larkplayer.core.designsystem.component.LarkSwitch
import com.larkplayer.core.designsystem.component.SettingsRow
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.feature.settings.SettingsViewModel

@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val accent = MaterialTheme.colorScheme.primary

    SettingsPage(title = "Configuración de notificaciones", onBack = onBack) {
        LarkCard(Modifier.fillMaxWidth()) {
            SettingsRow(
                icon = Icons.Rounded.NotificationsActive,
                title = "Notificación de reproducción",
                subtitle = "Muestra la carátula, el título y los controles",
                trailing = {
                    LarkSwitch(
                        checked = playback.notificationPlayback,
                        onCheckedChange = viewModel::setNotificationPlayback,
                        accent = accent,
                    )
                },
            )
            SettingsRow(
                icon = Icons.Rounded.Lock,
                title = "Pantalla de bloqueo",
                subtitle = "Los controles del sistema dependen de tu bloqueo seguro",
                showChevron = false,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Lark Player usa la notificación multimedia del sistema (Android MediaSession). " +
                "No envía notificaciones publicitarias ni promociones.",
            color = ApplePalette.TextSecondary,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}
