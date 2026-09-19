package com.uvindex.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.uvindex.app.MainActivity
import com.uvindex.app.R
import com.uvindex.app.SettingsActivity
import com.uvindex.app.data.local.DataStoreManager
import com.uvindex.app.data.model.AirQualityLevel
import com.uvindex.app.data.model.UVForecast
import com.uvindex.app.data.model.getAirQualityLevel
import com.uvindex.app.data.repository.FetchIntent
import com.uvindex.app.data.repository.WeatherRepository
import com.uvindex.app.schedule.BackgroundSchedule
import com.uvindex.app.ui.theme.AQIColorHelper
import com.uvindex.app.ui.theme.UVColorHelper
import com.uvindex.app.uv.SkinType
import com.uvindex.app.uv.UvRisk
import com.uvindex.app.wind.CompassOctant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The five widgets the host can update. */
enum class WidgetKind { HourlyUv, CurrentUv, Wind, AirQuality, SelfProtection }

/**
 * Applies pure widget bindings to RemoteViews. Loads the cached forecast (and, for the self-protection
 * widget, the skin type) once per refresh; providers are thin shells that delegate here.
 */
object WidgetHost {
    private const val TAG = "WidgetHost"

    /** Receiver entry point: keeps the broadcast alive via goAsync while the load runs (null outside onReceive). */
    fun updateFromReceiver(context: Context, kind: WidgetKind, pendingResult: BroadcastReceiver.PendingResult?) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                update(appContext, kind)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    /** Enqueues one tick that fetches fresh data and then refreshes every widget; nothing fetches in a receiver. */
    fun refreshNow(context: Context) {
        BackgroundSchedule.enqueueImmediateRefresh(context.applicationContext)
    }

