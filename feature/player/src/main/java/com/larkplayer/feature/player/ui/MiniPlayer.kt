package com.larkplayer.feature.player.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.larkplayer.core.designsystem.R as DesignR
import java.util.Locale
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

    // Lark Player Original: dark charcoal rounded surface (#212224)
    val background = Color(0xFF262626)

    Box(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onOpen),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .background(background)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1E1E1E)),
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
                    state.title.uppercase(Locale.getDefault()),
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    (state.artist.ifBlank { "Desconocido" }).uppercase(Locale.getDefault()),
                    color = Color(0xFF8E8E93),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Lark Original: mode toggle (shuffle / loop)
            val mode = viewModel.currentPlaybackMode()
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { viewModel.setPlaybackMode(nextMiniMode(mode)) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(
                        if (mode == com.larkplayer.feature.player.PlayerRepository.MODE_SHUFFLE) DesignR.drawable.ic_lark_shuffle
                        else DesignR.drawable.ic_lark_repeat
                    ),
                    "Modo de reproducción",
                    tint = Color(0xFFE0E0E0),
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(6.dp))

            // Circular Play / pause border button as in screenshot
            val playBtnScale by animateFloatAsState(
                targetValue = if (state.isPlaying) 1.04f else 1.0f,
                animationSpec = tween(150),
                label = "mini_play_scale",
            )
            Box(
                Modifier
                    .size(44.dp)
                    .scale(playBtnScale)
                    .clip(CircleShape)
                    .background(Color(0xFF333333))
                    .border(1.dp, Color(0xFF555555), CircleShape)
                    .clickable(onClick = viewModel::togglePlayPause),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(
                        if (state.isPlaying) DesignR.drawable.ic_lark_pause
                        else DesignR.drawable.ic_lark_play
                    ),
                    contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

private fun nextMiniMode(mode: Int): Int = when (mode) {
    com.larkplayer.feature.player.PlayerRepository.MODE_NORMAL ->
        com.larkplayer.feature.player.PlayerRepository.MODE_REPEAT_ALL
    com.larkplayer.feature.player.PlayerRepository.MODE_REPEAT_ALL ->
        com.larkplayer.feature.player.PlayerRepository.MODE_REPEAT_ONE
    com.larkplayer.feature.player.PlayerRepository.MODE_REPEAT_ONE ->
        com.larkplayer.feature.player.PlayerRepository.MODE_SHUFFLE
    else -> com.larkplayer.feature.player.PlayerRepository.MODE_NORMAL
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
