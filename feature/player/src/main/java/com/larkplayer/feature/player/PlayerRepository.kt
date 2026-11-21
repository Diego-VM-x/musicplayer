package com.larkplayer.feature.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
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
    @Volatile private var gaplessEnabled = true
    @Volatile private var desiredShuffle = false
    @Volatile private var desiredRepeat = Player.REPEAT_MODE_ALL
    @Volatile private var desiredSpeed = 1f
    @Volatile private var fadeInUntil = 0L
    private var lastMediaId: String? = null
    private var lastPosition = 0L
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
        scope.launch {
            settings.gapless.collect { enabled -> gaplessEnabled = enabled }
        }
        scope.launch {
            settings.shuffle.collect { enabled ->
                desiredShuffle = enabled
                withController { it.shuffleModeEnabled = enabled }
            }
        }
        scope.launch {
            settings.repeatMode.collect { mode ->
                desiredRepeat = mode
                withController { it.repeatMode = mode }
            }
        }
        scope.launch {
            settings.playbackSpeed.collect { speed ->
                desiredSpeed = speed
                withController {
                    it.setPlaybackParameters(androidx.media3.common.PlaybackParameters(speed))
                }
            }
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
                    // Restore the user's shuffle/repeat preferences on every reconnect.
                    runCatching {
                        controller.shuffleModeEnabled = desiredShuffle
                        controller.repeatMode = desiredRepeat
                        controller.volume = 1f
                        controller.setPlaybackParameters(
                            androidx.media3.common.PlaybackParameters(desiredSpeed),
                        )
                    }
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
    fun setShuffle(on: Boolean) {
        desiredShuffle = on
        withController { it.shuffleModeEnabled = on }
        scope.launch { runCatching { settings.setShuffle(on) } }
    }

    fun setRepeat(mode: Int) {
        desiredRepeat = mode
        withController { it.repeatMode = mode }
        scope.launch { runCatching { settings.setRepeatMode(mode) } }
    }

    fun setPlaybackSpeed(speed: Float) {
        val safe = speed.coerceIn(0.5f, 2f)
        desiredSpeed = safe
        withController { it.setPlaybackParameters(androidx.media3.common.PlaybackParameters(safe)) }
        scope.launch { runCatching { settings.setPlaybackSpeed(safe) } }
    }

    /** Mutually exclusive playback option: only one mode can be active. */
    fun setPlaybackMode(mode: Int) {
        when (mode) {
            MODE_REPEAT_ALL -> {
                withController { it.shuffleModeEnabled = false; it.repeatMode = Player.REPEAT_MODE_ALL }
                scope.launch {
                    runCatching { settings.setShuffle(false); settings.setRepeatMode(Player.REPEAT_MODE_ALL) }
                }
            }
            MODE_REPEAT_ONE -> {
                withController { it.shuffleModeEnabled = false; it.repeatMode = Player.REPEAT_MODE_ONE }
                scope.launch {
                    runCatching { settings.setShuffle(false); settings.setRepeatMode(Player.REPEAT_MODE_ONE) }
                }
            }
            MODE_SHUFFLE -> {
                withController { it.repeatMode = Player.REPEAT_MODE_OFF; it.shuffleModeEnabled = true }
                scope.launch {
                    runCatching { settings.setRepeatMode(Player.REPEAT_MODE_OFF); settings.setShuffle(true) }
                }
            }
            else -> {
                withController { it.shuffleModeEnabled = false; it.repeatMode = Player.REPEAT_MODE_OFF }
                scope.launch {
                    runCatching { settings.setShuffle(false); settings.setRepeatMode(Player.REPEAT_MODE_OFF) }
                }
            }
        }
        desiredShuffle = mode == MODE_SHUFFLE
        desiredRepeat = when (mode) {
            MODE_REPEAT_ALL -> Player.REPEAT_MODE_ALL
            MODE_REPEAT_ONE -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

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

    /**
     * Deterministic shuffled view of the queue. The same queue always yields the
     * same order while shuffle is on, so the user can see the swap and it does
     * not reshuffle on every recomposition. Turning shuffle off restores the
     * original order.
     */
    fun displayQueue(shuffle: Boolean): List<MediaItem> {
        val items = currentQueue()
        if (!shuffle || items.size < 2) return items
        val seed = items.fold(0L) { acc, item ->
            acc * 31L + (item.mediaId?.toLongOrNull() ?: item.hashCode().toLong())
        }
        return items.shuffled(java.util.Random(seed))
    }

    fun originalIndex(mediaId: String?): Int {
        if (mediaId == null) return -1
        return currentQueue().indexOfFirst { it.mediaId == mediaId }
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

    /**
     * Applies the user's "Transición de sonido": with crossfade disabled (or gapless
     * enabled) the volume stays at 1. With crossfade enabled we fade out during the
     * configured number of seconds before the end and fade in the incoming track.
     */
    private suspend fun crossfadeLoop() {
        while (scope.isActive) {
            val c = _controller.value
            if (c == null || !c.isConnected) {
                connect()
            } else {
                runCatching {
                    if (!crossfadeEnabled) {
                        if (c.volume != 1f) c.volume = 1f
                    } else if (c.isPlaying) {
                        val now = SystemClock.elapsedRealtime()
                        val currentId = c.currentMediaItem?.mediaId
                        val position = c.currentPosition
                        val restarted = currentId != null && currentId == lastMediaId &&
                            lastPosition - position > 1_000L
                        if (currentId != lastMediaId || restarted) {
                            lastMediaId = currentId
                            fadeInUntil = now + crossfadeMs
                        }
                        lastPosition = position
                        val duration = c.duration
                        val remaining = duration - position
                        val fadeOut = if (duration > 0 && remaining <= crossfadeMs) {
                            (remaining.toFloat() / crossfadeMs).coerceIn(0f, 1f)
                        } else 1f
                        val fadeInRemaining = fadeInUntil - now
                        val fadeIn = if (fadeInRemaining > 0) {
                            1f - (fadeInRemaining.toFloat() / crossfadeMs).coerceIn(0f, 1f)
                        } else 1f
                        c.volume = minOf(fadeOut, fadeIn).coerceIn(0f, 1f)
                    } else if (c.volume != 1f && fadeInUntil <= SystemClock.elapsedRealtime()) {
                        c.volume = 1f
                    }
                }
            }
            delay(120)
        }
    }

    private fun resetVolume() {
        runCatching { _controller.value?.volume = 1f }
        lastMediaId = null
        lastPosition = 0L
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
                .setExtras(Bundle().apply { putString(EXTRA_SONG_PATH, path) })
                .build()
        )
        .build()

    companion object {
        const val EXTRA_SONG_PATH = "lark_song_path"
        const val MODE_NORMAL = 0
        const val MODE_REPEAT_ALL = 1
        const val MODE_REPEAT_ONE = 2
        const val MODE_SHUFFLE = 3
    }
}
