package com.larkplayer.feature.lyrics.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larkplayer.core.model.Lyrics
import com.larkplayer.feature.lyrics.LyricsRepository
import com.larkplayer.feature.player.ui.PlayerViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val repository: LyricsRepository,
) : ViewModel() {
    private val _lyrics = MutableStateFlow(Lyrics(0))
    val lyrics: StateFlow<Lyrics> = _lyrics.asStateFlow()

    fun load(title: String, artist: String = "", path: String = "", songId: Long = -1L) =
        viewModelScope.launch {
            _lyrics.value = if (songId > 0) {
                repository.loadForSongId(songId).takeIf { it.lines.isNotEmpty() }
                    ?: repository.loadForTitle(title, artist, path)
            } else {
                repository.loadForTitle(title, artist, path)
            }
        }
}
