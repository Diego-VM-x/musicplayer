package com.larkplayer.feature.equalizer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.larkplayer.core.designsystem.component.CircleIconButton
import com.larkplayer.core.designsystem.component.LarkCard
import com.larkplayer.core.designsystem.component.LarkSectionLabel
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.feature.equalizer.EqPresets
import com.larkplayer.feature.equalizer.EqualizerViewModel

/**
 * Flat equalizer screen. Presets, names and vector icons are the ones shipped
 * with Lark Player 2027.1.8 (see EqPresets for the decompiled evidence).
 */
@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    viewModel: EqualizerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accentArgb by viewModel.accent.collectAsStateWithLifecycle()
    val accent = Color(accentArgb)

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
            "Presets, nombres e iconos del Lark Player original. Los cambios se aplican en tiempo real.",
            color = ApplePalette.TextSecondary,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 18.dp),
        )

        LarkCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Ecualizador", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (state.available) "Efectos de audio en la sesión actual"
                        else "Esperando a que el reproductor esté activo",
                        color = ApplePalette.TextSecondary,
                        fontSize = 10.5.sp,
                    )
                }
                Switch(
                    checked = state.enabled,
                    onCheckedChange = viewModel::setEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = accent,
                        checkedTrackColor = accent.copy(alpha = 0.55f),
                        uncheckedThumbColor = Color(0xFF9A9AA2),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.14f),
                    ),
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        LarkCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                LarkSectionLabel("PRESETS")
                Spacer(Modifier.height(12.dp))
                EqPresets.ALL.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        row.forEach { preset ->
                            PresetChip(
                                preset = preset,
                                selected = state.presetId == preset.id,
                                enabled = true,
                                accent = accent,
                                onClick = { viewModel.selectPreset(preset.id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(9.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        LarkCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                LarkSectionLabel("BANDAS")
                Spacer(Modifier.height(6.dp))
                if (!state.available) {
                    Text(
                        "Este dispositivo no expone el efecto de audio del sistema. " +
                            "Los presets y los niveles se conservan para cuando esté disponible.",
                        color = ApplePalette.TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
                state.levels.forEachIndexed { index, level ->
                    val hz = state.cutoffs.getOrNull(index) ?: 0
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (hz >= 1_000) "${hz / 1_000} kHz" else "$hz Hz",
                            color = Color(0xFFC9C9CF),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (level > 0) "+$level dB" else "$level dB",
                            color = ApplePalette.TextSecondary,
                            fontSize = 10.5.sp,
                        )
                    }
                    Slider(
                        value = level.toFloat().coerceIn(state.minLevel.toFloat(), state.maxLevel.toFloat()),
                        onValueChange = { viewModel.setLevel(index, it.toInt()) },
                        valueRange = state.minLevel.toFloat()..state.maxLevel.toFloat(),
                        enabled = state.enabled && state.available,
                        colors = SliderDefaults.colors(
                            thumbColor = accent,
                            activeTrackColor = accent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.16f),
                            disabledThumbColor = accent.copy(alpha = 0.45f),
                            disabledActiveTrackColor = accent.copy(alpha = 0.45f),
                        ),
                    )
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "La selección se guarda y se restaura al volver a abrir la app.",
            color = Color(0xFF6F6F77),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun PresetChip(
    preset: EqPresets.Preset,
    selected: Boolean,
    enabled: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f))
            .border(1.dp, if (selected) accent.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.07f), shape)
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(preset.iconRes),
            contentDescription = preset.name,
            tint = if (selected) accent else Color(0xFFC9C9CF),
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.height(7.dp))
        Text(
            preset.name,
            color = Color.White,
            fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
