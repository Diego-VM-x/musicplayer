package com.larkplayer.feature.library.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.larkplayer.core.designsystem.component.LarkGradientBackground
import com.larkplayer.core.designsystem.component.LarkPill
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
 * Lark Player home: header + pill tabs + dense rounded song cards.
 * The mini-player is rendered by the navigation layer on top of this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenPlayer: () -> Unit,
    onOpenSettings: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accentInt by playerViewModel.accent.collectAsStateWithLifecycle()
    val accent = Color(accentInt)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searching by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var pickerSong by remember { mutableStateOf<Song?>(null) }
    var createDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var playlistToDelete by remember { mutableStateOf<PlaylistWithCover?>(null) }

    LaunchedEffect(state.scanMessage) {
        if (state.scanMessage != null) {
            delay(3_500)
            viewModel.dismissScanMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        LarkGradientBackground(accent = accent, strength = 0.30f) {
            Column(Modifier.fillMaxSize()) {
                // ---- Header ----
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                    HeaderIcon(Icons.Default.Search, "Buscar") {
                        searching = !searching
                        if (!searching) viewModel.onQueryChange("")
                    }
                    Box {
                        HeaderIcon(Icons.Default.SwapVert, "Ordenar") { sortMenu = true }
                        DropdownMenu(
                            expanded = sortMenu,
                            onDismissRequest = { sortMenu = false },
                        ) {
                            listOf("Recientes", "Título", "Artista").forEachIndexed { index, label ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            label,
                                            color = if (state.sortMode == index) ApplePalette.Blue else Color.White,
                                        )
                                    },
                                    onClick = {
                                        viewModel.setSort(index)
                                        sortMenu = false
                                    },
                                )
                            }
                        }
                    }
                    HeaderIcon(Icons.Default.RadioButtonChecked, "Reproduciendo") { onOpenPlayer() }
                    HeaderIcon(Icons.Default.Settings, "Ajustes") { onOpenSettings() }
                }

                if (searching) {
                    TextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text("Buscar canciones, artistas…", color = ApplePalette.TextSecondary) },
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = ApplePalette.TextSecondary) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF1B1B1E),
                            unfocusedContainerColor = Color(0xFF1B1B1E),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = ApplePalette.Blue,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }

                // ---- Pill tabs ----
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
                            accent = ApplePalette.Blue,
                        )
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

                Box(Modifier.weight(1f)) {
                    when (state.tab) {
                        0 -> EmptyTab(
                            icon = Icons.Default.VideoLibrary,
                            title = "No hay videos",
                            subtitle = "Los videos locales aparecerán aquí.",
                        )
                        1 -> SongsTab(
                            songs = state.songs,
                            themeSize = state.themeSize,
                            accent = accent,
                            onOpenPlayer = onOpenPlayer,
                            onLongPress = { actionSong = it },
                            onPlay = { list, index -> viewModel.play(list, index); onOpenPlayer() },
                            onRescan = { viewModel.scan() },
                        )
                        2 -> PlaylistsTab(
                            playlists = state.playlists,
                            onCreate = { createDialog = true },
                            onOpen = { playlist -> viewModel.playPlaylist(playlist.playlist.id); onOpenPlayer() },
                            onLongPress = { playlistToDelete = it },
                        )
                        3 -> FoldersTab(
                            songs = state.songs,
                            onPlay = { list, index -> viewModel.play(list, index); onOpenPlayer() },
                        )
                        4 -> ArtistsTab(
                            songs = state.songs,
                            onPlay = { list, index -> viewModel.play(list, index); onOpenPlayer() },
                        )
                        else -> AlbumsTab(
                            songs = state.songs,
                            onPlay = { list, index -> viewModel.play(list, index); onOpenPlayer() },
                        )
                    }
                }
            }
        }
    }

    // ---- Song actions ----
    actionSong?.let { song ->
        ModalBottomSheet(
            onDismissRequest = { actionSong = null },
            containerColor = ApplePalette.Surface,
        ) {
            Column(Modifier.padding(bottom = 28.dp)) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SongArtwork(song, Modifier.size(52.dp), RoundedCornerShape(10.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(song.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, color = ApplePalette.TextSecondary, fontSize = 12.sp, maxLines = 1)
                    }
                }
                SheetAction(Icons.Default.QueueMusic, "Reproducir a continuación") {
                    viewModel.addToQueue(song)
                    actionSong = null
                }
                SheetAction(Icons.Default.PlaylistAdd, "Añadir a playlist") {
                    pickerSong = song
                    actionSong = null
                }
                SheetAction(Icons.Default.NotificationsActive, "Establecer como tono") {
                    if (Settings.System.canWrite(context)) {
                        val ok = RingtoneHelper.set(context, song)
                        Toast.makeText(
                            context,
                            if (ok) "Tono establecido" else "No se pudo establecer el tono",
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "Concede «Modificar ajustes del sistema» y vuelve a intentarlo",
                            Toast.LENGTH_LONG,
                        ).show()
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                    Uri.parse("package:${context.packageName}"),
                                )
                            )
                        }
                    }
                    actionSong = null
                }
                SheetAction(Icons.Default.VisibilityOff, "Ocultar de la biblioteca") {
                    viewModel.hideSong(song.id)
                    actionSong = null
                }
            }
        }
    }

    // ---- Add to playlist ----
    pickerSong?.let { song ->
        AlertDialog(
            onDismissRequest = { pickerSong = null },
            containerColor = ApplePalette.SurfaceElevated,
            title = { Text("Añadir a playlist", color = Color.White) },
            text = {
                Column {
                    if (state.playlists.isEmpty()) {
                        Text("Crea primero una playlist.", color = ApplePalette.TextSecondary)
                    } else {
                        state.playlists.forEach { playlist ->
                            TextButton(onClick = {
                                viewModel.addToPlaylist(playlist.playlist.id, song.id)
                                pickerSong = null
                            }) {
                                Text(playlist.playlist.name, color = Color.White)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pickerSong = null }) { Text("Cerrar") }
            },
        )
    }

    // ---- Create playlist ----
    if (createDialog) {
        AlertDialog(
            onDismissRequest = { createDialog = false },
            containerColor = ApplePalette.SurfaceElevated,
            title = { Text("Nueva playlist", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        scope.launch { viewModel.createPlaylist(newName.trim()) }
                        newName = ""
                        createDialog = false
                    }
                }) { Text("Crear") }
            },
            dismissButton = {
                TextButton(onClick = { createDialog = false }) { Text("Cancelar") }
            },
        )
    }

    // ---- Delete playlist ----
    playlistToDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            containerColor = ApplePalette.SurfaceElevated,
            title = { Text("Eliminar playlist", color = Color.White) },
            text = {
                Text("¿Eliminar «${playlist.playlist.name}»? Las canciones no se borran.", color = ApplePalette.TextSecondary)
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist(playlist.playlist.id)
                    playlistToDelete = null
                }) { Text("Eliminar", color = ApplePalette.Pink) }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun HeaderIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(23.dp))
    }
}

