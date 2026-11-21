package com.larkplayer.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews

/** Home-screen widget with now-playing metadata and transport controls. */
class LarkWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
        val prefs = context.getSharedPreferences("lark_widget", Context.MODE_PRIVATE)
        val title = prefs.getString(KEY_TITLE, "Lark Player").orEmpty().ifBlank { "Lark Player" }
        val artist = prefs.getString(KEY_ARTIST, "Toca para reproducir").orEmpty()
            .ifBlank { "Toca para reproducir" }
        val playing = prefs.getBoolean(KEY_PLAYING, false)

        val views = RemoteViews(context.packageName, R.layout.widget_lark).apply {
            setTextViewText(R.id.widget_title, title)
            setTextViewText(R.id.widget_artist, artist)
            setImageViewResource(
                R.id.widget_play,
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            )
            setOnClickPendingIntent(R.id.widget_prev, servicePendingIntent(context, ACTION_PREV))
            setOnClickPendingIntent(
                R.id.widget_play,
                servicePendingIntent(context, if (playing) ACTION_PAUSE else ACTION_PLAY),
            )
            setOnClickPendingIntent(R.id.widget_next, servicePendingIntent(context, ACTION_NEXT))
        }
        manager.updateAppWidget(id, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, LarkWidgetProvider::class.java),
            )
            onUpdate(context, manager, ids)
        }
    }

    companion object {
        private const val KEY_TITLE = "widget_title"
        private const val KEY_ARTIST = "widget_artist"
        private const val KEY_PLAYING = "widget_playing"
        private const val ACTION_UPDATE = "com.larkplayer.action.UPDATE_WIDGET"
        private const val ACTION_PLAY = "com.larkplayer.action.PLAY"
        private const val ACTION_PAUSE = "com.larkplayer.action.PAUSE"
        private const val ACTION_NEXT = "com.larkplayer.action.NEXT"
        private const val ACTION_PREV = "com.larkplayer.action.PREV"

        private fun servicePendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(action).setPackage(context.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            return PendingIntent.getService(context, action.hashCode(), intent, flags)
        }

        fun refresh(context: Context) {
            runCatching {
                context.sendBroadcast(Intent(ACTION_UPDATE).setPackage(context.packageName))
            }
        }
    }
}
