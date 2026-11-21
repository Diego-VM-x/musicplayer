package com.larkplayer.feature.library.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.larkplayer.core.common.MediaFileUtils
import com.larkplayer.core.common.RingtoneHelper
import com.larkplayer.core.database.entity.PlaylistWithCover
import com.larkplayer.core.designsystem.component.GlassClusterIcon
import com.larkplayer.core.designsystem.component.GlassIconButton
import com.larkplayer.core.designsystem.component.GlassIconCluster
import com.larkplayer.core.designsystem.component.GlassMenuItem
import com.larkplayer.core.designsystem.component.GlassMenu
import com.larkplayer.core.designsystem.component.GlassPillButton
import com.larkplayer.core.designsystem.component.GlassSelectionBar
import com.larkplayer.core.designsystem.component.GlassTag
import com.larkplayer.core.designsystem.component.LarkDialog
import com.larkplayer.core.designsystem.component.LarkGradientBackground
import com.larkplayer.core.designsystem.component.LarkPill
import com.larkplayer.core.designsystem.component.LarkSheet
import com.larkplayer.core.designsystem.component.LarkTextField
import com.larkplayer.core.designsystem.component.SelectionAction
import com.larkplayer.core.designsystem.component.SoftCheck
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.core.model.Song
import com.larkplayer.feature.player.ui.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val TABS = listOf("Videos", "Canciones", "Playlists", "Carpetas", "Artistas", "Álbumes")

/**
 * Lark Player home: glass header, floating menus, borderless lists and
 * multi-selection. The mini-player is drawn by the navigation layer.
 */
