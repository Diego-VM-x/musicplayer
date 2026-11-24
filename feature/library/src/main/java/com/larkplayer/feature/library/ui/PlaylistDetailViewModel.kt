package com.larkplayer.feature.library.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larkplayer.core.database.entity.PlaylistEntity
import com.larkplayer.core.database.entity.PlaylistWithCover
import com.larkplayer.core.database.entity.SongEntity
import com.larkplayer.core.datastore.SettingsDataStore
import com.larkplayer.core.model.Song
import com.larkplayer.feature.library.LibraryRepository
import com.larkplayer.feature.library.PlaylistRepository

import com.larkplayer.feature.player.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistDetailUiState(
    val playlist: PlaylistEntity? = null,
    val songs: List<Song> = emptyList(),
    val allSongs: List<Song> = emptyList(),
    val playlists: List<PlaylistWithCover> = emptyList(),
    /** 0 = más recientes, 1 = más reproducido, 2 = A a Z, 3 = manual. */
    val sortMode: Int = 0,
    val selectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val accent: Int = 0xFF0A84FF.toInt(),
)

/** Backs the playlist detail screen: songs, sorting, selection and edits. */
@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val libraryRepository: LibraryRepository,
    private val player: PlayerRepository,
    private val dataStore: SettingsDataStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val playlistId = savedStateHandle.get<Long>("playlistId") ?: 0L

    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    private var playlist: PlaylistEntity? = null
    private var rawSongs: List<Song> = emptyList()
    private var allSongs: List<Song> = emptyList()
    private var playlists: List<PlaylistWithCover> = emptyList()
    private var hidden: Set<Long> = emptySet()
    private var counts: Map<Long, Int> = emptyMap()
    private var accent: Int = 0xFF0A84FF.toInt()
    private var sortMode = 0
    private var selectionMode = false
    private var selectedIds: Set<Long> = emptySet()

    init {
        viewModelScope.launch {
            playlistRepository.playlist(playlistId).collect { value ->
                playlist = value
                publish()
            }
        }
        viewModelScope.launch {
            playlistRepository.songs(playlistId).collect { list ->
                rawSongs = list.map { it.toDomain() }
                publish()
            }
        }
        viewModelScope.launch {
            libraryRepository.songs.collect { list ->
                allSongs = list
                publish()
            }
        }
        viewModelScope.launch {
            playlistRepository.playlistsWithCovers.collect { list ->
                playlists = list
                publish()
            }
        }
        viewModelScope.launch {
            dataStore.hiddenSongIds.collect { ids ->
                hidden = ids
                publish()
            }
        }
        viewModelScope.launch {
            playlistRepository.playCounts().collect { map ->
                counts = map
                publish()
            }
        }
        viewModelScope.launch {
            dataStore.accentColor.collect { color ->
                accent = color.toInt()
                publish()
            }
        }
        // The detail sort (including the manual order after a drag) is per playlist.
        viewModelScope.launch {
            dataStore.playlistDetailSortMode(playlistId).collect { mode ->
                sortMode = mode
                publish()
            }
        }
    }

    private fun publish() {
        val visible = rawSongs.filter { it.id !in hidden }
        val sorted = when (sortMode) {
            0 -> visible.sortedByDescending { it.dateAdded }
            1 -> visible.sortedByDescending { counts[it.id] ?: 0 }
            2 -> visible.sortedBy { it.title.lowercase() }
            else -> visible
        }
        _uiState.value = PlaylistDetailUiState(
            playlist = playlist,
            songs = sorted,
            allSongs = allSongs.filter { it.id !in hidden },
            playlists = playlists,
            sortMode = sortMode,
            selectionMode = selectionMode,
            selectedIds = selectedIds,
            accent = accent,
        )
    }

    fun setSort(mode: Int) {
        sortMode = mode
        publish()
        viewModelScope.launch { runCatching { dataStore.setPlaylistDetailSortMode(playlistId, mode) } }
    }

    fun enterSelection(songId: Long? = null) {
        selectionMode = true
        selectedIds = songId?.let { setOf(it) } ?: emptySet()
        publish()
    }

    fun clearSelection() {
        selectionMode = false
        selectedIds = emptySet()
        publish()
    }

    fun toggleSelection(songId: Long) {
        selectedIds = if (songId in selectedIds) selectedIds - songId else selectedIds + songId
        if (selectedIds.isEmpty()) selectionMode = false
        publish()
    }

    fun selectAll() {
        selectedIds = _uiState.value.songs.map { it.id }.toSet()
        publish()
    }

    fun play(index: Int) {
        val songs = _uiState.value.songs
        songs.getOrNull(index)?.let { song ->
            player.playSongs(songs, index)
            viewModelScope.launch { runCatching { libraryRepository.recordPlay(song.id) } }
        }
    }

    fun playAll() {
        val songs = _uiState.value.songs
        if (songs.isNotEmpty()) {
            player.setShuffle(false)
            player.playSongs(songs, 0)
            recordFirst(songs)
        }
    }

    fun shuffleAll() {
        val songs = _uiState.value.songs
        if (songs.isNotEmpty()) {
            player.setShuffle(true)
            player.playSongs(songs, 0)
            recordFirst(songs)
        }
    }

    private fun recordFirst(songs: List<Song>) = viewModelScope.launch {
        runCatching { libraryRepository.recordPlay(songs.first().id) }
    }

    fun addToQueue(ids: Set<Long>) {
        _uiState.value.songs.filter { it.id in ids }.forEach { player.addToQueue(it) }
    }

    fun addSongsToOtherPlaylist(targetId: Long, ids: Set<Long>) = viewModelScope.launch {
        runCatching { playlistRepository.addSongs(targetId, ids.toList()) }
    }

    fun addSongsToThisPlaylist(ids: Set<Long>) = viewModelScope.launch {
        runCatching { playlistRepository.addSongs(playlistId, ids.toList()) }
    }

    fun removeFromPlaylist(ids: Set<Long>) = viewModelScope.launch {
        runCatching { playlistRepository.removeSongs(playlistId, ids.toList()) }
    }

    fun hideSongs(ids: Set<Long>) = viewModelScope.launch {
        runCatching { ids.forEach { dataStore.hideSong(it) } }
    }

    fun savePlaylist(name: String, coverUri: String?) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        runCatching { playlistRepository.update(playlistId, name.trim(), coverUri) }
    }

    fun deletePlaylist(onDeleted: () -> Unit) = viewModelScope.launch {
        runCatching {
            playlistRepository.delete(playlistId)
            onDeleted()
        }
    }

    fun moveSong(fromIndex: Int, toIndex: Int) {
        val list = _uiState.value.songs.toMutableList()
                if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        val moved = list.removeAt(fromIndex)
        list.add(toIndex, moved)
        sortMode = 3
        rawSongs = list
        publish()
        viewModelScope.launch {
            runCatching {
                playlistRepository.reorder(playlistId, list.map { it.id })
                dataStore.setPlaylistDetailSortMode(playlistId, 3)
            }
        }
    }

    private fun SongEntity.toDomain() = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        duration = duration,
        path = path,
        isVideo = isVideo,
        dateAdded = dateAdded,
    )
}
