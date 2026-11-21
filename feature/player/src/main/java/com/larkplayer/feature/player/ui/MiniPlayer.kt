package com.larkplayer.feature.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.larkplayer.core.designsystem.theme.ApplePalette

/**
 * Floating mini-player pill. Uses the artwork accent so it blends with the
 * library, exactly like Lark's bottom bar.
 */
@Composable
fun MiniPlayer(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accentInt by viewModel.accent.collectAsStateWithLifecycle()
    if (state.id <= 0) return
    val accent = Color(accentInt)

    // Solid accent-coloured bar — no blur, no artwork backdrop.
    val background = Brush.linearGradient(
        listOf(
            accent.copy(alpha = 0.55f),
            accent.copy(alpha = 0.32f),
            accent.copy(alpha = 0.5f),
        )
    )

    Box(
        modifier
            .fillMaxWidth()
            .height(74.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onOpen),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .background(background)
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(26.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF2A2A2E)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = state.albumArt,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                state.title,
                color = accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                buildString {
                    append(state.artist)
                    if (state.album.isNotBlank() && !state.album.equals("<unknown>", true)) {
                        append(" - ").append(state.album)
                    }
                },
                color = Color(0xFFC9C3D4),
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Repeat toggle with a progress ring.
        Box(
            Modifier.size(38.dp).clip(CircleShape).clickable(onClick = viewModel::cycleRepeat),
            contentAlignment = Alignment.Center,
        ) {
            val progress = if (state.duration > 0) {
                (state.position.toFloat() / state.duration).coerceIn(0f, 1f)
            } else 0f
            Canvas(Modifier.size(32.dp)) {
                drawArc(
                    color = accent.copy(alpha = 0.85f),
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = 2.2.dp.toPx()),
                    size = Size(size.width, size.height),
                )
            }
            Icon(
                if (state.repeatMode == 2) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                "Repetir",
                tint = Color.White,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(4.dp))

        // Play / pause
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
                .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                .clickable(onClick = viewModel::togglePlayPause),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (state.isPlaying) "Pausar" else "Reproducir",
                tint = accent,
                modifier = Modifier.size(25.dp),
            )
        }
        }
    }
}

/** Compact preview used by the Tema screen. */
@Composable
fun MiniPlayerPreview(accent: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.25f))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF3A3A40)))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("Spread My Wings", color = accent, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("Zendaya", color = Color(0xFFBEB6C9), fontSize = 6.sp, maxLines = 1)
        }
        Icon(Icons.Rounded.Repeat, null, tint = Color.White, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(24.dp).clip(CircleShape).border(1.dp, Color.White.copy(0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PlayArrow, null, tint = accent, modifier = Modifier.size(14.dp))
        }
    }
}
