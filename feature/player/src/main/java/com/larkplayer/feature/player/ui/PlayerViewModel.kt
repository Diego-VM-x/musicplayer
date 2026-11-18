package com.larkplayer.feature.player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.larkplayer.feature.player.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val title: String = "No media",
    val artist: String = "",
    val albumArt: String? = null,
    val isPlaying: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: PlayerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

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
        // Progress ticker
        viewModelScope.launch {
            while (isActive) {
                observedController?.let { c ->
                    _uiState.value = _uiState.value.copy(
                        position = c.currentPosition,
                        duration = c.duration.takeIf { it > 0 } ?: _uiState.value.duration,
                    )
                }
                delay(250)
            }
        }
    }

    private fun attachListener(c: MediaController) {
        val l = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val meta = mediaItem?.mediaMetadata
                _uiState.value = _uiState.value.copy(
                    title = meta?.title?.toString() ?: "Unknown",
                    artist = meta?.artist?.toString() ?: "",
                    albumArt = meta?.artworkUri?.toString(),
                )
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
        }
        listener = l
        c.addListener(l)
        l.onMediaItemTransition(c.currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        _uiState.value = _uiState.value.copy(
            isPlaying = c.isPlaying,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
        )
    }

    private fun detachListener() {
        listener?.let { observedController?.removeListener(it) }
        listener = null
    }

    fun togglePlayPause() = repository.togglePlayPause()
    fun skipNext() = repository.skipNext()
    fun skipPrevious() = repository.skipPrevious()
    fun seek(ms: Long) = repository.seekTo(ms)
    fun toggleShuffle() = repository.setShuffle(!_uiState.value.shuffle)
    fun cycleRepeat() {
        val next = when (_uiState.value.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        repository.setRepeat(next)
    }

    override fun onCleared() {
        detachListener()
        super.onCleared()
    }
}
