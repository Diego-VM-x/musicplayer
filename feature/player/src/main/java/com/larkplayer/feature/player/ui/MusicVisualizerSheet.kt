package com.larkplayer.feature.player.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.larkplayer.core.designsystem.component.LarkSheet
import com.larkplayer.feature.player.AudioBands
import com.larkplayer.feature.player.VisualizerEngine
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// Capture controller (Visualizer + automatic fallback)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Owns a [VisualizerEngine] tied to this composable's lifecycle.
 *
 * Connect [audioSessionId] from your player (`ExoPlayer.audioSessionId` or
 * `MediaPlayer.getAudioSessionId()`). If RECORD_AUDIO is missing or the id is
 * unavailable, the engine switches to a soft simulated signal automatically.
 */
@Composable
fun rememberVisualizerBands(
    audioSessionId: Int,
    enabled: Boolean,
    isPlaying: Boolean,
): androidx.compose.runtime.State<AudioBands> {
    val context = LocalContext.current
    val engine = remember { VisualizerEngine(context) }

    DisposableEffect(audioSessionId, enabled) {
        if (enabled) {
            engine.start(audioSessionId, isPlaying)
        } else {
            engine.release()
        }
        onDispose { engine.release() }
    }
    LaunchedEffect(isPlaying) { engine.setPlaying(isPlaying) }

    return engine.bands.collectAsStateWithLifecycle()
}

// ─────────────────────────────────────────────────────────────────────────────
// High-performance drawing component
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Audio-reactive canvas placed directly above the progress bar.
 *
 * Rendering runs on `withFrameNanos` (60 fps) and interpolates the capture
 * data with damping + peak decay so bars never jump between frames.
 */
@Composable
fun VisualizerCanvas(
    audioSessionId: Int,
    enabled: Boolean,
    isPlaying: Boolean,
    style: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val target by rememberVisualizerBands(audioSessionId, enabled, isPlaying)
    val display = remember { mutableStateOf(AudioBands()) }

    LaunchedEffect(enabled) {
        if (!enabled) {
            display.value = AudioBands()
            return@LaunchedEffect
        }
        var lastFrame = 0L
        withFrameNanos { now ->
            val dt = if (lastFrame == 0L) 16_000_000L else (now - lastFrame).coerceAtMost(50_000_000L)
            lastFrame = now
            val k = 1f - kotlin.math.exp(-dt / 70_000_000f)
            val d = display.value
            val spectrum = FloatArray(d.spectrum.size) { i ->
                d.spectrum[i] + (target.spectrum[i] - d.spectrum[i]) * k
            }
            val peaks = FloatArray(d.peaks.size) { i ->
                maxOf(spectrum[i], d.peaks[i] - 0.012f * (dt / 16_000_000f))
            }
            display.value = d.copy(
                spectrum = spectrum,
                peaks = peaks,
                waveform = target.waveform,
                bass = d.bass + (target.bass - d.bass) * k,
                mid = d.mid + (target.mid - d.mid) * k,
                treble = d.treble + (target.treble - d.treble) * k,
                fallback = target.fallback,
            )
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0 || !enabled) return@Canvas
        val bands = display.value
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
        val barH = (h * 0.86f * amp).coerceAtLeast(3f)
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
        val path = Path().apply {
            moveTo(0f, midY)
            for (step in 0..60) {
                val p = step / 60f
                val x = p * w
                val y = midY - sin(p * frequency * PI).toFloat() * amp -
                    sin(p * 9f * PI + index).toFloat() * h * 0.06f * bands.treble
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
    val top = Path().apply {
        moveTo(0f, midY)
        for (step in 0..80) {
            val p = step / 80f
            val wave = bands.waveform.getOrElse((p * bands.waveform.size).toInt()) { 0f }
            val y = midY - (sin(p * 3f * PI).toFloat() * amp + wave * h * 0.22f)
            lineTo(p * w, y)
        }
    }
    val bottom = Path().apply {
        moveTo(0f, midY)
        for (step in 0..80) {
            val p = step / 80f
            val wave = bands.waveform.getOrElse((p * bands.waveform.size).toInt()) { 0f }
            val y = midY + (sin(p * 3f * PI + 0.6).toFloat() * amp + wave * h * 0.18f)
            lineTo(p * w, y)
        }
    }
    drawPath(top, color = accent.copy(alpha = 0.75f * fade), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    drawPath(bottom, color = accent.copy(alpha = 0.45f * fade), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom sheet / mode selector
// ─────────────────────────────────────────────────────────────────────────────

private val MODES = listOf(
    "armonia" to "Armonía",
    "rebote" to "Rebote",
    "ascenso" to "Ascenso",
    "flujo" to "Flujo",
)

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
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // Even when denied the visualizer works in its simulated fallback.
        onEnabledChange(true)
    }
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
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { checked ->
                        if (checked &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            onEnabledChange(checked)
                        }
                    },
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
            val wave = sin(p * 2.5f * PI).toFloat() * 8f
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
