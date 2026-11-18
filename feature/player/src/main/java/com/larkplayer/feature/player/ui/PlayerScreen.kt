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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.larkplayer.core.designsystem.component.BlurredBackdrop
import com.larkplayer.core.designsystem.component.GlassIconButton
import com.larkplayer.core.designsystem.component.GlassSurface
import com.larkplayer.core.designsystem.theme.ApplePalette

/**
 * Apple Music inspired now-playing screen with liquid-glass surfaces.
 */
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onLyrics: () -> Unit = {},
    onQueue: () -> Unit = {},
    onSleepTimer: () -> Unit = {},
    onEqualizer: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accent = MaterialTheme.colorScheme.primary
    var sliderPos by remember(state.position) { mutableFloatStateOf(state.position.toFloat()) }

    Box(modifier = Modifier.fillMaxSize().background(ApplePalette.Background)) {
        // Blurred album art backdrop — the signature Apple Music look.
        BlurredBackdrop(model = state.albumArt)

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Glass top bar
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                alpha = 0.08f,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassIconButton(onClick = onBack) {
                        Icon(Icons.Default.KeyboardArrowDown, "Close", tint = Color.White)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("PLAYING FROM", color = ApplePalette.TextSecondary,
                            fontSize = 10.sp, letterSpacing = 2.sp)
                        Text("Library", color = Color.White, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold)
                    }
                    GlassIconButton(onClick = onEqualizer) {
                        Icon(Icons.Default.FavoriteBorder, "Equalizer", tint = Color.White)
                    }
                }
            }

            Spacer(Modifier.height(36.dp))

            // Artwork with a soft glow
            AsyncImage(
                model = state.albumArt,
                contentDescription = "Album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(290.dp)
                    .shadow(38.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )

            Spacer(Modifier.height(40.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.title, color = Color.White, fontSize = 26.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(state.artist, color = accent, fontSize = 16.sp,
                        fontWeight = FontWeight.Medium, maxLines = 1)
                }
                GlassIconButton(onClick = {}) {
                    Icon(Icons.Default.FavoriteBorder, "Favorite", tint = Color.White)
                }
            }

            Spacer(Modifier.height(26.dp))

            Slider(
                value = sliderPos.coerceIn(0f, state.duration.toFloat().coerceAtLeast(1f)),
                onValueChange = { sliderPos = it },
                onValueChangeFinished = { viewModel.seek(sliderPos.toLong()) },
                valueRange = 0f..state.duration.toFloat().coerceAtLeast(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(0.28f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(state.position), color = ApplePalette.TextSecondary, fontSize = 12.sp)
                Text("-" + formatTime(state.duration - state.position).let {
                    if (state.duration > state.position) it else formatTime(0)
                }, color = ApplePalette.TextSecondary, fontSize = 12.sp)
            }

            Spacer(Modifier.height(18.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::toggleShuffle) {
                    Icon(Icons.Default.Shuffle, "Shuffle",
                        tint = if (state.shuffle) accent else Color.White.copy(0.55f))
                }
                IconButton(onClick = viewModel::skipPrevious) {
                    Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White,
                        modifier = Modifier.size(42.dp))
                }
                // Main play button — white glass circle
                GlassSurface(
                    shape = RoundedCornerShape(50),
                    alpha = 0.95f,
                    borderAlpha = 0.6f,
                    elevation = 18.dp,
                ) {
                    IconButton(onClick = viewModel::togglePlayPause, modifier = Modifier.size(76.dp)) {
                        AnimatedContent(state.isPlaying) { playing ->
                            Icon(
                                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                if (playing) "Pause" else "Play",
                                tint = ApplePalette.Background,
                                modifier = Modifier.size(42.dp),
                            )
                        }
                    }
                }
                IconButton(onClick = viewModel::skipNext) {
                    Icon(Icons.Default.SkipNext, "Next", tint = Color.White,
                        modifier = Modifier.size(42.dp))
                }
                IconButton(onClick = viewModel::cycleRepeat) {
                    val icon = if (state.repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat
                    Icon(icon, "Repeat",
                        tint = if (state.repeatMode != 0) accent else Color.White.copy(0.55f))
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MiniGlassAction(Icons.Default.Lyrics, "Lyrics", onLyrics)
                MiniGlassAction(Icons.Default.QueueMusic, "Queue", onQueue)
                MiniGlassAction(Icons.Default.Timer, "Sleep timer", onSleepTimer)
            }
        }
    }
}

@Composable
private fun MiniGlassAction(icon: androidx.compose.ui.graphics.vector.ImageVector,
                           label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassSurface(shape = RoundedCornerShape(50), alpha = 0.09f) {
            IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
                Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = ApplePalette.TextSecondary, fontSize = 10.sp)
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
