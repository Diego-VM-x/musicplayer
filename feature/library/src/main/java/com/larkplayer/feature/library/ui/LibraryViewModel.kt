package com.larkplayer.feature.library.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larkplayer.core.model.Song
import com.larkplayer.feature.library.LibraryRepository
import com.larkplayer.feature.player.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val songs: List<Song> = emptyList(),
    val query: String = "",
    val tab: Int = 0,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val player: PlayerRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val tab = MutableStateFlow(0)

    val uiState: StateFlow<LibraryUiState> = combine(
        repository.songs, query, tab,
    ) { songs, q, t ->
        LibraryUiState(
            songs = repository.search(songs, q),
            query = q,
            tab = t,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onQueryChange(value: String) { query.value = value }
    fun onTabChange(index: Int) { tab.value = index }

    fun scan() = viewModelScope.launch { repository.scan() }

    fun play(songs: List<Song>, index: Int) {
        player.playSongs(songs, index)
        songs.getOrNull(index)?.let { viewModelScope.launch { repository.recordPlay(it.id) } }
    }
}
