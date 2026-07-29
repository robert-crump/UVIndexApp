package com.uvindex.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color.parseColor
import android.util.Log
import android.widget.RemoteViews
import com.uvindex.app.R
import com.uvindex.app.data.repository.WeatherRepository
import com.uvindex.app.ui.theme.UVColorHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UVWidgetCurrent : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.uvindex.app.widget.ACTION_REFRESH_CURRENT"
        private const val TAG = "UVWidgetCurrent"

        private fun backgroundResFor(uvIndex: Int): Int = when {
            uvIndex <= 2 -> R.drawable.widget_bg_low
            uvIndex <= 5 -> R.drawable.widget_bg_moderate
            uvIndex <= 7 -> R.drawable.widget_bg_high
            else -> R.drawable.widget_bg_very_high
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called with ${appWidgetIds.size} widgets")
        for (appWidgetId in appWidgetIds) {
            Log.d(TAG, "Updating widget ID: $appWidgetId")
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        Log.d(TAG, "onReceive called with action: ${intent.action}")

        if (intent.action == ACTION_REFRESH || intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, UVWidgetCurrent::class.java)
            )

            Log.d(TAG, "Manual refresh/update for ${appWidgetIds.size} widgets")

            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.uv_widget_current)

        // Intent to open the app
        val appIntent = Intent(context, com.uvindex.app.MainActivity::class.java)
        val appPendingIntent = PendingIntent.getActivity(
            context,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Set click listener for the entire widget
        views.setOnClickPendingIntent(R.id.widget_current_container, appPendingIntent)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val repository = WeatherRepository(context)
                val result = withContext(Dispatchers.IO) {
                    // Widgets use cache only (no location access needed)
                    // WidgetUpdateWorker has already cached fresh data
                    repository.getCachedForecastForWidget()
                }

                result.fold(
                    onSuccess = { forecast ->
                        val currentUV = forecast.currentHour.uvIndex.toInt()
                        val currentHourValue = forecast.currentHour.hour

                        Log.d(TAG, "Current UV updated: $currentUV at hour: $currentHourValue")

                        val fgColor = UVColorHelper.getColorInt(
                            currentUV.toDouble(), context, UVColorHelper.ColorType.FOREGROUND
                        )

                        views.setTextViewText(R.id.widget_current_uv_value, currentUV.toString())
                        views.setTextViewText(R.id.widget_current_time, String.format("%02d:00", currentHourValue))
                        views.setTextColor(R.id.widget_current_uv_value, fgColor)
                        views.setTextColor(R.id.widget_current_time, fgColor)
                        views.setInt(R.id.widget_current_container, "setBackgroundResource", backgroundResFor(currentUV))

                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Update failed: ${error.message}")
                        Log.e(TAG, "Error details: ", error)
                        views.setTextViewText(R.id.widget_current_uv_value, "-")
                        views.setTextViewText(R.id.widget_current_time, "--:--")
                        views.setTextColor(R.id.widget_current_uv_value, parseColor("#999999"))
                        views.setTextColor(R.id.widget_current_time, parseColor("#999999"))
                        views.setInt(R.id.widget_current_container, "setBackgroundResource", R.drawable.widget_bg_error)
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception during update", e)
                views.setTextViewText(R.id.widget_current_uv_value, "-")
                views.setTextColor(R.id.widget_current_uv_value, parseColor("#999999"))
                views.setInt(R.id.widget_current_container, "setBackgroundResource", R.drawable.widget_bg_error)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
