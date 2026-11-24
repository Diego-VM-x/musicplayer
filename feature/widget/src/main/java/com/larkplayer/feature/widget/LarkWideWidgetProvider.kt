package com.larkplayer.feature.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Horizontal widget sized for 2x3 / 2x4 launcher cells. The wider 2x4 variant
 * puts the transport buttons beside the artwork instead of underneath it.
 */
class LarkWideWidgetProvider : AppWidgetProvider() {

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
        val state = runCatching { WidgetState.readState(context) }.getOrNull()
        val accent = runCatching { WidgetState.accent(context) }
            .getOrDefault(WidgetState.DEFAULT_ACCENT)
        val views = runCatching {
            val options = runCatching { manager.getAppWidgetOptions(id) }.getOrNull()
            val width = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
            val layout = if (width >= 300) {
                R.layout.widget_lark_wide_large
            } else {
                R.layout.widget_lark_wide
            }
            WidgetState.buildViews(
                context = context,
                layoutRes = layout,
                state = state ?: WidgetState.readState(context),
                accent = accent,
                requestCodeOffset = 100,
            )
        }.getOrElse {
            WidgetState.fallbackViews(context, state?.title ?: "Lark Player", 100)
        }
        runCatching { manager.updateAppWidget(id, views) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        runCatching { super.onReceive(context, intent) }
        if (intent.action == WidgetState.ACTION_UPDATE) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, LarkWideWidgetProvider::class.java))
                onUpdate(context, manager, ids)
            }
        }
    }

    companion object {
        fun refresh(context: Context) = WidgetState.refresh(context)
    }
}
