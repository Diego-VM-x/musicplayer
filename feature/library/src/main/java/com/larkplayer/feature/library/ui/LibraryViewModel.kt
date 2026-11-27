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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Library tab indices. Must stay in sync with TABS in LibraryScreen. */
internal const val TAB_VIDEOS = 0
internal const val TAB_SONGS = 1
internal const val TAB_PLAYLISTS = 2
internal const val TAB_FOLDERS = 3
internal const val TAB_ARTISTS = 4
internal const val TAB_ALBUMS = 5
/** Clean startup always lands on Canciones, never on Videos. */
internal const val DEFAULT_LIBRARY_TAB = TAB_SONGS

data class LibraryUiState(
    val songs: List<Song> = emptyList(),
    /** Local video files, shown in the Videos tab and played on the video surface. */
    val videos: List<Song> = emptyList(),
    val hiddenSongs: List<Song> = emptyList(),
    val playlists: List<PlaylistWithCover> = emptyList(),
    val query: String = "",
    val tab: Int = DEFAULT_LIBRARY_TAB,
    val sortMode: Int = 0,
    /** Playlists tab: 0 = manual (Room position), 1 = nombre, 2 = creación. */
    val playlistSortMode: Int = 0,
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

private data class Prefs(
    val hidden: Set<Long>,
    val themeSize: Int,
    val playlistSortMode: Int,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val playlistRepository: PlaylistRepository,
    private val player: PlayerRepository,
    private val dataStore: SettingsDataStore,
    scanBus: MediaScanBus,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val tab = MutableStateFlow(DEFAULT_LIBRARY_TAB) // Canciones, nunca Videos en el arranque
    private val sortMode = MutableStateFlow(0)
    private val playlistSortMode = MutableStateFlow(0)
    private val isScanning = MutableStateFlow(false)
    private val scanMessage = MutableStateFlow<String?>(null)

    private val controls = combine(query, tab, sortMode, isScanning, scanMessage) { q, t, s, scanning, msg ->
        Controls(q, t, s, scanning, msg)
    }

    private val prefs = combine(
        dataStore.hiddenSongIds, dataStore.themeConfig, playlistSortMode,
    ) { hidden, theme, playlistSort ->
        Prefs(hidden, theme.themeSize, playlistSort)
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
            videos = sort(visible.filter { it.isVideo }, c.sortMode),
            hiddenSongs = allSongs.filter { it.id in p.hidden },
            playlists = sortPlaylists(playlists, p.playlistSortMode),
            query = c.query,
            tab = c.tab,
            sortMode = c.sortMode,
            playlistSortMode = p.playlistSortMode,
            themeSize = p.themeSize,
            isScanning = c.scanning,
            scanMessage = c.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init {
        // UI order/state comes from DataStore, so it survives a full app close.
        viewModelScope.launch {
            dataStore.librarySortMode.collect { sortMode.value = it }
        }
        viewModelScope.launch {
            // Restores the last tab, but a clean start (and any saved Videos)
            // must resolve to Canciones. Normalizing the stored value keeps the
            // persisted index and the displayed tab in sync.
            val saved = dataStore.libraryTab.first().coerceIn(TAB_VIDEOS, TAB_ALBUMS)
            val restored = if (saved == TAB_VIDEOS) DEFAULT_LIBRARY_TAB else saved
            tab.value = restored
            if (restored != saved) runCatching { dataStore.setLibraryTab(restored) }
        }
        viewModelScope.launch {
            dataStore.playlistSortMode.collect { playlistSortMode.value = it.coerceIn(0, 2) }
        }
        // MainActivity emits a scan request after the first frame (and after
        // permissions). Waiting for it avoids a MediaStore query competing with
        // the first composition; the delayed fallback covers other entry points.
        var scanRequested = false
        viewModelScope.launch {
            scanBus.events.collect {
                scanRequested = true
                scan()
            }
        }
        viewModelScope.launch {
            delay(1_500)
            if (!scanRequested) scan()
        }
    }

    fun onQueryChange(value: String) { query.value = value }
    fun onTabChange(index: Int) {
        val next = index.coerceIn(TAB_VIDEOS, TAB_ALBUMS)
        tab.value = next
        viewModelScope.launch { runCatching { dataStore.setLibraryTab(next) } }
    }
    fun cycleSort() { setSort((sortMode.value + 1) % 3) }
    fun setSort(mode: Int) {
        sortMode.value = mode
        viewModelScope.launch { runCatching { dataStore.setLibrarySortMode(mode) } }
    }

    /** Playlists tab sort: 0 = manual (Room position), 1 = nombre, 2 = creación. */
    fun setPlaylistSort(mode: Int) {
        playlistSortMode.value = mode
        viewModelScope.launch { runCatching { dataStore.setPlaylistSortMode(mode) } }
    }

    /** Moves a playlist one slot up/down in the manual order persisted by Room. */
    fun movePlaylist(playlistId: Long, delta: Int) = viewModelScope.launch {
        val ordered = runCatching { playlistRepository.playlists.first().map { it.id } }
            .getOrDefault(emptyList()).toMutableList()
        val from = ordered.indexOf(playlistId)
        if (from < 0) return@launch
        val to = (from + delta).coerceIn(0, ordered.lastIndex)
        if (from == to) return@launch
        ordered.add(to, ordered.removeAt(from))
        runCatching { playlistRepository.reorderPlaylists(ordered) }
    }

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

    private fun sortPlaylists(list: List<PlaylistWithCover>, mode: Int): List<PlaylistWithCover> =
        when (mode) {
            1 -> list.sortedBy { it.playlist.name.lowercase() }
            2 -> list.sortedBy { it.playlist.createdAt }
            else -> list.sortedBy { it.playlist.position }
        }

    private fun SongEntity.toDomain() = Song(
        id = id, title = title, artist = artist, album = album, albumId = albumId,
        duration = duration, path = path, isVideo = isVideo, dateAdded = dateAdded,
    )
}
