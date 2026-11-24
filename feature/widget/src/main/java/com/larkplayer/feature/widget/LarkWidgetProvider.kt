package com.larkplayer.feature.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Compact home-screen widget. Its layout is chosen from the size reported by
 * the launcher, so the same 2x2 widget looks right when resized in any grid.
 */
class LarkWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id -> updateOne(context, appWidgetManager, id) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        updateOne(context, appWidgetManager, appWidgetId)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    private fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
        // Never let one bad instance (or a corrupt preferences value) stop the
        // other widgets from being painted.
        val state = runCatching { WidgetState.readState(context) }.getOrNull()
        val accent = runCatching { WidgetState.accent(context) }
            .getOrDefault(WidgetState.DEFAULT_ACCENT)
        val views = runCatching {
            val options = runCatching { manager.getAppWidgetOptions(id) }.getOrNull()
            val width = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
            val height = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
            val layout = when {
                // A 2x2 that the user stretched horizontally gets the roomier
                // single-row arrangement instead of a tiny centred column.
                width >= 250 -> R.layout.widget_lark_wide
                width < 130 || height < 110 -> R.layout.widget_lark_compact
                else -> R.layout.widget_lark
            }
            WidgetState.buildViews(
                context = context,
                layoutRes = layout,
                state = state ?: WidgetState.readState(context),
                accent = accent,
                requestCodeOffset = 0,
            )
        }.getOrElse {
            WidgetState.fallbackViews(context, state?.title ?: "Lark Player")
        }
        runCatching { manager.updateAppWidget(id, views) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        runCatching { super.onReceive(context, intent) }
        if (intent.action == WidgetState.ACTION_UPDATE) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, LarkWidgetProvider::class.java))
                onUpdate(context, manager, ids)
            }
        }
    }

    companion object {
        fun refresh(context: Context) = WidgetState.refresh(context)
    }
}
