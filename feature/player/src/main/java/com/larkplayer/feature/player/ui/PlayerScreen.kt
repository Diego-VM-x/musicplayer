package com.larkplayer.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.larkplayer.core.designsystem.component.LarkGradientBackground
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.feature.player.AlbumColorRepository

/**
 * Lark's now-playing screen: huge rounded artwork, coloured title, thin progress
 * line, three big transport controls and the EQ / Letras / queue row.
 */
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onLyrics: () -> Unit = {},
    onQueue: () -> Unit = {},
    onSleepTimer: () -> Unit = {},
    onEqualizer: () -> Unit = {},
    onSettings: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accentInt by viewModel.accent.collectAsStateWithLifecycle()
    val accent = Color(accentInt)

    var sliderValue by remember(state.id) { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.position, dragging) {
        if (!dragging) sliderValue = state.position.toFloat()
    }

    LarkGradientBackground(accent = accent, strength = 0.46f) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp),
        ) {
            // Artwork
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shadow(24.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF242428)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.QueueMusic,
                    null,
                    tint = Color(0xFF5C5C64),
                    modifier = Modifier.size(64.dp),
                )
                AsyncImage(
                    model = state.albumArt,
                    contentDescription = "Carátula",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.height(22.dp))

            Text(
                state.title,
                color = accent,
                fontSize = 25.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = (-0.4).sp,
            )
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.artist.ifBlank { "Artista desconocido" },
                    color = Color(0xFFB9B9C0),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::toggleFavorite) {
                    Icon(
                        if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        if (state.isFavorite) "Quitar de favoritos" else "Favorito",
                        tint = if (state.isFavorite) accent else Color.White,
                        modifier = Modifier.size(23.dp),
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, "Más opciones", tint = Color.White, modifier = Modifier.size(23.dp))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Ecualizador") },
                            leadingIcon = { Icon(Icons.Default.Equalizer, null) },
                            onClick = { menuOpen = false; onEqualizer() },
                        )
                        DropdownMenuItem(
                            text = { Text("Temporizador para dormir") },
                            leadingIcon = { Icon(Icons.Default.Timer, null) },
                            onClick = { menuOpen = false; onSleepTimer() },
                        )
                        DropdownMenuItem(
                            text = { Text("Ajustes") },
                            onClick = { menuOpen = false; onSettings() },
                        )
                        DropdownMenuItem(
                            text = { Text("Ir a la biblioteca") },
                            onClick = { menuOpen = false; onBack() },
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            LaunchedEffect(state.position) { /* keeps slider synced above */ }
            Slider(
                value = sliderValue.coerceIn(0f, state.duration.toFloat().coerceAtLeast(1f)),
                onValueChange = {
                    dragging = true
                    sliderValue = it
                },
                onValueChangeFinished = {
                    viewModel.seek(sliderValue.toLong())
                    dragging = false
                },
                valueRange = 0f..state.duration.toFloat().coerceAtLeast(1f),
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.20f),
                ),
                modifier = Modifier.fillMaxWidth().height(24.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(state.position), color = Color(0xFF8E8E95), fontSize = 11.5.sp)
                Text(formatTime(state.duration), color = Color(0xFF8E8E95), fontSize = 11.5.sp)
            }

            Spacer(Modifier.height(20.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::skipPrevious, modifier = Modifier.size(56.dp)) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        "Anterior",
                        tint = accent,
                        modifier = Modifier.size(38.dp),
                    )
                }
                IconButton(onClick = viewModel::togglePlayPause, modifier = Modifier.size(76.dp)) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (state.isPlaying) "Pausar" else "Reproducir",
                        tint = accent,
                        modifier = Modifier.size(58.dp),
                    )
                }
                IconButton(onClick = viewModel::skipNext, modifier = Modifier.size(56.dp)) {
                    Icon(
                        Icons.Default.SkipNext,
                        "Siguiente",
                        tint = accent,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Row(
                Modifier.fillMaxWidth().padding(bottom = 18.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerBottomAction(
                    icon = Icons.Default.Equalizer,
                    label = null,
                    modifier = Modifier.weight(1f),
                    onClick = onEqualizer,
                )
                PlayerBottomLabel("Letras", Modifier.weight(1.3f), onClick = onLyrics)
                PlayerBottomAction(
                    icon = Icons.Default.QueueMusic,
                    label = null,
                    modifier = Modifier.weight(1f),
                    onClick = onQueue,
                )
            }
        }
    }
}

@Composable
private fun PlayerBottomAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = Color(0xFFCFCFD5), modifier = Modifier.size(23.dp))
        if (label != null) {
            Spacer(Modifier.height(3.dp))
            Text(label, color = Color(0xFFCFCFD5), fontSize = 11.sp)
        }
    }
}

@Composable
private fun PlayerBottomLabel(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color(0xFFD6D6DA), fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
