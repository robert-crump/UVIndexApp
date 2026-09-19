package com.uvindex.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/** 1x1 self-protection-time widget. Thin shell: [WidgetHost] loads the cache and applies the binding. */
class SelfProtectionTimeWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        WidgetHost.updateFromReceiver(context, WidgetKind.SelfProtection, goAsync())
    }
}
