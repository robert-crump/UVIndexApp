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

class UVWidgetMax : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.uvindex.app.widget.ACTION_REFRESH_MAX"
        private const val TAG = "UVWidgetMax"
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
                ComponentName(context, UVWidgetMax::class.java)
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
        val views = RemoteViews(context.packageName, R.layout.uv_widget_max)

        // Intent to open the app
        val appIntent = Intent(context, com.uvindex.app.MainActivity::class.java)
        val appPendingIntent = PendingIntent.getActivity(
            context,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Set click listener for the entire widget
        views.setOnClickPendingIntent(R.id.widget_max_container, appPendingIntent)

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
                        Log.d(TAG, "Forecast received successfully")
                        Log.d(TAG, "dailyMaxRemaining: ${forecast.dailyMaxRemaining}")
                        Log.d(TAG, "maxHourToday: ${forecast.maxHourToday}")

                        val maxUV = forecast.dailyMaxRemaining.toInt()
                        val maxHour = forecast.maxHourToday

                        Log.d(TAG, "Max UV updated: $maxUV at hour: $maxHour")

                        views.setTextViewText(R.id.widget_max_uv_value, maxUV.toString())
                        views.setTextViewText(R.id.widget_max_label, String.format("%02d:00", maxHour))
                        views.setTextColor(R.id.widget_max_uv_value, UVColorHelper.getWidgetUVColor(maxUV))

                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Update failed: ${error.message}")
                        Log.e(TAG, "Error details: ", error)
                        views.setTextViewText(R.id.widget_max_uv_value, "-")
                        views.setTextViewText(R.id.widget_max_label, "--:--")
                        views.setTextColor(R.id.widget_max_uv_value, parseColor("#999999"))
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception during update", e)
                views.setTextViewText(R.id.widget_max_uv_value, "-")
                views.setTextColor(R.id.widget_max_uv_value, parseColor("#999999"))
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}