package com.larkplayer.feature.player.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import com.larkplayer.core.designsystem.component.LarkSheet
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// Audio-band state holder (passed from PlayerScreen)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Real-time audio band levels captured from ExoPlayer via Android Visualizer API.
 * All values normalized 0f..1f.
 */
data class AudioBands(
    val bass: Float = 0f,     // 20–250 Hz  — kick, sub
    val mid: Float = 0f,      // 250–4000 Hz — vocals, melody
    val treble: Float = 0f,   // 4000+ Hz   — hi-hats, cymbals
    /** 16 log-spaced bins for studio-style per-frequency bars. */
    val spectrum: FloatArray = FloatArray(16),
)

// ─────────────────────────────────────────────────────────────────────────────
// Android Visualizer capture effect (composable lifecycle)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Attaches Android [Visualizer] to the given [audioSessionId] while this
 * composable is in the composition. Calls [onBands] on every FFT capture
 * (~30 fps). Requires RECORD_AUDIO permission.
 */
@Composable
fun rememberAudioBands(
    audioSessionId: Int,
    enabled: Boolean,
): AudioBands {
    var bands by remember { mutableStateOf(AudioBands()) }

    DisposableEffect(audioSessionId, enabled) {
        if (!enabled || audioSessionId == 0) {
            bands = AudioBands()
            onDispose {}
        } else {
            val vis = runCatching {
                Visualizer(audioSessionId).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[1] // max = 1024
                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(v: Visualizer, waveform: ByteArray, sr: Int) = Unit
                            override fun onFftDataCapture(v: Visualizer, fft: ByteArray, sr: Int) {
                                // fft[0] = DC, fft[1..n/2] = magnitude pairs
                                val n = fft.size
                                // Frequency per bin = sr / n  (sr in mHz → convert)
                                val binHz = (sr / 1000.0) / n

                                val mags = DoubleArray(n / 2)
                                var bassSum = 0.0
                                var bassCount = 0
                                var midSum = 0.0
                                var midCount = 0
                                var trebleSum = 0.0
                                var trebleCount = 0

                                for (i in 1 until n / 2) {
                                    val re = fft[2 * i].toDouble()
                                    val im = fft[2 * i + 1].toDouble()
                                    val mag = Math.sqrt(re * re + im * im) / 128.0
                                    mags[i] = mag
                                    val freq = binHz * i
                                    when {
                                        freq < 250 -> { bassSum += mag; bassCount++ }
                                        freq < 4000 -> { midSum += mag; midCount++ }
                                        else -> { trebleSum += mag; trebleCount++ }
                                    }
                                }

                                // Log-spaced spectrum: 16 bands from 40 Hz to Nyquist.
                                val spectrum = FloatArray(16)
                                val maxFreq = minOf(16000.0, (sr / 1000.0) / 2.0)
                                for (b in 0 until 16) {
                                    val f0 = 40.0 * Math.pow(maxFreq / 40.0, b / 16.0)
                                    val f1 = 40.0 * Math.pow(maxFreq / 40.0, (b + 1) / 16.0)
                                    var sum = 0.0
                                    var count = 0
                                    for (i in 1 until n / 2) {
                                        val freq = binHz * i
                                        if (freq >= f0 && freq < f1) { sum += mags[i]; count++ }
                                    }
                                    spectrum[b] = (if (count > 0) sum / count * 3.0 else 0.0)
                                        .toFloat().coerceIn(0f, 1f)
                                }

                                bands = AudioBands(
                                    bass = (if (bassCount > 0) (bassSum / bassCount * 2.5) else 0.0)
                                        .toFloat().coerceIn(0f, 1f),
                                    mid = (if (midCount > 0) (midSum / midCount * 3.0) else 0.0)
                                        .toFloat().coerceIn(0f, 1f),
                                    treble = (if (trebleCount > 0) (trebleSum / trebleCount * 3.5) else 0.0)
                                        .toFloat().coerceIn(0f, 1f),
                                    spectrum = spectrum,
                                )
                            }
                        },
                        1000,
                        false,
                        true,
                    )
                    setEnabled(true)
                }
            }.getOrNull()

            onDispose {
                runCatching { vis?.enabled = false; vis?.release() }
            }
        }
    }

    return bands
}

