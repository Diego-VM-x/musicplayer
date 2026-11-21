package com.larkplayer.feature.player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.larkplayer.core.datastore.SettingsDataStore
import com.larkplayer.feature.player.AlbumColorRepository
import com.larkplayer.feature.player.PlayerRepository
import com.larkplayer.feature.player.PlayerRepository.Companion.MODE_NORMAL
import com.larkplayer.feature.player.PlayerRepository.Companion.MODE_REPEAT_ALL
import com.larkplayer.feature.player.PlayerRepository.Companion.MODE_REPEAT_ONE
import com.larkplayer.feature.player.PlayerRepository.Companion.MODE_SHUFFLE
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val playbackSpeed: Float = 1f,
    val isFavorite: Boolean = false,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: PlayerRepository,
    private val albumColors: AlbumColorRepository,
    private val settings: SettingsDataStore,
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
        // Progress ticker
        viewModelScope.launch {
            while (isActive) {
                observedController?.let { c ->
                    runCatching {
                        _uiState.value = _uiState.value.copy(
                            position = c.currentPosition,
                            duration = c.duration.takeIf { it > 0 } ?: _uiState.value.duration,
                        )
                    }
                }
                delay(250)
            }
        }
    }

    private fun attachListener(c: MediaController) {
        val l = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val meta = mediaItem?.mediaMetadata
                val id = mediaItem?.mediaId?.toLongOrNull() ?: -1L
                _uiState.value = _uiState.value.copy(
                    id = id,
                    title = meta?.title?.toString() ?: "Desconocido",
                    artist = meta?.artist?.toString() ?: "",
                    album = meta?.albumTitle?.toString() ?: "",
                    albumArt = meta?.artworkUri?.toString(),
                    songPath = meta?.extras?.getString(PlayerRepository.EXTRA_SONG_PATH).orEmpty(),
                )
                viewModelScope.launch {
                    albumColors.load(meta?.artworkUri?.toString())
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

    fun queue() = repository.currentQueue()
    fun currentIndex() = repository.currentIndex()
    fun playAtIndex(index: Int) = repository.playAtIndex(index)
    fun removeFromQueue(index: Int) = repository.removeFromQueue(index)
    fun setSleepTimer(minutes: Int) = repository.setSleepTimer(minutes)
    fun cancelSleepTimer() = repository.cancelSleepTimer()

    fun togglePlayPause() = repository.togglePlayPause()
    fun skipNext() = repository.skipNext()
    fun skipPrevious() = repository.skipPrevious()
    fun seek(ms: Long) = repository.seekTo(ms)
    fun toggleShuffle() = repository.setShuffle(!_uiState.value.shuffle)
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

    override fun onCleared() {
        detachListener()
        super.onCleared()
    }
}
