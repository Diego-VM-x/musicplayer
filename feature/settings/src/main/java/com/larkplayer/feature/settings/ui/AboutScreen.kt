package com.larkplayer.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.larkplayer.core.designsystem.component.LarkCard
import com.larkplayer.core.designsystem.component.SettingsRow
import com.larkplayer.core.designsystem.theme.ApplePalette

@Composable
fun AboutScreen(onBack: () -> Unit) {
    SettingsPage(title = "Sobre", onBack = onBack) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(84.dp).background(
                    Brush.linearGradient(listOf(Color(0xFFFA243C), Color(0xFFB08CFF))),
                    RoundedCornerShape(22.dp),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(42.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("Lark Player", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("Versión 2027.5 · mod limpia", color = ApplePalette.TextSecondary, fontSize = 12.sp)
        }

        Spacer(Modifier.height(16.dp))
        LarkCard(Modifier.fillMaxWidth()) {
            SettingsRow(
                icon = Icons.Default.MusicNote,
                title = "Reproductor local",
                subtitle = "Media3 ExoPlayer · MP3, M4A, FLAC, OGG, WAV",
                showChevron = false,
            )
            SettingsRow(
                icon = Icons.Default.MusicNote,
                title = "Sin anuncios ni rastreadores",
                subtitle = "No incluye AD_ID, analytics ni librerías .so",
                showChevron = false,
            )
            SettingsRow(
                icon = Icons.Default.MusicNote,
                title = "Copia de seguridad local",
                subtitle = "Exporta e importa un ZIP sin servicios de Google",
                showChevron = false,
            )
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "Reconstrucción nativa inspirada en Lark Player. Hecha con Kotlin, Jetpack Compose y Media3. " +
                "Las carátulas y los archivos de audio pertenecen a sus autores.",
            color = ApplePalette.TextSecondary,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
        )
    }
}
