package com.larkplayer.feature.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.larkplayer.core.designsystem.component.LarkSheet
import com.larkplayer.feature.player.AudioBands
import com.larkplayer.feature.player.AudioSpectrumSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// Capture controller (Media3 PCM tap — no microphone, same as Lark Player)
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

/**
 * Samples the PCM tap installed in ExoPlayer's audio sink ([AudioSpectrumSource]).
 *
 * The original app does exactly this: an `AudioProcessor` copies the decoded
 * PCM before AudioTrack, computes the spectrum off the audio thread, and sends
 * it to the UI. No `Visualizer`, no `audioSessionId`, no RECORD_AUDIO.
 */
@Composable
fun rememberVisualizerBands(
    enabled: Boolean,
    isPlaying: Boolean,
): androidx.compose.runtime.State<AudioBands> {
    // Remembered so a play/pause change does not cancel and restart the loop.
    val playing by rememberUpdatedState(isPlaying)
    // produceState is the canonical Compose bridge from a coroutine to a State:
    // every write invalidates every composition that reads the returned state.
    return produceState(initialValue = AudioBands(), enabled) {
        if (!enabled) {
            value = AudioBands()
            return@produceState
        }
        var smooth = FloatArray(AudioSpectrumSource.BAND_COUNT)
        var peaks = FloatArray(AudioSpectrumSource.BAND_COUNT)
        var phase = 0f
        var frames = 0L
        while (isActive) {
            val raw = AudioSpectrumSource.currentBands()
            val gain = if (playing) 1f else 0f
            for (i in smooth.indices) {
                val target = (raw.getOrElse(i) { 0f }) * gain
                smooth[i] += (target - smooth[i]) * if (target > smooth[i]) 0.5f else 0.18f
                peaks[i] = maxOf(smooth[i], peaks[i] - 0.03f)
            }
            val energy = smooth.average().toFloat().coerceIn(0f, 1f)
            phase = (phase + 0.04f + 0.16f * energy) % (2f * PI.toFloat())
            frames++
            value = AudioBands(
                bass = smooth.take(4).average().toFloat().coerceIn(0f, 1f),
                mid = smooth.drop(4).take(8).average().toFloat().coerceIn(0f, 1f),
                treble = smooth.drop(12).average().toFloat().coerceIn(0f, 1f),
                spectrum = smooth.copyOf(),
                peaks = peaks.copyOf(),
                waveform = AudioSpectrumSource.currentWaveform(),
                phase = phase,
                frames = frames,
            )
            delay(16L)
        }
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// High-performance drawing component
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

/**
 * Audio-reactive canvas placed directly above the progress bar.
 *
 * A single `produceState` producer samples the real PCM tap at ~60 fps and
 * applies damping + peak decay; reading its state here redraws the Canvas.
 */
@Composable
fun VisualizerCanvas(
    enabled: Boolean,
    isPlaying: Boolean,
    style: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    // A single producer owns all smoothing. Reading its state here makes the
    // Canvas recompose on every captured frame; no second draw-loop is needed.
    val bands = rememberVisualizerBands(enabled, isPlaying).value

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0 || !enabled) return@Canvas
        val alphaFade = if (isPlaying) 1f else 0.55f

        when (style) {
            "rebote" -> drawBounceBars(w, h, bands, accent, alphaFade, rising = false)
            "ascenso" -> drawBounceBars(w, h, bands, accent, alphaFade, rising = true)
            "flujo" -> drawFlujo(w, h, bands, accent, alphaFade)
            else -> drawArmonia(w, h, bands, accent, alphaFade)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBounceBars(
    w: Float,
    h: Float,
    bands: AudioBands,
    accent: Color,
    fade: Float,
    rising: Boolean,
) {
    val count = bands.spectrum.size
    val barW = (w / (count * 1.5f)).coerceAtLeast(2f)
    val gap = barW * 0.5f
    val total = count * (barW + gap)
    val startX = (w - total) / 2f
    for (i in 0 until count) {
        val progress = i.toFloat() / (count - 1)
        val factor = if (rising) 0.3f + progress * 0.5f else 0.85f
        val amp = (bands.spectrum[i] * factor).coerceIn(0.04f, 0.95f)
        val sway = 1f + 0.12f * sin(bands.phase + i * 0.6f).toFloat()
        val barH = (h * 0.86f * amp * sway).coerceAtLeast(3f)
        val peakY = h - (h * 0.86f * bands.peaks[i] * factor).coerceAtLeast(3f)
        val x = startX + i * (barW + gap)
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(accent.copy(alpha = 0.95f * fade), accent.copy(alpha = 0.25f * fade)),
                startY = h - barH,
                endY = h,
            ),
            topLeft = Offset(x, h - barH),
            size = Size(barW, barH),
            cornerRadius = CornerRadius(barW / 2f),
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.35f * fade),
            topLeft = Offset(x, peakY),
            size = Size(barW, 2f),
            cornerRadius = CornerRadius(1f),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArmonia(
    w: Float,
    h: Float,
    bands: AudioBands,
    accent: Color,
    fade: Float,
) {
    val midY = h * 0.62f
    val layers = listOf(
        Triple(bands.bass, 0.72f, 2.2f),
        Triple(bands.mid, 0.48f, 3.1f),
        Triple(bands.treble, 0.28f, 5.4f),
    )
    layers.forEachIndexed { index, (level, scale, frequency) ->
        val amp = h * scale * level.coerceIn(0.05f, 1f)
        val t = bands.phase
        val path = Path().apply {
            moveTo(0f, midY)
            for (step in 0..60) {
                val p = step / 60f
                val x = p * w
                val y = midY - sin(p * frequency * PI.toFloat() + t * (index + 1) * 0.7f).toFloat() * amp -
                    sin(p * 9f * PI.toFloat() + index + t * 1.3f).toFloat() * h * 0.06f * bands.treble
                lineTo(x, y)
            }
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                listOf(
                    accent.copy(alpha = (0.30f - index * 0.07f) * fade),
                    accent.copy(alpha = 0.02f * fade),
                ),
                startY = midY - amp,
                endY = h,
            ),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFlujo(
    w: Float,
    h: Float,
    bands: AudioBands,
    accent: Color,
    fade: Float,
) {
    val midY = h * 0.55f
    val amp = h * 0.38f * bands.mid.coerceIn(0.08f, 1f)
    val t = bands.phase * 0.9f
    val top = Path().apply {
        moveTo(0f, midY)
        for (step in 0..80) {
            val p = step / 80f
            val wave = bands.waveform.getOrElse((p * bands.waveform.size).toInt()) { 0f }
            val y = midY - (sin(p * 3f * PI.toFloat() + t).toFloat() * amp + wave * h * 0.22f)
            lineTo(p * w, y)
        }
    }
    val bottom = Path().apply {
        moveTo(0f, midY)
        for (step in 0..80) {
            val p = step / 80f
            val wave = bands.waveform.getOrElse((p * bands.waveform.size).toInt()) { 0f }
            val y = midY + (sin(p * 3f * PI.toFloat() + 0.6f + t).toFloat() * amp + wave * h * 0.18f)
            lineTo(p * w, y)
        }
    }
    drawPath(top, color = accent.copy(alpha = 0.75f * fade), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    drawPath(bottom, color = accent.copy(alpha = 0.45f * fade), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// Bottom sheet / mode selector
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

private val MODES = listOf(
    "armonia" to "Armonía",
    "rebote" to "Rebote",
    "ascenso" to "Ascenso",
    "flujo" to "Flujo",
)

/** Small diagnostic: confirms whether the real PCM tap is delivering samples. */
@Composable
private fun AudioSignalLabel() {
    // Uses the very same producer as the canvas: if `frames` advances, the
    // capture loop and the state propagation are both working.
    val captured = rememberVisualizerBands(enabled = true, isPlaying = true).value
    val level = AudioSpectrumSource.level()
    val hasSignal = level > 0.002f && captured.frames > 0L
    Text(
        if (hasSignal) {
            "Señal PCM: activa · ${captured.frames} cuadros · nivel ${(level * 100).toInt()}%"
        } else {
            "Señal PCM: sin datos · ${captured.frames} cuadros"
        },
        color = if (hasSignal) Color(0xFF4CD964) else Color(0xFF8E8E93),
        fontSize = 11.sp,
    )
}

/** Switch + horizontal style cards. Starts OFF; user opts in. */
@Composable
fun MusicVisualizerSheet(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    currentStyle: String,
    onStyleChange: (String) -> Unit,
    accent: Color,
    onDismissRequest: () -> Unit,
) {
    LarkSheet(onDismissRequest = onDismissRequest) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Visualizador de música", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (enabled) "Reactivo al audio en tiempo real" else "Desactivado — toca para activar",
                        color = Color(0xFF8E8E93),
                        fontSize = 12.sp,
                    )
                    AudioSignalLabel()
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accent,
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFF47474C),
                        uncheckedBorderColor = Color.Transparent,
                    ),
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MODES.forEach { (id, label) ->
                    VisualizerStyleCard(
                        title = label,
                        selected = currentStyle == id,
                        accent = accent,
                        onClick = { onStyleChange(id) },
                    ) {
                        val previewAccent = if (currentStyle == id) Color.Black else accent
                        when (id) {
                            "armonia" -> MiniWavePreview(previewAccent)
                            "flujo" -> MiniFlowPreview(previewAccent)
                            else -> MiniBarsPreview(previewAccent, if (id == "ascenso") 1 else 0)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VisualizerStyleCard(
    title: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(112.dp)
            .height(84.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) accent else Color(0xFF1F2228))
            .border(
                1.dp,
                if (selected) Color.Transparent else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.height(34.dp), contentAlignment = Alignment.Center) { preview() }
        Text(
            title,
            color = if (selected) Color.Black else Color.White.copy(alpha = 0.75f),
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
fun MiniWavePreview(accent: Color) {
    Canvas(Modifier.size(64.dp, 30.dp)) {
        for (layer in 0..2) {
            val path = Path()
            for (x in 0..size.width.toInt() step 4) {
                val p = x / size.width
                val y = size.height / 2 + sin(p * (3f + layer)).toFloat() * (6f - layer * 1.5f)
                if (x == 0) path.moveTo(x.toFloat(), y) else path.lineTo(x.toFloat(), y)
            }
            drawPath(path, color = accent.copy(alpha = 0.8f - layer * 0.22f), style = Stroke(2f))
        }
    }
}

@Composable
fun MiniBarsPreview(accent: Color, mode: Int) {
    Canvas(Modifier.size(64.dp, 30.dp)) {
        for (i in 0 until 7) {
            val height = (8f + abs(sin(i * 1.1f)).toFloat() * 16f) * if (mode == 1) (0.5f + i * 0.08f) else 1f
            drawRoundRect(
                color = accent,
                topLeft = Offset(i * 9f, size.height - height),
                size = Size(5f, height),
                cornerRadius = CornerRadius(2f),
            )
        }
    }
}

@Composable
fun MiniFlowPreview(accent: Color) {
    Canvas(Modifier.size(64.dp, 30.dp)) {
        val top = Path()
        val bottom = Path()
        for (x in 0..size.width.toInt() step 4) {
            val p = x / size.width
            val wave = sin(p * 2.5f * PI.toFloat()).toFloat() * 8f
            if (x == 0) {
                top.moveTo(x.toFloat(), size.height / 2 - wave)
                bottom.moveTo(x.toFloat(), size.height / 2 + wave * 0.7f)
            } else {
                top.lineTo(x.toFloat(), size.height / 2 - wave)
                bottom.lineTo(x.toFloat(), size.height / 2 + wave * 0.7f)
            }
        }
        drawPath(top, color = accent, style = Stroke(2f, cap = StrokeCap.Round))
        drawPath(bottom, color = accent.copy(alpha = 0.55f), style = Stroke(2f, cap = StrokeCap.Round))
    }
}

