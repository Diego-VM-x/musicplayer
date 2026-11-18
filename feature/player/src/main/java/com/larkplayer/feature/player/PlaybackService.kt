package com.larkplayer.feature.player

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.larkplayer.core.datastore.SettingsDataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Foreground playback service. MediaSessionService automatically creates the
 * media notification, handles media buttons and the lock-screen session —
 * replacing the original app's PlaybackService + MediaSessionHandler.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var player: ExoPlayer
    @Inject lateinit var settings: SettingsDataStore

    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Mirrors the "Notificación de reproducción" preference. */
    @Volatile private var showNotification = true

    override fun onCreate() {
        super.onCreate()
        session = MediaSession.Builder(this, player)
            .setId("lark_player_session")
            .build()

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
            settings.notificationPlayback.collect { enabled ->
                showNotification = enabled
            }
        }
        Timber.d("PlaybackService created")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * "Notificación de reproducción" off → remove the media notification whenever
     * the service is not required to stay in the foreground.
     */
    override fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean,
    ) {
        if (!showNotification && !startInForegroundRequired) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_PLAY -> player.play()
                ACTION_PAUSE -> player.pause()
                ACTION_NEXT -> player.seekToNext()
                ACTION_PREV -> player.seekToPrevious()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        scope.cancel()
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
    }
}
