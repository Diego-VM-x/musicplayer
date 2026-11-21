package com.larkplayer.feature.library.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.larkplayer.core.common.MediaFileUtils
import com.larkplayer.core.designsystem.component.GlassClusterIcon
import com.larkplayer.core.designsystem.component.GlassDivider
import com.larkplayer.core.designsystem.component.GlassIconButton
import com.larkplayer.core.designsystem.component.GlassIconCluster
import com.larkplayer.core.designsystem.component.GlassMenuItem
import com.larkplayer.core.designsystem.component.GlassMenu
import com.larkplayer.core.designsystem.component.GlassPillButton
import com.larkplayer.core.designsystem.component.GlassSelectionBar
import com.larkplayer.core.designsystem.component.GlassSheetColor
import com.larkplayer.core.designsystem.component.GlassTag
import com.larkplayer.core.designsystem.component.LarkDialog
import com.larkplayer.core.designsystem.component.LarkGradientBackground
import com.larkplayer.core.designsystem.component.LarkSheet
import com.larkplayer.core.designsystem.component.LarkTextField
import com.larkplayer.core.designsystem.component.SelectionAction
import com.larkplayer.core.designsystem.component.SoftCheck
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.core.model.Song
import com.larkplayer.feature.player.ui.MiniPlayerOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lark-style playlist detail: floating glass controls, borderless song list,
 * multi-selection and an inline name/cover editor.
 */
