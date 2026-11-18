package com.larkplayer.feature.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.larkplayer.core.common.MediaFileUtils

private val TABS = listOf("Songs", "Albums", "Artists", "Playlists", "Genres")

/** Main library screen — mirrors Lark Player's tabbed library layout. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenPlayer: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    "Lark Player",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text("Search songs, artists...") },
            leadingIcon = { Icon(Icons.Default.Search, "Search") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(28.dp),
        )

        ScrollableTabRow(
            selectedTabIndex = state.tab,
            edgePadding = 12.dp,
        ) {
            TABS.forEachIndexed { index, title ->
                Tab(
                    selected = state.tab == index,
                    onClick = { viewModel.onTabChange(index) },
                    text = { Text(title) },
                )
            }
        }

        when (state.tab) {
            0 -> SongList(state.songs, onOpenPlayer, viewModel)
            1 -> AlbumGrid(state.songs, onOpenPlayer, viewModel)
            2 -> ArtistList(state.songs, onOpenPlayer, viewModel)
            3 -> PlaylistsPlaceholder()
            else -> GenrePlaceholder()
        }
    }
}

@Composable
private fun SongList(
    songs: List<com.larkplayer.core.model.Song>,
    onOpenPlayer: () -> Unit,
    viewModel: LibraryViewModel,
) {
    if (songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No songs found", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Grant storage permission and scan your library", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn {
        itemsIndexed(songs) { index, song ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.play(songs, index); onOpenPlayer() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = MediaFileUtils.albumArtUri(song.albumId),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge)
                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    formatTime(song.duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AlbumGrid(
    songs: List<com.larkplayer.core.model.Song>,
    onOpenPlayer: () -> Unit,
    viewModel: LibraryViewModel,
) {
    val albums = songs.groupBy { it.album }.map { it.value }
    LazyColumn {
        itemsIndexed(albums) { index, albumSongs ->
            val first = albumSongs.first()
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    viewModel.play(albumSongs, 0); onOpenPlayer()
                }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = MediaFileUtils.albumArtUri(first.albumId),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(first.album, style = MaterialTheme.typography.bodyLarge)
                    Text("${albumSongs.size} songs • ${first.artist}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ArtistList(
    songs: List<com.larkplayer.core.model.Song>,
    onOpenPlayer: () -> Unit,
    viewModel: LibraryViewModel,
) {
    val artists = songs.groupBy { it.artist }.map { it.value }
    LazyColumn {
        itemsIndexed(artists) { _, artistSongs ->
            val first = artistSongs.first()
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    viewModel.play(artistSongs, 0); onOpenPlayer()
                }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    Alignment.Center,
                ) { Text(first.artist.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(first.artist, style = MaterialTheme.typography.bodyLarge)
                    Text("${artistSongs.size} songs", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PlaylistsPlaceholder() = Box(Modifier.fillMaxSize(), Alignment.Center) {
    Text("Playlists — coming in next step", color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun GenrePlaceholder() = Box(Modifier.fillMaxSize(), Alignment.Center) {
    Text("Genres", color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun formatTime(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
