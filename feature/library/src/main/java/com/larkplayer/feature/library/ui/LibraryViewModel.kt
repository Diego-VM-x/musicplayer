package com.larkplayer.feature.library.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larkplayer.core.common.MediaScanBus
import com.larkplayer.core.common.getOrNull
import com.larkplayer.core.database.entity.PlaylistWithCover
import com.larkplayer.core.database.entity.SongEntity
import com.larkplayer.core.datastore.SettingsDataStore
import com.larkplayer.core.model.Song
import com.larkplayer.feature.library.LibraryRepository
import com.larkplayer.feature.library.PlaylistRepository
import com.larkplayer.feature.player.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val songs: List<Song> = emptyList(),
    val hiddenSongs: List<Song> = emptyList(),
    val playlists: List<PlaylistWithCover> = emptyList(),
    val query: String = "",
    val tab: Int = 0,
    val sortMode: Int = 0,
    val themeSize: Int = 1,
    val isScanning: Boolean = false,
    val scanMessage: String? = null,
)

private data class Controls(
    val query: String,
    val tab: Int,
    val sortMode: Int,
    val scanning: Boolean,
    val message: String?,
)

private data class Prefs(val hidden: Set<Long>, val themeSize: Int)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val playlistRepository: PlaylistRepository,
    private val player: PlayerRepository,
    private val dataStore: SettingsDataStore,
    scanBus: MediaScanBus,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val tab = MutableStateFlow(1) // 1 = Canciones, same as Lark
    private val sortMode = MutableStateFlow(0)
    private val isScanning = MutableStateFlow(false)
    private val scanMessage = MutableStateFlow<String?>(null)

    private val controls = combine(query, tab, sortMode, isScanning, scanMessage) { q, t, s, scanning, msg ->
        Controls(q, t, s, scanning, msg)
    }

    private val prefs = combine(dataStore.hiddenSongIds, dataStore.themeConfig) { hidden, theme ->
        Prefs(hidden, theme.themeSize)
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        repository.songs,
        playlistRepository.playlistsWithCovers,
        controls,
        prefs,
    ) { allSongs: List<Song>, playlists: List<PlaylistWithCover>, c: Controls, p: Prefs ->
        val visible = allSongs.filter { it.id !in p.hidden }
        LibraryUiState(
            songs = sort(repository.search(visible, c.query), c.sortMode),
            hiddenSongs = allSongs.filter { it.id in p.hidden },
            playlists = playlists,
            query = c.query,
            tab = c.tab,
            sortMode = c.sortMode,
            themeSize = p.themeSize,
            isScanning = c.scanning,
            scanMessage = c.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init {
        scan()
        viewModelScope.launch {
            scanBus.events.collect { scan() }
        }
    }

    fun onQueryChange(value: String) { query.value = value }
    fun onTabChange(index: Int) { tab.value = index }
    fun cycleSort() { sortMode.value = (sortMode.value + 1) % 3 }
    fun setSort(mode: Int) { sortMode.value = mode }
    /**
     * Uses the Room cache on startup: a full MediaStore scan only runs when the
     * database is empty, ten minutes have elapsed, or the caller forces it.
     */
    fun scan(force: Boolean = false) = viewModelScope.launch {
        if (!force) {
            val cached = runCatching { repository.songs.first() }.getOrDefault(emptyList())
            val lastScan = runCatching { dataStore.lastScanAt.first() }.getOrDefault(0L)
            if (cached.isNotEmpty() && System.currentTimeMillis() - lastScan < 10 * 60_000L) {
                return@launch
            }
        }
        isScanning.value = true
        val result = repository.scan()
        isScanning.value = false
        if (result.getOrNull() != null) {
            runCatching { dataStore.setLastScanAt(System.currentTimeMillis()) }
        }
        scanMessage.value = result.getOrNull()
            ?.let { "Encontradas $it canciones" }
            ?: "Error al escanear — revisa el permiso de almacenamiento"
    }

    fun dismissScanMessage() { scanMessage.value = null }

    fun play(songs: List<Song>, index: Int) {
        player.playSongs(songs, index)
        songs.getOrNull(index)?.let { song ->
            viewModelScope.launch { runCatching { repository.recordPlay(song.id) } }
        }
    }

    fun playPlaylist(playlistId: Long) = viewModelScope.launch {
        val songs = playlistRepository.songsOnce(playlistId).map { it.toDomain() }
        if (songs.isNotEmpty()) {
            player.playSongs(songs, 0)
            runCatching { repository.recordPlay(songs.first().id) }
        }
    }

    fun hideSong(songId: Long) = viewModelScope.launch { dataStore.hideSong(songId) }
    fun unhideSong(songId: Long) = viewModelScope.launch { dataStore.unhideSong(songId) }

    fun addToQueue(song: Song) {
        player.addToQueue(song)
    }

    /** Returns the new playlist id so the UI can immediately ask for songs. */
    suspend fun createPlaylist(name: String): Long? =
        runCatching { playlistRepository.create(name) }.getOrNull()
    fun renamePlaylist(id: Long, name: String) = viewModelScope.launch {
        runCatching { playlistRepository.rename(id, name) }
    }
    fun deletePlaylist(id: Long) = viewModelScope.launch {
        runCatching { playlistRepository.delete(id) }
    }
    fun addToPlaylist(playlistId: Long, songId: Long) = viewModelScope.launch {
        runCatching { playlistRepository.addSong(playlistId, songId) }
    }

    private fun sort(list: List<Song>, mode: Int): List<Song> = when (mode) {
        1 -> list.sortedBy { it.title.lowercase() }
        2 -> list.sortedBy { it.artist.lowercase() }
        else -> list.sortedByDescending { it.dateAdded }
    }

    private fun SongEntity.toDomain() = Song(
        id = id, title = title, artist = artist, album = album, albumId = albumId,
        duration = duration, path = path, isVideo = isVideo, dateAdded = dateAdded,
    )
}
