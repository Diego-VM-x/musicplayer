package com.larkplayer.feature.player

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.larkplayer.core.datastore.SettingsDataStore
import com.larkplayer.core.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connects to the playback service via MediaController and exposes player state.
 *
 * All MediaController calls happen on the main thread: Media3 throws if a
 * controller is touched from another thread, so the repository scope is pinned
 * to [Dispatchers.Main] and every controller access is wrapped.
 */
@Singleton
class PlayerRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val settings: SettingsDataStore,
) {
    private val appContext: Context = context.applicationContext
    private val sessionToken = SessionToken(
        context,
        ComponentName(context, PlaybackService::class.java),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private val _sleepTimerRemaining = MutableStateFlow(0L)
    val sleepTimerRemaining: StateFlow<Long> = _sleepTimerRemaining.asStateFlow()
    private var sleepJob: Job? = null

    @Volatile private var crossfadeEnabled = false
    @Volatile private var crossfadeMs = 4_000L
    @Volatile private var fadeInUntil = 0L
    private var lastMediaId: String? = null
    private var pendingPlayMs = 0L

    private var connecting = false
    private var pendingAction: ((MediaController) -> Unit)? = null

    init {
        connect()
        scope.launch {
            settings.crossfadeEnabled.collect { enabled ->
                crossfadeEnabled = enabled
                if (!enabled) resetVolume()
            }
        }
        scope.launch {
            settings.crossfadeSeconds.collect { seconds -> crossfadeMs = seconds * 1_000L }
        }
        scope.launch { crossfadeLoop() }
        scope.launch { listeningTimeLoop() }
    }

    // ---- Connection ---------------------------------------------------------

    private fun connect() {
        if (connecting || _controller.value?.isConnected == true) return
        connecting = true
        val future = MediaController.Builder(appContext, sessionToken)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    if (_controller.value === controller) _controller.value = null
                    connecting = false
                    scope.launch {
                        delay(500)
                        if (_controller.value?.isConnected != true) connect()
                    }
                }
            })
            .buildAsync()

        future.addListener({
            connecting = false
            runCatching { future.get() }
                .onSuccess { controller ->
                    _controller.value = controller
                    pendingAction?.let { action ->
                        pendingAction = null
                        runCatching { action(controller) }
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to connect to PlaybackService")
                    scope.launch {
                        delay(1_500)
                        connect()
                    }
                }
        }, MoreExecutors.directExecutor())
    }

    /** Runs [block] on the main thread as soon as a connected controller exists. */
    private fun withController(block: (MediaController) -> Unit) {
        val current = _controller.value
        if (current != null && current.isConnected) {
            runCatching { block(current) }
                .onFailure { Timber.e(it, "Controller action failed") }
        } else {
            pendingAction = block
            connect()
        }
    }

    // ---- Playback -----------------------------------------------------------

    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val items = songs.map { it.toMediaItem() }
        val safeIndex = startIndex.coerceIn(0, items.lastIndex)
        withController { c ->
            c.setMediaItems(items, safeIndex, 0L)
            c.prepare()
            c.play()
        }
    }

    fun addToQueue(song: Song) = withController { it.addMediaItem(song.toMediaItem()) }

    fun play(song: Song) = playSongs(listOf(song))

    fun togglePlayPause() = withController { c -> if (c.isPlaying) c.pause() else c.play() }

    fun skipNext() = withController { it.seekToNextMediaItem() }
    fun skipPrevious() = withController { it.seekToPreviousMediaItem() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun setShuffle(on: Boolean) = withController { it.shuffleModeEnabled = on }
    fun setRepeat(mode: Int) = withController { it.repeatMode = mode }

    // ---- Queue ----

    fun currentQueue(): List<MediaItem> {
        val c = _controller.value ?: return emptyList()
        return runCatching {
            (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it) }
        }.getOrDefault(emptyList())
    }

    fun currentIndex(): Int {
        val c = _controller.value ?: return -1
        return runCatching { c.currentMediaItemIndex }.getOrDefault(-1)
    }

    fun playAtIndex(index: Int) = withController { c ->
        c.seekTo(index, 0L)
        c.play()
    }

    fun removeFromQueue(index: Int) = withController { it.removeMediaItem(index) }

    // ---- Sleep timer ----

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerRemaining.value = 0L
            return
        }
        val totalMs = minutes * 60_000L
        _sleepTimerRemaining.value = totalMs
        sleepJob = scope.launch {
            var remaining = totalMs
            while (remaining > 0) {
                delay(1_000)
                remaining -= 1_000
                _sleepTimerRemaining.value = remaining.coerceAtLeast(0)
            }
            withController { it.pause() }
            _sleepTimerRemaining.value = 0L
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _sleepTimerRemaining.value = 0L
    }

    // ---- Crossfade ----------------------------------------------------------

    private suspend fun crossfadeLoop() {
        while (scope.isActive) {
            val c = _controller.value
            if (c != null && c.isConnected && crossfadeEnabled) {
                runCatching {
                    if (c.isPlaying) {
                        val currentId = c.currentMediaItem?.mediaId
                        if (currentId != null && currentId != lastMediaId) {
                            lastMediaId = currentId
                            fadeInUntil = SystemClock.elapsedRealtime() + crossfadeMs
                        }
                        val duration = c.duration
                        val position = c.currentPosition
                        val remaining = duration - position
                        val fadeInRemaining = fadeInUntil - SystemClock.elapsedRealtime()
                        val volume = when {
                            fadeInRemaining > 0 -> 1f - (fadeInRemaining.toFloat() / crossfadeMs) * 0.9f
                            remaining in 0..crossfadeMs -> (remaining.toFloat() / crossfadeMs).coerceIn(0.05f, 1f)
                            else -> 1f
                        }
                        c.volume = volume.coerceIn(0.05f, 1f)
                    }
                }
            } else if (c == null || !c.isConnected) {
                connect()
            }
            delay(150)
        }
    }

    private fun resetVolume() {
        runCatching { _controller.value?.volume = 1f }
        lastMediaId = null
        fadeInUntil = 0L
    }

    // ---- Listening stats ----------------------------------------------------

    private suspend fun listeningTimeLoop() {
        while (scope.isActive) {
            delay(10_000)
            val playing = runCatching { _controller.value?.isPlaying == true }.getOrDefault(false)
            if (playing) {
                pendingPlayMs += 10_000
                if (pendingPlayMs >= 30_000) {
                    settings.addPlayTime(pendingPlayMs)
                    pendingPlayMs = 0
                }
            } else if (pendingPlayMs > 0) {
                settings.addPlayTime(pendingPlayMs)
                pendingPlayMs = 0
            }
        }
    }

    internal fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
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