@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val accent = Color(state.accent)

    var editing by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var addSheet by remember { mutableStateOf(false) }
    var targetSheet by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var addSelected by remember { mutableStateOf(emptySet<Long>()) }

    // Manual reorder: a local copy keeps the list responsive while the finger
    // moves a lifted row. The ViewModel persists each swap as it happens.
    var displayOrder by remember { mutableStateOf<List<Song>>(emptyList()) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val rowPx = with(density) { 66.dp.toPx() }
    LaunchedEffect(state.songs, draggingId) {
        if (draggingId == null) displayOrder = state.songs
    }

    // The editor (including cover cropping) is a full-screen flow: hide the
    // floating mini-player while it is open.
    DisposableEffect(editing) {
        MiniPlayerOverlay.setVisible(!editing)
        onDispose { MiniPlayerOverlay.setVisible(true) }
    }

    LaunchedEffect(state.playlist?.coverUri, state.songs.firstOrNull()?.albumId) {
        val uri = state.playlist?.coverUri
            ?: state.songs.firstOrNull()?.let { MediaFileUtils.albumArtUri(it.albumId).toString() }
        viewModel.loadAccent(uri)
    }

    val share: (List<Song>) -> Unit = { songs ->
        songs.firstOrNull()?.let { song ->
            runCatching {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    putExtra(Intent.EXTRA_STREAM, MediaFileUtils.songUri(song))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Compartir canción"))
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LarkGradientBackground(accent = accent, strength = 0.36f) {
            Column(Modifier.fillMaxSize()) {
                if (state.selectionMode) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassIconButton(
                            Icons.Rounded.Close,
                            "Cancelar selección",
                            onClick = { viewModel.clearSelection() },
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            "${state.selectedIds.size} seleccionado${if (state.selectedIds.size == 1) "" else "s"}",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 110.dp),
                ) {
                    item {
                        PlaylistHero(
                            state = state,
                            accent = accent,
                            sortMenu = sortMenu,
                            onSortMenuChange = { sortMenu = it },
                            moreMenu = moreMenu,
                            onMoreMenuChange = { moreMenu = it },
                            onBack = {
                                if (state.selectionMode) viewModel.clearSelection() else onBack()
                            },
                            onSort = { mode ->
                                viewModel.setSort(mode)
                                sortMenu = false
                            },
                            onEdit = { editing = true },
                            onAddSongs = { addSheet = true; moreMenu = false },
                            onManage = { viewModel.enterSelection(); moreMenu = false },
                            onDelete = { confirmDelete = true; moreMenu = false },
                            onPlay = { viewModel.playAll(); onOpenPlayer() },
                            onShuffle = { viewModel.shuffleAll(); onOpenPlayer() },
                        )
                    }
                    itemsIndexed(displayOrder, key = { _, song -> song.id }) { index, song ->
                        DetailSongRow(
                            song = song,
                            accent = accent,
                            manualOrder = state.sortMode == 3,
                            lifted = draggingId == song.id,
                            dragOffsetY = dragOffsetY,
                            selected = song.id in state.selectedIds,
                            selectionMode = state.selectionMode,
                            onClick = {
                                if (state.selectionMode) viewModel.toggleSelection(song.id)
                                else {
                                    viewModel.play(index)
                                    onOpenPlayer()
                                }
                            },
                            onLongClick = { viewModel.enterSelection(song.id) },
                            onDragStart = {
                                draggingId = song.id
                                dragOffsetY = 0f
                            },
                            onDrag = { amount ->
                                dragOffsetY += amount
                                val steps = (dragOffsetY / rowPx).toInt()
                                if (steps != 0) {
                                    val from = displayOrder.indexOfFirst { it.id == song.id }
                                    if (from >= 0) {
                                        val target = (from + steps).coerceIn(0, displayOrder.lastIndex)
                                        if (target != from) {
                                            displayOrder = displayOrder.toMutableList().apply {
                                                add(target, removeAt(from))
                                            }
                                            viewModel.moveSong(from, target)
                                            dragOffsetY -= (target - from) * rowPx
                                        }
                                    }
                                }
                            },
                            onDragEnd = {
                                draggingId = null
                                dragOffsetY = 0f
                                displayOrder = state.songs
                            },
                        )
                    }
                    if (state.playlist != null && state.songs.isEmpty()) {
                        item {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    Icons.Rounded.MusicNote,
                                    null,
                                    tint = Color(0xFF5A5A61),
                                    modifier = Modifier.size(48.dp),
                                )
                                Spacer(Modifier.height(12.dp))
                                Text("Esta playlist está vacía", color = Color.White, fontSize = 15.sp)
                                Spacer(Modifier.height(16.dp))
                                GlassPillButton("Agregar canciones", onClick = { addSheet = true }, icon = Icons.Rounded.Add)
                            }
                        }
                    }
                }
            }
        }

        if (state.selectionMode) {
            Box(Modifier.align(Alignment.BottomCenter)) {
                GlassSelectionBar(
                    actions = listOf(
                        SelectionAction(
                            Icons.Rounded.PlaylistAdd,
                            "Agregar a",
                            onClick = { targetSheet = true },
                        ),
                        SelectionAction(
                            Icons.Rounded.QueuePlayNext,
                            "A continuación",
                            onClick = {
                                viewModel.addToQueue(state.selectedIds)
                                viewModel.clearSelection()
                            },
                        ),
                        SelectionAction(
                            Icons.Rounded.Share,
                            "Compartir",
                            onClick = {
                                share(state.songs.filter { it.id in state.selectedIds })
                                viewModel.clearSelection()
                            },
                        ),
                        SelectionAction(
                            Icons.Rounded.VisibilityOff,
                            "Ocultar",
                            onClick = {
                                viewModel.hideSongs(state.selectedIds)
                                viewModel.clearSelection()
                            },
                        ),
                        SelectionAction(
                            Icons.Rounded.DeleteOutline,
                            "Quitar",
                            onClick = {
                                viewModel.removeFromPlaylist(state.selectedIds)
                                viewModel.clearSelection()
                            },
                            destructive = true,
                        ),
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                )
            }
        }
    }

    // ---- Add songs to this playlist ----
    if (addSheet) {
        LarkSheet(onDismissRequest = { addSheet = false }) {
            val existing = state.songs.map { it.id }.toSet()
            val candidates = state.allSongs.filter { it.id !in existing }
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                Text(
                    "Agregar canciones",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                LazyColumn(Modifier.height(380.dp)) {
                    itemsIndexed(candidates, key = { _, song -> song.id }) { _, song ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    addSelected = if (song.id in addSelected) addSelected - song.id else addSelected + song.id
                                }
                                .padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SoftCheck(
                                selected = song.id in addSelected,
                                onClick = {
                                    addSelected = if (song.id in addSelected) addSelected - song.id else addSelected + song.id
                                },
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(song.title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(song.artist, color = ApplePalette.TextSecondary, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                GlassPillButton(
                    text = if (addSelected.isEmpty()) "Selecciona canciones" else "Agregar (${addSelected.size})",
                    onClick = {
                        if (addSelected.isNotEmpty()) {
                            viewModel.addSongsToThisPlaylist(addSelected)
                            addSelected = emptySet()
                            addSheet = false
                        }
                    },
                    filled = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    // ---- Add selection to another playlist ----
    if (targetSheet) {
        LarkSheet(onDismissRequest = { targetSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
                Text(
                    "Agregar a",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                val targets = state.playlists.filter { it.playlist.id != state.playlist?.id }
                if (targets.isEmpty()) {
                    Text(
                        "Crea otra playlist para añadir estas canciones.",
                        color = ApplePalette.TextSecondary,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
                targets.forEach { playlist ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.addSongsToOtherPlaylist(playlist.playlist.id, state.selectedIds)
                                targetSheet = false
                                viewModel.clearSelection()
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.PlaylistAdd,
                            null,
                            tint = ApplePalette.Blue,
                            modifier = Modifier.size(21.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(playlist.playlist.name, color = Color.White, fontSize = 14.5.sp)
                        Spacer(Modifier.weight(1f))
                        Text("${playlist.songCount}", color = ApplePalette.TextSecondary, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(22.dp))
            }
        }
    }

    // ---- Delete confirmation ----
    if (confirmDelete) {
        LarkDialog(onDismissRequest = { confirmDelete = false }) {
            Text("Eliminar playlist", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                "¿Eliminar «${state.playlist?.name.orEmpty()}»? Las canciones no se borrarán.",
                color = ApplePalette.TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "Cancelar",
                    color = ApplePalette.TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { confirmDelete = false }.padding(12.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Eliminar",
                    color = Color(0xFFFF6B6B),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        confirmDelete = false
                        viewModel.deletePlaylist(onBack)
                    }.padding(12.dp),
                )
            }
        }
    }

    // ---- Full-screen editor ----
    if (editing) {
        PlaylistEditor(
            name = state.playlist?.name.orEmpty(),
            cover = state.playlist?.coverUri,
            accent = accent,
            fallbackCover = state.songs.firstOrNull()?.albumId?.let { MediaFileUtils.albumArtUri(it).toString() },
            onCancel = { editing = false },
            onSave = { newName, coverUri ->
                viewModel.savePlaylist(newName, coverUri)
                editing = false
            },
        )
    }
}

@Composable
private fun PlaylistHero(
    state: PlaylistDetailUiState,
    accent: Color,
    sortMenu: Boolean,
    onSortMenuChange: (Boolean) -> Unit,
    moreMenu: Boolean,
    onMoreMenuChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSort: (Int) -> Unit,
    onEdit: () -> Unit,
    onAddSongs: () -> Unit,
    onManage: () -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás", onBack)
            Spacer(Modifier.weight(1f))
            Box {
                GlassIconCluster {
                    GlassClusterIcon(Icons.Rounded.SwapVert, "Ordenar", { onSortMenuChange(true) })
                    GlassClusterIcon(Icons.Rounded.MoreVert, "Más opciones", { onMoreMenuChange(true) }, dividerBefore = true)
                }
                GlassMenu(expanded = sortMenu, onDismissRequest = { onSortMenuChange(false) }) {
                    listOf("Más recientes", "Más reproducido", "A a Z").forEachIndexed { index, label ->
                        GlassMenuItem(label, selected = state.sortMode == index, onClick = { onSort(index) })
                    }
                    GlassDivider()
                    GlassMenuItem(
                        "Organizar manualmente",
                        selected = state.sortMode == 3,
                        onClick = { onSort(3) },
                    )
                }
                GlassMenu(expanded = moreMenu, onDismissRequest = { onMoreMenuChange(false) }) {
                    GlassMenuItem("Agregar canciones", icon = Icons.Rounded.Add, onClick = onAddSongs)
                    GlassMenuItem("Administrar", icon = Icons.Rounded.CheckCircleOutline, onClick = onManage)
                    GlassDivider()
                    GlassMenuItem(
                        "Eliminar lista de reproducción",
                        icon = Icons.Rounded.DeleteOutline,
                        destructive = true,
                        onClick = onDelete,
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        val coverModel = state.playlist?.coverUri
            ?: state.songs.firstOrNull()?.let { MediaFileUtils.albumArtUri(it.albumId).toString() }
        Box(
            Modifier
                .size(210.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF222226)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MusicNote, null, tint = Color(0xFF55555C), modifier = Modifier.size(40.dp))
            AsyncImage(
                model = coverModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.playlist?.name.orEmpty(),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Rounded.Edit,
                    "Editar nombre y portada",
                    tint = Color(0xFFAFAFB6),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${state.songs.size} Canciones",
            color = ApplePalette.TextSecondary,
            fontSize = 12.5.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(26.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassPillButton(
                "Reproducir",
                onClick = onPlay,
                icon = Icons.Rounded.PlayArrow,
                accent = accent,
                modifier = Modifier.weight(1f),
            )
            GlassPillButton(
                "Aleatorio",
                onClick = onShuffle,
                icon = Icons.Rounded.Shuffle,
                accent = accent,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(22.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailSongRow(
    song: Song,
    accent: Color,
    manualOrder: Boolean,
    lifted: Boolean,
    dragOffsetY: Float,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val context = LocalContext.current
    var hasLyrics by remember(song.path) { mutableStateOf(false) }
    LaunchedEffect(song.path) {
        hasLyrics = withContext(Dispatchers.IO) { MediaFileUtils.hasLocalLyrics(context, song) }
    }

    // Spring-driven lift: the row grows slightly, gains a shadow and follows
    // the finger with a smooth spring instead of jumping between positions.
    val liftScale by animateFloatAsState(
        targetValue = if (lifted) 1.035f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
        label = "playlist_row_lift",
    )
    val animatedY by animateFloatAsState(
        targetValue = if (lifted) dragOffsetY else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 520f),
        label = "playlist_row_drag",
    )
    val elevation = with(LocalDensity.current) {
        if (lifted) 18.dp.toPx() else 0f
    }

    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = animatedY
                scaleX = liftScale
                scaleY = liftScale
                shadowElevation = elevation
            }
            .padding(horizontal = 16.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    lifted -> Color(0xEF2D2E36)
                    selected -> accent.copy(alpha = 0.12f)
                    else -> Color.Transparent
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (manualOrder) null else onLongClick,
            )
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            SoftCheck(selected = selected, onClick = onClick)
            Spacer(Modifier.width(12.dp))
        }
        Box(
            Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2A2A2E)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MusicNote, null, tint = Color(0xFF6F6F76), modifier = Modifier.size(20.dp))
            AsyncImage(
                model = if (song.albumId >= 0) MediaFileUtils.albumArtUri(song.albumId) else null,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                song.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasLyrics) {
                    GlassTag("LETRAS")
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    song.artist,
                    color = ApplePalette.TextSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (manualOrder) {
            // Right-side long-press handle: press and drag to lift the row.
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .pointerInput(song.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                        ) { change, amount ->
                            change.consume()
                            onDrag(amount.y)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Reorder,
                    "Mantén pulsado y arrastra para reordenar",
                    tint = if (lifted) accent else Color(0xFF9A9AA2),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** Full-screen playlist editor with SAF cover picking. */
@Composable
private fun PlaylistEditor(
    name: String,
    cover: String?,
    fallbackCover: String?,
    accent: Color,
    onCancel: () -> Unit,
    onSave: (String, String?) -> Unit,
) {
    val context = LocalContext.current
    var newName by remember { mutableStateOf(name) }
    var coverUri by remember { mutableStateOf(cover) }
    var cropping by remember { mutableStateOf<String?>(null) }

    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            cropping = uri.toString()
        }
    }

    Box(Modifier.fillMaxSize().background(GlassSheetColor)) {
        LarkGradientBackground(accent = accent, strength = 0.30f) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassIconButton(Icons.Rounded.Close, "Cancelar", onCancel)
                    Spacer(Modifier.weight(1f))
                    Text("Editar", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.size(44.dp))
                }
                Spacer(Modifier.height(34.dp))
                Box(
                    Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color(0xFF222226))
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.MusicNote, null, tint = Color(0xFF55555C), modifier = Modifier.size(36.dp))
                    AsyncImage(
                        model = coverUri ?: fallbackCover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.height(18.dp))
                GlassPillButton(
                    "Cambiar imagen",
                    onClick = { runCatching { pickCover.launch(arrayOf("image/*")) } },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(40.dp))
                LarkTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = "Nombre de playlist",
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(26.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Cancelar",
                        color = ApplePalette.TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable(onClick = onCancel).padding(12.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    GlassPillButton(
                        "Guardar",
                        onClick = { if (newName.isNotBlank()) onSave(newName.trim(), coverUri) },
                        filled = true,
                    )
                }
            }
        }

        cropping?.let { source ->
            CoverCropper(
                sourceUri = source,
                accent = accent,
                onCancel = { cropping = null },
                onApply = { result ->
                    coverUri = result
                    cropping = null
                },
            )
        }
    }
}

private fun shareSongs(context: android.content.Context, songs: List<Song>) {
    songs.firstOrNull()?.let { song ->
        runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, MediaFileUtils.songUri(song))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Compartir canción"))
        }.onFailure {
            Toast.makeText(context, "No se pudo compartir", Toast.LENGTH_SHORT).show()
        }
    }
}
