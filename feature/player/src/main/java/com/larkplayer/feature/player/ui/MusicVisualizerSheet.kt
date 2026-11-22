package com.larkplayer.feature.player.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.larkplayer.core.designsystem.component.LarkSheet
import kotlin.math.PI
import kotlin.math.sin

/**
 * Visualizer sheet matching Lark Player original ("Visualizador de música").
 * Options: Armonía (fluid waves), Rebote (bouncing bars), Ascenso (rising spectrum).
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
                Text(
                    "Visualizador de música",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
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
    val bg = if (selected) Color(0xFFFFDAB9) else Color(0xFF1F2F38).copy(alpha = 0.6f)
    val textColor = if (selected) Color.Black else Color.White.copy(alpha = 0.75f)

    Column(
        modifier = Modifier
            .width(118.dp)
            .height(84.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(
                1.dp,
                if (selected) Color.Transparent else Color.White.copy(alpha = 0.15f),
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

@Composable
fun VisualizerWave(
    modifier: Modifier = Modifier,
    accent: Color,
    isPlaying: Boolean,
    style: String = "armonia",
) {
    val transition = rememberInfiniteTransition(label = "wave_anim")
    
    // Frecuencia baja (bajos): movimiento rítmico amplio y pesado (~1.1 seg)
    val bassPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(if (isPlaying) 1100 else 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "bass_phase",
    )

    // Frecuencia media (voces/melodía): oscilación armónica fluida (~1.7 seg)
    val midPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(if (isPlaying) 1700 else 8500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "mid_phase",
    )

    // Frecuencia alta (agudos/hi-hats): vibración rápida y precisa (~0.55 seg)
    val treblePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(if (isPlaying) 550 else 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "treble_phase",
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width <= 0 || height <= 0) return@Canvas

        when (style) {
            "rebote", "ascenso" -> {
                val barCount = 36
                val barWidth = (width / (barCount * 1.55f)).coerceAtLeast(3f)
                val spacing = barWidth * 0.55f
                val totalWidth = barCount * (barWidth + spacing)
                val startX = (width - totalWidth) / 2f

                for (i in 0 until barCount) {
                    val progress = i.toFloat() / (barCount - 1)
                    
                    // Separación espectral real:
                    // Primer tercio (0.0..0.33): Bajos intensos impulsados por bassPhase
                    // Segundo tercio (0.33..0.66): Medios y voces impulsados por midPhase
                    // Último tercio (0.66..1.0): Agudos rápidos impulsados por treblePhase
                    val bandAmp = when {
                        progress < 0.35f -> {
                            val b = (sin((bassPhase + i * 0.6).toDouble()).toFloat() * 0.5f + 0.5f)
                            b * 0.95f
                        }
                        progress < 0.70f -> {
                            val m = (sin((midPhase + i * 0.8).toDouble()).toFloat() * 0.5f + 0.5f)
                            m * 0.75f + (sin((bassPhase * 0.5 + i * 0.2).toDouble()).toFloat() * 0.2f)
                        }
                        else -> {
                            val t = (sin((treblePhase + i * 1.2).toDouble()).toFloat() * 0.5f + 0.5f)
                            t * 0.85f
                        }
                    }

                    val factor = if (style == "ascenso") (0.3f + progress * 0.7f) else 1f
                    val waveAmp = if (isPlaying) (bandAmp * factor).coerceIn(0.08f, 1f) else 0.12f
                    val barHeight = (height * 0.92f * waveAmp).coerceAtLeast(4f)
                    val x = startX + i * (barWidth + spacing)
                    val y = height - barHeight
                    
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                accent.copy(alpha = 0.95f),
                                accent.copy(alpha = 0.5f),
                                accent.copy(alpha = 0.2f),
                            ),
                            startY = y,
                            endY = height,
                        ),
                        topLeft = Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
                    )
                }
            }
            else -> {
                // "armonia" - Espectro armónico multicapa (onda de bajos + detalle de agudos)
                val midY = height * 0.62f
                val bassAmp = if (isPlaying) height * 0.28f else height * 0.06f
                val trebleAmp = if (isPlaying) height * 0.12f else height * 0.02f

                // Capa 1: Relleno suave con efecto de bajos
                val path = Path()
                path.moveTo(0f, midY)
                val steps = 50
                for (step in 0..steps) {
                    val p = step.toFloat() / steps
                    val x = p * width
                    val yBass = sin((bassPhase + p * 2.5 * PI).toDouble()).toFloat() * bassAmp
                    val yTreble = sin((treblePhase + p * 6.0 * PI).toDouble()).toFloat() * trebleAmp
                    val y = midY + yBass + yTreble
                    path.lineTo(x, y)
                }
                path.lineTo(width, height)
                path.lineTo(0f, height)
                path.close()

                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.42f), accent.copy(alpha = 0.08f), Color.Transparent),
                        startY = midY - bassAmp - trebleAmp,
                        endY = height,
                    ),
                )

                // Capa 2: Línea vibrante nítida en la cresta
                val linePath = Path()
                linePath.moveTo(0f, midY)
                for (step in 0..steps) {
                    val p = step.toFloat() / steps
                    val x = p * width
                    val yBass = sin((bassPhase + p * 2.5 * PI).toDouble()).toFloat() * bassAmp
                    val yTreble = sin((treblePhase + p * 6.0 * PI).toDouble()).toFloat() * trebleAmp
                    val y = midY + yBass + yTreble
                    linePath.lineTo(x, y)
                }
                drawPath(
                    path = linePath,
                    color = accent.copy(alpha = 0.90f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }
}

@Composable
private fun MiniWavePreview(accent: Color) {
    Canvas(Modifier.size(width = 70.dp, height = 24.dp)) {
        val path = Path()
        val midY = size.height / 2f
        path.moveTo(0f, midY)
        path.cubicTo(size.width * 0.25f, midY - 10f, size.width * 0.5f, midY + 10f, size.width * 0.75f, midY - 6f)
        path.lineTo(size.width, midY)
        drawPath(path, color = accent, style = Stroke(width = 2.dp.toPx()))
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
                size = androidx.compose.ui.geometry.Size(barW, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2),
            )
        }
    }
}
