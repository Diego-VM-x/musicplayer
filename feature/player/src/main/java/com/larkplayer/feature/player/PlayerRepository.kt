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
import com.larkplayer.core.database.dao.QueueDao
import com.larkplayer.core.database.entity.QueueEntity
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
import kotlinx.coroutines.flow.first
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
    private val queueDao: QueueDao,
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
    /** The mixed order of media ids. When shuffle is on this IS the real Media3 timeline. */
    @Volatile private var visibleOrder: List<String> = emptyList()
    /** Un-shuffled order, kept so turning shuffle off restores the original queue. */
    @Volatile private var naturalOrder: List<String> = emptyList()
    /** Last shuffle order persisted in DataStore (legacy SharedPreferences on upgrade). */
    @Volatile private var savedShuffleOrder: String? = null

    /**
     * Shuffle is a repository-level intent: Media3 itself always runs with
     * shuffleModeEnabled = false because the mixed order is applied to the
     * media items directly. The UI must follow this flag, not the controller.
     */
    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    /** Playback state as a plain Flow so the app module needs no Media3 types. */
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

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
            // Load the order before connect() runs (it is delayed in the same scope).
            // One-time migration from the old SharedPreferences key.
            val stored = runCatching { settings.shuffleOrder.first() }.getOrDefault("")
            val legacy = runCatching { statePrefs.getString(LEGACY_KEY_SHUFFLE_ORDER, null) }.getOrDefault(null)
            val initial = stored.takeIf { it.isNotBlank() } ?: legacy.orEmpty()
            if (initial.isNotBlank() && stored.isBlank()) {
                runCatching { settings.setShuffleOrder(initial) }
            }
            savedShuffleOrder = initial.takeIf { it.isNotBlank() }
            settings.shuffleOrder.collect { order ->
                savedShuffleOrder = order.takeIf { it.isNotBlank() }
            }
        }
        scope.launch {
            settings.shuffle.collect { enabled ->
                desiredShuffle = enabled
                // Media3 always runs with shuffle off; the mix is applied by
                // rewriting the media items in setShuffle()/connect(). Never
                // rotate it here: only an explicit shuffle tap makes a new mix.
                _shuffleEnabled.value = enabled
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
                    // The mixed sequence is written into the media items
                    // themselves, so Media3's shuffle engine must stay off:
                    // its DefaultShuffleOrder would otherwise pick its own
                    // "next" and diverge from the list shown in the queue.
                    runCatching {
                        controller.shuffleModeEnabled = false
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
                    ensureNaturalOrder(controller)
                    if (desiredShuffle && controller.mediaItemCount > 0) {
                        // Reuse the exact sequence saved in DataStore; only a new
                        // tap of the shuffle button may create a different mix.
                        rebuildShuffleOrder(force = false)
                        applyQueueOrder(controller, visibleOrder)
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
                _isPlaying.value = isPlaying
                writeWidgetState(
                    title = controller.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty(),
                    artist = controller.currentMediaItem?.mediaMetadata?.artist?.toString().orEmpty(),
                    playing = isPlaying,
                )
            }
        }
        widgetListener = listener
        runCatching { controller.addListener(listener) }

        // No transition "correction" listener is needed any more: the mixed
        // sequence is the real Media3 timeline, so automatic transitions,
        // notification buttons and crossfade all pick the visible next row.

        // Keep the on-disk playback cache in sync with the real session.
        val stateCacheListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem == null) clearPersistedQueue()
                else persistQueue(controller, 0L, controller.isPlaying)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                persistQueue(controller, controller.currentPosition, isPlaying)
            }
        }
        runCatching { controller.addListener(stateCacheListener) }

        _isPlaying.value = runCatching { controller.isPlaying }.getOrDefault(false)
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
        var tracks = runCatching {
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

        // The live timeline is the mix; cache the *natural* order so a restart
        // restores the original queue first and then re-applies the saved mix.
        val currentId = runCatching { c.currentMediaItem?.mediaId }.getOrNull()
        if (desiredShuffle && naturalOrder.isNotEmpty()) {
            val byId = tracks.associateBy { it.id.toString() }
            val reordered = naturalOrder.mapNotNull { byId[it] }
            if (reordered.size == tracks.size) tracks = reordered
        }
        val index = tracks.indexOfFirst { it.id.toString() == currentId }
            .let { if (it >= 0) it else runCatching { c.currentMediaItemIndex }.getOrDefault(0) }
            .coerceAtLeast(0)
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
                // Room owns the durable queue order (SharedPreferences keeps the
                // full track metadata used to rebuild the session).
                queueDao.replace(tracks.filter { it.id > 0 }.mapIndexed { i, t ->
                    QueueEntity(songId = t.id, position = i)
                })
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
        // The cached queue is stored in its natural order; the mix is applied
        // right after this by connect().
        naturalOrder = songs.mapNotNull { it.id.toString() }
        c.setMediaItems(songs.map { it.toMediaItem() }, index, position)
        c.prepare()
        if (shouldPlay) c.play()
        Timber.d("Restored cached queue: %d tracks", songs.size)
    }

    private fun clearPersistedQueue() {
        statePrefs.edit().clear().apply()
        ioScope.launch { runCatching { queueDao.clear() } }
    }

    // ---- Playback -----------------------------------------------------------

    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val items = songs.map { it.toMediaItem() }
        val safeIndex = startIndex.coerceIn(0, items.lastIndex)
        val startId = items[safeIndex].mediaId
        // The natural (user) order is the source of truth for future re-mixes
        // and for restoring the queue when shuffle is switched off.
        naturalOrder = items.mapNotNull { it.mediaId }
        withController { c ->
            val order = if (desiredShuffle) {
                rebuildShuffleOrder(force = false, currentId = startId)
                visibleOrder
            } else naturalOrder
            val byId = items.associateBy { it.mediaId }
            val ordered = order.mapNotNull { byId[it] }
            val finalItems = if (ordered.size == items.size) ordered else items
            val index = finalItems.indexOfFirst { it.mediaId == startId }
                .let { if (it >= 0) it else 0 }
            c.setMediaItems(finalItems, index, 0L)
            c.shuffleModeEnabled = false
            c.prepare()
            c.play()
            persistQueue(c, 0L, playing = true)
        }
    }

    fun addToQueue(song: Song) = withController { c ->
        val item = song.toMediaItem()
        val id = item.mediaId
        c.addMediaItem(item)
        c.shuffleModeEnabled = false
        if (id != null) {
            if (naturalOrder.isNotEmpty() && id !in naturalOrder) naturalOrder = naturalOrder + id
            if (desiredShuffle) {
                // Append at the end of the visible order instead of reshuffling
                // everything the user is already listening to. The physical
                // queue gets the same append, so "next" stays the visible row.
                if (visibleOrder.isEmpty()) {
                    rebuildShuffleOrder(currentId = id)
                    applyQueueOrder(c, visibleOrder)
                } else if (id !in visibleOrder) {
                    visibleOrder = visibleOrder + id
                    persistShuffleOrder()
                }
            }
        }
        persistQueue(c, c.currentPosition, c.isPlaying)
    }

    fun play(song: Song) = playSongs(listOf(song))

    fun togglePlayPause() = withController { c -> if (c.isPlaying) c.pause() else c.play() }

    /**
     * Skips by an explicit index in the fixed timeline. Since the timeline IS
     * the mixed order, this is the same row the queue shows as "next".
     */
    fun skipNext() = withController { c ->
        val count = c.mediaItemCount
        if (count == 0) return@withController
        val current = c.currentMediaItemIndex.coerceIn(0, count - 1)
        val target = if (current < count - 1) {
            current + 1
        } else if (c.repeatMode == Player.REPEAT_MODE_ALL) {
            0
        } else null
        if (target != null) c.seekTo(target, 0L)
        c.play()
    }

    fun skipPrevious() = withController { c ->
        if (c.currentPosition > 3_000L) {
            c.seekTo(0L)
            c.play()
            return@withController
        }
        val count = c.mediaItemCount
        if (count == 0) return@withController
        val current = c.currentMediaItemIndex.coerceIn(0, count - 1)
        val target = if (current > 0) {
            current - 1
        } else if (c.repeatMode == Player.REPEAT_MODE_ALL) {
            count - 1
        } else null
        if (target != null) c.seekTo(target, 0L)
        c.play()
    }

    fun seekTo(ms: Long) = withController { it.seekTo(ms) }

    fun setShuffle(on: Boolean) = setShuffleInternal(on, forceNewMix = false)

    private fun setShuffleInternal(on: Boolean, forceNewMix: Boolean) {
        desiredShuffle = on
        _shuffleEnabled.value = on
        if (on) rebuildShuffleOrder(force = forceNewMix)
        withController { c ->
            ensureNaturalOrder(c)
            // The mixed sequence is written into the media items, so Media3's
            // shuffle engine stays off and cannot pick a different "next".
            applyQueueOrder(c, if (on) visibleOrder else naturalOrder)
            c.shuffleModeEnabled = false
        }
        scope.launch { runCatching { settings.setShuffle(on) } }
    }

    /**
     * Current track first. When [force] is false an order saved in a previous
     * session is reused verbatim if the queue still contains the same songs,
     * so the mix survives app restarts without being rotated around the current
     * track. Only a fresh shuffle tap ([force] = true) creates a new order.
     */
    private fun rebuildShuffleOrder(force: Boolean = false, currentId: String? = null) {
        val ids = naturalOrder.filter { it.isNotBlank() }
        if (ids.isEmpty()) return
        if (!force) {
            val saved = savedShuffleOrder?.split(',')?.filter { it.isNotBlank() }
            if (saved != null && saved.size == ids.size && saved.toSet() == ids.toSet()) {
                visibleOrder = saved
                return
            }
        }
        val anchor = (currentId ?: runCatching { _controller.value?.currentMediaItem?.mediaId }.getOrNull())
            ?.takeIf { it in ids }
        visibleOrder = listOfNotNull(anchor) + ids.filter { it != anchor }.shuffled()
        persistShuffleOrder()
    }

    private fun persistShuffleOrder() {
        val order = visibleOrder.joinToString(",")
        savedShuffleOrder = order.takeIf { it.isNotBlank() }
        scope.launch { runCatching { settings.setShuffleOrder(order) } }
    }

    /** Remembers the un-shuffled sequence when the session was restored by the service. */
    private fun ensureNaturalOrder(c: MediaController) {
        if (naturalOrder.isNotEmpty()) return
        val ids = (0 until c.mediaItemCount).mapNotNull { i ->
            runCatching { c.getMediaItemAt(i).mediaId }.getOrNull()
        }
        if (ids.isNotEmpty()) naturalOrder = ids
    }

    /**
     * Rewrites the real Media3 timeline in [order]. This is the core of the fix:
     * once the mixed sequence is the timeline, automatic transitions, skip
     * buttons, notification actions and crossfade all follow the visible queue,
     * with Media3's own shuffle engine permanently disabled.
     * The current media item and its position are preserved.
     */
    private fun applyQueueOrder(c: MediaController, order: List<String>) {
        if (order.isEmpty()) return
        val byId = (0 until c.mediaItemCount).mapNotNull { i ->
            runCatching { c.getMediaItemAt(i) }.getOrNull()
        }.associateBy { it.mediaId }
        val ordered = order.mapNotNull { byId[it] }
        if (ordered.size != c.mediaItemCount) return // stale ids; retried on next order change
        val currentIds = (0 until c.mediaItemCount).mapNotNull { i ->
            runCatching { c.getMediaItemAt(i).mediaId }.getOrNull()
        }
        if (currentIds == ordered.map { it.mediaId }) return // already exact; don't reset playback

        val currentId = runCatching { c.currentMediaItem?.mediaId }.getOrNull()
        val position = runCatching { c.currentPosition }.getOrDefault(0L)
        val wasPlaying = runCatching { c.isPlaying }.getOrDefault(false)
        val index = ordered.indexOfFirst { it.mediaId == currentId }.let { if (it >= 0) it else 0 }
        c.setMediaItems(ordered, index, position.coerceAtLeast(0L))
        c.shuffleModeEnabled = false
        c.prepare()
        if (wasPlaying) c.play()
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
        // Only a NEW tap while shuffle is already active generates a fresh mix.
        // Turning it on from another mode reuses the persisted order.
        val wasShuffle = desiredShuffle
        setRepeat(
            when (mode) {
                MODE_REPEAT_ONE -> Player.REPEAT_MODE_ONE
                MODE_NORMAL -> Player.REPEAT_MODE_OFF
                else -> Player.REPEAT_MODE_ALL
            },
        )
        setShuffleInternal(shuffle, forceNewMix = shuffle && wasShuffle)
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

    /** Moves [fromId] to [toId]'s slot in an id-ordered list. */
    private fun moveId(order: List<String>, fromId: String, toId: String): List<String> {
        val from = order.indexOf(fromId)
        val to = order.indexOf(toId)
        if (from < 0 || to < 0 || from == to) return order
        return order.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun removeFromQueue(index: Int) = withController { c ->
        val safe = index.coerceIn(0, (c.mediaItemCount - 1).coerceAtLeast(0))
        val removedId = runCatching { c.getMediaItemAt(safe).mediaId }.getOrNull()
        c.removeMediaItem(safe)
        c.shuffleModeEnabled = false
        if (removedId != null) {
            naturalOrder = naturalOrder.filter { it != removedId }
            if (desiredShuffle) {
                // Keep the user's visible order; only drop the removed entry.
                visibleOrder = visibleOrder.filter { it != removedId }
                if (visibleOrder.isEmpty() && c.mediaItemCount > 0) {
                    rebuildShuffleOrder()
                    applyQueueOrder(c, visibleOrder)
                } else {
                    persistShuffleOrder()
                }
            }
        }
        persistQueue(c, c.currentPosition, c.isPlaying)
    }

    /** Reorders two items in the real timeline (used by the queue drag handles). */
    fun moveQueueItem(from: Int, to: Int) = withController { c ->
        val safeFrom = from.coerceIn(0, (c.mediaItemCount - 1).coerceAtLeast(0))
        val safeTo = to.coerceIn(0, (c.mediaItemCount - 1).coerceAtLeast(0))
        if (safeFrom == safeTo) return@withController
        val fromId = runCatching { c.getMediaItemAt(safeFrom).mediaId }.getOrNull()
        val toId = runCatching { c.getMediaItemAt(safeTo).mediaId }.getOrNull()
        c.moveMediaItem(safeFrom, safeTo)
        c.shuffleModeEnabled = false
        if (fromId != null && toId != null) {
            naturalOrder = moveId(naturalOrder, fromId, toId)
            if (desiredShuffle) {
                // Preserve the manual arrangement instead of reshuffling it away.
                visibleOrder = moveId(visibleOrder, fromId, toId)
                persistShuffleOrder()
            }
        }
        persistQueue(c, c.currentPosition, c.isPlaying)
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
        c.shuffleModeEnabled = false
        // Apply exactly the move requested by the queue screen to both the
        // natural and the (possibly mixed) sequence.
        naturalOrder = moveId(naturalOrder, fromId, toId)
        if (desiredShuffle) {
            visibleOrder = moveId(visibleOrder, fromId, toId)
            persistShuffleOrder()
        }
        persistQueue(c, c.currentPosition, c.isPlaying)
    }

    /** Stops playback and empties the queue (the "clear queue" action). */
    fun clearQueue() = withController { c ->
        c.stop()
        c.clearMediaItems()
        visibleOrder = emptyList()
        naturalOrder = emptyList()
        savedShuffleOrder = null
        scope.launch { runCatching { settings.setShuffleOrder("") } }
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
                // Without this the system/custom notification shows 0:00 and an empty bar.
                .setDurationMs(duration)
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
        private const val LEGACY_KEY_SHUFFLE_ORDER = "shuffle_order"
        private const val KEY_WIDGET_TITLE = "widget_title"
        private const val KEY_WIDGET_ARTIST = "widget_artist"
        private const val KEY_WIDGET_PLAYING = "widget_playing"
        const val MODE_NORMAL = 0
        const val MODE_REPEAT_ALL = 1
        const val MODE_REPEAT_ONE = 2
        const val MODE_SHUFFLE = 3
    }
}
