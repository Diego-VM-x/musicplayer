package com.larkplayer.feature.player

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.larkplayer.core.datastore.SettingsDataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Foreground playback service. MediaSessionService creates the media
 * notification, handles media buttons and the lock-screen session. A partial
 * wake lock keeps audio alive when the screen is off / the app is in background.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var player: ExoPlayer
    @Inject lateinit var settings: SettingsDataStore
    @Inject lateinit var repository: PlayerRepository

    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var notificationProvider: LarkMediaNotificationProvider

    /** Mirrors the "Notificación de reproducción" preference. */
    @Volatile private var showNotification = true

    /** Mirrors "Reproducir en segundo plano": keeps the foreground service alive. */
    @Volatile private var backgroundPlayback = true

    /** Set by the notification X: suppress the notification until playback resumes. */
    @Volatile private var shuttingDown = false

    private var wakeLock: PowerManager.WakeLock? = null

    // ---- Real crossfade: two players mixed by the system audio mixer --------
    private var audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()
    @Volatile private var crossfadeEnabled = false
    @Volatile private var crossfadeMs = 4_000L
    private var mixPlayer: ExoPlayer? = null
    private var crossfadeAnimator: ValueAnimator? = null
    private var crossfadeInProgress = false
    private var crossfadeCancelled = false
    private var lastCrossfadeId: String? = null
    private val fadeHandler = Handler(Looper.getMainLooper())
    private val fadeRunnable = object : Runnable {
        override fun run() {
            maybeStartCrossfade()
            fadeHandler.postDelayed(this, 200L)
        }
    }

    private val playerListener = object : Player.Listener {
        // The wake lock is owned by the "background playback" setting, not by
        // the play/pause state: pausing and closing the app must not let the
        // system freeze the process and lose the paused session.
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                shuttingDown = false
                // The X button released the wake lock; a new playback session
                // must reacquire it or background playback silently freezes.
                updateWakeLock()
                return
            }
            // pauseAtEndOfMediaItems pauses the outgoing track right when the
            // mix ends. That internal pause must not abort the crossfade; only
            // a real user pause (well before the end) should cancel it.
            val atEndOfTrack = runCatching {
                player.duration > 0L && player.currentPosition >= player.duration - 800L
            }.getOrDefault(false)
            if (!crossfadeInProgress || !atEndOfTrack) cancelCrossfade()
        }
    }

    override fun onCreate() {
        super.onCreate()
        // The mixed sequence is applied by rewriting the media items in
        // PlayerRepository. Media3's own shuffle engine must stay off or it
        // would pick its own random "next" instead of the visible queue row.
        player.shuffleModeEnabled = false
        // Same channel id/semantics as the original app: a silent, non-badged
        // media channel keeps the foreground service alive reliably.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val manager = getSystemService(NotificationManager::class.java)
                if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                    manager.createNotificationChannel(
                        NotificationChannel(
                            CHANNEL_ID,
                            "Reproducción",
                            NotificationManager.IMPORTANCE_DEFAULT,
                        ).apply {
                            setSound(null, null)
                            setShowBadge(false)
                            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                        },
                    )
                }
            }
        }
        // Custom Lark shade card. It validates its RemoteViews in-process and
        // falls back to Media3's layout if the device cannot render it.
        notificationProvider = LarkMediaNotificationProvider(context = this)
        runCatching { setMediaNotificationProvider(notificationProvider) }.onFailure {
            setMediaNotificationProvider(
                DefaultMediaNotificationProvider.Builder(this)
                    .setChannelId(CHANNEL_ID)
                    .setNotificationId(NOTIFICATION_ID)
                    .build(),
            )
        }
        scope.launch {
            while (isActive) {
                delay(1_000L)
                if (player.isPlaying) notificationProvider.tick()
            }
        }
        val closeCommand = SessionCommand(COMMAND_STOP_ALL, Bundle.EMPTY)
        session = MediaSession.Builder(this, player)
            .setId("lark_player_session")
            .setCustomLayout(
                listOf(
                    CommandButton.Builder()
                        .setDisplayName("Cerrar reproducción")
                        .setIconResId(android.R.drawable.ic_menu_close_clear_cancel)
                        .setSessionCommand(closeCommand)
                        .build(),
                ),
            )
            .setCallback(object : MediaSession.Callback {
                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        COMMAND_STOP_ALL -> stopPlaybackCompletely()
                        COMMAND_GET_AUDIO_SESSION -> {
                            val extras = Bundle().apply { putInt(EXTRA_AUDIO_SESSION_ID, player.audioSessionId) }
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, extras))
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()
        player.addListener(playerListener)
        updateWakeLock()

        // "Reproduce junto con otras aplicaciones" — when enabled we don't take
        // exclusive audio focus, so other apps keep playing.
        scope.launch {
            settings.playWithOthers.collect { together ->
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build()
                player.setAudioAttributes(audioAttributes, /* handleAudioFocus = */ !together)
            }
        }
        scope.launch {
            settings.crossfadeEnabled.collect { enabled ->
                if (!enabled) cancelCrossfade()
                crossfadeEnabled = enabled
                fadeHandler.removeCallbacks(fadeRunnable)
                if (enabled) fadeHandler.post(fadeRunnable)
            }
        }
        scope.launch {
            settings.crossfadeSeconds.collect { seconds -> crossfadeMs = seconds * 1_000L }
        }
        scope.launch {
            settings.notificationPlayback.collect { enabled -> showNotification = enabled }
        }
        scope.launch {
            settings.backgroundPlayback.collect { enabled ->
                backgroundPlayback = enabled
                updateWakeLock()
            }
        }
        Timber.d("PlaybackService created")
    }

    // ---- Crossfade ----------------------------------------------------------

    /**
     * Starts the incoming track in a second ExoPlayer while the outgoing one is
     * still playing. The system audio mixer blends both streams, so the songs
     * really overlap instead of a sequential fade-out / fade-in.
     */
    private fun maybeStartCrossfade() {
        if (!crossfadeEnabled || crossfadeInProgress || !player.isPlaying) return
        val duration = player.duration
        val remaining = duration - player.currentPosition
        if (duration <= 0L || remaining > crossfadeMs || remaining < 120L) return
        if (player.repeatMode == Player.REPEAT_MODE_ONE) return

        val nextIndex = when {
            player.hasNextMediaItem() -> player.nextMediaItemIndex
            player.repeatMode == Player.REPEAT_MODE_ALL -> 0
            else -> return
        }
        val nextItem = runCatching { player.getMediaItemAt(nextIndex) }.getOrNull() ?: return
        val currentId = player.currentMediaItem?.mediaId
        if (currentId != null && currentId == lastCrossfadeId) return
        lastCrossfadeId = currentId
        // Fade only over the time that is actually left. Using the full setting
        // would start the incoming track early but land 4s into it on handover.
        val fadeDuration = remaining.coerceIn(400L, crossfadeMs)

        crossfadeInProgress = true
        crossfadeCancelled = false
        // Keep the primary player from auto-advancing while both are audible.
        @Suppress("DEPRECATION")
        player.pauseAtEndOfMediaItems = true

        val incoming = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ false)
            .build()
            .also { mix ->
                mix.setMediaItems(listOf(nextItem))
                mix.playbackParameters = player.playbackParameters
                mix.volume = 0f
                mix.prepare()
                mix.play()
            }
        mixPlayer = incoming

        val targetIndex = nextIndex
        crossfadeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = fadeDuration
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                incoming.volume = value
                player.volume = 1f - value
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (crossfadeCancelled) {
                        crossfadeCancelled = false
                        return
                    }
                    finishCrossfade(targetIndex, fadeDuration)
                }
            })
            start()
        }
    }

    /** Hands audio back to the primary player at the point the mix reached. */
    private fun finishCrossfade(targetIndex: Int, offsetMs: Long) {
        mixPlayer?.let { mix ->
            runCatching { mix.stop() }
            runCatching { mix.release() }
        }
        mixPlayer = null
        crossfadeAnimator = null
        @Suppress("DEPRECATION")
        player.pauseAtEndOfMediaItems = false
        runCatching { player.seekTo(targetIndex, offsetMs) }
        player.volume = 1f
        player.play()
        crossfadeInProgress = false
    }

    private fun cancelCrossfade() {
        if (!crossfadeInProgress) return
        crossfadeCancelled = true
        crossfadeAnimator?.cancel()
        crossfadeAnimator = null
        mixPlayer?.let { mix ->
            runCatching { mix.stop() }
            runCatching { mix.release() }
        }
        mixPlayer = null
        @Suppress("DEPRECATION")
        player.pauseAtEndOfMediaItems = false
        player.volume = 1f
        crossfadeInProgress = false
    }

    /** Held while background playback is on, even when the track is paused. */
    private fun updateWakeLock() {
        if (!backgroundPlayback) {
            releaseWakeLock()
            return
        }
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(PowerManager::class.java) ?: return
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "larkplayer:playback")
            .apply { setReferenceCounted(false); acquire() }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * Android only guarantees background playback while this is a foreground
     * service, so keep it alive whenever background playback is allowed or a
     * track is actually playing — even if the media notification is hidden.
     */
    override fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean,
    ) {
        if (shuttingDown && !player.isPlaying) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            return
        }
        if (showNotification || backgroundPlayback || startInForegroundRequired || player.isPlaying) {
            super.onUpdateNotification(session, startInForegroundRequired)
        } else {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> player.play()
            ACTION_PAUSE -> player.pause()
            ACTION_NEXT -> { cancelCrossfade(); player.seekToNext() }
            ACTION_PREV -> { cancelCrossfade(); player.seekToPrevious() }
            ACTION_CLOSE_NOTIFICATION -> stopPlaybackCompletely()
            ACTION_FAVORITE -> {
                val id = intent.getLongExtra(EXTRA_SONG_ID, -1L)
                if (id > 0) scope.launch { runCatching { settings.toggleFavorite(id) } }
            }
        }
        val result = super.onStartCommand(intent, flags, startId)
        return if (backgroundPlayback && result == START_NOT_STICKY) START_STICKY else result
    }

    /** Swiping the app away from Recents must not kill a paused session. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!backgroundPlayback) super.onTaskRemoved(rootIntent)
    }

    /**
     * X button in the media notification: stops playback, clears the queue and
     * removes the notification. The process is left alive so an open UI is not
     * killed mid-use (the old `exitProcess(0)` closed the app from the shade).
     */
    private fun stopPlaybackCompletely() {
        cancelCrossfade()
        shuttingDown = true
        fadeHandler.removeCallbacks(fadeRunnable)
        runCatching { player.pause() }
        runCatching { player.stop() }
        runCatching { player.clearMediaItems() }
        // Clear the persisted queue too, so reopening the app does not restore
        // the list the user just closed from the notification.
        runCatching { repository.clearQueue() }
        releaseWakeLock()
        scope.launch {
            runCatching { settings.setShuffle(false) }
            runCatching { settings.setRepeatMode(Player.REPEAT_MODE_ALL) }
        }
        runCatching { stopForeground(Service.STOP_FOREGROUND_REMOVE) }
        runCatching { stopSelf() }
    }

    override fun onDestroy() {
        fadeHandler.removeCallbacks(fadeRunnable)
        cancelCrossfade()
        scope.cancel()
        runCatching { player.removeListener(playerListener) }
        releaseWakeLock()
        // The ExoPlayer instance is application-scoped: releasing it here would
        // leave the next MediaSession with a dead player and crash on playback.
        session?.release()
        session = null
        super.onDestroy()
        Timber.d("PlaybackService destroyed")
    }

    companion object {
        const val CHANNEL_ID = "player_service_v2"
        const val NOTIFICATION_ID = 3
        const val COMMAND_GET_AUDIO_SESSION = "com.larkplayer.command.GET_AUDIO_SESSION"
        const val EXTRA_AUDIO_SESSION_ID = "lark_audio_session_id"
        const val ACTION_PLAY = "com.larkplayer.action.PLAY"
        const val ACTION_PAUSE = "com.larkplayer.action.PAUSE"
        const val ACTION_NEXT = "com.larkplayer.action.NEXT"
        const val ACTION_PREV = "com.larkplayer.action.PREV"
        const val ACTION_CLOSE_NOTIFICATION = "com.larkplayer.action.CLOSE_NOTIFICATION"
        const val ACTION_FAVORITE = "com.larkplayer.action.FAVORITE"
        const val EXTRA_SONG_ID = "lark_song_id"
        const val COMMAND_STOP_ALL = "com.larkplayer.command.STOP_ALL"
    }
}
