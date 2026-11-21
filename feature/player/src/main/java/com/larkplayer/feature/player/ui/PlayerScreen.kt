package com.larkplayer.feature.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.larkplayer.core.designsystem.component.GlassIconButton
import com.larkplayer.core.designsystem.component.GlassMenuItem
import com.larkplayer.core.designsystem.component.GlassPill
import com.larkplayer.core.designsystem.component.GlassMenu
import com.larkplayer.core.designsystem.component.LarkGradientBackground
import com.larkplayer.core.designsystem.theme.ApplePalette

/**
 * Lark's now-playing screen: glass controls, rounded artwork, coloured title,
 * thin progress line and the EQ / Letras / queue row.
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
    var speedMenu by remember { mutableStateOf(false) }
    var visualizerSheetOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.position, dragging) {
        if (!dragging) sliderValue = state.position.toFloat()
    }

    LarkGradientBackground(accent = accent, strength = 0.46f) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 10.dp),
        ) {
            // ---- Top bar ----
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás", onBack, size = 42.dp)
                Spacer(Modifier.weight(1f))
                Text(
                    "Reproduciendo",
                    color = Color(0xFFC5C5CB),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Box {
                    GlassIconButton(
                        Icons.Rounded.MoreVert,
                        "Más opciones",
                        { menuOpen = true },
                        size = 42.dp,
                    )
                    if (menuOpen) {
                        PlayerMoreSheet(
                            title = state.title,
                            artist = state.artist,
                            albumArt = state.albumArt,
                            onAddToPlaylist = {
                                menuOpen = false
                                onBack() // Go to library with selection
                            },
                            onShare = {
                                menuOpen = false
                            },
                            onVisualizer = {
                                menuOpen = false
                                visualizerSheetOpen = true
                            },
                            onSpeed = {
                                menuOpen = false
                                speedMenu = true
                            },
                            onSetRingtone = {
                                menuOpen = false
                            },
                            onSleepTimer = {
                                menuOpen = false
                                onSleepTimer()
                            },
                            onHide = {
                                menuOpen = false
                            },
                            onDelete = {
                                menuOpen = false
                            },
                            onDismissRequest = { menuOpen = false },
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // ---- Artwork (with swipe gesture and slide animation) ----
            val artworkScale by animateFloatAsState(
                targetValue = if (state.isPlaying) 1.0f else 0.94f,
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f),
                label = "artwork_scale",
            )
            var dragDistance by remember { mutableFloatStateOf(0f) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .aspectRatio(1f)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (dragDistance > 60f) {
                                    viewModel.skipPrevious()
                                } else if (dragDistance < -60f) {
                                    viewModel.skipNext()
                                }
                                dragDistance = 0f
                            },
                            onDragCancel = { dragDistance = 0f },
                        ) { change, dragAmount ->
                            change.consume()
                            dragDistance += dragAmount
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = state.id,
                    transitionSpec = {
                        (slideInHorizontally(tween(260)) { width -> width / 3 } + fadeIn(tween(260))) togetherWith
                            (slideOutHorizontally(tween(200)) { width -> -width / 3 } + fadeOut(tween(200)))
                    },
                    label = "artwork_slide_transition",
                ) { targetId ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .scale(artworkScale)
                            .shadow(if (state.isPlaying) 28.dp else 16.dp, RoundedCornerShape(26.dp))
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0xFF242428)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.MusicNote,
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
                }
            }

            Spacer(Modifier.height(22.dp))

            AnimatedContent(
                targetState = state.title,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 4 }) togetherWith
                        (fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { -it / 4 })
                },
                label = "title_transition",
            ) { titleText ->
                Text(
                    titleText,
                    color = accent,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = (-0.4).sp,
                )
            }
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
                Box {
                    GlassPill(modifier = Modifier.clickable { speedMenu = true }) {
                        Text(
                            "${String.format("%.2f", state.playbackSpeed).trimEnd('0').trimEnd('.')}x",
                            color = accent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    GlassMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                            GlassMenuItem(
                                label = "${speed}x",
                                selected = kotlin.math.abs(state.playbackSpeed - speed) < 0.01f,
                                onClick = {
                                    viewModel.setPlaybackSpeed(speed)
                                    speedMenu = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                GlassIconButton(
                    if (state.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (state.isFavorite) "Quitar de favoritos" else "Favorito",
                    viewModel::toggleFavorite,
                    size = 42.dp,
                    iconSize = 22.dp,
                    tint = if (state.isFavorite) accent else Color.White,
                )
            }

            Spacer(Modifier.weight(1f))

            if (state.visualizerEnabled) {
                VisualizerWave(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clickable { visualizerSheetOpen = true },
                    accent = accent,
                    isPlaying = state.isPlaying,
                    style = state.visualizerStyle,
                )
                Spacer(Modifier.height(10.dp))
            }

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
                // Playback mode cycle (Normal -> Repeat All -> Repeat One -> Shuffle)
                val currentMode = viewModel.currentPlaybackMode()
                val (modeIcon, modeDesc) = when (currentMode) {
                    com.larkplayer.feature.player.PlayerRepository.MODE_REPEAT_ALL -> Icons.Rounded.Repeat to "Repetir todo"
                    com.larkplayer.feature.player.PlayerRepository.MODE_REPEAT_ONE -> Icons.Rounded.RepeatOne to "Repetir una"
                    com.larkplayer.feature.player.PlayerRepository.MODE_SHUFFLE -> Icons.Rounded.Shuffle to "Aleatorio"
                    else -> Icons.Rounded.Repeat to "Normal"
                }
                val modeActive = currentMode != com.larkplayer.feature.player.PlayerRepository.MODE_NORMAL
                GlassIconButton(
                    icon = modeIcon,
                    contentDescription = modeDesc,
                    onClick = {
                        val nextMode = when (currentMode) {
                            com.larkplayer.feature.player.PlayerRepository.MODE_NORMAL -> com.larkplayer.feature.player.PlayerRepository.MODE_REPEAT_ALL
                            com.larkplayer.feature.player.PlayerRepository.MODE_REPEAT_ALL -> com.larkplayer.feature.player.PlayerRepository.MODE_REPEAT_ONE
                            com.larkplayer.feature.player.PlayerRepository.MODE_REPEAT_ONE -> com.larkplayer.feature.player.PlayerRepository.MODE_SHUFFLE
                            else -> com.larkplayer.feature.player.PlayerRepository.MODE_NORMAL
                        }
                        viewModel.setPlaybackMode(nextMode)
                    },
                    size = 46.dp,
                    iconSize = 24.dp,
                    tint = if (modeActive) accent else Color.White.copy(alpha = 0.65f),
                )

                GlassIconButton(
                    Icons.Rounded.SkipPrevious,
                    "Anterior",
                    viewModel::skipPrevious,
                    size = 56.dp,
                    iconSize = 36.dp,
                    tint = accent,
                )
                val playButtonScale by animateFloatAsState(
                    targetValue = if (state.isPlaying) 1.05f else 1.0f,
                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
                    label = "play_button_scale",
                )
                Box(
                    Modifier
                        .size(76.dp)
                        .scale(playButtonScale)
                        .shadow(20.dp, CircleShape, clip = false)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.10f))
                            )
                        )
                        .clickable(onClick = viewModel::togglePlayPause),
                    contentAlignment = Alignment.Center,
                ) {
                    Crossfade(targetState = state.isPlaying, animationSpec = tween(180), label = "play_pause_crossfade") { playing ->
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (playing) "Pausar" else "Reproducir",
                            tint = accent,
                            modifier = Modifier.size(46.dp),
                        )
                    }
                }
                GlassIconButton(
                    Icons.Rounded.SkipNext,
                    "Siguiente",
                    viewModel::skipNext,
                    size = 56.dp,
                    iconSize = 36.dp,
                    tint = accent,
                )

                // Placeholder / queue quick access to balance the row
                GlassIconButton(
                    Icons.Rounded.QueueMusic,
                    "Cola de reproducción",
                    onQueue,
                    size = 46.dp,
                    iconSize = 24.dp,
                    tint = Color.White.copy(alpha = 0.65f),
                )
            }

            Spacer(Modifier.weight(1f))

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerBottomAction(
                    icon = Icons.Rounded.Equalizer,
                    label = "Ecualizador",
                    modifier = Modifier.weight(1f),
                    onClick = onEqualizer,
                )
                PlayerBottomLabel("Letras", Modifier.weight(1.3f), onClick = onLyrics)
                PlayerBottomAction(
                    icon = Icons.Rounded.QueueMusic,
                    label = "A continuación",
                    modifier = Modifier.weight(1f),
                    onClick = onQueue,
                )
            }
        }

        if (visualizerSheetOpen) {
            MusicVisualizerSheet(
                enabled = state.visualizerEnabled,
                onEnabledChange = viewModel::setVisualizerEnabled,
                currentStyle = state.visualizerStyle,
                onStyleChange = viewModel::setVisualizerStyle,
                accent = accent,
                onDismissRequest = { visualizerSheetOpen = false },
            )
        }
    }
}

@Composable
private fun PlayerBottomAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = ApplePalette.TextSecondary, modifier = Modifier.size(23.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color(0xFFD6D6DA), fontSize = 10.5.sp)
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
