package com.larkplayer.feature.lyrics.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larkplayer.core.model.Lyrics
import com.larkplayer.feature.lyrics.LyricsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val repository: LyricsRepository,
) : ViewModel() {
    private val _lyrics = MutableStateFlow(Lyrics(0))
    val lyrics: StateFlow<Lyrics> = _lyrics.asStateFlow()

    private var loadJob: Job? = null

    fun load(title: String, artist: String = "", path: String = "", songId: Long = -1L) {
        // Cancel a previous lookup so fast track changes cannot show stale lyrics.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val byId = if (songId > 0) repository.loadForSongId(songId) else Lyrics(0)
                if (byId.hasText) byId else repository.loadForTitle(title, artist, path)
            }
            _lyrics.value = result
        }
    }
}
