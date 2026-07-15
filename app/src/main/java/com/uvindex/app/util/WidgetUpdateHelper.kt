package com.uvindex.app.util

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.uvindex.app.widget.AirQualityWidget
import com.uvindex.app.widget.SelfProtectionTimeWidget
import com.uvindex.app.widget.UVWidget
import com.uvindex.app.widget.UVWidgetMax
import com.uvindex.app.widget.WindWidget

/**
 * Utility object for widget updates.
 * Consolidates widget update logic in one place.
 */
object WidgetUpdateHelper {
    
    /**
     * Updates all app widgets.
     *
     * @param context Android Context
     */
    fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        
        // Update UV Widget (4x1)
        updateWidget(context, appWidgetManager, UVWidget::class.java)
        
        // Update Max UV Widget (1x1)
        updateWidget(context, appWidgetManager, UVWidgetMax::class.java)

        // Update Wind Widget (1x1)
        updateWidget(context, appWidgetManager, WindWidget::class.java)

        // Update Self-protection Time Widget (1x1)
        updateWidget(context, appWidgetManager, SelfProtectionTimeWidget::class.java)

        // Update Air Quality Widget (1x1)
        updateWidget(context, appWidgetManager, AirQualityWidget::class.java)
    }
    
    /**
     * Updates a specific widget.
     *
     * @param context Android Context
     * @param manager AppWidgetManager
     * @param widgetClass Class of the widget to update
     */
    private fun updateWidget(
        context: Context,
        manager: AppWidgetManager,
        widgetClass: Class<*>
    ) {
        val intent = Intent(context, widgetClass).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        }
        val ids = manager.getAppWidgetIds(ComponentName(context, widgetClass))
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        context.sendBroadcast(intent)
    }
}
