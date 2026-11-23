package com.larkplayer.feature.player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.larkplayer.core.database.dao.PlaylistDao
import com.larkplayer.core.database.dao.SongDao
import com.larkplayer.core.database.entity.PlaylistEntity
import com.larkplayer.core.database.entity.PlaylistSong
import com.larkplayer.core.datastore.SettingsDataStore
import com.larkplayer.core.model.Song
import com.larkplayer.feature.player.AlbumColorRepository
import com.larkplayer.feature.player.PlayerRepository
import com.larkplayer.feature.player.PlayerRepository.Companion.MODE_NORMAL
import com.larkplayer.feature.player.PlayerRepository.Companion.MODE_REPEAT_ALL
import com.larkplayer.feature.player.PlayerRepository.Companion.MODE_REPEAT_ONE
import com.larkplayer.feature.player.PlayerRepository.Companion.MODE_SHUFFLE
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val id: Long = -1L,
    val title: String = "Sin reproducción",
    val artist: String = "",
    val album: String = "",
    val albumArt: String? = null,
    val songPath: String = "",
    val isPlaying: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_ALL,
    val playbackSpeed: Float = 1f,
    val isFavorite: Boolean = false,
    val visualizerEnabled: Boolean = false,
    val visualizerStyle: String = "armonia",
    val audioSessionId: Int = 0,
    val hasLyrics: Boolean = false,
    val currentLyricsLine: String? = null,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val repository: PlayerRepository,
    private val albumColors: AlbumColorRepository,
    private val settings: SettingsDataStore,
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    /** Accent extracted from the current artwork, shared by player + mini-player + library. */
    val accent: StateFlow<Int> = albumColors.accent

    private var listener: Player.Listener? = null
    private var observedController: MediaController? = null

    init {
        viewModelScope.launch {
            repository.controller.collect { controller ->
                if (controller == observedController) return@collect
                detachListener()
                observedController = controller
                if (controller != null) attachListener(controller)
            }
        }
        viewModelScope.launch {
            settings.favorites.collect { favorites ->
                _uiState.value = _uiState.value.copy(isFavorite = _uiState.value.id in favorites)
            }
        }
        viewModelScope.launch {
            settings.visualizerEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(visualizerEnabled = enabled)
            }
        }
        viewModelScope.launch {
            settings.visualizerStyle.collect { style ->
                _uiState.value = _uiState.value.copy(visualizerStyle = style)
            }
        }
        // Progress ticker & lyrics sync
        viewModelScope.launch {
            while (isActive) {
                observedController?.let { c ->
                    runCatching {
                        val pos = c.currentPosition
                        val currentLine = activeLyricsLines.lastOrNull { it.first <= pos }?.second
                        _uiState.value = _uiState.value.copy(
                            position = pos,
                            duration = c.duration.takeIf { it > 0 } ?: _uiState.value.duration,
                            audioSessionId = repository.audioSessionId(),
                            currentLyricsLine = currentLine,
                        )
                    }
                }
                delay(250)
            }
        }
    }

    private var activeLyricsLines: List<Pair<Long, String>> = emptyList()

    private fun attachListener(c: MediaController) {
        val l = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val meta = mediaItem?.mediaMetadata
                val id = mediaItem?.mediaId?.toLongOrNull() ?: -1L
                val title = meta?.title?.toString() ?: "Desconocido"
                val artist = meta?.artist?.toString() ?: ""
                val path = meta?.extras?.getString(PlayerRepository.EXTRA_SONG_PATH).orEmpty()

                _uiState.value = _uiState.value.copy(
                    id = id,
                    title = title,
                    artist = artist,
                    album = meta?.albumTitle?.toString() ?: "",
                    albumArt = meta?.artworkUri?.toString(),
                    songPath = path,
                    hasLyrics = false,
                    currentLyricsLine = null,
                )
                viewModelScope.launch {
                    albumColors.load(meta?.artworkUri?.toString())
                }
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val rawLrc = songDao.lyricsFor(id)
                        ?: com.larkplayer.feature.player.EmbeddedLyricsReader.read(context, path, id)
                        ?: com.larkplayer.core.common.MediaFileUtils.findLyricsText(
                            context,
                            Song(id = id, title = title, artist = artist, path = path),
                        )
                    if (!rawLrc.isNullOrBlank() && songDao.lyricsFor(id).isNullOrBlank()) {
                        runCatching { songDao.updateLyrics(id, rawLrc) }
                    }
                    if (!rawLrc.isNullOrBlank()) {
                        val parsed = parseSimpleLrc(rawLrc)
                        activeLyricsLines = parsed
                        _uiState.value = _uiState.value.copy(hasLyrics = parsed.isNotEmpty())
                    } else {
                        activeLyricsLines = emptyList()
                        _uiState.value = _uiState.value.copy(hasLyrics = false)
                    }
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
            }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _uiState.value = _uiState.value.copy(shuffle = shuffleModeEnabled)
            }
            override fun onRepeatModeChanged(repeatMode: Int) {
                _uiState.value = _uiState.value.copy(repeatMode = repeatMode)
            }
            override fun onPlaybackParametersChanged(parameters: androidx.media3.common.PlaybackParameters) {
                _uiState.value = _uiState.value.copy(playbackSpeed = parameters.speed)
            }
        }
        listener = l
        runCatching {
            c.addListener(l)
            l.onMediaItemTransition(c.currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
            _uiState.value = _uiState.value.copy(
                isPlaying = c.isPlaying,
                shuffle = c.shuffleModeEnabled,
                repeatMode = c.repeatMode,
                playbackSpeed = c.playbackParameters.speed,
            )
        }.onFailure { observedController = null }
    }

    private fun detachListener() {
        val l = listener ?: return
        runCatching { observedController?.removeListener(l) }
        listener = null
    }

    val sleepTimerRemaining = repository.sleepTimerRemaining

    /** Playlists shown by the "add to playlist" sheet of the player. */
    val playlists: StateFlow<List<PlaylistEntity>> = playlistDao.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun queue() = repository.currentQueue()
    fun visibleQueue(shuffle: Boolean) = repository.displayQueue(shuffle)
    fun originalIndex(mediaId: String?) = repository.originalIndex(mediaId)
    fun currentIndex() = repository.currentIndex()
    fun playAtIndex(index: Int) = repository.playAtIndex(index)
    fun removeFromQueue(index: Int) = repository.removeFromQueue(index)
    fun moveQueueItem(from: Int, to: Int) = repository.moveQueueItem(from, to)
    fun moveQueueItemById(fromId: String?, toId: String?) = repository.moveQueueItemById(fromId, toId)
    fun setSleepTimer(minutes: Int) = repository.setSleepTimer(minutes)
    fun cancelSleepTimer() = repository.cancelSleepTimer()
    fun clearQueue() {
        repository.clearQueue()
        _uiState.value = PlayerUiState()
    }

    /** Snapshot of the current track for share / ringtone / delete actions. */
    fun currentSong(): Song? {
        val s = _uiState.value
        if (s.id <= 0) return null
        return Song(
            id = s.id,
            title = s.title,
            artist = s.artist,
            album = s.album,
            duration = s.duration,
            path = s.songPath,
        )
    }

    fun hideCurrent() {
        val id = _uiState.value.id
        if (id > 0) viewModelScope.launch { runCatching { settings.hideSong(id) } }
    }

    fun addCurrentToPlaylist(playlistId: Long) = viewModelScope.launch {
        val songId = _uiState.value.id
        if (songId <= 0 || playlistId <= 0) return@launch
        runCatching {
            val existing = playlistDao.observeSongs(playlistId).first().map { it.id }.toSet()
            if (songId !in existing) {
                playlistDao.addSongs(
                    listOf(PlaylistSong(playlistId, songId, playlistDao.maxPosition(playlistId) + 1)),
                )
            }
        }
    }

    fun createPlaylistWithCurrent(name: String) = viewModelScope.launch {
        val clean = name.trim()
        val songId = _uiState.value.id
        if (clean.isBlank() || songId <= 0) return@launch
        runCatching {
            val playlistId = playlistDao.insert(PlaylistEntity(name = clean))
            playlistDao.addSongs(listOf(PlaylistSong(playlistId, songId, 0)))
        }
    }

    fun togglePlayPause() = repository.togglePlayPause()
    fun skipNext() = repository.skipNext()
    fun skipPrevious() = repository.skipPrevious()
    fun seek(ms: Long) = repository.seekTo(ms)
    /** Kept for compatibility: toggles shuffle through the single-choice mode API. */
    fun toggleShuffle() {
        setPlaybackMode(if (_uiState.value.shuffle) MODE_NORMAL else MODE_SHUFFLE)
    }
    fun setRepeatMode(mode: Int) = repository.setRepeat(mode)

    fun currentPlaybackMode(): Int = when {
        _uiState.value.shuffle -> MODE_SHUFFLE
        _uiState.value.repeatMode == Player.REPEAT_MODE_ALL -> MODE_REPEAT_ALL
        _uiState.value.repeatMode == Player.REPEAT_MODE_ONE -> MODE_REPEAT_ONE
        else -> MODE_NORMAL
    }

    fun setPlaybackMode(mode: Int) = repository.setPlaybackMode(mode)
    fun setPlaybackSpeed(speed: Float) = repository.setPlaybackSpeed(speed)
    fun cycleRepeat() {
        val next = when (_uiState.value.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        repository.setRepeat(next)
    }

    fun toggleFavorite() {
        val id = _uiState.value.id
        if (id > 0) viewModelScope.launch { settings.toggleFavorite(id) }
    }

    fun setVisualizerEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setVisualizerEnabled(enabled) }
    }

    fun setVisualizerStyle(style: String) {
        viewModelScope.launch { settings.setVisualizerStyle(style) }
    }

    private fun parseSimpleLrc(text: String): List<Pair<Long, String>> {
        val pattern = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\](.*)""")
        return text.lineSequence().mapNotNull { line ->
            pattern.matchEntire(line.trim())?.let { match ->
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val msStr = match.groupValues[3].padEnd(3, '0').take(3)
                val ms = msStr.toLongOrNull() ?: 0L
                val totalMs = min * 60_000L + sec * 1_000L + ms
                val lyricText = match.groupValues[4].trim()
                if (lyricText.isNotBlank()) totalMs to lyricText else null
            }
        }.sortedBy { it.first }.toList()
    }

    override fun onCleared() {
        detachListener()
        super.onCleared()
    }
}
