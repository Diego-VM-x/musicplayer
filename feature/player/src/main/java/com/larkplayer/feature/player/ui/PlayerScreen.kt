package com.larkplayer.feature.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.larkplayer.core.designsystem.theme.Purple40
import com.larkplayer.core.designsystem.theme.Purple80

/**
 * Full now-playing screen. Visual design approximates Lark Player's player:
 * gradient backdrop, large rounded artwork, centered meta, seek bar and
 * transport controls. All colors come from MaterialTheme so custom themes apply.
 */
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onLyrics: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accent = MaterialTheme.colorScheme.primary
    var sliderPos by remember(state.position) { mutableFloatStateOf(state.position.toFloat()) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Gradient backdrop (Lark uses album-art-derived colors; ours uses theme accent).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.55f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NOW PLAYING", color = Color.White.copy(0.7f), fontSize = 11.sp, letterSpacing = 2.sp)
                    Text(state.title, color = Color.White, fontSize = 13.sp, maxLines = 1)
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Default.MoreVert, "More", tint = Color.White)
                }
            }

            Spacer(Modifier.height(28.dp))

            // Artwork
            AsyncImage(
                model = state.albumArt,
                contentDescription = "Album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )

            Spacer(Modifier.height(32.dp))

            Text(
                state.title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(state.artist, color = Color.White.copy(0.7f), fontSize = 15.sp)

            Spacer(Modifier.height(24.dp))

            // Seek bar
            Slider(
                value = sliderPos.coerceIn(0f, state.duration.toFloat().coerceAtLeast(1f)),
                onValueChange = { sliderPos = it },
                onValueChangeFinished = { viewModel.seek(sliderPos.toLong()) },
                valueRange = 0f..state.duration.toFloat().coerceAtLeast(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(0.3f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(state.position), color = Color.White.copy(0.7f), fontSize = 12.sp)
                Text(formatTime(state.duration), color = Color.White.copy(0.7f), fontSize = 12.sp)
            }

            Spacer(Modifier.height(12.dp))

            // Transport controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::toggleShuffle) {
                    Icon(
                        Icons.Default.Shuffle, "Shuffle",
                        tint = if (state.shuffle) accent else Color.White.copy(0.6f),
                    )
                }
                IconButton(onClick = viewModel::skipPrevious) {
                    Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
                }
                // Main play button
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(36.dp))
                        .background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = viewModel::togglePlayPause, modifier = Modifier.size(72.dp)) {
                        AnimatedContent(state.isPlaying) { playing ->
                            Icon(
                                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                if (playing) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                }
                IconButton(onClick = viewModel::skipNext) {
                    Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = viewModel::cycleRepeat) {
                    val icon = if (state.repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat
                    Icon(
                        icon, "Repeat",
                        tint = if (state.repeatMode != 0) accent else Color.White.copy(0.6f),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Secondary actions
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = onLyrics) {
                    Icon(Icons.Default.Lyrics, "Lyrics", tint = Color.White.copy(0.8f))
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Default.FavoriteBorder, "Favorite", tint = Color.White.copy(0.8f))
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Default.Timer, "Sleep timer", tint = Color.White.copy(0.8f))
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