// ─────────────────────────────────────────────────────────────────────────────
// Visualizer settings sheet
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bottom sheet for toggling and styling the visualizer.
 * Default state: OFF (switch unchecked). User activates it here.
 */
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
    ) { granted -> if (granted) onEnabledChange(true) }
    LarkSheet(onDismissRequest = onDismissRequest) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        "Visualizador de música",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
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
                            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
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
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VisualizerStyleCard(
                    title = "Armonía",
                    selected = currentStyle == "armonia",
                    accent = accent,
                    onClick = { onStyleChange("armonia") },
                ) {
                    MiniWavePreview(accent = if (currentStyle == "armonia") Color.Black else accent)
                }

                VisualizerStyleCard(
                    title = "Rebote",
                    selected = currentStyle == "rebote",
                    accent = accent,
                    onClick = { onStyleChange("rebote") },
                ) {
                    MiniBarsPreview(accent = if (currentStyle == "rebote") Color.Black else accent, mode = 0)
                }

                VisualizerStyleCard(
                    title = "Ascenso",
                    selected = currentStyle == "ascenso",
                    accent = accent,
                    onClick = { onStyleChange("ascenso") },
                ) {
                    MiniBarsPreview(accent = if (currentStyle == "ascenso") Color.Black else accent, mode = 1)
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
    val bg = if (selected) accent else Color(0xFF1F2228)
    val textColor = if (selected) Color.Black else Color.White.copy(alpha = 0.75f)

    Column(
        modifier = Modifier
            .width(118.dp)
            .height(84.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
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
        Box(Modifier.height(34.dp), contentAlignment = Alignment.Center) {
            preview()
        }
        Text(
            title,
            color = textColor,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main visualizer composable (shown in PlayerScreen bar)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Audio-reactive waveform/bars drawn on a [Canvas].
 *
 * - **[enabled] = false** → renders a flat static baseline (no animation, no CPU cost).
 * - **[enabled] = true** → reacts to real [AudioBands] from the Android Visualizer API.
 *
 * Band routing:
 * - Bass  → wave amplitude / bar height base
 * - Mid   → secondary wave layer / bar colour modulation
 * - Treble → fine wave detail / small bar jitter on top
 */
@Composable
fun VisualizerWave(
    modifier: Modifier = Modifier,
    accent: Color,
    isPlaying: Boolean,
    style: String = "armonia",
    enabled: Boolean = false,
    bands: AudioBands = AudioBands(),
) {
    // Smoothed band levels (spring-like follow)
    val smoothBass = remember { Animatable(0f) }
    val smoothMid = remember { Animatable(0f) }
    val smoothTreble = remember { Animatable(0f) }

    LaunchedEffect(bands.bass, enabled) {
        if (enabled && isPlaying) smoothBass.animateTo(bands.bass, tween(80))
        else smoothBass.animateTo(0f, tween(400))
    }
    LaunchedEffect(bands.mid, enabled) {
        if (enabled && isPlaying) smoothMid.animateTo(bands.mid, tween(80))
        else smoothMid.animateTo(0f, tween(400))
    }
    LaunchedEffect(bands.treble, enabled) {
        if (enabled && isPlaying) smoothTreble.animateTo(bands.treble, tween(60))
        else smoothTreble.animateTo(0f, tween(400))
    }

    val bassVal = smoothBass.value
    val midVal = smoothMid.value
    val trebleVal = smoothTreble.value

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0 || h <= 0) return@Canvas
        // The visualizer is opt-in: when off, draw absolutely nothing.
        if (!enabled) return@Canvas

        when (style) {
            "rebote", "ascenso" -> {
                val barCount = 36
                val barWidth = (w / (barCount * 1.55f)).coerceAtLeast(3f)
                val spacing = barWidth * 0.55f
                val totalWidth = barCount * (barWidth + spacing)
                val startX = (w - totalWidth) / 2f

                for (i in 0 until barCount) {
                    val progress = i.toFloat() / (barCount - 1)

                    // Each bar follows its own log-spaced frequency bin.
                    val bandAmp = bands.spectrum.getOrElse((progress * bands.spectrum.size).toInt()) {
                        when {
                            progress < 0.35f -> bassVal
                            progress < 0.70f -> midVal
                            else -> trebleVal
                        }
                    }

                    val factor = if (style == "ascenso") (0.3f + progress * 0.7f) else 1f
                    val waveAmp = (bandAmp * factor).coerceIn(0.04f, 1f)
                    val barHeight = (h * 0.92f * waveAmp).coerceAtLeast(4f)
                    val x = startX + i * (barWidth + spacing)
                    val y = h - barHeight

                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                accent.copy(alpha = 0.95f),
                                accent.copy(alpha = 0.5f),
                                accent.copy(alpha = 0.2f),
                            ),
                            startY = y,
                            endY = h,
                        ),
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f),
                    )
                }
            }

            else -> {
                // "armonia" — fluid filled wave, audio-reactive
                val midY = h * 0.72f

                if (!enabled) {
                    // Static flat baseline
                    drawLine(
                        color = Color.White.copy(alpha = 0.18f),
                        start = Offset(0f, midY),
                        end = Offset(w, midY),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    return@Canvas
                }

                val bassAmp = h * 0.42f * (0.08f + bassVal * 0.92f)
                val midAmp  = h * 0.28f * (0.05f + midVal * 0.95f)

                val steps = 60

                // Layer 1 — bass-driven wide wave
                val path1 = Path()
                path1.moveTo(0f, midY)
                for (step in 0..steps) {
                    val p = step.toFloat() / steps
                    val x = p * w
                    // Bass drives the dominant oscillation; treble adds rapid micro-ripple
                    val y = midY - (
                        sin(p * 2.1 * PI).toFloat() * bassAmp
                        + sin(p * 5.3 * PI + trebleVal * PI).toFloat() * h * 0.06f * trebleVal
                    )
                    path1.lineTo(x, y)
                }
                path1.lineTo(w, h)
                path1.lineTo(0f, h)
                path1.close()

                drawPath(
                    path = path1,
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.05f), Color.Transparent),
                        startY = midY - bassAmp,
                        endY = h,
                    ),
                )

                // Layer 2 — mid-frequency secondary wave
                val path2 = Path()
                path2.moveTo(0f, midY)
                for (step in 0..steps) {
                    val p = step.toFloat() / steps
                    val x = p * w
                    val y = midY - (
                        sin(p * 2.8 * PI + 0.8).toFloat() * midAmp
                        + sin(p * 4.0 * PI).toFloat() * h * 0.04f * bassVal
                    )
                    path2.lineTo(x, y)
                }
                path2.lineTo(w, h)
                path2.lineTo(0f, h)
                path2.close()

                drawPath(
                    path = path2,
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.02f), Color.Transparent),
                        startY = midY - midAmp,
                        endY = h,
                    ),
                )

                // Treble line — crisp top highlight that shimmers with hi-hats
                if (trebleVal > 0.05f) {
                    val path3 = Path()
                    path3.moveTo(0f, midY)
                    for (step in 0..steps) {
                        val p = step.toFloat() / steps
                        val x = p * w
                        val y = midY - sin(p * 7.5 * PI).toFloat() * h * 0.08f * trebleVal
                        if (step == 0) path3.moveTo(x, y) else path3.lineTo(x, y)
                    }
                    drawPath(
                        path = path3,
                        color = Color.White.copy(alpha = trebleVal * 0.35f),
                        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Thumbnail previews for style cards
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MiniWavePreview(accent: Color) {
    Canvas(Modifier.size(width = 70.dp, height = 24.dp)) {
        val path = Path()
        val midY = size.height / 2f
        path.moveTo(0f, midY)
        path.cubicTo(size.width * 0.25f, midY - 10f, size.width * 0.5f, midY + 10f, size.width * 0.75f, midY - 6f)
        path.lineTo(size.width, midY)
        drawPath(path, color = accent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun MiniBarsPreview(accent: Color, mode: Int) {
    Canvas(Modifier.size(width = 70.dp, height = 24.dp)) {
        val bars = 14
        val barW = size.width / (bars * 1.6f)
        for (i in 0 until bars) {
            val h = if (mode == 0) {
                (sin(i * 0.5) * 8 + 12).toFloat()
            } else {
                (4 + (i.toFloat() / bars) * 16)
            }
            val x = i * (barW * 1.6f)
            val y = size.height - h
            drawRoundRect(
                color = accent,
                topLeft = Offset(x, y),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2),
            )
        }
    }
}
