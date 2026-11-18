package com.larkplayer.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.larkplayer.core.designsystem.component.GlassPillButton
import com.larkplayer.core.designsystem.component.LarkCard
import com.larkplayer.core.designsystem.component.LarkTextField
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.core.update.UpdateManager
import com.larkplayer.core.update.UpdateState

/** Ajustes → Actualizaciones: estado del actualizador in-app y preferencias. */
@Composable
fun UpdateSettingsScreen(onBack: () -> Unit) {
    val state by UpdateManager.state.collectAsStateWithLifecycle()
    val prefs by UpdateManager.prefsState.collectAsStateWithLifecycle()
    var repoInput by remember(prefs.repo) { mutableStateOf(prefs.repo) }
    val accent = MaterialTheme.colorScheme.primary

    SettingsPage(title = "Actualizaciones", onBack = onBack) {
        LarkCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text(
                    "Versión instalada",
                    color = ApplePalette.TextSecondary,
                    fontSize = 11.5.sp,
                )
                Text(
                    "${UpdateManager.versionName} (${UpdateManager.versionCode})",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(18.dp))
                UpdateStatus(state, accent, prefs.wifiOnly)
            }
        }

        Spacer(Modifier.height(14.dp))

        LarkCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text(
                    "Repositorio de actualizaciones",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "publica un release con larkplayer-<versionCode>.apk",
                    color = ApplePalette.TextSecondary,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(10.dp))
                LarkTextField(
                    value = repoInput,
                    onValueChange = { repoInput = it },
                    placeholder = "usuario/proyecto",
                )
                Spacer(Modifier.height(12.dp))
                GlassPillButton(
                    "Guardar y buscar",
                    onClick = { UpdateManager.setRepo(repoInput) },
                    filled = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        LarkCard(Modifier.fillMaxWidth()) {
            PlayToggleRow(
                icon = Icons.Rounded.Download,
                title = "Descargar automáticamente",
                subtitle = "Descarga el APK cuando haya una versión nueva",
                checked = prefs.autoDownload,
                onCheckedChange = UpdateManager::setAutoDownload,
                accent = accent,
            )
            PlayToggleRow(
                icon = Icons.Rounded.Wifi,
                title = "Solo con Wi-Fi",
                subtitle = "Evita usar datos móviles para la descarga",
                checked = prefs.wifiOnly,
                onCheckedChange = UpdateManager::setWifiOnly,
                accent = accent,
            )
        }
    }
}

@Composable
private fun UpdateStatus(state: UpdateState, accent: Color, wifiOnly: Boolean) {
    when (state) {
        UpdateState.Idle -> {
            Text("No hay actualizaciones pendientes.", color = ApplePalette.TextSecondary, fontSize = 12.5.sp)
            Spacer(Modifier.height(12.dp))
            GlassPillButton(
                "Buscar actualizaciones",
                icon = Icons.Rounded.SystemUpdate,
                accent = accent,
                onClick = { UpdateManager.checkNow(manual = true) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        UpdateState.Checking -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.padding(end = 12.dp))
                Text("Buscando actualizaciones…", color = Color.White, fontSize = 13.sp)
            }
        }
        is UpdateState.Available -> {
            Text("Versión ${state.info.versionName} disponible", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (state.info.notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(state.info.notes, color = ApplePalette.TextSecondary, fontSize = 11.5.sp)
            }
            Spacer(Modifier.height(14.dp))
            GlassPillButton(
                "Descargar ahora",
                icon = Icons.Rounded.Download,
                onClick = { UpdateManager.downloadNow(state.info, allowMetered = false) },
                filled = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        is UpdateState.Downloading -> {
            val percent = state.progress
            Text(
                if (state.waitingWifi) "Esperando a estar en Wi-Fi…" else "Descargando… ${percent?.let { "$it%" } ?: ""}",
                color = Color.White,
                fontSize = 13.sp,
            )
            if (percent != null) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { percent / 100f },
                    color = accent,
                    trackColor = Color.White.copy(alpha = 0.16f),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.waitingWifi || wifiOnly) {
                    GlassPillButton(
                        "Usar datos móviles",
                        modifier = Modifier.weight(1f),
                        onClick = { UpdateManager.downloadNow(state.info, allowMetered = true) },
                    )
                }
                GlassPillButton(
                    "Cancelar",
                    modifier = Modifier.weight(1f),
                    onClick = { UpdateManager.cancelDownload() },
                )
            }
        }
        is UpdateState.Verifying -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.padding(end = 12.dp))
                Text("Verificando el APK…", color = Color.White, fontSize = 13.sp)
            }
        }
        is UpdateState.Ready -> {
            Text("Lista para instalar", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(state.info.versionName, color = ApplePalette.TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            GlassPillButton(
                "Instalar ahora",
                onClick = { UpdateManager.installReady() },
                filled = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        is UpdateState.Error -> {
            Text(state.message, color = Color(0xFFFF8A8A), fontSize = 12.5.sp)
            Spacer(Modifier.height(12.dp))
            GlassPillButton(
                "Reintentar",
                onClick = { UpdateManager.checkNow(manual = true) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
