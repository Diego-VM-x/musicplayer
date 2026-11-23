package com.larkplayer.feature.lyrics.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.larkplayer.core.datastore.SettingsDataStore
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
    private val settings: SettingsDataStore,
) : ViewModel() {
    private val _lyrics = MutableStateFlow(Lyrics(0))
    val lyrics: StateFlow<Lyrics> = _lyrics.asStateFlow()

    /** Per-song manual sync correction in milliseconds. */
    private val _offset = MutableStateFlow(0)
    val offset: StateFlow<Int> = _offset.asStateFlow()

    private var loadJob: Job? = null
    private var offsetJob: Job? = null

    fun load(title: String, artist: String = "", path: String = "", songId: Long = -1L) {
        // Cancel a previous lookup so fast track changes cannot show stale lyrics.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val byId = if (songId > 0) repository.loadForSongId(songId) else Lyrics(0)
                if (byId.hasText) byId else repository.loadForTitle(title, artist, path)
            }
            _lyrics.value = result
            offsetJob?.cancel()
            val resolvedId = if (result.songId > 0) result.songId else songId
            if (resolvedId > 0) {
                offsetJob = viewModelScope.launch {
                    settings.lyricsOffset(resolvedId).collect { _offset.value = it }
                }
            }
        }
    }

    /** Nudges the highlight earlier (negative) or later (positive). */
    fun adjustOffset(deltaMs: Int) = viewModelScope.launch {
        val id = _lyrics.value.songId
        if (id <= 0) return@launch
        val next = (_offset.value + deltaMs).coerceIn(-60_000, 60_000)
        settings.setLyricsOffset(id, next)
    }
}