    /** Loads the cached forecast once and applies the [kind]'s binding to every placed widget of that kind. */
    suspend fun update(context: Context, kind: WidgetKind) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, providerClass(kind)))
        if (ids.isEmpty()) return
        val forecast = loadCachedForecast(context)
        val skinType = if (kind == WidgetKind.SelfProtection) loadSkinType(context) else null
        for (id in ids) {
            manager.updateAppWidget(id, render(context, kind, forecast, skinType))
        }
    }

    private fun providerClass(kind: WidgetKind): Class<*> = when (kind) {
        WidgetKind.HourlyUv -> UVWidget::class.java
        WidgetKind.CurrentUv -> UVWidgetCurrent::class.java
        WidgetKind.Wind -> WindWidget::class.java
        WidgetKind.AirQuality -> AirQualityWidget::class.java
        WidgetKind.SelfProtection -> SelfProtectionTimeWidget::class.java
    }

    private suspend fun loadCachedForecast(context: Context): UVForecast? =
        try {
            WeatherRepository(context).getUVForecast(FetchIntent.CachedOnly).getOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Loading cached forecast failed", e)
            null
        }

    private suspend fun loadSkinType(context: Context): SkinType? =
        try {
            DataStoreManager(context).getSkinType().first()
        } catch (e: Exception) {
            Log.e(TAG, "Loading skin type failed", e)
            null
        }

    private fun render(context: Context, kind: WidgetKind, forecast: UVForecast?, skinType: SkinType?): RemoteViews =
        when (kind) {
            WidgetKind.HourlyUv -> applyHourlyUv(context, bindHourlyUv(forecast))
            WidgetKind.CurrentUv -> applyCurrentUv(context, bindCurrentUv(forecast))
            WidgetKind.Wind -> applyWind(context, bindWind(forecast))
            WidgetKind.AirQuality -> applyAirQuality(context, bindAirQuality(forecast))
            WidgetKind.SelfProtection -> applySelfProtection(context, bindSelfProtection(forecast, skinType))
        }

    private fun foreground(context: Context, tone: WidgetTone): Int = when (tone) {
        is WidgetTone.Risk -> UVColorHelper.getColorInt(tone.risk, context, UVColorHelper.ColorType.FOREGROUND)
        WidgetTone.Neutral -> WIDGET_ERROR_COLOR
    }

    private fun applyCurrentUv(context: Context, binding: CurrentUvBinding): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.uv_widget_current)
        val textColor = foreground(context, binding.tone)
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

    private val HOURLY_CELL_IDS = intArrayOf(R.id.widget_cell_0, R.id.widget_cell_1, R.id.widget_cell_2, R.id.widget_cell_3)
    private val HOURLY_UV_IDS = intArrayOf(R.id.widget_uv_hour_0, R.id.widget_uv_hour_1, R.id.widget_uv_hour_2, R.id.widget_uv_hour_3)
    private val HOURLY_TIME_IDS = intArrayOf(R.id.widget_time_hour_0, R.id.widget_time_hour_1, R.id.widget_time_hour_2, R.id.widget_time_hour_3)

    private fun endBackgroundRes(tone: WidgetTone, isLeftEnd: Boolean): Int = when (tone) {
        is WidgetTone.Risk -> when (tone.risk) {
            UvRisk.None -> if (isLeftEnd) R.drawable.widget_row_bg_low_left else R.drawable.widget_row_bg_low_right
            UvRisk.Moderate -> if (isLeftEnd) R.drawable.widget_row_bg_moderate_left else R.drawable.widget_row_bg_moderate_right
            UvRisk.High -> if (isLeftEnd) R.drawable.widget_row_bg_high_left else R.drawable.widget_row_bg_high_right
            UvRisk.VeryHigh -> if (isLeftEnd) R.drawable.widget_row_bg_very_high_left else R.drawable.widget_row_bg_very_high_right
        }
        WidgetTone.Neutral -> if (isLeftEnd) R.drawable.widget_row_bg_error_left else R.drawable.widget_row_bg_error_right
    }

    private fun applyHourlyUv(context: Context, binding: HourlyUvBinding): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.uv_widget)
        val last = binding.cells.lastIndex
        binding.cells.forEachIndexed { index, cell ->
            val fg = foreground(context, cell.tone)
            views.setTextViewText(HOURLY_UV_IDS[index], cell.uvText)
            views.setTextColor(HOURLY_UV_IDS[index], fg)
            views.setTextViewText(HOURLY_TIME_IDS[index], cell.timeText)
            views.setTextColor(HOURLY_TIME_IDS[index], fg)

            if (index == last) {
                views.setInt(HOURLY_CELL_IDS[index], "setBackgroundResource", endBackgroundRes(cell.tone, isLeftEnd = false))
            } else {
                val background = when (val tone = cell.tone) {
                    is WidgetTone.Risk -> UVColorHelper.getColorInt(tone.risk, context, UVColorHelper.ColorType.BACKGROUND)
                    WidgetTone.Neutral -> ContextCompat.getColor(context, R.color.widget_uv_error_background)
                }
                views.setInt(HOURLY_CELL_IDS[index], "setBackgroundColor", background)
            }
            if (index == 0) {
                // The icon column left of cell 0 owns the rounded left corner and the current hour's tint.
                views.setInt(R.id.widget_icon_column, "setBackgroundResource", endBackgroundRes(cell.tone, isLeftEnd = true))
                views.setInt(R.id.widget_icon, "setColorFilter", fg)
            }
        }
        views.setViewVisibility(R.id.widget_refresh_button, View.VISIBLE)
        views.setViewVisibility(R.id.widget_loading_indicator, View.GONE)
        views.setOnClickPendingIntent(R.id.widget_container, tapIntent(context, binding.tapTarget))
        val refresh = Intent(context, UVWidget::class.java).apply { action = UVWidget.ACTION_REFRESH }
        views.setOnClickPendingIntent(
            R.id.widget_refresh_button,
            PendingIntent.getBroadcast(context, 0, refresh, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )
        return views
    }

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

    private fun applyWind(context: Context, binding: WindBinding): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.wind_widget)
        views.setTextViewText(R.id.wind_widget_speed, binding.speedText)
        views.setImageViewResource(R.id.wind_widget_arrow, arrowDrawableFor(binding.arrow))
        views.setOnClickPendingIntent(R.id.wind_widget_container, tapIntent(context, binding.tapTarget))
        return views
    }

    private fun aqiBackgroundRes(aqi: Double?): Int = when (aqi?.let { getAirQualityLevel(it) }) {
        AirQualityLevel.GOOD -> R.drawable.widget_bg_aqi_good
        AirQualityLevel.FAIR -> R.drawable.widget_bg_aqi_fair
        AirQualityLevel.MODERATE -> R.drawable.widget_bg_aqi_moderate
        AirQualityLevel.POOR -> R.drawable.widget_bg_aqi_poor
        AirQualityLevel.VERY_POOR -> R.drawable.widget_bg_aqi_very_poor
        AirQualityLevel.EXTREMELY_POOR -> R.drawable.widget_bg_aqi_extremely_poor
        null -> R.drawable.widget_bg_aqi_error
    }

    private fun applyAirQuality(context: Context, binding: AirQualityBinding): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.air_quality_widget)
        val aqi = binding.aqi
        val color = if (aqi != null) {
            AQIColorHelper.getColorInt(aqi, context, AQIColorHelper.ColorType.FOREGROUND)
        } else {
            WIDGET_ERROR_COLOR
        }
        views.setTextViewText(R.id.widget_air_quality_value, binding.valueText)
        views.setTextColor(R.id.widget_air_quality_value, color)
        views.setInt(R.id.widget_air_quality_container, "setBackgroundResource", aqiBackgroundRes(aqi))
        views.setOnClickPendingIntent(R.id.widget_air_quality_container, tapIntent(context, binding.tapTarget))
        return views
    }

    private fun applySelfProtection(context: Context, binding: SelfProtectionBinding): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.self_protection_time_widget)
        views.setTextViewText(R.id.widget_self_protection_value, binding.valueText)
        views.setTextColor(R.id.widget_self_protection_value, foreground(context, binding.tone))
        views.setOnClickPendingIntent(R.id.widget_self_protection_container, tapIntent(context, binding.tapTarget))
        return views
    }

    private fun tapIntent(context: Context, target: WidgetTapTarget): PendingIntent {
        val activity = when (target) {
            WidgetTapTarget.MainActivity -> MainActivity::class.java
            WidgetTapTarget.Settings -> SettingsActivity::class.java
        }
        return PendingIntent.getActivity(
            context, 0, Intent(context, activity),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
