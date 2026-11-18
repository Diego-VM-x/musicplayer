package com.larkplayer.feature.player

import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
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

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        session = MediaSession.Builder(this, player)
            .setId("lark_player_session")
            .build()
        Timber.d("PlaybackService created")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

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
        session?.run {
            player.release()
            release()
        }
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
