package com.uvindex.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.uvindex.app.R
import com.uvindex.app.data.repository.WeatherRepository
import com.uvindex.app.wind.CompassOctant
import com.uvindex.app.wind.travelOctant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class WindWidget : AppWidgetProvider() {

    companion object {
        private const val TAG = "WindWidget"

        private fun arrowDrawableFor(octant: CompassOctant): Int = when (octant) {
            CompassOctant.N -> R.drawable.ic_wind_arrow_n
            CompassOctant.NE -> R.drawable.ic_wind_arrow_ne
            CompassOctant.E -> R.drawable.ic_wind_arrow_e
            CompassOctant.SE -> R.drawable.ic_wind_arrow_se
            CompassOctant.S -> R.drawable.ic_wind_arrow_s
            CompassOctant.SW -> R.drawable.ic_wind_arrow_sw
            CompassOctant.W -> R.drawable.ic_wind_arrow_w
            CompassOctant.NW -> R.drawable.ic_wind_arrow_nw
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
                ComponentName(context, WindWidget::class.java)
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
        val views = RemoteViews(context.packageName, R.layout.wind_widget)

        val appIntent = Intent(context, com.uvindex.app.MainActivity::class.java)
        val appPendingIntent = PendingIntent.getActivity(
            context,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.wind_widget_container, appPendingIntent)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val repository = WeatherRepository(context)
                val result = withContext(Dispatchers.IO) {
                    repository.getCachedForecastForWidget()
                }

                result.fold(
                    onSuccess = { forecast ->
                        val windSpeed = forecast.currentHour.windSpeed
                        val octant = travelOctant(forecast.currentHour.windDirection)

                        views.setTextViewText(R.id.wind_widget_speed, "${windSpeed.roundToInt()} km/h")
                        views.setImageViewResource(R.id.wind_widget_arrow, arrowDrawableFor(octant))

                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Update failed: ${error.message}")
                        views.setTextViewText(R.id.wind_widget_speed, "-- km/h")
                        views.setImageViewResource(R.id.wind_widget_arrow, R.drawable.ic_wind_arrow_n)
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception during update", e)
                views.setTextViewText(R.id.wind_widget_speed, "-- km/h")
                views.setImageViewResource(R.id.wind_widget_arrow, R.drawable.ic_wind_arrow_n)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
