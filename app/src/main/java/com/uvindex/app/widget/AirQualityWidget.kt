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
import com.uvindex.app.data.model.AirQualityLevel
import com.uvindex.app.data.model.getAirQualityLevel
import com.uvindex.app.data.repository.WeatherRepository
import com.uvindex.app.ui.theme.AQIColorHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AirQualityWidget : AppWidgetProvider() {

    companion object {
        private const val TAG = "AirQualityWidget"
        private val FAILURE_COLOR = parseColor("#999999")

        private fun backgroundResFor(aqi: Double): Int = when (getAirQualityLevel(aqi)) {
            AirQualityLevel.GOOD -> R.drawable.widget_bg_aqi_good
            AirQualityLevel.FAIR -> R.drawable.widget_bg_aqi_fair
            AirQualityLevel.MODERATE -> R.drawable.widget_bg_aqi_moderate
            AirQualityLevel.POOR -> R.drawable.widget_bg_aqi_poor
            AirQualityLevel.VERY_POOR -> R.drawable.widget_bg_aqi_very_poor
            AirQualityLevel.EXTREMELY_POOR -> R.drawable.widget_bg_aqi_extremely_poor
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, AirQualityWidget::class.java)
            )

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
        val views = RemoteViews(context.packageName, R.layout.air_quality_widget)

        val appIntent = Intent(context, com.uvindex.app.MainActivity::class.java)
        val appPendingIntent = PendingIntent.getActivity(
            context,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_air_quality_container, appPendingIntent)

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
                        val aqi = forecast.airQuality

                        if (aqi != null) {
                            views.setTextViewText(R.id.widget_air_quality_value, aqi.toInt().toString())
                            views.setTextColor(
                                R.id.widget_air_quality_value,
                                AQIColorHelper.getColorInt(aqi, context, AQIColorHelper.ColorType.FOREGROUND)
                            )
                            views.setInt(
                                R.id.widget_air_quality_container,
                                "setBackgroundResource",
                                backgroundResFor(aqi)
                            )
                        } else {
                            views.setTextViewText(R.id.widget_air_quality_value, "–")
                            views.setTextColor(R.id.widget_air_quality_value, FAILURE_COLOR)
                            views.setInt(
                                R.id.widget_air_quality_container,
                                "setBackgroundResource",
                                R.drawable.widget_bg_aqi_error
                            )
                        }

                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Update failed: ${error.message}", error)
                        views.setTextViewText(R.id.widget_air_quality_value, "–")
                        views.setTextColor(R.id.widget_air_quality_value, FAILURE_COLOR)
                        views.setInt(
                            R.id.widget_air_quality_container,
                            "setBackgroundResource",
                            R.drawable.widget_bg_aqi_error
                        )
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception during update", e)
                views.setTextViewText(R.id.widget_air_quality_value, "–")
                views.setTextColor(R.id.widget_air_quality_value, FAILURE_COLOR)
                views.setInt(
                    R.id.widget_air_quality_container,
                    "setBackgroundResource",
                    R.drawable.widget_bg_aqi_error
                )
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
