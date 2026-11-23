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
import org.json.JSONArray
import org.json.JSONObject
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
    private val statePrefs = appContext.getSharedPreferences("lark_playback_state", Context.MODE_PRIVATE)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var widgetListener: Player.Listener? = null
    private val sessionToken = SessionToken(
        context,
        ComponentName(context, PlaybackService::class.java),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _controller = MutableStateFlow<MediaController?>(null)
    private val _audioSessionId = MutableStateFlow(0)
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

    /** Moment until which the start of the track stays muted (gapless disabled). */
    @Volatile private var gapUntil = 0L
    private var lastMediaId: String? = null
    private var lastPosition = 0L
    @Volatile private var visibleOrder: List<String> = emptyList()
    private var pendingPlayMs = 0L

    private var connecting = false
    private var pendingAction: ((MediaController) -> Unit)? = null

    init {
        // Don't bind the playback service during the first, most expensive
        // frames. Any play action still connects immediately via withController.
        scope.launch {
            delay(900)
            connect()
        }
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
                // Applied when the controller connects (see connect() onSuccess).
            }
        }
        scope.launch {
            settings.repeatMode.collect { mode -> desiredRepeat = mode }
        }
        scope.launch {
            settings.playbackSpeed.collect { speed -> desiredSpeed = speed }
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
                    // Ask the service for the real ExoPlayer audio session id.
                    runCatching {
                        val cmd = androidx.media3.session.SessionCommand(
                            PlaybackService.COMMAND_GET_AUDIO_SESSION,
                            Bundle.EMPTY,
                        )
                        val future = controller.sendCustomCommand(cmd, Bundle.EMPTY)
                        future.addListener(
                            {
                                runCatching {
                                    val result = future.get()
                                    _audioSessionId.value = result.extras.getInt(
                                        PlaybackService.EXTRA_AUDIO_SESSION_ID,
                                        0,
                                    )
                                }
                            },
                            MoreExecutors.directExecutor(),
                        )
                    }
                    // Recover the last queue/position if the service process was
                    // killed while the app was closed.
                    restoreQueueIfEmpty(controller)
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
        
        // Automatic transitions must follow the order displayed in the queue.
        // ExoPlayer may still pick its own random item if the custom order has
        // not reached the service yet, so we correct the transition immediately.
        var lastAutoId: String? = runCatching { controller.currentMediaItem?.mediaId }.getOrNull()
        val shuffleSyncListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val newId = mediaItem?.mediaId
                if (desiredShuffle && visibleOrder.isNotEmpty() && newId != null &&
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                ) {
                    val previousPosition = visibleOrder.indexOf(lastAutoId)
                    val expected = when {
                        previousPosition in 0 until visibleOrder.lastIndex ->
                            visibleOrder[previousPosition + 1]
                        desiredRepeat == Player.REPEAT_MODE_ALL -> visibleOrder.firstOrNull()
                        else -> null
                    }
                    if (expected != null && expected != newId) {
                        val index = currentQueue().indexOfFirst { it.mediaId == expected }
                        if (index >= 0) {
                            runCatching { controller.seekTo(index, 0L) }
                            runCatching { controller.play() }
                        }
                    }
                }
                lastAutoId = newId
                if (desiredShuffle && visibleOrder.isEmpty() && currentQueue().isNotEmpty()) {
                    rebuildShuffleOrder()
                }
            }
        }
        runCatching { controller.addListener(shuffleSyncListener) }

        // Keep the on-disk playback cache in sync with the real session.
        val stateCacheListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem == null) clearPersistedQueue()
                else persistQueue(controller, 0L, controller.isPlaying)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                persistQueue(controller, controller.currentPosition, isPlaying)
            }
        }
        runCatching { controller.addListener(stateCacheListener) }

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

    // ---- Playback state cache -----------------------------------------------

    /**
     * Writes the queue to disk so playback can be reconstructed if the service
     * process dies while the app is in the background. Runs on IO and never on
     * the startup path.
     */
    private data class CachedTrack(
        val id: Long,
        val title: String,
        val artist: String,
        val album: String,
        val albumId: Long,
        val duration: Long,
        val path: String,
    )

    private fun persistQueue(c: MediaController, position: Long, playing: Boolean) {
        // MediaController must only be touched on the main thread: take the
        // snapshot here, serialize and write on IO.
        val tracks = runCatching {
            (0 until c.mediaItemCount).mapNotNull { i ->
                val item = c.getMediaItemAt(i)
                val meta = item.mediaMetadata
                CachedTrack(
                    id = item.mediaId?.toLongOrNull() ?: 0L,
                    title = meta.title?.toString().orEmpty(),
                    artist = meta.artist?.toString().orEmpty(),
                    album = meta.albumTitle?.toString().orEmpty(),
                    albumId = meta.artworkUri?.toString()?.substringAfterLast('/')?.toLongOrNull() ?: -1L,
                    duration = meta.extras?.getLong(EXTRA_SONG_DURATION) ?: 0L,
                    path = meta.extras?.getString(EXTRA_SONG_PATH).orEmpty(),
                )
            }
        }.getOrNull() ?: return
        val index = runCatching { c.currentMediaItemIndex }.getOrDefault(0).coerceAtLeast(0)
        ioScope.launch {
            runCatching {
                val array = JSONArray()
                tracks.forEach { t ->
                    array.put(
                        JSONObject().apply {
                            put("id", t.id)
                            put("title", t.title)
                            put("artist", t.artist)
                            put("album", t.album)
                            put("albumId", t.albumId)
                            put("duration", t.duration)
                            put("path", t.path)
                        }
                    )
                }
                statePrefs.edit()
                    .putString("queue", array.toString())
                    .putInt("index", index)
                    .putLong("position", position)
                    .putBoolean("playing", playing)
                    .apply()
            }.onFailure { Timber.e(it, "Failed to cache playback state") }
        }
    }

    private fun restoreQueueIfEmpty(c: MediaController) {
        if (c.mediaItemCount != 0) return
        val json = statePrefs.getString("queue", null) ?: return
        val songs = runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val o = array.getJSONObject(i)
                val id = o.optLong("id", 0L)
                if (id <= 0L) null else Song(
                    id = id,
                    title = o.optString("title"),
                    artist = o.optString("artist"),
                    album = o.optString("album"),
                    albumId = o.optLong("albumId", -1L),
                    duration = o.optLong("duration", 0L),
                    path = o.optString("path"),
                )
            }
        }.getOrNull().orEmpty()
        if (songs.isEmpty()) return

        val index = statePrefs.getInt("index", 0).coerceIn(0, songs.lastIndex)
        val position = statePrefs.getLong("position", 0L)
        val shouldPlay = statePrefs.getBoolean("playing", false)
        c.setMediaItems(songs.map { it.toMediaItem() }, index, position)
        c.prepare()
        if (shouldPlay) c.play()
        Timber.d("Restored cached queue: %d tracks", songs.size)
    }

    private fun clearPersistedQueue() = statePrefs.edit().clear().apply()

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
            persistQueue(c, 0L, playing = true)
        }
    }

    fun addToQueue(song: Song) = withController { c ->
        val item = song.toMediaItem()
        c.addMediaItem(item)
        if (desiredShuffle) {
            // Append at the end of the visible order instead of reshuffling
            // everything the user is already listening to.
            if (visibleOrder.isEmpty()) rebuildShuffleOrder()
            else if (item.mediaId !in visibleOrder) visibleOrder = visibleOrder + item.mediaId
            sendShuffleOrder(c, enabled = true)
        }
        persistQueue(c, c.currentPosition, c.isPlaying)
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
        if (!on) visibleOrder = emptyList()
        withController { controller -> sendShuffleOrder(controller, on) }
        scope.launch { runCatching { settings.setShuffle(on) } }
    }

    /**
     * Current track first. When [force] is false an order saved in a previous
     * session is reused if the queue still contains the same songs, so the
     * mix survives app restarts. Pressing shuffle again forces a new order.
     */
    private fun rebuildShuffleOrder(force: Boolean = false) {
        val items = currentQueue()
        if (items.isEmpty()) {
            visibleOrder = emptyList()
            return
        }
        val currentId = runCatching { _controller.value?.currentMediaItem?.mediaId }.getOrNull()
        val ids = items.mapNotNull { it.mediaId }.toSet()
        if (!force) {
            val saved = statePrefs.getString(KEY_SHUFFLE_ORDER, null)
                ?.split(',')
                ?.filter { it.isNotBlank() }
            if (saved != null && saved.toSet() == ids) {
                visibleOrder = listOfNotNull(currentId) + saved.filter { it != currentId }
                persistShuffleOrder()
                return
            }
        }
        val first = currentId?.takeIf { id -> ids.contains(id) }
        val rest = ids.filter { it != first }.shuffled()
        visibleOrder = listOfNotNull(first) + rest
        persistShuffleOrder()
    }

    private fun persistShuffleOrder() {
        ioScope.launch {
            runCatching {
                statePrefs.edit().putString(KEY_SHUFFLE_ORDER, visibleOrder.joinToString(",")).apply()
            }
        }
    }

    /** Sends the visible order to the service; the service owns Media3's shuffle state. */
    private fun sendShuffleOrder(controller: MediaController, enabled: Boolean) {
        if (enabled) {
            val command = androidx.media3.session.SessionCommand(
                PlaybackService.COMMAND_SET_SHUFFLE_ORDER,
                Bundle.EMPTY,
            )
            val args = Bundle().apply {
                putStringArray(
                    PlaybackService.EXTRA_SHUFFLE_ORDER,
                    visibleOrder.toTypedArray(),
                )
            }
            // install the custom order BEFORE enabling shuffle: enabling first
            // makes ExoPlayer generate a random default order and a track can
            // already advance with it before our command is processed.
            runCatching { controller.sendCustomCommand(command, args) }
        }
        runCatching { controller.shuffleModeEnabled = enabled }
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
        // Pressing shuffle while it is already active generates a fresh order.
        if (shuffle) rebuildShuffleOrder(force = desiredShuffle)
        setRepeat(
            when (mode) {
                MODE_REPEAT_ONE -> Player.REPEAT_MODE_ONE
                // Only three radio-like modes exist: one is always active.
                else -> Player.REPEAT_MODE_ALL
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

    fun audioSessionId(): Int = _audioSessionId.value

    /** Re-requests the session id after prepare, when ExoPlayer assigns it. */
    fun refreshAudioSession() {
        val controller = _controller.value ?: return
        runCatching {
            val cmd = androidx.media3.session.SessionCommand(
                PlaybackService.COMMAND_GET_AUDIO_SESSION,
                Bundle.EMPTY,
            )
            val future = controller.sendCustomCommand(cmd, Bundle.EMPTY)
            future.addListener(
                {
                    runCatching {
                        _audioSessionId.value = future.get().extras.getInt(
                            PlaybackService.EXTRA_AUDIO_SESSION_ID,
                            0,
                        )
                    }
                },
                MoreExecutors.directExecutor(),
            )
        }
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
        val safe = index.coerceIn(0, (c.mediaItemCount - 1).coerceAtLeast(0))
        val removedId = runCatching { c.getMediaItemAt(safe).mediaId }.getOrNull()
        c.removeMediaItem(safe)
        if (desiredShuffle) {
            // Keep the user's visible order; only drop the removed entry.
            if (removedId != null) visibleOrder = visibleOrder - removedId
            sendShuffleOrder(c, enabled = true)
        }
    }

    /** Reorders two items in the real timeline (used by the queue drag handles). */
    fun moveQueueItem(from: Int, to: Int) = withController { c ->
        val safeFrom = from.coerceIn(0, (c.mediaItemCount - 1).coerceAtLeast(0))
        val safeTo = to.coerceIn(0, (c.mediaItemCount - 1).coerceAtLeast(0))
        if (safeFrom == safeTo) return@withController
        val fromId = runCatching { c.getMediaItemAt(safeFrom).mediaId }.getOrNull()
        val toId = runCatching { c.getMediaItemAt(safeTo).mediaId }.getOrNull()
        c.moveMediaItem(safeFrom, safeTo)
        if (desiredShuffle) {
            // Preserve the manual arrangement instead of reshuffling it away.
            if (fromId != null && toId != null) {
                val fromPos = visibleOrder.indexOf(fromId)
                val toPos = visibleOrder.indexOf(toId)
                if (fromPos >= 0 && toPos >= 0) {
                    visibleOrder = visibleOrder.toMutableList().apply {
                        add(toPos, removeAt(fromPos))
                    }
                }
            }
            sendShuffleOrder(c, enabled = true)
        }
    }

    /**
     * Reorders by mediaId so the caller doesn't need to track live indices.
     * This avoids stale-index bugs in the drag-and-drop UI.
     */
    fun moveQueueItemById(fromId: String?, toId: String?) = withController { c ->
        if (fromId == null || toId == null || fromId == toId) return@withController
        val items = (0 until c.mediaItemCount).map { c.getMediaItemAt(it) }
        val fromIdx = items.indexOfFirst { it.mediaId == fromId }
        val toIdx   = items.indexOfFirst { it.mediaId == toId }
        if (fromIdx < 0 || toIdx < 0) return@withController
        c.moveMediaItem(fromIdx, toIdx)
        if (desiredShuffle) {
            // Apply exactly the move requested by the queue screen.
            val fromPos = visibleOrder.indexOf(fromId)
            val toPos = visibleOrder.indexOf(toId)
            if (fromPos >= 0 && toPos >= 0 && fromPos != toPos) {
                visibleOrder = visibleOrder.toMutableList().apply {
                    add(toPos, removeAt(fromPos))
                }
            }
            sendShuffleOrder(c, enabled = true)
        }
    }

    /** Stops playback and empties the queue (the "clear queue" action). */
    fun clearQueue() = withController { c ->
        c.stop()
        c.clearMediaItems()
        visibleOrder = emptyList()
        clearPersistedQueue()
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
     * Only handles the "gapless off" short silence. Real crossfade volume ramps
     * are owned by PlaybackService, which mixes two ExoPlayer instances so the
     * outgoing and incoming songs overlap.
     */
    private suspend fun crossfadeLoop() {
        while (scope.isActive) {
            val c = _controller.value
            if (c != null && c.isConnected && !crossfadeEnabled) {
                runCatching {
                    if (!gaplessEnabled && c.isPlaying) {
                        val now = SystemClock.elapsedRealtime()
                        val currentId = c.currentMediaItem?.mediaId
                        if (currentId != null && lastMediaId != null && currentId != lastMediaId) {
                            gapUntil = now + 350L
                        }
                        lastMediaId = currentId
                        c.volume = if (now < gapUntil) 0f else 1f
                    } else if (c.volume != 1f) {
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
        gapUntil = 0L
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
            _controller.value?.let { c -> persistQueue(c, c.currentPosition, c.isPlaying) }
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
                .setExtras(Bundle().apply {
                    putString(EXTRA_SONG_PATH, path)
                    putLong(EXTRA_SONG_DURATION, duration)
                })
                .build()
        )
        .build()

    companion object {
        const val EXTRA_SONG_PATH = "lark_song_path"
        const val EXTRA_SONG_DURATION = "lark_song_duration"
        private const val KEY_SHUFFLE_ORDER = "shuffle_order"
        private const val KEY_WIDGET_TITLE = "widget_title"
        private const val KEY_WIDGET_ARTIST = "widget_artist"
        private const val KEY_WIDGET_PLAYING = "widget_playing"
        const val MODE_NORMAL = 0
        const val MODE_REPEAT_ALL = 1
        const val MODE_REPEAT_ONE = 2
        const val MODE_SHUFFLE = 3
    }
}
