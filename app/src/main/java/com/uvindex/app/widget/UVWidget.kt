package com.uvindex.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color.parseColor
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.uvindex.app.R
import com.uvindex.app.data.repository.WeatherRepository
import com.uvindex.app.ui.theme.UVColorHelper
import com.uvindex.app.util.WidgetUpdateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UVWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.uvindex.app.widget.ACTION_REFRESH"
        private const val TAG = "UVWidget"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, showLoading = false)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_REFRESH) {
            Log.d(TAG, "Refresh button clicked - forcing data update")

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, UVWidget::class.java)
            )

            for (appWidgetId in appWidgetIds) {
                showLoadingState(context, appWidgetManager, appWidgetId)
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repository = WeatherRepository(context)
                    repository.getUVForecast(forceRefresh = true).fold(
                        onSuccess = {
                            Log.d(TAG, "Data refreshed successfully")
                            WidgetUpdateHelper.updateAllWidgets(context)
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Refresh failed: ${error.message}")
                            for (appWidgetId in appWidgetIds) {
                                hideLoadingState(context, appWidgetManager, appWidgetId)
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during refresh", e)
                    for (appWidgetId in appWidgetIds) {
                        hideLoadingState(context, appWidgetManager, appWidgetId)
                    }
                }
            }
        }
    }

    private fun showLoadingState(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.uv_widget)
        views.setViewVisibility(R.id.widget_refresh_button, View.GONE)
        views.setViewVisibility(R.id.widget_loading_indicator, View.VISIBLE)
        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
    }

    private fun hideLoadingState(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.uv_widget)
        views.setViewVisibility(R.id.widget_refresh_button, View.VISIBLE)
        views.setViewVisibility(R.id.widget_loading_indicator, View.GONE)
        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        showLoading: Boolean = false
    ) {
        val views = RemoteViews(context.packageName, R.layout.uv_widget)

        if (showLoading) {
            views.setViewVisibility(R.id.widget_refresh_button, View.GONE)
            views.setViewVisibility(R.id.widget_loading_indicator, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_refresh_button, View.VISIBLE)
            views.setViewVisibility(R.id.widget_loading_indicator, View.GONE)
        }

        val appIntent = Intent(context, com.uvindex.app.MainActivity::class.java)
        val appPendingIntent = PendingIntent.getActivity(
            context,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_container, appPendingIntent)

        val refreshIntent = Intent(context, UVWidget::class.java).apply {
            action = ACTION_REFRESH
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val repository = WeatherRepository(context)
                val result = withContext(Dispatchers.IO) {
                    repository.getCachedForecastForWidget()
                }

                result.fold(
                    onSuccess = { forecast ->
                        val locationText = if (!forecast.locationName.isNullOrEmpty()) {
                            "UV-Index (${forecast.locationName})"
                        } else {
                            "UV-Index"
                        }
                        views.setTextViewText(R.id.widget_location, locationText)

                        val currentIndex = forecast.allDayForecasts.indexOfFirst {
                            it.hour == forecast.currentHour.hour
                        }

                        val hours = mutableListOf<com.uvindex.app.data.model.HourlyForecast>()
                        if (currentIndex >= 0) {
                            hours.addAll(forecast.allDayForecasts.drop(currentIndex))
                        } else {
                            hours.add(forecast.currentHour)
                            hours.addAll(forecast.nextHours)
                        }

                        val displayHours = if (hours.size >= 4) hours else hours.toMutableList()

                        displayHours.forEachIndexed { index, hourForecast ->
                            if (index > 3) return@forEachIndexed

                            val uvId = when (index) {
                                0 -> R.id.widget_uv_hour_0
                                1 -> R.id.widget_uv_hour_1
                                2 -> R.id.widget_uv_hour_2
                                3 -> R.id.widget_uv_hour_3
                                else -> return@forEachIndexed
                            }
                            val timeId = when (index) {
                                0 -> R.id.widget_time_hour_0
                                1 -> R.id.widget_time_hour_1
                                2 -> R.id.widget_time_hour_2
                                3 -> R.id.widget_time_hour_3
                                else -> return@forEachIndexed
                            }

                            val uv = hourForecast.uvIndex.toInt()
                            views.setTextViewText(uvId, uv.toString())
                            views.setTextColor(uvId, UVColorHelper.getWidgetUVColor(uv))
                            views.setTextViewText(timeId, String.format("%02d:00", hourForecast.hour))
                        }

                        for (i in displayHours.size until 4) {
                            val uvId = when (i) {
                                0 -> R.id.widget_uv_hour_0
                                1 -> R.id.widget_uv_hour_1
                                2 -> R.id.widget_uv_hour_2
                                3 -> R.id.widget_uv_hour_3
                                else -> continue
                            }
                            val timeId = when (i) {
                                0 -> R.id.widget_time_hour_0
                                1 -> R.id.widget_time_hour_1
                                2 -> R.id.widget_time_hour_2
                                3 -> R.id.widget_time_hour_3
                                else -> continue
                            }

                            views.setTextViewText(uvId, "-")
                            views.setTextColor(uvId, parseColor("#666666"))
                            views.setTextViewText(timeId, "--:--")
                        }

                        views.setViewVisibility(R.id.widget_refresh_button, View.VISIBLE)
                        views.setViewVisibility(R.id.widget_loading_indicator, View.GONE)

                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Update failed: ${error.message}")
                        views.setViewVisibility(R.id.widget_refresh_button, View.VISIBLE)
                        views.setViewVisibility(R.id.widget_loading_indicator, View.GONE)
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception during update", e)
                views.setViewVisibility(R.id.widget_refresh_button, View.VISIBLE)
                views.setViewVisibility(R.id.widget_loading_indicator, View.GONE)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
