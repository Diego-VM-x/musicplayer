package com.larkplayer.feature.equalizer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.larkplayer.core.designsystem.component.LarkGradientBackground
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.feature.equalizer.EqPresets
import com.larkplayer.feature.equalizer.EqualizerViewModel
import kotlin.math.roundToInt

/**
 * The original Lark equalizer sheet: a header with the on/off switch, ten vertical
 * bands and a two-column grid of presets with their shipped icons and Spanish labels.
 */
@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    viewModel: EqualizerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accentArgb by viewModel.accent.collectAsStateWithLifecycle()
    val accent = Color(accentArgb)

    LarkGradientBackground(accent = accent, strength = 0.24f) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Ecualizador",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.enabled,
                    onCheckedChange = viewModel::setEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accent,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.White.copy(alpha = 0.22f),
                    ),
                )
            }
            Spacer(Modifier.height(22.dp))

            // Ten vertical bands: dB value on top, hairline track and frequency below.
            val levels = tenLevels(state.levels)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                repeat(EQ_BAND_COUNT) { index ->
                    BandColumn(
                        label = frequencyLabel(EqPresets.CUTOFFS_10[index]),
                        level = levels[index],
                        min = state.minLevel,
                        max = state.maxLevel,
                        accent = accent,
                        gestureKey = state.bandCount,
                        onValueChange = { level ->
                            viewModel.setLevel(realBandIndex(index, state.levels.size), level)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            EQ_GRID_ORDER.forEachIndexed { rowIndex, ids ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ids.forEach { id ->
                        val preset = EqPresets.preset(id)
                        PresetCard(
                            preset = preset,
                            selected = state.presetId == preset.id,
                            accent = accent,
                            onClick = { viewModel.selectPreset(preset.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (rowIndex < EQ_GRID_ORDER.lastIndex) Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BandColumn(
    label: String,
    level: Int,
    min: Int,
    max: Int,
    accent: Color,
    gestureKey: Any,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (level > 0) "+$level" else "$level",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        VerticalBandSlider(
            value = level,
            min = min,
            max = max,
            accent = accent,
            gestureKey = gestureKey,
            onValueChange = onValueChange,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = ApplePalette.TextSecondary,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** Thin vertical track with a round white thumb; dragging up raises the band. */
@Composable
private fun VerticalBandSlider(
    value: Int,
    min: Int,
    max: Int,
    accent: Color,
    gestureKey: Any,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = remember { mutableIntStateOf(value) }
    current.intValue = value
    val heightPx = remember { mutableFloatStateOf(0f) }
    val fraction = if (max > min) {
        ((value - min).toFloat() / (max - min)).coerceIn(0f, 1f)
    } else {
        0.5f
    }
    val trackHeight = 112.dp
    val thumbSize = 18.dp

    Box(
        modifier = modifier
            .height(132.dp)
            .fillMaxWidth()
            .onSizeChanged { heightPx.floatValue = it.height.toFloat() }
            .pointerInput(gestureKey, min, max) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    val pxPerUnit = heightPx.floatValue / (max - min).coerceAtLeast(1)
                    if (pxPerUnit <= 0f) return@detectVerticalDragGestures
                    val delta = (-dragAmount / pxPerUnit).roundToInt()
                    if (delta == 0) return@detectVerticalDragGestures
                    val next = (current.intValue + delta).coerceIn(min, max)
                    current.intValue = next
                    onValueChange(next)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(2.dp)
                .height(trackHeight)
                .clip(RoundedCornerShape(1.dp))
                .background(if (value >= 0) accent.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.26f)),
        )
        Box(Modifier.height(trackHeight).width(thumbSize)) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (trackHeight - thumbSize) * (1f - fraction))
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

/** Translucent, thin-bordered card with the icon top-left and the label bottom-left. */
@Composable
private fun PresetCard(
    preset: EqPresets.Preset,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .heightIn(min = 84.dp)
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f))
            .border(
                width = 1.dp,
                color = if (selected) accent.copy(alpha = 0.80f) else Color.White.copy(alpha = 0.10f),
                shape = shape,
            )
            .clickable { onClick() }
            .padding(14.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(
            painter = painterResource(preset.iconRes),
            contentDescription = null,
            tint = if (selected) accent else Color.White,
            modifier = Modifier.size(26.dp),
        )
        Text(
            preset.label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = TextAlign.Start,
            maxLines = 2,
        )
    }
}

private const val EQ_BAND_COUNT = 10

/** Captured order first, then the remaining canonical presets. */
private val EQ_GRID_ORDER: List<List<Int>> = listOf(
    listOf(0, 1),       // Personalizado, Aumento de graves
    listOf(2, 3),       // Aumento de voz, Aumento de agudos
    listOf(15, 10),     // Normal, Pop
    listOf(4, 5),       // Rock, Clásico
    listOf(6, 7),       // Electrónica, HipHop
    listOf(8, 9),       // Baile, Jazz
    listOf(11, 12),     // R&B, Heavy metal
    listOf(13, 14),     // Folk, Latín
)

private fun frequencyLabel(hz: Int): String =
    if (hz >= 1_000) "${hz / 1_000}K" else hz.toString()

/** The sheet always shows ten bands, even when the platform effect exposes fewer. */
private fun tenLevels(source: IntArray): IntArray {
    if (source.size == EQ_BAND_COUNT) return source
    if (source.isEmpty()) return IntArray(EQ_BAND_COUNT)
    return IntArray(EQ_BAND_COUNT) { i ->
        val pos = i.toFloat() * (source.size - 1) / (EQ_BAND_COUNT - 1)
        val lo = pos.toInt().coerceIn(0, source.lastIndex)
        val hi = (lo + 1).coerceAtMost(source.lastIndex)
        val f = pos - lo
        (source[lo] * (1f - f) + source[hi] * f).roundToInt()
    }
}

/** Maps a displayed band onto a real one when the device has fewer than ten bands. */
private fun realBandIndex(index: Int, bandCount: Int): Int =
    if (bandCount >= EQ_BAND_COUNT) {
        index
    } else {
        (index.toFloat() * (bandCount - 1) / (EQ_BAND_COUNT - 1))
            .roundToInt()
            .coerceIn(0, maxOf(0, bandCount - 1))
    }
