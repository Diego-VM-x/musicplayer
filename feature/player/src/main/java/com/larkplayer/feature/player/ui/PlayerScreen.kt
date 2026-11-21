package com.larkplayer.feature.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.larkplayer.core.designsystem.component.GlassIconButton
import com.larkplayer.core.designsystem.component.GlassMenu
import com.larkplayer.core.designsystem.component.GlassMenuItem
import com.larkplayer.core.designsystem.component.GlassPill
import com.larkplayer.core.designsystem.component.LarkGradientBackground
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.feature.player.PlayerRepository
import java.util.Locale
import kotlin.math.abs

/**
 * Lark's now-playing screen, aligned with `fragment_player_motion_audio.xml`:
 * no top bar, swipe-down to close, white two-line title, three transport
 * controls, and the EQ / Letras / queue row with the mode badge on the queue.
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
    var dragDown by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(state.position, dragging) {
        if (!dragging) sliderValue = state.position.toFloat()
    }

    val currentMode = viewModel.currentPlaybackMode()
    val speedVisible = abs(state.playbackSpeed - 1f) > 0.01f

    LarkGradientBackground(accent = accent, strength = 0.46f) {
        Column(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragDown > 120f) onBack()
                            dragDown = 0f
                        },
                        onDragCancel = { dragDown = 0f },
                    ) { _, dragAmount ->
                        dragDown += dragAmount
                    }
                }
                .padding(horizontal = 20.dp)
                .padding(bottom = 10.dp),
        ) {
            // ---- Drag-down handle (the original player has no top bar) ----
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(30.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Cerrar reproductor",
                    tint = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                )
            }

            // ---- Artwork ----
            val artworkScale by animateFloatAsState(
                targetValue = if (state.isPlaying) 1.0f else 0.94f,
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f),
                label = "artwork_scale",
            )
            var dragDistance by remember { mutableFloatStateOf(0f) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 239.dp)
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
                ) {
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

            Spacer(Modifier.height(18.dp))

            // ---- Title + artist, with favourite and overflow on the right ----
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
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
                            color = Color.White,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            lineHeight = 27.sp,
                            overflow = TextOverflow.Ellipsis,
                            letterSpacing = (-0.3).sp,
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        state.artist.ifBlank { "Artista desconocido" },
                        color = Color(0xFFB9B9C0),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                GlassIconButton(
                    if (state.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (state.isFavorite) "Quitar de favoritos" else "Favorito",
                    viewModel::toggleFavorite,
                    size = 42.dp,
                    iconSize = 23.dp,
                    tint = if (state.isFavorite) accent else Color.White,
                    flat = true,
                )
                Box {
                    GlassIconButton(
                        Icons.Rounded.MoreVert,
                        "Más opciones",
                        { menuOpen = true },
                        size = 38.dp,
                        iconSize = 23.dp,
                        tint = Color.White.copy(alpha = 0.78f),
                        flat = true,
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

            // Speed is only shown when it differs from 1x, like the original.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (speedVisible) {
                    GlassPill(modifier = Modifier.clickable { speedMenu = true }) {
                        Text(
                            formatSpeed(state.playbackSpeed),
                            color = accent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                GlassMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                        GlassMenuItem(
                            label = formatSpeed(speed),
                            selected = abs(state.playbackSpeed - speed) < 0.01f,
                            onClick = {
                                viewModel.setPlaybackSpeed(speed)
                                speedMenu = false
                            },
                        )
                    }
                }
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
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    formatTime(state.position),
                    color = Color(0xFF8E8E95),
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(start = 2.dp),
                )
                Text(
                    formatTime(state.duration),
                    color = Color(0xFF8E8E95),
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(end = 2.dp),
                )
            }

            Spacer(Modifier.height(18.dp))

            // ---- Transport: previous / play / next only ----
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(30.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(
                    Icons.Rounded.SkipPrevious,
                    "Anterior",
                    viewModel::skipPrevious,
                    size = 54.dp,
                    iconSize = 34.dp,
                    tint = accent,
                    flat = true,
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
                        .shadow(16.dp, CircleShape, clip = false)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF32343B), Color(0xFF222328))
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
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
                    size = 54.dp,
                    iconSize = 34.dp,
                    tint = accent,
                    flat = true,
                )
            }

            Spacer(Modifier.weight(1f))

            // ---- EQ / Letras / queue (mode badge lives on the queue icon) ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    GlassIconButton(
                        Icons.Rounded.Equalizer,
                        "Ecualizador",
                        onEqualizer,
                        size = 44.dp,
                        iconSize = 24.dp,
                        tint = ApplePalette.TextSecondary,
                        flat = true,
                    )
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    PlayerBottomLabel("Letras", onClick = onLyrics)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Box {
                        GlassIconButton(
                            Icons.Rounded.QueueMusic,
                            "A continuación",
                            onQueue,
                            size = 46.dp,
                            iconSize = 24.dp,
                            tint = ApplePalette.TextSecondary,
                            flat = true,
                        )
                        PlayerModeBadge(
                            mode = currentMode,
                            accent = accent,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 5.dp, y = (-4).dp),
                            onClick = { viewModel.setPlaybackMode(nextMode(currentMode)) },
                        )
                    }
                }
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

/** Small badge on the queue icon; tapping it cycles the playback mode. */
@Composable
private fun PlayerModeBadge(
    mode: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val icon = when (mode) {
        PlayerRepository.MODE_REPEAT_ONE -> Icons.Rounded.RepeatOne
        PlayerRepository.MODE_SHUFFLE -> Icons.Rounded.Shuffle
        else -> Icons.Rounded.Repeat
    }
    val description = when (mode) {
        PlayerRepository.MODE_REPEAT_ALL -> "Repetir todo"
        PlayerRepository.MODE_REPEAT_ONE -> "Repetir una"
        PlayerRepository.MODE_SHUFFLE -> "Aleatorio"
        else -> "Modo normal"
    }
    Box(
        modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(Color(0xFF1B1C20))
            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            description,
            tint = if (mode == PlayerRepository.MODE_NORMAL) Color.White.copy(alpha = 0.55f) else accent,
            modifier = Modifier.size(13.dp),
        )
    }
}

private fun nextMode(mode: Int): Int = when (mode) {
    PlayerRepository.MODE_NORMAL -> PlayerRepository.MODE_REPEAT_ALL
    PlayerRepository.MODE_REPEAT_ALL -> PlayerRepository.MODE_REPEAT_ONE
    PlayerRepository.MODE_REPEAT_ONE -> PlayerRepository.MODE_SHUFFLE
    else -> PlayerRepository.MODE_NORMAL
}

@Composable
private fun PlayerBottomLabel(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Color(0xFFD6D6DA),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatSpeed(speed: Float): String {
    val text = String.format(Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.')
    return "${text}x"
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    return "%d:%02d".format(Locale.US, minutes, seconds)
}
