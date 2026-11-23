package com.larkplayer.feature.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MyLocation
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
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
import com.larkplayer.core.designsystem.component.LarkDialog
import com.larkplayer.core.designsystem.component.LarkGradientBackground
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.feature.player.PlayerRepository.Companion.MODE_NORMAL
import com.larkplayer.feature.player.PlayerRepository.Companion.MODE_REPEAT_ALL
import com.larkplayer.feature.player.PlayerRepository.Companion.MODE_REPEAT_ONE
import com.larkplayer.feature.player.PlayerRepository.Companion.MODE_SHUFFLE
import kotlinx.coroutines.launch

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
    var draggingId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(queue) {
        if (!dragging) displayOrder = queue
    }
    val density = LocalDensity.current
    val rowPx = with(density) { 72.dp.toPx() }
    val currentId = viewModel.queue().getOrNull(current)?.mediaId
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }

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
                Spacer(Modifier.width(6.dp))
                GlassIconButton(
                    Icons.Rounded.MyLocation,
                    "Ir a la canción actual",
                    onClick = {
                        val index = displayOrder.indexOfFirst { it.mediaId == currentId }
                        if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
                    },
                    size = 36.dp,
                    iconSize = 19.dp,
                    tint = Color.White.copy(alpha = 0.72f),
                    flat = true,
                )
                GlassIconButton(
                    Icons.Rounded.DeleteSweep,
                    "Vaciar cola",
                    onClick = { confirmClear = true },
                    size = 36.dp,
                    iconSize = 19.dp,
                    tint = Color.White.copy(alpha = 0.72f),
                    flat = true,
                )
            }
            Spacer(Modifier.height(10.dp))

            AnimatedContent(
                targetState = state.shuffle,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically { it / 12 }) togetherWith
                        (fadeOut(tween(120)) + slideOutVertically { -it / 12 })
                },
                modifier = Modifier.weight(1f),
            ) { shuffled ->
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp),
                ) {
                    itemsIndexed(
                        displayOrder,
                        key = { index, item -> item.mediaId ?: "queue-$index" },
                    ) { index, item ->
                        val isCurrent = item.mediaId != null && item.mediaId == currentId
                        var dragY by remember(item.mediaId) { mutableFloatStateOf(0f) }
                        var rowTopPx by remember(item.mediaId) { mutableFloatStateOf(0f) }
                        val lifted = draggingId == item.mediaId
                        val liftScale by animateFloatAsState(
                            targetValue = if (lifted) 1.06f else 1f,
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
                            label = "queue_row_lift",
                        )
                        val animatedY by animateFloatAsState(
                            targetValue = if (lifted) dragY else 0f,
                            animationSpec = spring(dampingRatio = 0.9f, stiffness = 900f),
                            label = "queue_row_drag",
                        )
                        val elevationPx = with(density) { if (lifted) 24.dp.toPx() else 0f }
                        Row(
                            Modifier
                                // The rest of the list glides to its new place like
                                // Android launcher icons; the dragged row keeps its
                                // own spring so it never fights this animation.
                                .then(
                                    if (!lifted) {
                                        Modifier.animateItemPlacement(
                                            spring(dampingRatio = 0.8f, stiffness = 500f),
                                        )
                                    } else Modifier
                                )
                                .fillMaxWidth()
                                .onGloballyPositioned { rowTopPx = it.positionInParent().y }
                                .graphicsLayer {
                                    translationY = animatedY
                                    scaleX = liftScale
                                    scaleY = liftScale
                                    shadowElevation = elevationPx
                                    rotationZ = if (lifted) 1.2f else 0f
                                }
                                .padding(horizontal = 12.dp, vertical = 3.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    when {
                                        lifted -> Color(0xEF2D2E36)
                                        isCurrent -> accentColor.copy(alpha = 0.16f)
                                        else -> Color.Transparent
                                    }
                                )
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
                                            onDragStart = {
                                                dragging = true
                                                draggingId = item.mediaId
                                                dragY = 0f
                                            },
                                            onDragEnd = {
                                                dragging = false
                                                draggingId = null
                                                dragY = 0f
                                            },
                                            onDragCancel = {
                                                dragging = false
                                                draggingId = null
                                                dragY = 0f
                                            },
                                        ) { change, dragAmount ->
                                            change.consume()
                                            dragY += dragAmount.y

                                            // Edge auto-scroll: dragging near the top or
                                            // bottom scrolls the queue so a song can be
                                            // moved far without repeated small gestures.
                                            if (lifted) {
                                                val pointerY = rowTopPx + change.position.y
                                                val start = listState.layoutInfo.viewportStartOffset.toFloat()
                                                val end = listState.layoutInfo.viewportEndOffset.toFloat()
                                                val edgePx = with(density) { 72.dp.toPx() }
                                                val speed = with(density) { 28.dp.toPx() }
                                                when {
                                                    pointerY < start + edgePx -> {
                                                        val strength = ((start + edgePx - pointerY) / edgePx).coerceIn(0f, 1f)
                                                        scope.launch { listState.animateScrollBy(-speed * strength) }
                                                    }
                                                    pointerY > end - edgePx -> {
                                                        val strength = ((pointerY - (end - edgePx)) / edgePx).coerceIn(0f, 1f)
                                                        scope.launch { listState.animateScrollBy(speed * strength) }
                                                    }
                                                }
                                            }

                                            val steps = (dragY / rowPx).toInt()
                                            if (steps != 0) {
                                                // Always look up the current live index by ID to
                                                // avoid using a stale captured `index` value after
                                                // previous moves have shifted the list.
                                                val liveFrom = displayOrder.indexOfFirst { it.mediaId == item.mediaId }
                                                if (liveFrom >= 0) {
                                                    val target = (liveFrom + steps).coerceIn(0, displayOrder.lastIndex)
                                                    if (target != liveFrom) {
                                                        val movingId = displayOrder[liveFrom].mediaId
                                                        val targetId = displayOrder[target].mediaId
                                                        displayOrder = displayOrder.toMutableList().apply {
                                                            add(target, removeAt(liveFrom))
                                                        }
                                                        viewModel.moveQueueItemById(movingId, targetId)
                                                        dragY -= (target - liveFrom) * rowPx
                                                    }
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Reorder,
                                    "Arrastrar para reordenar",
                                    tint = if (lifted) accentColor else Color(0xFF9A9AA2),
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
                    onClick = { viewModel.setPlaybackMode(MODE_REPEAT_ALL) },
                )
                QueueToggle(
                    icon = Icons.Rounded.RepeatOne,
                    label = "Repetir una",
                    active = mode == MODE_REPEAT_ONE,
                    accent = accentColor,
                    onClick = { viewModel.setPlaybackMode(MODE_REPEAT_ONE) },
                )
                QueueToggle(
                    icon = Icons.Rounded.Shuffle,
                    label = "Aleatorio",
                    active = mode == MODE_SHUFFLE,
                    accent = accentColor,
                    onClick = { viewModel.setPlaybackMode(MODE_SHUFFLE) },
                )
            }
        }
    }

    if (confirmClear) {
        LarkDialog(onDismissRequest = { confirmClear = false }) {
            Text("Vaciar cola", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                "Se quitarán todas las canciones de la cola y se detendrá la reproducción.",
                color = ApplePalette.TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "Cancelar",
                    color = ApplePalette.TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { confirmClear = false }.padding(12.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Vaciar",
                    color = Color(0xFFFF6B6B),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            confirmClear = false
                            viewModel.clearQueue()
                            onBack()
                        }
                        .padding(12.dp),
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
