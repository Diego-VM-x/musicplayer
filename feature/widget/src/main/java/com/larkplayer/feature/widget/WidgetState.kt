package com.larkplayer.feature.widget

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import com.larkplayer.core.datastore.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray

/**
 * Shared state/rendering logic for both home-screen widgets.
 *
 * The app process publishes now-playing data to `lark_widget` (title, artist,
 * play state) and to `lark_playback_state` (queue JSON with album id, duration
 * and position). The widget only reads those files, so it keeps working even
 * when the UI has been killed.
 */
internal object WidgetState {

    const val ACTION_UPDATE = "com.larkplayer.action.UPDATE_WIDGET"
    const val PLAYER_EXTRA = "lark_open_player"

    private const val WIDGET_PREFS = "lark_widget"
    private const val PLAYBACK_PREFS = "lark_playback_state"
    private const val META_PREFS = "lark_widget_meta"
    private const val SERVICE_CLASS = "com.larkplayer.feature.player.PlaybackService"

    private const val KEY_TITLE = "widget_title"
    private const val KEY_ARTIST = "widget_artist"
    private const val KEY_PLAYING = "widget_playing"

    private val DEFAULT_ACCENT = 0xFF0A84FF.toInt()
    private val SECONDARY_TEXT = 0xFFC9C9D5.toInt()

    @Volatile
    private var accentCache: Int? = null

    data class State(
        val title: String,
        val artist: String,
        val playing: Boolean,
        val albumId: Long,
        val songId: Long,
        val positionMs: Long,
        val durationMs: Long,
        val progress: Int,
    )

    /** Reads the published playback state and computes an extrapolated progress. */
    fun readState(context: Context): State {
        val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        val title = prefs.getString(KEY_TITLE, "Lark Player").orEmpty()
            .ifBlank { "Lark Player" }
        val artist = prefs.getString(KEY_ARTIST, "Toca para reproducir").orEmpty()
            .ifBlank { "Toca para reproducir" }
        val playing = prefs.getBoolean(KEY_PLAYING, false)

        var albumId = -1L
        var songId = -1L
        var position = 0L
        var duration = 0L
        runCatching {
            val state = context.getSharedPreferences(PLAYBACK_PREFS, Context.MODE_PRIVATE)
            position = state.getLong("position", 0L)
            val queue = state.getString("queue", null)
            if (!queue.isNullOrBlank()) {
                val array = JSONArray(queue)
                if (array.length() > 0) {
                    val index = state.getInt("index", 0).coerceIn(0, array.length() - 1)
                    val item = array.getJSONObject(index)
                    songId = item.optLong("id", -1L)
                    albumId = item.optLong("albumId", -1L)
                    duration = item.optLong("duration", 0L)
                }
            }
        }

        // A broadcast fires on track / play-state changes, not every second.
        // Remember when the last change arrived so the thin progress bar can
        // move smoothly until the next update.
        val meta = context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
        val now = SystemClock.elapsedRealtime()
        val unchanged = meta.getString("last_title", null) == title &&
            meta.getBoolean("last_playing", false) == playing
        val stamp = if (unchanged) meta.getLong("stamp", now) else now
        val base = if (unchanged) meta.getLong("base_position", position) else position
        if (!unchanged) {
            meta.edit()
                .putString("last_title", title)
                .putBoolean("last_playing", playing)
                .putLong("stamp", stamp)
                .putLong("base_position", base)
                .apply()
        }

        val elapsed = if (playing) (now - stamp).coerceAtLeast(0L) else 0L
        val current = base + elapsed
        val bounded = if (duration > 0) current.coerceAtMost(duration) else current
        val progress = if (duration > 0) {
            (bounded * 1000 / duration).toInt().coerceIn(0, 1000)
        } else 0

        return State(
            title = title,
            artist = artist,
            playing = playing,
            albumId = albumId,
            songId = songId,
            positionMs = bounded,
            durationMs = duration,
            progress = progress,
        )
    }

    /** User-selected accent colour, cached after the first DataStore read. */
    fun accent(context: Context): Int {
        accentCache?.let { return it }
        val color = runCatching {
            runBlocking { SettingsDataStore(context.applicationContext).accentColor.first() }.toInt()
        }.getOrDefault(DEFAULT_ACCENT)
        accentCache = color
        return color
    }

    /** Fills any of the widget layouts; all of them declare the same view ids. */
    fun buildViews(
        context: Context,
        layoutRes: Int,
        state: State,
        accent: Int,
        requestCodeOffset: Int,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layoutRes)
        views.setTextViewText(R.id.widget_title, state.title)
        views.setTextViewText(R.id.widget_artist, state.artist)

