package com.larkplayer.feature.library.ui

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.imageLoader
import com.larkplayer.core.common.MediaFileUtils
import com.larkplayer.core.designsystem.component.GlassDivider
import com.larkplayer.core.designsystem.component.GlassIconButton
import com.larkplayer.core.designsystem.component.GlassMenu
import com.larkplayer.core.designsystem.component.GlassMenuItem
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
import com.larkplayer.core.designsystem.R as DesignR
import com.larkplayer.core.model.Song
import com.larkplayer.feature.library.R
import com.larkplayer.feature.player.ui.MiniPlayerOverlay
import com.larkplayer.feature.player.ui.PlayerUiState
import com.larkplayer.feature.player.ui.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lark-style playlist detail: a cover-tinted vertical wash, large artwork,
 * two big playback pills, a borderless song list and an inline mini-player.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val accent = Color(state.accent)

    val coverModel = state.playlist?.coverUri
        ?: state.songs.firstOrNull()?.let { MediaFileUtils.albumArtUri(it.albumId).toString() }
    val coverAccent = rememberCoverAccent(coverModel, accent)

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

    val showMini = !editing && !state.selectionMode && playerState.id > 0L
    val topColor = lerp(Color(0xFF08171C), coverAccent, 0.52f)
    val midColor = lerp(Color(0xFF050609), coverAccent, 0.16f)
    val detailBackground = Brush.verticalGradient(
        0f to topColor,
        0.34f to midColor,
        0.72f to Color(0xFF030304),
        1f to Color.Black,
    )

    Box(Modifier.fillMaxSize().background(detailBackground)) {
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
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                item {
                    PlaylistHero(
                        state = state,
                        accent = accent,
                        coverModel = coverModel,
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
                    val rowLifted = draggingId == song.id
                    Box(
                        Modifier.then(
                            if (!rowLifted) {
                                Modifier.animateItemPlacement(
                                    spring(dampingRatio = 0.8f, stiffness = 500f),
                                )
                            } else Modifier
                        )
                    ) {
                        DetailSongRow(
                            song = song,
                            accent = accent,
                            playing = playerState.id == song.id,
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

            if (showMini) {
                DetailMiniPlayer(
                    state = playerState,
                    coverAccent = coverAccent,
                    onOpen = onOpenPlayer,
                    onTogglePlay = { playerViewModel.togglePlayPause() },
                    onToggleShuffle = { playerViewModel.toggleShuffle() },
                )
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

/** Dominant cover colour, falling back to the user accent when there is no artwork. */
@Composable
private fun rememberCoverAccent(coverModel: String?, fallback: Color): Color {
    val context = LocalContext.current
    var color by remember { mutableStateOf(fallback) }
    LaunchedEffect(coverModel, fallback) {
        val model = coverModel
        if (model.isNullOrBlank()) {
            color = fallback
            return@LaunchedEffect
        }
        val dominant = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context).data(model).allowHardware(false).build()
                val drawable = context.imageLoader.execute(request).drawable
                (drawable as? BitmapDrawable)?.bitmap?.let { bitmap ->
                    Palette.from(bitmap).generate().dominantSwatch?.rgb
                }
            }.getOrNull()
        }
        color = dominant?.let { Color(it) } ?: fallback
    }
    return color
}

@Composable
private fun PlaylistHero(
    state: PlaylistDetailUiState,
    accent: Color,
    coverModel: String?,
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
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lark_detail_back),
                    contentDescription = "Atrás",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Box {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { onSortMenuChange(true) }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lark_detail_sort),
                            contentDescription = "Ordenar",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Box(
                        Modifier
                            .padding(horizontal = 2.dp)
                            .width(1.dp)
                            .height(20.dp)
                            .background(Color.White.copy(alpha = 0.12f)),
                    )
                    IconButton(onClick = { onMoreMenuChange(true) }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            painter = painterResource(DesignR.drawable.ic_lark_more),
                            contentDescription = "Más opciones",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
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

        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .size(280.dp)
                .shadow(20.dp, RoundedCornerShape(30.dp), clip = false)
                .clip(RoundedCornerShape(30.dp))
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
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state.playlist?.name.orEmpty(),
                color = Color.White,
                fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 250.dp),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_lark_detail_edit),
                    contentDescription = "Editar nombre y portada",
                    tint = Color(0xFFC2C2C9),
                    modifier = Modifier.size(17.dp),
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
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DetailPillButton(
                text = "Reproducir",
                icon = DesignR.drawable.ic_lark_play,
                tint = accent,
                onClick = onPlay,
                modifier = Modifier.weight(1f),
            )
            DetailPillButton(
                text = "Aleatorio",
                icon = DesignR.drawable.ic_lark_shuffle,
                tint = accent,
                onClick = onShuffle,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(14.dp))
    }
}

/** Semi-transparent dark pill with blue label, as in the playlist detail view. */
@Composable
private fun DetailPillButton(
    text: String,
    icon: Int,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.09f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(9.dp))
        Text(text, color = tint, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
    }
}

/** Mini-player pinned to the bottom of this screen (the global one is hidden here). */
@Composable
private fun DetailMiniPlayer(
    state: PlayerUiState,
    coverAccent: Color,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val barColor = lerp(Color(0xFF0E0F13), coverAccent, 0.42f)
    val progress = if (state.duration > 0L) {
        (state.position.toFloat() / state.duration.toFloat()).coerceIn(0f, 1f)
    } else 0f
    Column(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(barColor)
            .clickable(onClick = onOpen),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
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
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    state.artist.ifBlank { "Desconocido" },
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleShuffle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(DesignR.drawable.ic_lark_shuffle),
                    contentDescription = "Aleatorio",
                    tint = if (state.shuffle) Color(0xFF6FB6FF) else Color.White.copy(alpha = 0.78f),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(2.dp))
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .clickable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(
                        if (state.isPlaying) DesignR.drawable.ic_lark_pause else DesignR.drawable.ic_lark_play,
                    ),
                    contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.size(23.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.12f))) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(coverAccent),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailSongRow(
    song: Song,
    accent: Color,
    playing: Boolean,
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
        hasLyrics = withContext(Dispatchers.IO) { MediaFileUtils.hasLocalLyrics(song.path) }
    }

    // Spring-driven lift: the row grows slightly, gains a shadow and follows
    // the finger with a smooth spring instead of jumping between positions.
    val liftScale by animateFloatAsState(
        targetValue = if (lifted) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
        label = "playlist_row_lift",
    )
    val animatedY by animateFloatAsState(
        targetValue = if (lifted) dragOffsetY else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 900f),
        label = "playlist_row_drag",
    )
    val elevation = with(LocalDensity.current) {
        if (lifted) 24.dp.toPx() else 0f
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
            .height(66.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            SoftCheck(selected = selected, onClick = onClick)
            Spacer(Modifier.width(12.dp))
        }
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
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
            if (playing) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                )
                Icon(
                    painter = painterResource(DesignR.drawable.ic_lark_equalizer),
                    contentDescription = "Reproduciéndose",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                song.title,
                color = if (playing) accent else Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasLyrics) {
                    GlassTag("LETRAS")
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_lark_detail_verified),
                        contentDescription = "Letras verificadas",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    listOf(song.artist, song.album).filter { it.isNotBlank() }.joinToString(" - "),
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
