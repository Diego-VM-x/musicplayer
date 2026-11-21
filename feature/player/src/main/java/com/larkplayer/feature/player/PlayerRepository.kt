package com.larkplayer.feature.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
    private val widgetPrefs = appContext.getSharedPreferences("lark_widget", Context.MODE_PRIVATE)
    private var widgetListener: Player.Listener? = null
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
    @Volatile private var visibleOrder: List<String> = emptyList()
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
                if (enabled) rebuildShuffleOrder() else visibleOrder = emptyList()
                withController { controller -> sendShuffleOrder(controller, enabled) }
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
                    widgetListener?.let { runCatching { controller.removeListener(it) } }
                    widgetListener = null
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
                    if (desiredShuffle) {
                        rebuildShuffleOrder()
                        sendShuffleOrder(controller, enabled = true)
                    }
                    attachWidgetListener(controller)
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

    /** Publishes now-playing metadata for the home-screen widget. */
    private fun attachWidgetListener(controller: MediaController) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                writeWidgetState(
                    title = mediaItem?.mediaMetadata?.title?.toString().orEmpty(),
                    artist = mediaItem?.mediaMetadata?.artist?.toString().orEmpty(),
                    playing = controller.isPlaying,
                )
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                writeWidgetState(
                    title = controller.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty(),
                    artist = controller.currentMediaItem?.mediaMetadata?.artist?.toString().orEmpty(),
                    playing = isPlaying,
                )
            }
        }
        widgetListener = listener
        runCatching { controller.addListener(listener) }
        writeWidgetState(
            title = controller.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty(),
            artist = controller.currentMediaItem?.mediaMetadata?.artist?.toString().orEmpty(),
            playing = controller.isPlaying,
        )
    }

    private fun writeWidgetState(title: String, artist: String, playing: Boolean) {
        widgetPrefs.edit()
            .putString(KEY_WIDGET_TITLE, title)
            .putString(KEY_WIDGET_ARTIST, artist)
            .putBoolean(KEY_WIDGET_PLAYING, playing)
            .apply()
        runCatching {
            appContext.sendBroadcast(Intent("com.larkplayer.action.UPDATE_WIDGET").setPackage(appContext.packageName))
        }
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
            if (desiredShuffle) {
                rebuildShuffleOrder()
                sendShuffleOrder(c, enabled = true)
            }
            c.prepare()
            c.play()
        }
    }

    fun addToQueue(song: Song) = withController { c ->
        c.addMediaItem(song.toMediaItem())
        if (desiredShuffle) {
            rebuildShuffleOrder()
            sendShuffleOrder(c, enabled = true)
        }
    }

    fun play(song: Song) = playSongs(listOf(song))

    fun togglePlayPause() = withController { c -> if (c.isPlaying) c.pause() else c.play() }

    fun skipNext() = withController { controller ->
        if (desiredShuffle && visibleOrder.isNotEmpty()) {
            val currentId = runCatching { controller.currentMediaItem?.mediaId }.getOrNull()
            val position = visibleOrder.indexOf(currentId)
            val nextId = if (position in 0 until visibleOrder.lastIndex) {
                visibleOrder[position + 1]
            } else if (desiredRepeat == Player.REPEAT_MODE_ALL) {
                visibleOrder.firstOrNull()
            } else {
                null
            }
            if (nextId != null) {
                val index = currentQueue().indexOfFirst { it.mediaId == nextId }
                if (index >= 0) {
                    controller.seekTo(index, 0L)
                    controller.play()
                    return@withController
                }
            }
        }
        controller.seekToNextMediaItem()
        controller.play()
    }

    fun skipPrevious() = withController { controller ->
        if (controller.currentPosition > 3_000L) {
            controller.seekTo(0L)
            return@withController
        }
        if (desiredShuffle && visibleOrder.isNotEmpty()) {
            val currentId = runCatching { controller.currentMediaItem?.mediaId }.getOrNull()
            val position = visibleOrder.indexOf(currentId)
            val previousId = if (position > 0) {
                visibleOrder[position - 1]
            } else if (desiredRepeat == Player.REPEAT_MODE_ALL) {
                visibleOrder.lastOrNull()
            } else {
                null
            }
            if (previousId != null) {
                val index = currentQueue().indexOfFirst { it.mediaId == previousId }
                if (index >= 0) {
                    controller.seekTo(index, 0L)
                    controller.play()
                    return@withController
                }
            }
        }
        controller.seekToPreviousMediaItem()
        controller.play()
    }

    fun seekTo(ms: Long) = withController { it.seekTo(ms) }

    fun setShuffle(on: Boolean) {
        desiredShuffle = on
        if (on) rebuildShuffleOrder() else visibleOrder = emptyList()
        withController { controller -> sendShuffleOrder(controller, on) }
        scope.launch { runCatching { settings.setShuffle(on) } }
    }

    /** Current track first, then a brand-new random order on every activation. */
    private fun rebuildShuffleOrder() {
        val items = currentQueue()
        if (items.isEmpty()) {
            visibleOrder = emptyList()
            return
        }
        val currentId = runCatching { _controller.value?.currentMediaItem?.mediaId }.getOrNull()
        val first = currentId?.takeIf { id -> items.any { it.mediaId == id } }
        // No fixed seed: each activation produces a different order.
        val rest = items.mapNotNull { it.mediaId }
            .filter { it != first }
            .shuffled()
        visibleOrder = listOfNotNull(first) + rest
    }

    /** Sends the visible order to the service; the service owns Media3's shuffle state. */
    private fun sendShuffleOrder(controller: MediaController, enabled: Boolean) {
        val command = androidx.media3.session.SessionCommand(
            PlaybackService.COMMAND_SET_SHUFFLE_ORDER,
            Bundle.EMPTY,
        )
        val args = Bundle().apply {
            putStringArray(
                PlaybackService.EXTRA_SHUFFLE_ORDER,
                if (enabled) visibleOrder.toTypedArray() else emptyArray(),
            )
        }
        val sent = runCatching { controller.sendCustomCommand(command, args) }.isSuccess
        // Fallback if the service did not receive the command.
        if (!sent || !enabled) runCatching { controller.shuffleModeEnabled = enabled }
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
        val shuffle = mode == MODE_SHUFFLE
        setRepeat(
            when (mode) {
                MODE_REPEAT_ALL -> Player.REPEAT_MODE_ALL
                MODE_REPEAT_ONE -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            },
        )
        setShuffle(shuffle)
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
        if (visibleOrder.isEmpty()) rebuildShuffleOrder()
        val byId = items.associateBy { it.mediaId }
        val ordered = visibleOrder.mapNotNull { byId[it] }
        return ordered + items.filter { it.mediaId !in visibleOrder }
    }

    fun originalIndex(mediaId: String?): Int {
        if (mediaId == null) return -1
        return currentQueue().indexOfFirst { it.mediaId == mediaId }
    }

    fun playAtIndex(index: Int) = withController { c ->
        c.seekTo(index, 0L)
        c.play()
    }

    fun removeFromQueue(index: Int) = withController { c ->
        c.removeMediaItem(index)
        if (desiredShuffle) {
            rebuildShuffleOrder()
            sendShuffleOrder(c, enabled = true)
        }
    }

    /** Reorders two items in the real timeline (used by the queue drag handles). */
    fun moveQueueItem(from: Int, to: Int) = withController { c ->
        val safeFrom = from.coerceIn(0, c.mediaItemCount - 1)
        val safeTo = to.coerceIn(0, c.mediaItemCount - 1)
        if (safeFrom != safeTo) c.moveMediaItem(safeFrom, safeTo)
        if (desiredShuffle) {
            rebuildShuffleOrder()
            sendShuffleOrder(c, enabled = true)
        }
    }

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
        private const val KEY_WIDGET_TITLE = "widget_title"
        private const val KEY_WIDGET_ARTIST = "widget_artist"
        private const val KEY_WIDGET_PLAYING = "widget_playing"
        const val MODE_NORMAL = 0
        const val MODE_REPEAT_ALL = 1
        const val MODE_REPEAT_ONE = 2
        const val MODE_SHUFFLE = 3
    }
}
