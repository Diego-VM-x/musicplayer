package com.larkplayer.feature.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.larkplayer.core.common.MediaFileUtils
import com.larkplayer.core.designsystem.component.GlassSurface
import com.larkplayer.core.designsystem.theme.ApplePalette
import com.larkplayer.core.model.Song
import kotlinx.coroutines.launch

private val TABS = listOf("Songs", "Albums", "Artists", "Playlists", "Genres")

/** Apple Music inspired library: bold hero title, glass search, horizontal cards. */
@Composable
fun LibraryScreen(
    onOpenPlayer: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(ApplePalette.Background)) {
        // Ambient glow at top
        Box(
            Modifier.fillMaxWidth().height(260.dp).background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.primary.copy(0.22f), androidx.compose.ui.graphics.Color.Transparent)
                )
            )
        )

        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
                Text("Library", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text("All your music, beautifully organized", color = ApplePalette.TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))

                GlassSurface(shape = RoundedCornerShape(30.dp), alpha = 0.09f) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text("Search", color = ApplePalette.TextSecondary) },
                        leadingIcon = { Icon(Icons.Default.Search, "Search", tint = ApplePalette.TextSecondary) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                        shape = RoundedCornerShape(30.dp),
                    )
                }
            }

            ScrollableTabRow(
                selectedTabIndex = state.tab,
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                contentColor = Color.White,
                edgePadding = 16.dp,
                indicator = {},
                divider = {},
            ) {
                TABS.forEachIndexed { index, title ->
                    Tab(
                        selected = state.tab == index,
                        onClick = { viewModel.onTabChange(index) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = ApplePalette.TextSecondary,
                        text = { Text(title, fontWeight = if (state.tab == index) FontWeight.Bold else FontWeight.Normal) },
                    )
                }
            }

            state.scanMessage?.let { msg ->
                Text(msg, color = ApplePalette.TextSecondary, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp))
            }

            when (state.tab) {
                0 -> SongsTab(state.songs, onOpenPlayer, viewModel)
                1 -> AlbumsTab(state.songs, onOpenPlayer, viewModel)
                2 -> ArtistsTab(state.songs, onOpenPlayer, viewModel)
                3 -> PlaylistsTab(state.playlists, state.songs, onOpenPlayer, viewModel) {
                    showCreateDialog = true
                }
                4 -> GenresTab(state.songs, onOpenPlayer, viewModel)
            }
        }

        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("New playlist") },
                text = {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Playlist name") },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            scope.launch { viewModel.createPlaylist(newPlaylistName.trim()) }
                            newPlaylistName = ""
                            showCreateDialog = false
                        }
                    }) { Text("Create") }
                },
                dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun SongsTab(songs: List<Song>, onOpenPlayer: () -> Unit, viewModel: LibraryViewModel) {
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)) {
        if (songs.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No songs found", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text("Check storage permission, then pull to refresh", color = ApplePalette.TextSecondary)
                    }
                }
            }
        } else {
            item {
                Text("Recently added", color = Color.White, fontSize = 22.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(22.dp, 14.dp, 22.dp, 8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp)) {
                    itemsIndexed(songs.take(12)) { index, song ->
                        RecentCard(song) { viewModel.play(songs, index); onOpenPlayer() }
                    }
                }
                Text("Songs", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(22.dp, 22.dp, 22.dp, 8.dp))
            }
            itemsIndexed(songs) { index, song ->
                SongRow(song, onOpen = { viewModel.play(songs, index); onOpenPlayer() })
            }
        }
    }
}

@Composable
private fun RecentCard(song: Song, onClick: () -> Unit) {
    GlassSurface(shape = RoundedCornerShape(16.dp), alpha = 0.10f,
        modifier = Modifier.size(150.dp).clickable(onClick = onClick)) {
        Column(Modifier.padding(10.dp)) {
            AsyncImage(
                model = MediaFileUtils.albumArtUri(song.albumId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(130.dp).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.height(8.dp))
            Text(song.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = ApplePalette.TextSecondary, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SongRow(song: Song, onOpen: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 18.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = MediaFileUtils.albumArtUri(song.albumId),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(9.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium)
            Text(song.artist, color = ApplePalette.TextSecondary, maxLines = 1,
                overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
        }
        Text(formatTime(song.duration), color = ApplePalette.TextSecondary, fontSize = 11.sp)
        IconButton(onClick = { com.larkplayer.core.common.RingtoneHelper.set(context, song) }) {
            Text("🔔", fontSize = 14.sp)
        }
    }
}

@Composable
private fun AlbumsTab(songs: List<Song>, onOpenPlayer: () -> Unit, viewModel: LibraryViewModel) {
    val albums = songs.groupBy { it.album }.map { it.value }
    LazyColumn {
        itemsIndexed(albums) { _, albumSongs ->
            val first = albumSongs.first()
            Row(Modifier.fillMaxWidth().clickable {
                viewModel.play(albumSongs, 0); onOpenPlayer()
            }.padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = MediaFileUtils.albumArtUri(first.albumId), contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(first.album, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("${albumSongs.size} songs • ${first.artist}", color = ApplePalette.TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ArtistsTab(songs: List<Song>, onOpenPlayer: () -> Unit, viewModel: LibraryViewModel) {
    val artists = songs.groupBy { it.artist }.map { it.value }
    LazyColumn {
        itemsIndexed(artists) { _, artistSongs ->
            val first = artistSongs.first()
            Row(Modifier.fillMaxWidth().clickable {
                viewModel.play(artistSongs, 0); onOpenPlayer()
            }.padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(0.3f)), Alignment.Center) {
                    Text(first.artist.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(first.artist, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("${artistSongs.size} songs", color = ApplePalette.TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PlaylistsTab(
    playlists: List<com.larkplayer.core.database.entity.PlaylistEntity>,
    songs: List<Song>,
    onOpenPlayer: () -> Unit,
    viewModel: LibraryViewModel,
    onCreate: () -> Unit,
) {
    LazyColumn {
        item {
            GlassSurface(shape = RoundedCornerShape(16.dp), alpha = 0.08f,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)
                    .clickable(onClick = onCreate)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, "New playlist", tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Create playlist", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        itemsIndexed(playlists) { _, playlist ->
            GlassSurface(shape = RoundedCornerShape(14.dp), alpha = 0.07f,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(0.35f)), Alignment.Center) {
                        Text("♫", color = Color.White, fontSize = 22.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(playlist.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Playlist", color = ApplePalette.TextSecondary, fontSize = 12.sp)
                    }
                    IconButton(onClick = { viewModel.deletePlaylist(playlist.id) }) {
                        Text("✕", color = ApplePalette.TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun GenresTab(songs: List<Song>, onOpenPlayer: () -> Unit, viewModel: LibraryViewModel) {
    val genres = songs.groupBy { it.album }.map { it.value }
    LazyColumn {
        itemsIndexed(genres) { _, genreSongs ->
            val first = genreSongs.first()
            Row(Modifier.fillMaxWidth().clickable {
                viewModel.play(genreSongs, 0); onOpenPlayer()
            }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassSurface(shape = RoundedCornerShape(12.dp), alpha = 0.12f) {
                    Text("  ${first.album}  ", color = Color.White, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
