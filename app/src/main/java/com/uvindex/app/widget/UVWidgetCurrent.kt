package com.uvindex.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/** 1x1 current-UV widget. Thin shell: [WidgetHost] loads the cache and applies [bindCurrentUv]. */
class UVWidgetCurrent : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        WidgetHost.updateCurrentUvFromReceiver(context, goAsync())
    }
}
