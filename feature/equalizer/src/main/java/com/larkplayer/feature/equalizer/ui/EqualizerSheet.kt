package com.larkplayer.feature.equalizer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Simple equalizer UI (bands driven by EqualizerManager in the full build). */
@Composable
fun EqualizerSheet() {
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Text("Equalizer", style = MaterialTheme.typography.headlineSmall)
        Text("Band controls appear here using the platform audiofx engine.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = 0.5f, onValueChange = {}, modifier = Modifier.fillMaxWidth())
    }
}
