package com.uvindex.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color.parseColor
import android.util.Log
import android.widget.RemoteViews
import com.uvindex.app.R
import com.uvindex.app.SettingsActivity
import com.uvindex.app.data.local.DataStoreManager
import com.uvindex.app.data.repository.WeatherRepository
import com.uvindex.app.ui.theme.UVColorHelper
import com.uvindex.app.uv.protectionTimeCompact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SelfProtectionTimeWidget : AppWidgetProvider() {

    companion object {
        private const val TAG = "SelfProtectionTimeWidget"
        private const val NO_RISK_UV_THRESHOLD = 1.0
        private val FAILURE_COLOR = parseColor("#999999")
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

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.self_protection_time_widget)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val repository = WeatherRepository(context)
                val dataStoreManager = DataStoreManager(context)

                val skinType = withContext(Dispatchers.IO) {
                    dataStoreManager.getSkinType().first()
                }

                if (skinType == null) {
                    views.setTextViewText(R.id.widget_self_protection_value, "?")
                    views.setTextColor(R.id.widget_self_protection_value, FAILURE_COLOR)
                    setOpenActivityOnClick(context, views, SettingsActivity::class.java)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                    return@launch
                }

                val result = withContext(Dispatchers.IO) {
                    // Widgets use cache only (no location access needed)
                    // WidgetUpdateWorker has already cached fresh data
                    repository.getCachedForecastForWidget()
                }

                result.fold(
                    onSuccess = { forecast ->
                        val currentUV = forecast.currentHour.uvIndex

                        if (currentUV < NO_RISK_UV_THRESHOLD) {
                            views.setTextViewText(R.id.widget_self_protection_value, "–")
                            views.setTextColor(
                                R.id.widget_self_protection_value,
                                UVColorHelper.getWidgetUVColor(0)
                            )
                        } else {
                            val minutes = skinType.protectionMinutes(currentUV)
                            views.setTextViewText(
                                R.id.widget_self_protection_value,
                                protectionTimeCompact(minutes)
                            )
                            views.setTextColor(
                                R.id.widget_self_protection_value,
                                UVColorHelper.getWidgetUVColor(currentUV.toInt())
                            )
                        }

                        setOpenActivityOnClick(context, views, com.uvindex.app.MainActivity::class.java)
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Update failed: ${error.message}", error)
                        views.setTextViewText(R.id.widget_self_protection_value, "–")
                        views.setTextColor(R.id.widget_self_protection_value, FAILURE_COLOR)
                        setOpenActivityOnClick(context, views, com.uvindex.app.MainActivity::class.java)
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception during update", e)
                views.setTextViewText(R.id.widget_self_protection_value, "–")
                views.setTextColor(R.id.widget_self_protection_value, FAILURE_COLOR)
                setOpenActivityOnClick(context, views, com.uvindex.app.MainActivity::class.java)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    private fun setOpenActivityOnClick(context: Context, views: RemoteViews, activityClass: Class<*>) {
        val intent = Intent(context, activityClass)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_self_protection_container, pendingIntent)
    }
}
