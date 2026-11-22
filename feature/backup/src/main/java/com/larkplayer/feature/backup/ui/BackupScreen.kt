package com.larkplayer.feature.backup.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Folder
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
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.feature.backup.BackupViewModel
import kotlinx.coroutines.launch

/** Restores only the local Lark Player backup (`Backup_of_Lark_Player_*.lrc`). */
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    val accent = MaterialTheme.colorScheme.primary

    val localLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch { status = viewModel.importLocalBackup(uri) }
        }
    }
    val localBackup by remember { mutableStateOf(viewModel.findLocalBackup()) }

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
            "Restaurar copia local",
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Text(
            "Importa tus playlists desde la copia local de Lark Player.",
            color = ApplePalette.TextSecondary,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 20.dp),
        )

        LarkCard(Modifier.fillMaxWidth()) {
            if (localBackup != null) {
                BackupAction(
                    icon = Icons.Rounded.Unarchive,
                    title = "Restaurar copia detectada",
                    subtitle = "Copia de Lark Player encontrada — toca para importar",
                    accent = accent,
                    onClick = {
                        localBackup?.let { uri ->
                            scope.launch { status = viewModel.importLocalBackup(uri) }
                        }
                    },
                )
            }
            BackupAction(
                icon = Icons.Rounded.Folder,
                title = "Seleccionar copia local",
                subtitle = "Elige Backup_of_Lark_Player_*.lrc",
                accent = accent,
                onClick = { localLauncher.launch(arrayOf("*/*")) },
            )
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
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.14f)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = ApplePalette.TextSecondary, fontSize = 11.sp)
        }
    }
}