@Composable
private fun SongsTab(
    songs: List<Song>,
    themeSize: Int,
    accent: Color,
    onOpenPlayer: () -> Unit,
    onLongPress: (Song) -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onRescan: () -> Unit,
) {
    if (songs.isEmpty()) {
        EmptyTab(
            icon = Icons.Default.MusicNote,
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
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 104.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(count = songs.size, key = { songs[it].id }) { index ->
                val song = songs[index]
                SongGridCard(song, onClick = { onPlay(songs, index) }, onLongClick = { onLongPress(song) })
            }
        }
    } else {
        val rowHeight = if (themeSize == 0) 56.dp else 64.dp
        LazyColumn(contentPadding = PaddingValues(top = 2.dp, bottom = 104.dp)) {
            itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                SongRow(
                    song = song,
                    rowHeight = rowHeight,
                    onClick = { onPlay(songs, index) },
                    onLongClick = { onLongPress(song) },
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
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var hasLyrics by remember(song.path) { mutableStateOf(false) }
    LaunchedEffect(song.path) {
        hasLyrics = withContext(Dispatchers.IO) { MediaFileUtils.hasLocalLyrics(song.path) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ApplePalette.Card)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .height(rowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Edge-to-edge artwork: fills the row height with no border or inset.
        SongArtwork(
            song = song,
            modifier = Modifier.fillMaxHeight().width(rowHeight),
            shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
        )
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
private fun SongGridCard(song: Song, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ApplePalette.Card)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        SongArtwork(
            song,
            Modifier.fillMaxWidth().aspectRatio(1f),
            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        )
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
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
}

@Composable
private fun LyricsBadge() {
    Box(
        Modifier
            .clip(RoundedCornerShape(3.dp))
            .border(BorderStroke(1.dp, Color(0xFF6C6C73)), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text("LETRAS", color = Color(0xFFB9B9BF), fontSize = 7.sp, letterSpacing = 0.4.sp)
    }
}

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
        Icon(Icons.Default.MusicNote, null, tint = Color(0xFF6F6F76), modifier = Modifier.size(22.dp))
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
    LazyColumn(contentPadding = PaddingValues(top = 2.dp, bottom = 104.dp)) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ApplePalette.Surface)
                    .clickable(onClick = onCreate)
                    .padding(vertical = 22.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Add, null, tint = Color(0xFF9A9AA2), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Nueva playlist", color = Color(0xFF9A9AA2), fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ApplePalette.Card)
            .combinedClickable(onClick = { onOpen(playlist) }, onLongClick = { onLongPress(playlist) })
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(64.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                .background(Color(0xFF2A2A2E)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.QueueMusic, null, tint = Color(0xFF6F6F76), modifier = Modifier.size(22.dp))
            if (playlist.coverAlbumId >= 0) {
                AsyncImage(
                    model = MediaFileUtils.albumArtUri(playlist.coverAlbumId),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            playlist.playlist.name,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("${playlist.songCount}", color = Color(0xFF8E8E95), fontSize = 12.sp)
        Spacer(Modifier.width(14.dp))
    }
}

@Composable
private fun ArtistsTab(songs: List<Song>, onPlay: (List<Song>, Int) -> Unit) {
    val artists = songs.groupBy { it.artist }.toList().sortedBy { it.first.lowercase() }
    LazyColumn(contentPadding = PaddingValues(top = 2.dp, bottom = 104.dp)) {
        itemsIndexed(artists, key = { _, item -> item.first }) { _, (artist, artistSongs) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ApplePalette.Card)
                    .clickable { onPlay(artistSongs, 0) }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
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
                Spacer(Modifier.width(6.dp))
            }
        }
    }
}

@Composable
private fun AlbumsTab(songs: List<Song>, onPlay: (List<Song>, Int) -> Unit) {
    val albums = songs.groupBy { it.album to it.albumId }.toList().sortedBy { it.first.first.lowercase() }
    LazyColumn(contentPadding = PaddingValues(top = 2.dp, bottom = 104.dp)) {
        items(albums.size) { index ->
            val (key, albumSongs) = albums[index]
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ApplePalette.Card)
                    .clickable { onPlay(albumSongs, 0) }
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SongArtwork(
                    albumSongs.first(),
                    Modifier.fillMaxHeight().width(64.dp),
                    RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                )
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
    LazyColumn(contentPadding = PaddingValues(top = 2.dp, bottom = 104.dp)) {
        items(folders.size) { index ->
            val (folder, folderSongs) = folders[index]
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ApplePalette.Card)
                    .clickable { onPlay(folderSongs, 0) }
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SongArtwork(
                    folderSongs.first(),
                    Modifier.fillMaxHeight().width(64.dp),
                    RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                )
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(ApplePalette.Blue)
                        .clickable(onClick = onAction)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Refresh, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(actionLabel, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color(0xFFD6D6DC), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, color = Color.White, fontSize = 14.5.sp)
    }
}
