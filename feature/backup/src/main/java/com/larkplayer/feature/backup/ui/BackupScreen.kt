package com.larkplayer.feature.backup.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.larkplayer.core.designsystem.component.CircleIconButton
import com.larkplayer.core.designsystem.component.LarkCard
import com.larkplayer.core.designsystem.component.LarkSwitch
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.feature.backup.BackupViewModel
import kotlinx.coroutines.launch

/** Copia de seguridad local en ZIP (sin Google Drive ni servicios externos). */
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var includeSongs by remember { mutableStateOf(false) }
    val autoBackup by remember { mutableStateOf(viewModel.findAutoBackup()) }
    val accent = MaterialTheme.colorScheme.primary

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? -> uri?.let { scope.launch { status = viewModel.export(it, includeSongs) } } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { scope.launch { status = viewModel.import(it) } } }

    val originalLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) scope.launch { status = viewModel.importOriginalFiles(uris) }
    }
    val originalFiles by remember { mutableStateOf(viewModel.findOriginalFiles()) }

    Column(
        Modifier
            .fillMaxSize()
            .background(ApplePalette.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        CircleIconButton(onClick = onBack)
        Spacer(Modifier.height(16.dp))
        Text(
            "Respaldar y guardar",
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Text(
            "Guarda playlists, favoritos e historial en un ZIP local compatible con el formato del original. " +
                "Elige si incluir las canciones; nada se sube a Internet.",
            color = ApplePalette.TextSecondary,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 20.dp),
        )

        LarkCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Incluir canciones", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Copia también los archivos de audio para no volver a descargarlos",
                        color = ApplePalette.TextSecondary,
                        fontSize = 11.sp,
                    )
                }
                LarkSwitch(checked = includeSongs, onCheckedChange = { includeSongs = it }, accent = accent)
            }
            BackupAction(
                icon = Icons.Rounded.CloudUpload,
                title = "Exportar copia de seguridad",
                subtitle = "Crea un ZIP con tus datos de Lark Player",
                accent = accent,
                onClick = { exportLauncher.launch("lark-backup.zip") },
            )
            BackupAction(
                icon = Icons.Rounded.Unarchive,
                title = "Importar copia de seguridad",
                subtitle = "Restaura playlists y ajustes desde un ZIP",
                accent = accent,
                onClick = { importLauncher.launch(arrayOf("application/zip")) },
            )
        }

        if (autoBackup != null) {
            Spacer(Modifier.height(12.dp))
            LarkCard(Modifier.fillMaxWidth()) {
                BackupAction(
                    icon = Icons.Rounded.Unarchive,
                    title = "Restaurar copia detectada",
                    subtitle = "Se encontró un ZIP de respaldo; restaura las playlists automáticamente",
                    accent = accent,
                    onClick = {
                        autoBackup?.let { uri -> scope.launch { status = viewModel.import(uri) } }
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        LarkCard(Modifier.fillMaxWidth()) {
            BackupAction(
                icon = Icons.Rounded.Unarchive,
                title = "Importar playlists de Lark Player",
                subtitle = "Selecciona los archivos .info de tu copia original",
                accent = accent,
                onClick = { originalLauncher.launch(arrayOf("*/*")) },
            )
            originalFiles.take(5).forEach { uri ->
                BackupAction(
                    icon = Icons.Rounded.Folder,
                    title = uri.lastPathSegment ?: "Copia de Lark Player",
                    subtitle = "Detectada automáticamente — toca para importar",
                    accent = accent,
                    onClick = { scope.launch { status = viewModel.importOriginalFiles(listOf(uri)) } },
                )
            }
        }

        if (status.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            LarkCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Archive, null, tint = accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(12.dp))
                    Text(status, color = Color.White, fontSize = 12.5.sp, lineHeight = 17.sp)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Rounded.Info, null, tint = Color(0xFF6F6F77), modifier = Modifier.size(15.dp))
            Spacer(Modifier.size(8.dp))
            Text(
                "El ZIP se crea en el almacenamiento del dispositivo usando el selector de archivos de Android. " +
                    "No se necesita cuenta de Google ni conexión a Internet.",
                color = Color(0xFF6F6F77),
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun BackupAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(accent.copy(alpha = 0.16f)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = Color(0xFF7E7E86), fontSize = 11.5.sp)
        }
    }
}
