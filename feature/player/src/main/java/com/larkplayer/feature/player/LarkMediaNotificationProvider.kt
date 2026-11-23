package com.larkplayer.feature.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.larkplayer.core.designsystem.R as DesignR
import java.util.Locale

/**
 * Flat, dark media notification that mirrors the Lark Player shade card:
 * app row, circular artwork, title/artist, progress with times and the five
 * transport buttons (prev / play / next / favourite / close).
 *
 * The audio is still driven by Media3; this only replaces the visual layout.
 */
class LarkMediaNotificationProvider(
    private val context: Context,
) : MediaNotification.Provider {

    @Volatile private var cachedSession: MediaSession? = null
    @Volatile private var cachedFactory: MediaNotification.ActionFactory? = null
    @Volatile private var cachedLayout: ImmutableList<CommandButton> = ImmutableList.of()
    @Volatile private var callback: MediaNotification.Provider.Callback? = null

    /** Re-posts the notification once a second so the progress bar really moves. */
    fun tick() {
        val session = cachedSession ?: return
        val factory = cachedFactory ?: return
        val cb = callback ?: return
        runCatching { cb.onNotificationChanged(createNotification(session, cachedLayout, factory, cb)) }
    }

    override fun createNotification(
        session: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        callback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        cachedSession = session
        cachedFactory = actionFactory
        cachedLayout = customLayout
        this.callback = callback

        val player = session.player
        val metadata: MediaMetadata = player.mediaMetadata ?: MediaMetadata.EMPTY
        val title = metadata.title?.toString()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.notif_unknown_title)
        val artist = metadata.artist?.toString().orEmpty()
        val duration = (metadata.durationMs ?: 0L).takeIf { it > 0 } ?: 0L
        val position = if (duration > 0) player.currentPosition.coerceIn(0L, duration) else 0L
        val isPlaying = player.isPlaying && player.playbackState != Player.STATE_ENDED
        val songId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L

        val views = RemoteViews(context.packageName, R.layout.notification_lark)
        views.setTextViewText(R.id.notif_title, title)
        views.setTextViewText(R.id.notif_artist, artist)
        views.setTextViewText(R.id.notif_elapsed, formatTime(position))
        views.setTextViewText(R.id.notif_duration, formatTime(duration))
        views.setProgressBar(
            R.id.notif_progress,
            1000,
            if (duration > 0) (position * 1000 / duration).toInt().coerceIn(0, 1000) else 0,
            false,
        )
        views.setImageViewBitmap(R.id.notif_app_icon, appIconBitmap())
        loadArtwork(metadata)?.let { views.setImageViewBitmap(R.id.notif_art, circularBitmap(it, 128)) }
        views.setImageViewResource(
            R.id.notif_play,
            if (isPlaying) DesignR.drawable.ic_lark_pause else DesignR.drawable.ic_lark_play,
        )

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else PendingIntent.FLAG_UPDATE_CURRENT

        views.setOnClickPendingIntent(
            R.id.notif_play,
            actionFactory.createMediaActionPendingIntent(session, Player.COMMAND_PLAY_PAUSE.toLong()),
        )
        views.setOnClickPendingIntent(
            R.id.notif_prev,
            actionFactory.createMediaActionPendingIntent(session, Player.COMMAND_SEEK_TO_PREVIOUS.toLong()),
        )
        views.setOnClickPendingIntent(
            R.id.notif_next,
            actionFactory.createMediaActionPendingIntent(session, Player.COMMAND_SEEK_TO_NEXT.toLong()),
        )
        views.setOnClickPendingIntent(R.id.notif_favorite, serviceIntent(PlaybackService.ACTION_FAVORITE, songId, flags))
        views.setOnClickPendingIntent(R.id.notif_close, serviceIntent(PlaybackService.ACTION_CLOSE_NOTIFICATION, -1L, flags))
        views.setOnClickPendingIntent(R.id.notif_art, contentIntent(flags))
        views.setOnClickPendingIntent(R.id.notif_title, contentIntent(flags))

        val notification = NotificationCompat.Builder(context, PlaybackService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lark)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(contentIntent(flags))
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionCompatToken)
                    .setShowActionsInCompactView(),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .build()
        return MediaNotification(PlaybackService.NOTIFICATION_ID, notification)
    }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean = false

    private fun serviceIntent(action: String, songId: Long, flags: Int): PendingIntent {
        val intent = Intent(context, PlaybackService::class.java).setAction(action)
        if (songId > 0) intent.putExtra(PlaybackService.EXTRA_SONG_ID, songId)
        return PendingIntent.getService(context, action.hashCode(), intent, flags)
    }

    private fun contentIntent(flags: Int): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return PendingIntent.getActivity(context, 0, launch, flags)
    }

    private fun loadArtwork(metadata: MediaMetadata): Bitmap? {
        metadata.artworkData?.let { data ->
            runCatching { BitmapFactory.decodeByteArray(data, 0, data.size) }.getOrNull()?.let { return it }
        }
        val uri = metadata.artworkUri ?: return null
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    private fun appIconBitmap(): Bitmap {
        val size = 48
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }

    private fun circularBitmap(source: Bitmap, size: Int): Bitmap {
        val side = minOf(source.width, source.height)
        val x = (source.width - side) / 2
        val y = (source.height - side) / 2
        val square = Bitmap.createBitmap(source, x, y, side, side)
        val scaled = Bitmap.createScaledBitmap(square, size, size, true)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        paint.xfermode = null
        return out
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return "0:00"
        val total = ms / 1000
        return String.format(Locale.getDefault(), "%d:%02d", total / 60, total % 60)
    }
}
