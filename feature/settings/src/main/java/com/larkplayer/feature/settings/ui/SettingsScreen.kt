package com.larkplayer.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.larkplayer.core.designsystem.component.CircleIconButton
import com.larkplayer.core.designsystem.component.LarkCard
import com.larkplayer.core.designsystem.component.SettingsRow
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.core.model.ThemeConfig
import com.larkplayer.feature.settings.SettingsViewModel

private val LANGUAGE_NAMES = mapOf(
    "es" to "Español",
    "en" to "English",
    "pt" to "Português",
    "fr" to "Français",
    "de" to "Deutsch",
    "it" to "Italiano",
    "ja" to "日本語",
    "ko" to "한국어",
    "zh" to "简体中文",
    "ru" to "Русский",
)

/** Main Lark settings screen — grouped dark cards, no bottom navigation. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onPlayTime: () -> Unit,
    onBackup: () -> Unit,
    onTheme: () -> Unit,
    onSleepTimer: () -> Unit,
    onHiddenFiles: () -> Unit,
    onRecentlyDeleted: () -> Unit,
    onPlaybackSettings: () -> Unit,
    onNotifications: () -> Unit,
    onLanguage: () -> Unit,
    onFeedback: () -> Unit,
    onAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val config by viewModel.themeConfig.collectAsStateWithLifecycle(ThemeConfig())
    val language by viewModel.language.collectAsStateWithLifecycle()
    val totalPlayMs by viewModel.totalPlayMs.collectAsStateWithLifecycle()
    val hiddenCount by viewModel.hiddenCount.collectAsStateWithLifecycle()
    val sleepMinutes by viewModel.sleepTimerMinutes.collectAsStateWithLifecycle()

    val sizeName = when (config.themeSize) {
        0 -> "Pequeño"
        2 -> "Grande"
        else -> "Medio"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(ApplePalette.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        CircleIconButton(onClick = onBack)
        Spacer(Modifier.height(18.dp))
        Text(
            "Ajustes",
            color = androidx.compose.ui.graphics.Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.8).sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 18.dp),
        )

        LarkCard(Modifier.fillMaxWidth()) {
            SettingsRow(
                icon = Icons.Default.WatchLater,
                title = "Tiempo de reproducción",
                value = formatPlayTime(totalPlayMs),
                onClick = onPlayTime,
            )
            SettingsRow(
                icon = Icons.Default.CloudUpload,
                title = "Respaldar y guardar",
                subtitle = "Copia local (ZIP)",
                onClick = onBackup,
            )
        }

        Spacer(Modifier.height(14.dp))

        LarkCard(Modifier.fillMaxWidth()) {
            SettingsRow(
                icon = Icons.Default.Checkroom,
                title = "Tema",
                value = "Clásico / $sizeName",
                onClick = onTheme,
            )
            SettingsRow(
                icon = Icons.Default.Alarm,
                title = "Temporizador para dormir",
                value = if (sleepMinutes > 0) "$sleepMinutes min" else "Apagar",
                onClick = onSleepTimer,
            )
            SettingsRow(
                icon = Icons.Default.Block,
                title = "Sin anuncios",
                value = "Activo",
                showChevron = false,
            )
        }

        Spacer(Modifier.height(14.dp))

        LarkCard(Modifier.fillMaxWidth()) {
            SettingsRow(
                icon = Icons.Default.VisibilityOff,
                title = "Archivos ocultos",
                value = "$hiddenCount archivos",
                onClick = onHiddenFiles,
            )
            SettingsRow(
                icon = Icons.Default.DeleteOutline,
                title = "Eliminado recientemente",
                value = "0 archivos",
                onClick = onRecentlyDeleted,
            )
            SettingsRow(
                icon = Icons.Default.Headphones,
                title = "Ajustes de reproducción",
                onClick = onPlaybackSettings,
            )
            SettingsRow(
                icon = Icons.Default.NotificationsActive,
                title = "Configuración de notificaciones",
                onClick = onNotifications,
            )
            SettingsRow(
                icon = Icons.Default.Language,
                title = "Idioma",
                value = LANGUAGE_NAMES[language] ?: "Español",
                onClick = onLanguage,
            )
        }

        Spacer(Modifier.height(14.dp))

        LarkCard(Modifier.fillMaxWidth()) {
            SettingsRow(
                icon = Icons.Default.MailOutline,
                title = "Opinión",
                onClick = onFeedback,
            )
            SettingsRow(
                icon = Icons.Default.Info,
                title = "Sobre",
                value = "2027.5",
                onClick = onAbout,
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}

private fun formatPlayTime(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
