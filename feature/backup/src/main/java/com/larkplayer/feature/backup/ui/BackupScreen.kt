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
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.larkplayer.core.designsystem.component.CircleIconButton
import com.larkplayer.core.designsystem.component.LarkCard
import com.larkplayer.core.designsystem.component.LarkSwitch
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.feature.backup.BackupViewModel
import com.larkplayer.feature.backup.DetectedBackup
import kotlinx.coroutines.launch

@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf("") }
    var isOperating by remember { mutableStateOf(false) }
    var includeSongsInExport by remember { mutableStateOf(false) }
    val lastErrorLog by viewModel.lastErrorLog.collectAsState()
    val accent = MaterialTheme.colorScheme.primary

    var showTutorial by remember { mutableStateOf(false) }
    val availableBackups by remember { mutableStateOf(viewModel.findAvailableBackups()) }

    val openBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isOperating = true
                statusMessage = "Importando respaldo..."
                statusMessage = viewModel.importBackup(uri)
                isOperating = false
            }
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isOperating = true
                statusMessage = "Exportando respaldo..."
                statusMessage = viewModel.exportZip(uri, includeSongsInExport)
                isOperating = false
            }
        }
    }

    val exportDriveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isOperating = true
                statusMessage = "Exportando respaldo de Drive..."
                statusMessage = viewModel.exportDriveInfo(uri)
                isOperating = false
            }
        }
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
        Spacer(Modifier.height(16.dp))

        Text(
            "Copia de seguridad",
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Text(
            "Escanea e importa automáticamente tus playlists desde Lark Player original " +
                "(copias locales .lrc, respaldos de Google Drive .info/.json o copias ZIP) sin duplicar datos.",
            color = ApplePalette.TextSecondary,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
        )

        // Tarjeta de Tutorial / Instrucciones desplegable
        LarkCard(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showTutorial = !showTutorial }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accent.copy(alpha = 0.15f)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Rounded.HelpOutline, null, tint = accent, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Tutorial: ¿Cómo transferir desde Lark Player?",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (showTutorial) "Toca para ocultar instrucciones" else "Toca para ver el paso a paso detallado",
                            color = ApplePalette.TextSecondary,
                            fontSize = 11.sp,
                        )
                    }
                    Icon(
                        if (showTutorial) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        null,
                        tint = ApplePalette.TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                if (showTutorial) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    ) {
                        TutorialStep(
                            number = "1",
                            title = "En Lark Player original:",
                            desc = "Ve a 'Yo' > 'Copia de seguridad' y selecciona 'Copia de seguridad en Google Drive' o 'Copia local'.",
                        )
                        Spacer(Modifier.height(10.dp))
                        TutorialStep(
                            number = "2",
                            title = "Si usaste Google Drive:",
                            desc = "Abre tu Google Drive, busca el archivo que empiece por '(Don’t delete) Playlists...' o con extensión '.info' y descárgalo a tu teléfono (quedará en la carpeta Descargas / Download).",
                        )
                        Spacer(Modifier.height(10.dp))
                        TutorialStep(
                            number = "3",
                            title = "Detección o selección:",
                            desc = "Al entrar aquí, la app lo detectará automáticamente arriba en 'RESPALDOS DETECTADOS'. Solo tócalo para importar. O usa 'Seleccionar archivo' si lo guardaste en otra carpeta.",
                        )
                    }
                }
            }
        }

        // Estado y mensajes
        if (isOperating) {
            LarkCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = accent,
                        strokeWidth = 2.5.dp,
                    )
                    Spacer(Modifier.size(14.dp))
                    Text("Procesando...", color = Color.White, fontSize = 13.sp)
                }
            }
        } else if (statusMessage.isNotBlank()) {
            LarkCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (lastErrorLog != null) Icons.Rounded.ErrorOutline else Icons.Rounded.TaskAlt,
                            null,
                            tint = if (lastErrorLog != null) Color(0xFFFF5252) else accent,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            if (lastErrorLog != null) "Resultado con errores" else "Operación completada",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        statusMessage,
                        color = if (lastErrorLog != null) Color(0xFFFFCDD2) else Color.White.copy(alpha = 0.9f),
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                    )

                    // Log simplificado de error si ocurrió
                    lastErrorLog?.let { log ->
                        Spacer(Modifier.height(10.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF261818))
                                .padding(10.dp)
                        ) {
                            Text(
                                "LOG SIMPLIFICADO DEL ERROR:",
                                color = Color(0xFFFF8A80),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                log,
                                color = Color(0xFFFFD5D5),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 15.sp,
                            )
                        }
                    }
                }
            }
        }

        // Sección 1: Respaldos detectados automáticamente
        if (availableBackups.isNotEmpty()) {
            Text(
                "RESPALDOS DETECTADOS",
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
            )
            LarkCard(Modifier.fillMaxWidth()) {
                availableBackups.take(4).forEach { item ->
                    BackupItemRow(
                        icon = Icons.Rounded.Restore,
                        title = item.displayName,
                        subtitle = "${item.type} — Toca para importar automáticamente",
                        accent = accent,
                        onClick = {
                            if (!isOperating) {
                                scope.launch {
                                    isOperating = true
                                    statusMessage = "Importando ${item.displayName}..."
                                    statusMessage = viewModel.importBackup(item.uri)
                                    isOperating = false
                                }
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Sección 2: Importación manual universal
        Text(
            "IMPORTACIÓN MANUAL",
            color = ApplePalette.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        )
        LarkCard(Modifier.fillMaxWidth()) {
            BackupItemRow(
                icon = Icons.Rounded.FolderOpen,
                title = "Seleccionar archivo de respaldo",
                subtitle = "Compatible con .lrc (local), .info / .json (Drive) y .zip",
                accent = accent,
                onClick = {
                    if (!isOperating) {
                        openBackupLauncher.launch(arrayOf("*/*"))
                    }
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        // Sección 3: Exportar copia de seguridad
        Text(
            "CREAR RESPALDO",
            color = ApplePalette.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        )
        LarkCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Incluir archivos de audio", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text("Empaqueta canciones en el ZIP", color = ApplePalette.TextSecondary, fontSize = 11.sp)
                }
                LarkSwitch(
                    checked = includeSongsInExport,
                    onCheckedChange = { includeSongsInExport = it },
                    accent = accent,
                )
            }
            BackupItemRow(
                icon = Icons.Rounded.CloudUpload,
                title = "Exportar copia completa (.zip)",
                subtitle = "Guarda tus playlists, canciones y favoritos localmente",
                accent = accent,
                onClick = {
                    if (!isOperating) {
                        exportBackupLauncher.launch("lark-backup-${System.currentTimeMillis() / 1000}.zip")
                    }
                },
            )
            BackupItemRow(
                icon = Icons.Rounded.CloudDownload,
                title = "Exportar formato Google Drive (.info)",
                subtitle = "Genera el archivo cifrado exacto de Lark Player para subir a Drive",
                accent = accent,
                onClick = {
                    if (!isOperating) {
                        exportDriveLauncher.launch("(Don’t delete) Playlists you backed up in Lark Player.info")
                    }
                },
            )
        }

        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Rounded.Info, null, tint = Color(0xFF6F6F77), modifier = Modifier.size(15.dp))
            Spacer(Modifier.size(8.dp))
            Text(
                "Las playlists importadas se fusionan inteligentemente con tu música local sin duplicados.",
                color = Color(0xFF7E7E86),
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun TutorialStep(
    number: String,
    title: String,
    desc: String,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF33333E)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(number, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(desc, color = ApplePalette.TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun BackupItemRow(
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
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.14f)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = ApplePalette.TextSecondary, fontSize = 11.sp)
        }
    }
}
