package com.larkplayer.feature.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.larkplayer.core.designsystem.component.CircleIconButton
import com.larkplayer.core.designsystem.component.GlassIconButton
import com.larkplayer.core.designsystem.component.LarkGradientBackground
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.feature.player.PlayerRepository.Companion.MODE_NORMAL
import com.larkplayer.feature.player.PlayerRepository.Companion.MODE_REPEAT_ALL
import com.larkplayer.feature.player.PlayerRepository.Companion.MODE_REPEAT_ONE
import com.larkplayer.feature.player.PlayerRepository.Companion.MODE_SHUFFLE

/** Cola de reproducción con controles de repetición y aleatorio siempre visibles. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueueScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accentInt by viewModel.accent.collectAsStateWithLifecycle()
    val queue = viewModel.visibleQueue(state.shuffle)
    val current = viewModel.currentIndex()
    val accentColor = Color(accentInt)

    // Local copy so drag-to-reorder animates smoothly; synced from the player.
    var displayOrder by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(queue) {
        if (!dragging) displayOrder = queue
    }
    val density = LocalDensity.current
    val rowPx = with(density) { 72.dp.toPx() }

    LarkGradientBackground(accent = accentColor, strength = 0.30f) {
        Column(Modifier.fillMaxSize()) {
            // Header row with top bar, title "Fila" and quick clean/search icons
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(onClick = onBack)
                Spacer(Modifier.width(14.dp))
                Text(
                    "Fila",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${queue.size}",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(10.dp))

            val currentId = viewModel.queue().getOrNull(current)?.mediaId
            AnimatedContent(
                targetState = state.shuffle,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically { it / 12 }) togetherWith
                        (fadeOut(tween(120)) + slideOutVertically { -it / 12 })
                },
                modifier = Modifier.weight(1f),
            ) { shuffled ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp),
                ) {
                    itemsIndexed(
                        displayOrder,
                        key = { index, item -> item.mediaId ?: "queue-$index" },
                    ) { index, item ->
                        val isCurrent = item.mediaId != null && item.mediaId == currentId
                        var dragY by remember(item.mediaId) { mutableFloatStateOf(0f) }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 3.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isCurrent) accentColor.copy(alpha = 0.16f) else Color.Transparent)
                                .clickable {
                                    val original = viewModel.originalIndex(item.mediaId)
                                    viewModel.playAtIndex(if (original >= 0) original else index)
                                }
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(46.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xFF2A2A2E)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.MusicNote, null, tint = Color(0xFF6F6F76), modifier = Modifier.size(20.dp))
                                AsyncImage(
                                    model = item.mediaMetadata.artworkUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.mediaMetadata.title?.toString() ?: "Desconocido",
                                    color = if (isCurrent) accentColor else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    item.mediaMetadata.artist?.toString() ?: "",
                                    color = ApplePalette.TextSecondary,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (isCurrent) {
                                Text("SONANDO", color = accentColor, fontSize = 9.sp, letterSpacing = 0.6.sp)
                                Spacer(Modifier.width(4.dp))
                            }
                            // Drag handle: long-press and drag to reorder the queue.
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .pointerInput(item.mediaId) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { dragging = true; dragY = 0f },
                                            onDragEnd = { dragging = false },
                                            onDragCancel = { dragging = false },
                                        ) { change, dragAmount ->
                                            change.consume()
                                            dragY += dragAmount.y
                                            val steps = (dragY / rowPx).toInt()
                                            if (steps != 0 && displayOrder.indices.contains(index)) {
                                                val target = (index + steps).coerceIn(0, displayOrder.lastIndex)
                                                if (target != index) {
                                                    displayOrder = displayOrder.toMutableList().apply {
                                                        add(target, removeAt(index))
                                                    }
                                                    viewModel.moveQueueItem(index, target)
                                                    dragY = 0f
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Reorder,
                                    "Arrastrar para reordenar",
                                    tint = Color(0xFF9A9AA2),
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Single-choice playback option: exactly one of these modes is active.
            val mode = viewModel.currentPlaybackMode()
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 92.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QueueToggle(
                    icon = Icons.Rounded.Repeat,
                    label = "Repetir",
                    active = mode == MODE_REPEAT_ALL,
                    accent = accentColor,
                    onClick = {
                        viewModel.setPlaybackMode(
                            if (mode == MODE_REPEAT_ALL) MODE_NORMAL else MODE_REPEAT_ALL
                        )
                    },
                )
                QueueToggle(
                    icon = Icons.Rounded.RepeatOne,
                    label = "Repetir una",
                    active = mode == MODE_REPEAT_ONE,
                    accent = accentColor,
                    onClick = {
                        viewModel.setPlaybackMode(
                            if (mode == MODE_REPEAT_ONE) MODE_NORMAL else MODE_REPEAT_ONE
                        )
                    },
                )
                QueueToggle(
                    icon = Icons.Rounded.Shuffle,
                    label = "Aleatorio",
                    active = mode == MODE_SHUFFLE,
                    accent = accentColor,
                    onClick = {
                        viewModel.setPlaybackMode(
                            if (mode == MODE_SHUFFLE) MODE_NORMAL else MODE_SHUFFLE
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun QueueToggle(
    icon: ImageVector,
    label: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(82.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(if (active) Color.White else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) Color.Black else Color.White.copy(alpha = 0.65f),
            modifier = Modifier.size(24.dp),
        )
    }
}
