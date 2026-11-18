package com.larkplayer.feature.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.larkplayer.core.common.Result
import com.larkplayer.core.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connects to the playback service via MediaController and exposes player state.
 * Replaces the original app's PlayerRepository + MediaSessionHandler.
 */
@Singleton
class PlayerRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob())
    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private val _sleepTimerRemaining = MutableStateFlow(0L)
    val sleepTimerRemaining: StateFlow<Long> = _sleepTimerRemaining.asStateFlow()
    private var sleepJob: Job? = null

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching { future.get() }
                .onSuccess { controller -> _controller.value = controller }
                .onFailure { error -> Timber.e(error, "Failed to connect to PlaybackService") }
        }, MoreExecutors.directExecutor())
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        val c = _controller.value ?: return
        val items = songs.map { it.toMediaItem() }
        c.setMediaItems(items, startIndex, 0L)
        c.prepare()
        c.play()
    }

    fun play(song: Song) = playSongs(listOf(song))

    fun togglePlayPause() {
        val c = _controller.value ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun skipNext() = _controller.value?.seekToNextMediaItem()
    fun skipPrevious() = _controller.value?.seekToPreviousMediaItem()
    fun seekTo(ms: Long) { _controller.value?.seekTo(ms) }
    fun setShuffle(on: Boolean) { _controller.value?.shuffleModeEnabled = on }
    fun setRepeat(mode: Int) { _controller.value?.repeatMode = mode }

    // ---- Queue ----
    fun currentQueue(): List<MediaItem> = _controller.value?.mediaItemCount?.let { count ->
        (0 until count).mapNotNull { _controller.value?.getMediaItemAt(it) }
    } ?: emptyList()

    fun currentIndex(): Int = _controller.value?.currentMediaItemIndex ?: -1

    fun playAtIndex(index: Int) {
        _controller.value?.let { c ->
            c.seekTo(index, 0L)
            c.play()
        }
    }

    fun removeFromQueue(index: Int) {
        _controller.value?.removeMediaItem(index)
    }

    // ---- Sleep timer ----
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) { _sleepTimerRemaining.value = 0L; return }
        val totalMs = minutes * 60_000L
        _sleepTimerRemaining.value = totalMs
        sleepJob = scope.launch {
            var remaining = totalMs
            while (remaining > 0) {
                delay(1_000)
                remaining -= 1_000
                _sleepTimerRemaining.value = remaining.coerceAtLeast(0)
            }
            _controller.value?.pause()
            _sleepTimerRemaining.value = 0L
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _sleepTimerRemaining.value = 0L
    }

    private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
        .setUri(contentUri)
        .setMediaId(id.toString())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(android.net.Uri.parse(
                    "content://media/external/audio/albumart/$albumId"
                ))
                .build()
        )
        .build()
}
