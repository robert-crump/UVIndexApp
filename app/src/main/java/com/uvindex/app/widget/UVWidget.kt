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
import androidx.core.content.ContextCompat
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
        // Kept for the ACTION_REFRESH broadcast/pending intent wiring below, which is no
        // longer exposed via a visible button in uv_widget.xml (widget_refresh_button is
        // present but zero-size) but still works if triggered programmatically.
        const val ACTION_REFRESH = "com.uvindex.app.widget.ACTION_REFRESH"
        private const val TAG = "UVWidget"

        private fun endBackgroundRes(uvIndex: Int, isLeftEnd: Boolean): Int = when {
            uvIndex <= 2 -> if (isLeftEnd) R.drawable.widget_row_bg_low_left else R.drawable.widget_row_bg_low_right
            uvIndex <= 5 -> if (isLeftEnd) R.drawable.widget_row_bg_moderate_left else R.drawable.widget_row_bg_moderate_right
            uvIndex <= 7 -> if (isLeftEnd) R.drawable.widget_row_bg_high_left else R.drawable.widget_row_bg_high_right
            else -> if (isLeftEnd) R.drawable.widget_row_bg_very_high_left else R.drawable.widget_row_bg_very_high_right
        }

        private val CONTAINER_IDS = intArrayOf(R.id.widget_cell_0, R.id.widget_cell_1, R.id.widget_cell_2, R.id.widget_cell_3)
        private val UV_TEXT_IDS = intArrayOf(R.id.widget_uv_hour_0, R.id.widget_uv_hour_1, R.id.widget_uv_hour_2, R.id.widget_uv_hour_3)
        private val TIME_TEXT_IDS = intArrayOf(R.id.widget_time_hour_0, R.id.widget_time_hour_1, R.id.widget_time_hour_2, R.id.widget_time_hour_3)
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

                            val containerId = CONTAINER_IDS[index]
                            val uvId = UV_TEXT_IDS[index]
                            val timeId = TIME_TEXT_IDS[index]

                            val uv = hourForecast.uvIndex.toInt()
                            val fgColor = UVColorHelper.getColorInt(uv.toDouble(), context, UVColorHelper.ColorType.FOREGROUND)

                            views.setTextViewText(uvId, uv.toString())
                            views.setTextColor(uvId, fgColor)
                            views.setTextViewText(timeId, String.format("%02d:00", hourForecast.hour))
                            views.setTextColor(timeId, fgColor)

                            when (index) {
                                0 -> views.setInt(containerId, "setBackgroundResource", endBackgroundRes(uv, isLeftEnd = true))
                                3 -> views.setInt(containerId, "setBackgroundResource", endBackgroundRes(uv, isLeftEnd = false))
                                else -> views.setInt(
                                    containerId, "setBackgroundColor",
                                    UVColorHelper.getColorInt(uv.toDouble(), context, UVColorHelper.ColorType.BACKGROUND)
                                )
                            }
                        }

                        for (i in displayHours.size until 4) {
                            val containerId = CONTAINER_IDS[i]
                            val uvId = UV_TEXT_IDS[i]
                            val timeId = TIME_TEXT_IDS[i]

                            views.setTextViewText(uvId, "-")
                            views.setTextColor(uvId, parseColor("#999999"))
                            views.setTextViewText(timeId, "--:--")
                            views.setTextColor(timeId, parseColor("#999999"))

                            when (i) {
                                0 -> views.setInt(containerId, "setBackgroundResource", R.drawable.widget_row_bg_error_left)
                                3 -> views.setInt(containerId, "setBackgroundResource", R.drawable.widget_row_bg_error_right)
                                else -> views.setInt(
                                    containerId, "setBackgroundColor",
                                    ContextCompat.getColor(context, R.color.widget_uv_error_background)
                                )
                            }
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
