package com.uvindex.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

/** 4x1 hourly-UV widget. Thin shell: [WidgetHost] loads the cache and applies the binding. */
class UVWidget : AppWidgetProvider() {

    companion object {
        /** Refresh-button broadcast; the button is zero-size in uv_widget.xml but the wiring stays. */
        const val ACTION_REFRESH = "com.uvindex.app.widget.ACTION_REFRESH"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        WidgetHost.updateFromReceiver(context, WidgetKind.HourlyUv, goAsync())
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) WidgetHost.refreshNow(context)
    }
}
