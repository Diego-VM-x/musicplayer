package com.larkplayer.feature.library.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larkplayer.core.common.MediaScanBus
import com.larkplayer.core.common.getOrNull
import com.larkplayer.core.database.entity.PlaylistEntity
import com.larkplayer.core.model.Song
import com.larkplayer.feature.library.LibraryRepository
import com.larkplayer.feature.library.PlaylistRepository
import com.larkplayer.feature.player.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val songs: List<Song> = emptyList(),
    val query: String = "",
    val tab: Int = 0,
    val isScanning: Boolean = false,
    val playlists: List<PlaylistEntity> = emptyList(),
    val scanMessage: String? = null,
)

private data class Controls(val query: String, val tab: Int, val scanning: Boolean, val message: String?)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val playlistRepository: PlaylistRepository,
    private val player: PlayerRepository,
    scanBus: MediaScanBus,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val tab = MutableStateFlow(0)
    private val isScanning = MutableStateFlow(false)
    private val scanMessage = MutableStateFlow<String?>(null)

    private val controls = combine(query, tab, isScanning, scanMessage) { q, t, scanning, msg ->
        Controls(q, t, scanning, msg)
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        repository.songs, playlistRepository.playlists, controls,
    ) { songs: List<Song>, playlists: List<PlaylistEntity>, c: Controls ->
        LibraryUiState(
            songs = repository.search(songs, c.query),
            playlists = playlists,
            query = c.query,
            tab = c.tab,
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

    fun scan() = viewModelScope.launch {
        isScanning.value = true
        val result = repository.scan()
        isScanning.value = false
        scanMessage.value = result.getOrNull()?.let { "Found $it songs" } ?: "Scan failed — check storage permission"
    }

    fun dismissScanMessage() { scanMessage.value = null }

    fun play(songs: List<Song>, index: Int) {
        player.playSongs(songs, index)
        songs.getOrNull(index)?.let { viewModelScope.launch { repository.recordPlay(it.id) } }
    }

    suspend fun createPlaylist(name: String) = playlistRepository.create(name)
    fun renamePlaylist(id: Long, name: String) = viewModelScope.launch { playlistRepository.rename(id, name) }
    fun deletePlaylist(id: Long) = viewModelScope.launch { playlistRepository.delete(id) }
}
