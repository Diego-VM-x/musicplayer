package com.larkplayer.feature.equalizer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.larkplayer.core.designsystem.component.CircleIconButton
import com.larkplayer.core.designsystem.component.LarkCard
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.feature.equalizer.EqualizerViewModel

/** Ecualizador real por bandas, con el estilo oscuro de Lark. */
@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    viewModel: EqualizerViewModel = hiltViewModel(),
) {
    val bands = remember { viewModel.bands() }
    val accent = MaterialTheme.colorScheme.primary

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
            "Ecualizador",
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Text(
            "Ajusta cada banda de frecuencia del audio que se está reproduciendo.",
            color = ApplePalette.TextSecondary,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 18.dp),
        )

        if (bands.isEmpty()) {
            LarkCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 38.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Rounded.MusicNote, null, tint = Color(0xFF5D5D64), modifier = Modifier.height(44.dp))
                    Spacer(Modifier.height(14.dp))
                    Text("Ecualizador no disponible", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Este dispositivo no expone el efecto de audio del sistema.",
                        color = ApplePalette.TextSecondary,
                        fontSize = 11.5.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
            return@Column
        }

        LarkCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                bands.forEach { band ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${band.label} Hz",
                            color = Color(0xFFC9C9CF),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (band.level > 0) "+${band.level} dB" else "${band.level} dB",
                            color = ApplePalette.TextSecondary,
                            fontSize = 10.5.sp,
                        )
                    }
                    Slider(
                        value = band.level.toFloat(),
                        onValueChange = { viewModel.setLevel(band.index, it.toInt().toShort()) },
                        valueRange = band.min.toFloat()..band.max.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = accent,
                            activeTrackColor = accent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.16f),
                        ),
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Los cambios se aplican en tiempo real y se conservan para la próxima sesión.",
            color = Color(0xFF6F6F77),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Spacer(Modifier.height(28.dp))
    }
}
