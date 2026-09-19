package com.uvindex.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color.parseColor
import android.util.Log
import android.widget.RemoteViews
import com.uvindex.app.MainActivity
import com.uvindex.app.R
import com.uvindex.app.data.model.UVForecast
import com.uvindex.app.data.repository.FetchIntent
import com.uvindex.app.data.repository.WeatherRepository
import com.uvindex.app.ui.theme.UVColorHelper
import com.uvindex.app.uv.UvRisk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Applies pure widget bindings to RemoteViews. Loads the cached forecast once per update;
 * providers are thin shells that delegate here.
 */
object WidgetHost {
    private const val TAG = "WidgetHost"
    private const val NEUTRAL_COLOR = "#999999"

    /** Receiver entry point: keeps the broadcast alive via goAsync while the load runs (null outside onReceive). */
    fun updateCurrentUvFromReceiver(context: Context, pendingResult: BroadcastReceiver.PendingResult?) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateCurrentUv(appContext)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    /** Loads the cached forecast once and applies the 1x1 current-UV binding to every placed widget. */
    suspend fun updateCurrentUv(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, UVWidgetCurrent::class.java))
        if (ids.isEmpty()) return
        val forecast = loadCachedForecast(context)
        val binding = bindCurrentUv(forecast)
        for (id in ids) {
            manager.updateAppWidget(id, applyCurrentUv(context, binding))
        }
    }

    private suspend fun loadCachedForecast(context: Context): UVForecast? =
        try {
            WeatherRepository(context).getUVForecast(FetchIntent.CachedOnly).getOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Loading cached forecast failed", e)
            null
        }

    private fun applyCurrentUv(context: Context, binding: CurrentUvBinding): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.uv_widget_current)
        val textColor = when (val tone = binding.tone) {
            is WidgetTone.Risk -> UVColorHelper.getColorInt(tone.risk, context, UVColorHelper.ColorType.FOREGROUND)
            WidgetTone.Neutral -> parseColor(NEUTRAL_COLOR)
        }
        val background = when (val tone = binding.tone) {
            is WidgetTone.Risk -> when (tone.risk) {
                UvRisk.None -> R.drawable.widget_bg_low
                UvRisk.Moderate -> R.drawable.widget_bg_moderate
                UvRisk.High -> R.drawable.widget_bg_high
                UvRisk.VeryHigh -> R.drawable.widget_bg_very_high
            }
            WidgetTone.Neutral -> R.drawable.widget_bg_error
        }
        views.setTextViewText(R.id.widget_current_uv_value, binding.valueText)
        views.setTextViewText(R.id.widget_current_time, binding.timeText)
        views.setTextColor(R.id.widget_current_uv_value, textColor)
        views.setTextColor(R.id.widget_current_time, textColor)
        views.setInt(R.id.widget_current_container, "setBackgroundResource", background)
        views.setOnClickPendingIntent(R.id.widget_current_container, tapIntent(context, binding.tapTarget))
        return views
    }

    private fun tapIntent(context: Context, target: WidgetTapTarget): PendingIntent {
        val activity = when (target) {
            WidgetTapTarget.MainActivity -> MainActivity::class.java
        }
        return PendingIntent.getActivity(
            context, 0, Intent(context, activity),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
