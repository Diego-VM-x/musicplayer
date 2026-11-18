package com.larkplayer.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.WatchLater
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
import com.larkplayer.core.designsystem.component.LarkCard
import com.larkplayer.core.designsystem.component.SettingsRow
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.feature.settings.SettingsViewModel

@Composable
fun PlayTimeScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val total by viewModel.totalPlayMs.collectAsStateWithLifecycle()

    SettingsPage(title = "Tiempo de reproducción", onBack = onBack) {
        LarkCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("TIEMPO TOTAL", color = ApplePalette.TextSecondary, fontSize = 10.5.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    formatTotal(total),
                    color = Color.White,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1).sp,
                )
                Spacer(Modifier.height(6.dp))
                Text("escuchando música", color = ApplePalette.TextSecondary, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        LarkCard(Modifier.fillMaxWidth()) {
            SettingsRow(Icons.Rounded.WatchLater, "Sesión actual", value = "En curso", showChevron = false)
            SettingsRow(Icons.Rounded.Headphones, "Reproducciones registradas", value = "—", showChevron = false)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "El tiempo se acumula mientras Lark Player reproduce audio, incluso en segundo plano.",
            color = ApplePalette.TextSecondary,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

private fun formatTotal(ms: Long): String {
    val minutes = ms / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days > 0 -> "${days}d ${hours % 24}h"
        hours > 0 -> "${hours}h ${minutes % 60}m"
        else -> "${minutes}m"
    }
}