        views.setImageViewResource(
            R.id.widget_play,
            if (state.playing) R.drawable.widget_ic_pause else R.drawable.widget_ic_play,
        )
        views.setColorStateList(
            R.id.widget_play,
            "setImageTintList",
            ColorStateList.valueOf(accent),
        )
        listOf(R.id.widget_prev, R.id.widget_next).forEach { id ->
            views.setImageViewResource(
                id,
                if (id == R.id.widget_prev) R.drawable.widget_ic_prev else R.drawable.widget_ic_next,
            )
            views.setColorStateList(id, "setImageTintList", ColorStateList.valueOf(SECONDARY_TEXT))
        }

        val artwork = loadArtwork(context, state.albumId, state.songId)
        if (artwork != null) {
            views.setImageViewBitmap(R.id.widget_art, artwork)
        } else {
            views.setImageViewResource(R.id.widget_art, R.drawable.widget_ic_music)
            views.setColorStateList(
                R.id.widget_art,
                "setImageTintList",
                ColorStateList.valueOf(accent),
            )
        }

        views.setProgressBar(R.id.widget_progress, 1000, state.progress, false)
        views.setColorStateList(
            R.id.widget_progress,
            "setProgressTintList",
            ColorStateList.valueOf(accent),
        )
        views.setInt(R.id.widget_accent, "setBackgroundColor", accent)

        views.setOnClickPendingIntent(
            R.id.widget_prev,
            serviceIntent(context, "com.larkplayer.action.PREV", requestCodeOffset + 1),
        )
        views.setOnClickPendingIntent(
            R.id.widget_play,
            serviceIntent(
                context,
                if (state.playing) "com.larkplayer.action.PAUSE" else "com.larkplayer.action.PLAY",
                requestCodeOffset + 2,
            ),
        )
        views.setOnClickPendingIntent(
            R.id.widget_next,
            serviceIntent(context, "com.larkplayer.action.NEXT", requestCodeOffset + 3),
        )

        val openPlayer = openPlayerIntent(context, requestCodeOffset + 4)
        listOf(R.id.widget_root, R.id.widget_art, R.id.widget_title, R.id.widget_artist).forEach { id ->
            views.setOnClickPendingIntent(id, openPlayer)
        }
        return views
    }

    /** Asks all widget providers to re-read their state. */
    fun refresh(context: Context) {
        runCatching {
            context.sendBroadcast(Intent(ACTION_UPDATE).setPackage(context.packageName))
        }
    }

    private fun flags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else PendingIntent.FLAG_UPDATE_CURRENT

    /**
     * Explicit component: the service only declares media intents in its
     * manifest, so an implicit action-only intent would never resolve.
     */
    private fun serviceIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(action)
            .setComponent(ComponentName(context.packageName, SERVICE_CLASS))
        return PendingIntent.getService(context, requestCode, intent, flags())
    }

    private fun openPlayerIntent(context: Context, requestCode: Int): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(context.packageName)
        launch.putExtra(PLAYER_EXTRA, true)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, requestCode, launch, flags())
    }

    /** Album art from `content://media/external/audio/albumart/<albumId>`. */
    private fun loadArtwork(context: Context, albumId: Long, songId: Long): Bitmap? {
        var artId = albumId
        if (artId <= 0 && songId > 0) {
            runCatching {
                val media = Uri.withAppendedPath(
                    Uri.parse("content://media/external/audio/media/"),
                    songId.toString(),
                )
                context.contentResolver.query(
                    media,
                    arrayOf("album_id"),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val column = cursor.getColumnIndex("album_id")
                        if (column >= 0) artId = cursor.getLong(column)
                    }
                }
            }
        }
        if (artId <= 0) return null

        val uri = Uri.parse("content://media/external/audio/albumart/$artId")
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: return@runCatching null

            val side = minOf(bounds.outWidth, bounds.outHeight)
            if (side <= 0) return@runCatching null
            val options = BitmapFactory.Options().apply {
                inSampleSize = (side / 128).coerceAtLeast(1)
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@runCatching null
            rounded(decoded, 128)
        }.getOrNull()
    }

    private fun rounded(source: Bitmap, size: Int): Bitmap {
        val side = minOf(source.width, source.height)
        val x = (source.width - side) / 2
        val y = (source.height - side) / 2
        val square = Bitmap.createBitmap(source, x, y, side, side)
        val scaled = Bitmap.createScaledBitmap(square, size, size, true)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), size / 5f, size / 5f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        paint.xfermode = null
        return out
    }
}