@Composable
fun LibraryScreen(
    onOpenPlayer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The library keeps a stable look; the artwork accent is used only by the
    // full player and the floating mini-player.
    val accent = ApplePalette.Blue
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searching by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var createDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var pendingAddPlaylistId by remember { mutableStateOf<Long?>(null) }
    var pendingSelectionIds by remember { mutableStateOf(emptySet<Long>()) }
    var newPlaylistSongs by remember { mutableStateOf(emptySet<Long>()) }
    var pickerForIds by remember { mutableStateOf<Set<Long>?>(null) }
    var playlistToDelete by remember { mutableStateOf<PlaylistWithCover?>(null) }

    val clearSelection = {
        selectionMode = false
        selectedIds = emptySet()
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_CANCELED) {
            Toast.makeText(context, "Borrado cancelado", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Canciones borradas", Toast.LENGTH_SHORT).show()
            viewModel.scan(force = true)
        }
        clearSelection()
    }

    LaunchedEffect(state.scanMessage) {
        if (state.scanMessage != null) {
            delay(3_500)
            viewModel.dismissScanMessage()
        }
    }

    val selectedSongs = state.songs.filter { it.id in selectedIds }

    Box(Modifier.fillMaxSize()) {
        LarkGradientBackground(accent = accent, strength = 0.30f) {
            Column(Modifier.fillMaxSize()) {
                // ---- Header ----
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selectionMode) {
                        GlassIconButton(Icons.Rounded.Close, "Cancelar selección", clearSelection)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            "${selectedIds.size} seleccionado${if (selectedIds.size == 1) "" else "s"}",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Text(
                            "Lark Player",
                            color = Color.White,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        GlassIconCluster {
                            GlassClusterIcon(
                                Icons.Rounded.Search,
                                "Buscar",
                                onClick = {
                                    searching = !searching
                                    if (!searching) viewModel.onQueryChange("")
                                },
                            )
                            Box {
                                GlassClusterIcon(Icons.Rounded.SwapVert, "Ordenar", onClick = { sortMenu = true })
                                GlassMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                    listOf("Recientes", "Título", "Artista").forEachIndexed { index, label ->
                                        GlassMenuItem(
                                            label,
                                            selected = state.sortMode == index,
                                            onClick = {
                                                viewModel.setSort(index)
                                                sortMenu = false
                                            },
                                        )
                                    }
                                }
                            }
                            GlassClusterIcon(Icons.Rounded.Radio, "Reproduciendo", onOpenPlayer)
                            GlassClusterIcon(Icons.Rounded.Settings, "Ajustes", onOpenSettings)
                        }
                    }
                }

                if (searching && !selectionMode) {
                    TextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text("Buscar canciones, artistas…", color = ApplePalette.TextSecondary) },
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = ApplePalette.TextSecondary) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.07f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.07f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = ApplePalette.Blue,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }

                // ---- Pill tabs ----
                if (!selectionMode) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TABS.forEachIndexed { index, label ->
                            LarkPill(
                                text = label,
                                selected = state.tab == index,
                                onClick = { viewModel.onTabChange(index) },
                                accent = accent,
                            )
                        }
                    }
                }

                state.scanMessage?.let { msg ->
                    Text(
                        msg,
                        color = ApplePalette.TextSecondary,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                    )
                }

                Box(
                    Modifier
                        .weight(1f)
                        .pointerInput(state.tab) {
                            var dragged = 0f
                            detectHorizontalDragGestures(
                                onDragEnd = { dragged = 0f },
                            ) { change, amount ->
                                change.consume()
                                dragged += amount
                                if (kotlin.math.abs(dragged) >= 72f) {
                                    val next = (state.tab + if (dragged < 0f) 1 else -1)
                                        .coerceIn(0, TABS.lastIndex)
                                    if (next != state.tab) viewModel.onTabChange(next)
                                    dragged = 0f
                                }
                            }
                        },
                ) {
                    AnimatedContent(
                        targetState = state.tab,
                        transitionSpec = {
                            (fadeIn(tween(180)) + slideInHorizontally { it / 16 }) togetherWith
                                (fadeOut(tween(120)) + slideOutHorizontally { -it / 16 })
                        },
                    ) { tab ->
                        when (tab) {
                            0 -> EmptyTab(
                            icon = Icons.Rounded.Album,
                            title = "No hay videos",
                            subtitle = "Los videos locales aparecerán aquí.",
                        )
                        1 -> SongsTab(
                            songs = state.songs,
                            themeSize = state.themeSize,
                            accent = accent,
                            selectionMode = selectionMode,
                            selectedIds = selectedIds,
                            onToggle = { id ->
                                selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                            },
                            onLongPress = { id ->
                                selectionMode = true
                                selectedIds = setOf(id)
                            },
                            onPlay = { list, index ->
                                viewModel.play(list, index)
                                onOpenPlayer()
                            },
                            onRescan = { viewModel.scan(force = true) },
                        )
                        2 -> PlaylistsTab(
                            playlists = state.playlists,
                            onCreate = { createDialog = true },
                            onOpen = { playlist -> onOpenPlaylist(playlist.playlist.id) },
                            onLongPress = { playlistToDelete = it },
                        )
                        3 -> FoldersTab(
                            songs = state.songs,
                            onPlay = { list, index ->
                                viewModel.play(list, index)
                                onOpenPlayer()
                            },
                        )
                        4 -> ArtistsTab(
                            songs = state.songs,
                            onPlay = { list, index ->
                                viewModel.play(list, index)
                                onOpenPlayer()
                            },
                        )
                        else -> AlbumsTab(
                            songs = state.songs,
                            onPlay = { list, index ->
                                viewModel.play(list, index)
                                onOpenPlayer()
                            },
                        )
                        }
                    }
                }
            }
        }

        if (selectionMode) {
            Box(Modifier.align(Alignment.BottomCenter)) {
                GlassSelectionBar(
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 84.dp),
                    actions = listOf(
                        SelectionAction(
                            Icons.Rounded.PlaylistAdd,
                            "Agregar a",
                            onClick = { pickerForIds = selectedIds },
                        ),
                        SelectionAction(
                            Icons.Rounded.QueuePlayNext,
                            "A continuación",
                            onClick = {
                                selectedSongs.forEach { viewModel.addToQueue(it) }
                                clearSelection()
                            },
                        ),
                        SelectionAction(
                            Icons.Rounded.Share,
                            "Compartir",
                            onClick = {
                                selectedSongs.firstOrNull()?.let { song ->
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "audio/*"
                                            putExtra(Intent.EXTRA_STREAM, MediaFileUtils.songUri(song))
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Compartir canción"))
                                    }
                                }
                                clearSelection()
                            },
                        ),
                        SelectionAction(
                            Icons.Rounded.VisibilityOff,
                            "Ocultar",
                            onClick = {
                                selectedIds.forEach { viewModel.hideSong(it) }
                                clearSelection()
                            },
                        ),
                        SelectionAction(
                            Icons.Rounded.DeleteOutline,
                            "Borrar",
                            onClick = { deleteSelected(selectedSongs, context, deleteLauncher) },
                            destructive = true,
                        ),
                    ),
                )
            }
        }
    }

    // ---- Ask which songs to add right after creating a playlist ----
    pendingAddPlaylistId?.let { playlistId ->
        LarkSheet(onDismissRequest = { pendingAddPlaylistId = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                Text("Agregar canciones", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Elige las canciones para tu nueva playlist",
                    color = ApplePalette.TextSecondary,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                )
                LazyColumn(Modifier.height(360.dp)) {
                    itemsIndexed(state.songs, key = { _, song -> song.id }) { _, song ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    newPlaylistSongs = if (song.id in newPlaylistSongs) {
                                        newPlaylistSongs - song.id
                                    } else newPlaylistSongs + song.id
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SoftCheck(
                                selected = song.id in newPlaylistSongs,
                                onClick = {
                                    newPlaylistSongs = if (song.id in newPlaylistSongs) {
                                        newPlaylistSongs - song.id
                                    } else newPlaylistSongs + song.id
                                },
                            )
                            Spacer(Modifier.width(14.dp))
                            SongArtwork(song, Modifier.size(42.dp), RoundedCornerShape(9.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(song.title, color = Color.White, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(song.artist, color = ApplePalette.TextSecondary, fontSize = 10.5.sp, maxLines = 1)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassPillButton(
                        "Omitir",
                        onClick = { pendingAddPlaylistId = null },
                        modifier = Modifier.weight(1f),
                    )
                    GlassPillButton(
                        if (newPlaylistSongs.isEmpty()) "Agregar" else "Agregar (${newPlaylistSongs.size})",
                        onClick = {
                            newPlaylistSongs.forEach { viewModel.addToPlaylist(playlistId, it) }
                            pendingAddPlaylistId = null
                            onOpenPlaylist(playlistId)
                        },
                        modifier = Modifier.weight(1f),
                        filled = true,
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    // ---- Add selection to playlist ----
    pickerForIds?.let { ids ->
        LarkSheet(onDismissRequest = { pickerForIds = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
                Text("Agregar a", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            pendingSelectionIds = ids
                            pickerForIds = null
                            createDialog = true
                        }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, null, tint = ApplePalette.Blue, modifier = Modifier.size(21.dp))
                    Spacer(Modifier.width(14.dp))
                    Text("Nueva playlist", color = ApplePalette.Blue, fontSize = 14.5.sp)
                }
                state.playlists.forEach { playlist ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                ids.forEach { viewModel.addToPlaylist(playlist.playlist.id, it) }
                                pickerForIds = null
                                clearSelection()
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.PlaylistAdd,
                            null,
                            tint = Color(0xFFD6D6DC),
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

    // ---- Create playlist ----
    if (createDialog) {
        LarkDialog(onDismissRequest = { createDialog = false }) {
            Text("Nueva playlist", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            LarkTextField(
                value = newName,
                onValueChange = { newName = it },
                placeholder = "Nombre de playlist",
            )
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    "Cancelar",
                    color = ApplePalette.TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable {
                        createDialog = false
                        newName = ""
                    }.padding(12.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Crear",
                    color = if (newName.isBlank()) Color(0xFF5A5A61) else ApplePalette.Blue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(enabled = newName.isNotBlank()) {
                            val name = newName.trim()
                            scope.launch {
                                val id = viewModel.createPlaylist(name)
                                if (id != null) {
                                    if (pendingSelectionIds.isNotEmpty()) {
                                        pendingSelectionIds.forEach { songId ->
                                            viewModel.addToPlaylist(id, songId)
                                        }
                                        pendingSelectionIds = emptySet()
                                        clearSelection()
                                        onOpenPlaylist(id)
                                    } else {
                                        pendingAddPlaylistId = id
                                        newPlaylistSongs = emptySet()
                                    }
                                }
                            }
                            newName = ""
                            createDialog = false
                        }
                        .padding(12.dp),
                )
            }
        }
    }

    // ---- Delete playlist ----
    playlistToDelete?.let { playlist ->
        LarkSheet(onDismissRequest = { playlistToDelete = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text(
                    "Eliminar lista de reproducción",
                    color = Color(0xFFFF6B6B),
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clickable {
                            viewModel.deletePlaylist(playlist.playlist.id)
                            playlistToDelete = null
                        }
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

/** Deletes the selected files via MediaStore (system confirmation on Android 11+). */
private fun deleteSelected(
    songs: List<Song>,
    context: android.content.Context,
    launcher: ActivityResultLauncher<IntentSenderRequest>,
) {
    val uris = songs.map { MediaFileUtils.songUri(it) }
    if (uris.isEmpty()) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching {
            val request = MediaStore.createDeleteRequest(context.contentResolver, uris)
            launcher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }.onFailure {
            Toast.makeText(context, "No se pudieron borrar las canciones", Toast.LENGTH_SHORT).show()
        }
    } else {
        var ok = true
        uris.forEach { uri ->
            runCatching { context.contentResolver.delete(uri, null, null) }
                .onFailure { ok = false }
        }
        Toast.makeText(
            context,
            if (ok) "Canciones borradas" else "No se pudieron borrar todas las canciones",
            Toast.LENGTH_SHORT,
        ).show()
    }
}

@Composable
private fun SongsTab(
    songs: List<Song>,
    themeSize: Int,
    accent: Color,
    selectionMode: Boolean,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onLongPress: (Long) -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onRescan: () -> Unit,
) {
    if (songs.isEmpty()) {
        EmptyTab(
            icon = Icons.Rounded.MusicNote,
            title = "No encontramos canciones",
            subtitle = "Comprueba el permiso de almacenamiento y vuelve a escanear.",
            actionLabel = "Volver a escanear",
            onAction = onRescan,
        )
        return
    }
    if (themeSize >= 2) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 110.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(count = songs.size, key = { songs[it].id }) { index ->
                val song = songs[index]
                SongGridCard(
                    song = song,
                    accent = accent,
                    selected = song.id in selectedIds,
                    selectionMode = selectionMode,
                    onClick = {
                        if (selectionMode) onToggle(song.id) else onPlay(songs, index)
                    },
                    onLongClick = { onLongPress(song.id) },
                )
            }
        }
    } else {
        val rowHeight = if (themeSize == 0) 56.dp else 64.dp
        LazyColumn(contentPadding = PaddingValues(top = 2.dp, bottom = 110.dp)) {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                SongRow(
                    song = song,
                    rowHeight = rowHeight,
                    accent = accent,
                    selected = song.id in selectedIds,
                    selectionMode = selectionMode,
                    onClick = {
                        if (selectionMode) onToggle(song.id) else onPlay(songs, index)
                    },
                    onLongClick = { onLongPress(song.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRow(
    song: Song,
    rowHeight: Dp,
    accent: Color,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    var hasLyrics by remember(song.path) { mutableStateOf(false) }
    LaunchedEffect(song.path) {
        hasLyrics = withContext(Dispatchers.IO) { MediaFileUtils.hasLocalLyrics(context, song) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accent.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .height(rowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            SoftCheck(selected = selected, onClick = onClick)
            Spacer(Modifier.width(12.dp))
        }
        SongArtwork(song, Modifier.size(rowHeight - 14.dp), RoundedCornerShape(12.dp))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                song.title,
                color = Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasLyrics) {
                    LyricsBadge()
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
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongGridCard(
    song: Song,
    accent: Color,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) accent.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(4.dp),
    ) {
        Box {
            SongArtwork(song, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(16.dp))
            if (selectionMode) {
                SoftCheck(
                    selected = selected,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    onClick = onClick,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            song.title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            lineHeight = 16.sp,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            song.artist,
            color = ApplePalette.TextSecondary,
            fontSize = 10.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LyricsBadge() = GlassTag("LETRAS")

@Composable
fun SongArtwork(
    song: Song,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    circular: Boolean = false,
) {
    Box(
        modifier
            .clip(if (circular) CircleShape else shape)
            .background(Color(0xFF2A2A2E)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.MusicNote, null, tint = Color(0xFF6F6F76), modifier = Modifier.size(22.dp))
        AsyncImage(
            model = if (song.albumId >= 0) MediaFileUtils.albumArtUri(song.albumId) else null,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun PlaylistsTab(
    playlists: List<PlaylistWithCover>,
    onCreate: () -> Unit,
    onOpen: (PlaylistWithCover) -> Unit,
    onLongPress: (PlaylistWithCover) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(top = 2.dp, bottom = 110.dp)) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF1B1B1E))
                    .clickable(onClick = onCreate)
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.Add, null, tint = Color(0xFFC4C4CC), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Nueva playlist", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        items(playlists, key = { it.playlist.id }) { playlist ->
            PlaylistRow(playlist, onOpen, onLongPress)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistRow(
    playlist: PlaylistWithCover,
    onOpen: (PlaylistWithCover) -> Unit,
    onLongPress: (PlaylistWithCover) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF1B1B1E))
            .combinedClickable(onClick = { onOpen(playlist) }, onLongClick = { onLongPress(playlist) })
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF2A2A2E)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.QueueMusic, null, tint = Color(0xFF6F6F76), modifier = Modifier.size(24.dp))
            val cover = playlist.playlist.coverUri
                ?: if (playlist.coverAlbumId >= 0) MediaFileUtils.albumArtUri(playlist.coverAlbumId) else null
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            playlist.playlist.name,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("${playlist.songCount}", color = Color(0xFF8E8E95), fontSize = 12.5.sp)
        Spacer(Modifier.width(10.dp))
    }
}

@Composable
private fun ArtistsTab(songs: List<Song>, onPlay: (List<Song>, Int) -> Unit) {
    val artists = songs.groupBy { it.artist }.toList().sortedBy { it.first.lowercase() }
    LazyColumn(contentPadding = PaddingValues(top = 2.dp, bottom = 110.dp)) {
        itemsIndexed(artists, key = { _, item -> item.first }) { _, (artist, artistSongs) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 1.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onPlay(artistSongs, 0) }
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SongArtwork(artistSongs.first(), Modifier.size(54.dp), circular = true)
                Spacer(Modifier.width(14.dp))
                Text(
                    artist,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${artistSongs.size}", color = Color(0xFF8E8E95), fontSize = 12.sp)
                Spacer(Modifier.width(14.dp))
            }
        }
    }
}

@Composable
private fun AlbumsTab(songs: List<Song>, onPlay: (List<Song>, Int) -> Unit) {
    val albums = songs.groupBy { it.album to it.albumId }.toList().sortedBy { it.first.first.lowercase() }
    LazyColumn(contentPadding = PaddingValues(top = 2.dp, bottom = 110.dp)) {
        items(albums.size) { index ->
            val (key, albumSongs) = albums[index]
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 1.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onPlay(albumSongs, 0) }
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SongArtwork(albumSongs.first(), Modifier.size(56.dp), RoundedCornerShape(12.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        key.first,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${albumSongs.first().artist} · ${albumSongs.size} canciones",
                        color = ApplePalette.TextSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun FoldersTab(songs: List<Song>, onPlay: (List<Song>, Int) -> Unit) {
    val folders = songs.groupBy { folderName(it.path) }.toList().sortedBy { it.first.lowercase() }
    LazyColumn(contentPadding = PaddingValues(top = 2.dp, bottom = 110.dp)) {
        items(folders.size) { index ->
            val (folder, folderSongs) = folders[index]
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 1.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onPlay(folderSongs, 0) }
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SongArtwork(folderSongs.first(), Modifier.size(56.dp), RoundedCornerShape(12.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        folder,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("${folderSongs.size} archivos", color = ApplePalette.TextSecondary, fontSize = 11.5.sp)
                }
            }
        }
    }
}

private fun folderName(path: String): String {
    val parent = runCatching { File(path).parentFile?.name }.getOrNull()
    return parent?.takeIf { it.isNotBlank() } ?: "Almacenamiento"
}

@Composable
private fun EmptyTab(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize().padding(38.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = Color(0xFF5A5A61), modifier = Modifier.size(54.dp))
            Spacer(Modifier.height(14.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = ApplePalette.TextSecondary, fontSize = 12.sp)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(18.dp))
                GlassPillButton(actionLabel, onClick = onAction, filled = true)
            }
        }
    }
}
