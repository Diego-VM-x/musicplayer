package com.larkplayer.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.larkplayer.core.designsystem.component.CircleIconButton
import com.larkplayer.core.designsystem.component.LarkCard
import com.larkplayer.core.designsystem.theme.ApplePalette

/** Temporizador para dormir, con el estilo de tarjetas de Lark. */
@Composable
fun SleepTimerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val remaining by viewModel.sleepTimerRemaining.collectAsStateWithLifecycle()
    val options = listOf(5, 10, 15, 30, 45, 60)
    val accent = MaterialTheme.colorScheme.primary

    Column(
        Modifier
            .fillMaxSize()
            .background(ApplePalette.Background)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        CircleIconButton(onClick = onBack)
        Spacer(Modifier.height(16.dp))
        Text(
            "Temporizador para dormir",
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 18.dp),
        )

        LarkCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.Alarm, null, tint = accent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(10.dp))
                Text(
                    "LA MÚSICA SE DETIENE EN",
                    color = ApplePalette.TextSecondary,
                    fontSize = 10.5.sp,
                    letterSpacing = 2.sp,
                )
                Spacer(Modifier.height(10.dp))
                val mins = remaining / 60_000
                val secs = (remaining / 1000) % 60
                Text(
                    "%02d:%02d".format(mins, secs),
                    color = Color.White,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1).sp,
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Text(
            "Elige una duración",
            color = ApplePalette.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
        )
        val chunks = options.chunked(3)
        chunks.forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { minutes ->
                    TimerChip(
                        label = "$minutes min",
                        accent = accent,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.setSleepTimer(minutes) },
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(ApplePalette.Card)
                .clickable { viewModel.cancelSleepTimer() }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.TimerOff, null, tint = Color(0xFFC7C7CD), modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Cancelar temporizador", color = Color(0xFFC7C7CD), fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "La reproducción se detendrá suavemente al terminar el tiempo, sin cerrar la aplicación.",
            color = Color(0xFF6F6F77),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
    }
}

@Composable
private fun TimerChip(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = accent, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    }
}
