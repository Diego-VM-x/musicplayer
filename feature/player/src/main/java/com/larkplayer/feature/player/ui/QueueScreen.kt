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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import coil.compose.AsyncImage
import com.larkplayer.core.designsystem.component.LarkDialog
import com.larkplayer.core.designsystem.component.LarkGradientBackground
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.core.designsystem.R as DesignR
import com.larkplayer.feature.player.PlayerRepository.Companion.MODE_REPEAT_ALL
import com.larkplayer.feature.player.PlayerRepository.Companion.MODE_REPEAT_ONE
import com.larkplayer.feature.player.PlayerRepository.Companion.MODE_SHUFFLE
import com.larkplayer.feature.player.R as FeatureR
import kotlinx.coroutines.launch

/** Cola de reproducción con mini-player fijo, controles de modo y reordenación. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueueScreen(
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accentInt by viewModel.accent.collectAsStateWithLifecycle()
    val accentColor = Color(accentInt)
    val queue = viewModel.visibleQueue(state.shuffle)
    val current = viewModel.currentIndex()

    // Local copy so drag-to-reorder animates smoothly; synced from the player.
    // Keyed on the media-id sequence, not on list identity: the progress ticker
    // rebuilds `queue` four times a second but the rows must keep their places.
    var displayOrder by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var dragging by remember { mutableStateOf(false) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    val queueSignature = queue.joinToString(",") { it.mediaId.orEmpty() }
    LaunchedEffect(queueSignature) {
        if (!dragging) displayOrder = queue
    }
    val density = LocalDensity.current
    val rowPx = with(density) { 66.dp.toPx() }
    val currentId = viewModel.queue().getOrNull(current)?.mediaId
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }

    // This screen owns a pinned mini-player, so the floating one is hidden here.
    DisposableEffect(Unit) {
        MiniPlayerOverlay.setVisible(false)
        onDispose { MiniPlayerOverlay.setVisible(true) }
    }

    // Soft blue/teal wash at the top fading into the dark app background.
    val topWash = lerp(accentColor, Color(0xFF0E7C86), 0.35f)
    LarkGradientBackground(accent = accentColor, strength = 0.24f) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(topWash.copy(alpha = 0.26f), Color.Transparent),
                        ),
                    ),
            )
            Column(Modifier.fillMaxSize()) {
                QueueMiniPlayer(
                    state = state,
                    accent = accentColor,
                    onPlayPause = viewModel::togglePlayPause,
                    onSeek = viewModel::seek,
                    onOpenPlayer = onOpenPlayer,
                )
                QueueHeader(
                    count = queue.size,
                    onLocate = {
                        val index = displayOrder.indexOfFirst { it.mediaId == currentId }
                        if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
                    },
                    onClear = { confirmClear = true },
                )

                AnimatedContent(
                    targetState = state.shuffle,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically { it / 12 }) togetherWith
                            (fadeOut(tween(120)) + slideOutVertically { -it / 12 })
                    },
                    modifier = Modifier.weight(1f),
                ) { _ ->
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 12.dp),
                    ) {
                        itemsIndexed(
                            displayOrder,
                            key = { index, item -> item.mediaId ?: "queue-$index" },
                        ) { index, item ->
                            val isCurrent = item.mediaId == currentId
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
                            val artist = item.mediaMetadata.artist?.toString().orEmpty()
                            val album = item.mediaMetadata.albumTitle?.toString().orEmpty()
                            val subtitle = listOf(artist, album)
                                .filter { it.isNotBlank() }
                                .distinct()
                                .joinToString(" · ")
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
                                            isCurrent -> accentColor.copy(alpha = 0.14f)
                                            else -> Color.Transparent
                                        },
                                    )
                                    .clickable {
                                        val original = viewModel.originalIndex(item.mediaId)
                                        viewModel.playAtIndex(if (original >= 0) original else index)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF24252A)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Rounded.MusicNote,
                                        null,
                                        tint = Color(0xFF6F6F76),
                                        modifier = Modifier.size(21.dp),
                                    )
                                    AsyncImage(
                                        model = item.mediaMetadata.artworkUri,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            item.mediaMetadata.title?.toString() ?: "Desconocido",
                                            color = if (isCurrent) accentColor else Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        if (isCurrent) {
                                            Spacer(Modifier.width(5.dp))
                                            Icon(
                                                painterResource(DesignR.drawable.ic_lark_equalizer),
                                                "Sonando",
                                                tint = accentColor,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier
                                                .border(
                                                    1.dp,
                                                    Color.White.copy(alpha = 0.42f),
                                                    RoundedCornerShape(4.dp),
                                                )
                                                .padding(horizontal = 5.dp, vertical = 1.dp),
                                        ) {
                                            Text(
                                                "LETRAS",
                                                color = Color.White.copy(alpha = 0.78f),
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.4.sp,
                                            )
                                        }
                                        Spacer(Modifier.width(5.dp))
                                        Icon(
                                            painterResource(FeatureR.drawable.ic_lark_verified),
                                            null,
                                            tint = accentColor,
                                            modifier = Modifier.size(12.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            subtitle,
                                            color = ApplePalette.TextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(6.dp))
                                // Drag handle: long-press and drag to reorder the queue.
                                Box(
                                    Modifier
                                        .size(34.dp)
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
                                        painterResource(FeatureR.drawable.ic_lark_drag_lines),
                                        "Arrastrar para reordenar",
                                        tint = if (lifted) accentColor else Color(0xFF9A9AA2),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // Exactly one playback mode is active at a time.
                val mode = viewModel.currentPlaybackMode()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QueueToggle(
                        iconRes = DesignR.drawable.ic_lark_repeat_one,
                        label = "Repetir una",
                        active = mode == MODE_REPEAT_ONE,
                        onClick = { viewModel.setPlaybackMode(MODE_REPEAT_ONE) },
                        modifier = Modifier.weight(1f),
                    )
                    QueueToggle(
                        iconRes = DesignR.drawable.ic_lark_repeat,
                        label = "Repetir todo",
                        active = mode == MODE_REPEAT_ALL,
                        onClick = { viewModel.setPlaybackMode(MODE_REPEAT_ALL) },
                        modifier = Modifier.weight(1f),
                    )
                    QueueToggle(
                        iconRes = DesignR.drawable.ic_lark_shuffle,
                        label = "Aleatorio",
                        active = mode == MODE_SHUFFLE,
                        onClick = { viewModel.setPlaybackMode(MODE_SHUFFLE) },
                        modifier = Modifier.weight(1f),
                    )
                }
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

/** Mini reproductor fijo en la cabecera de la cola. */
@Composable
private fun QueueMiniPlayer(
    state: PlayerUiState,
    accent: Color,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val progress = if (state.duration > 0L) state.position.toFloat() / state.duration.toFloat() else 0f
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF24252A)),
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
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    state.artist.ifBlank { "Desconocido" },
                    color = ApplePalette.TextSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(
                        if (state.isPlaying) DesignR.drawable.ic_lark_pause
                        else DesignR.drawable.ic_lark_play,
                    ),
                    contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        // Thin timeline with a generous touch target: tapping any point maps
        // the x offset to a fraction of the duration and seeks there.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .pointerInput(state.duration, onSeek) {
                    detectTapGestures { offset ->
                        val duration = state.duration
                        if (duration > 0L && size.width > 0) {
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            onSeek((fraction * duration).toLong())
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.30f)),
            )
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent),
            )
        }
        // No chevron. Tapping this strip or swiping down opens the full player.
        Box(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .clickable(onClick = onOpenPlayer)
                .pointerInput(onOpenPlayer) {
                    var swipedDown = false
                    detectVerticalDragGestures(
                        onDragStart = { swipedDown = false },
                        onDragEnd = { if (swipedDown) onOpenPlayer() },
                        onDragCancel = { swipedDown = false },
                    ) { _, dragAmount ->
                        if (dragAmount > 4f) swipedDown = true
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = 36.dp, height = 3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.30f)),
            )
        }
    }
}

/** Cabecera "Fila" con acceso a la canción actual y a vaciar la cola. */
@Composable
private fun QueueHeader(
    count: Int,
    onLocate: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Fila",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.6).sp,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "$count",
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(14.dp))
        Icon(
            painterResource(FeatureR.drawable.ic_lark_target),
            "Ir a la canción actual",
            tint = Color.White.copy(alpha = 0.78f),
            modifier = Modifier
                .size(22.dp)
                .clickable(onClick = onLocate),
        )
        Spacer(Modifier.width(18.dp))
        Icon(
            painterResource(FeatureR.drawable.ic_lark_broom),
            "Vaciar cola",
            tint = Color.White.copy(alpha = 0.78f),
            modifier = Modifier
                .size(22.dp)
                .clickable(onClick = onClear),
        )
    }
}

/** Botón ancho de modo: fondo claro e icono oscuro cuando está activo. */
@Composable
private fun QueueToggle(
    iconRes: Int,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) Color.White else Color.White.copy(alpha = 0.07f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = label,
            tint = if (active) Color(0xFF141518) else Color.White.copy(alpha = 0.68f),
            modifier = Modifier.size(22.dp),
        )
    }
}
