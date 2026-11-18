package com.larkplayer.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.larkplayer.core.designsystem.component.GlassSurface
import com.larkplayer.core.designsystem.theme.ApplePalette

/** Sleep timer screen (Lark had this feature). */
@Composable
fun SleepTimerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val remaining by viewModel.sleepTimerRemaining.collectAsStateWithLifecycle()
    val options = listOf(5, 10, 15, 30, 45, 60)

    Column(Modifier.fillMaxSize().background(ApplePalette.Background)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Text("Sleep Timer", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(20.dp))
            GlassSurface(shape = RoundedCornerShape(24.dp), alpha = 0.10f) {
                Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLAYBACK STOPS IN", color = ApplePalette.TextSecondary,
                        fontSize = 11.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.height(10.dp))
                    val mins = remaining / 60_000
                    val secs = (remaining / 1000) % 60
                    Text("%02d:%02d".format(mins, secs), color = Color.White,
                        fontSize = 52.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(36.dp))
            Text("Choose a duration", color = ApplePalette.TextSecondary)
            Spacer(Modifier.height(16.dp))
            val chunks = options.chunked(3)
            chunks.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { minutes ->
                        Button(
                            onClick = { viewModel.setSleepTimer(minutes) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("$minutes min") }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { viewModel.cancelSleepTimer() }) { Text("Cancel timer") }
        }
    }
}
