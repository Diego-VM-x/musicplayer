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
                        uncheckedThumbColor = Color(0xFFC0C0C4),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.20f),
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
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(if (isPlaying) 2200 else 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width <= 0 || height <= 0) return@Canvas

        when (style) {
            "rebote", "ascenso" -> {
                val barCount = 32
                val barWidth = (width / (barCount * 1.5f)).coerceAtLeast(3f)
                val spacing = barWidth * 0.5f
                val totalWidth = barCount * (barWidth + spacing)
                val startX = (width - totalWidth) / 2f

                for (i in 0 until barCount) {
                    val factor = if (style == "ascenso") (i.toFloat() / barCount) else 1f
                    val waveAmp = if (isPlaying) {
                        (sin((phase + i * 0.35f).toDouble()).toFloat() * 0.5f + 0.5f) * factor
                    } else 0.15f
                    val barHeight = (height * 0.85f * waveAmp).coerceAtLeast(4f)
                    val x = startX + i * (barWidth + spacing)
                    val y = height - barHeight
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(accent.copy(alpha = 0.95f), accent.copy(alpha = 0.35f)),
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
                // "armonia" - Fluid wave
                val path = Path()
                val midY = height * 0.65f
                val amp = if (isPlaying) height * 0.32f else height * 0.08f

                path.moveTo(0f, midY)
                val steps = 40
                for (step in 0..steps) {
                    val x = (step.toFloat() / steps) * width
                    val y = midY + sin((phase + step * 0.28f).toDouble()).toFloat() * amp
                    path.lineTo(x, y)
                }
                path.lineTo(width, height)
                path.lineTo(0f, height)
                path.close()

                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.38f), Color.Transparent),
                        startY = midY - amp,
                        endY = height,
                    ),
                )

                // Outline line for wave crest
                val linePath = Path()
                linePath.moveTo(0f, midY)
                for (step in 0..steps) {
                    val x = (step.toFloat() / steps) * width
                    val y = midY + sin((phase + step * 0.28f).toDouble()).toFloat() * amp
                    linePath.lineTo(x, y)
                }
                drawPath(
                    path = linePath,
                    color = accent.copy(alpha = 0.80f),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
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
