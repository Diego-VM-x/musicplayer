package com.larkplayer.feature.equalizer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.larkplayer.core.designsystem.component.GlassSurface
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.feature.equalizer.EqualizerViewModel

/** Apple Music style equalizer with real audiofx bands. */
@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    viewModel: EqualizerViewModel = hiltViewModel(),
) {
    val bands = remember { viewModel.bands() }
    Column(Modifier.fillMaxSize().background(ApplePalette.Background)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Text("Equalizer", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        if (bands.isEmpty()) {
            Text("Equalizer not available on this device", color = ApplePalette.TextSecondary,
                modifier = Modifier.padding(22.dp))
            return@Column
        }
        GlassSurface(shape = RoundedCornerShape(24.dp), alpha = 0.08f,
            modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Column(Modifier.padding(20.dp)) {
                bands.forEach { band ->
                    Text("${band.label} Hz", color = ApplePalette.TextSecondary, fontSize = 11.sp)
                    Slider(
                        value = band.level.toFloat(),
                        onValueChange = { viewModel.setLevel(band.index, it.toInt().toShort()) },
                        valueRange = band.min.toFloat()..band.max.toFloat(),
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
