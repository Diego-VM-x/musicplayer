package com.larkplayer.feature.player

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
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
import kotlinx.coroutines.launch
import kotlin.system.exitProcess
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

    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Mirrors the "Notificación de reproducción" preference. */
    @Volatile private var showNotification = true

    /** Mirrors "Reproducir en segundo plano": keeps the foreground service alive. */
    @Volatile private var backgroundPlayback = true

    private var wakeLock: PowerManager.WakeLock? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) acquireWakeLock() else releaseWakeLock()
        }
    }

    override fun onCreate() {
        super.onCreate()
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
                    if (customCommand.customAction == COMMAND_STOP_ALL) stopPlaybackCompletely()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()
        player.addListener(playerListener)
        if (player.isPlaying) acquireWakeLock()

        // "Reproduce junto con otras aplicaciones" — when enabled we don't take
        // exclusive audio focus, so other apps keep playing.
        scope.launch {
            settings.playWithOthers.collect { together ->
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    /* handleAudioFocus = */ !together,
                )
            }
        }
        scope.launch {
            settings.notificationPlayback.collect { enabled -> showNotification = enabled }
        }
        scope.launch {
            settings.backgroundPlayback.collect { enabled -> backgroundPlayback = enabled }
        }
        Timber.d("PlaybackService created")
    }

    private fun acquireWakeLock() {
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
            ACTION_NEXT -> player.seekToNext()
            ACTION_PREV -> player.seekToPrevious()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /** X button in the media notification: stop everything and kill the process. */
    private fun stopPlaybackCompletely() {
        runCatching { player.stop() }
        runCatching { player.clearMediaItems() }
        releaseWakeLock()
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        runCatching { session?.release() }
        session = null
        scope.cancel()
        stopSelf()
        exitProcess(0)
    }

    override fun onDestroy() {
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
        const val ACTION_PLAY = "com.larkplayer.action.PLAY"
        const val ACTION_PAUSE = "com.larkplayer.action.PAUSE"
        const val ACTION_NEXT = "com.larkplayer.action.NEXT"
        const val ACTION_PREV = "com.larkplayer.action.PREV"
        const val COMMAND_STOP_ALL = "com.larkplayer.command.STOP_ALL"
    }
}
