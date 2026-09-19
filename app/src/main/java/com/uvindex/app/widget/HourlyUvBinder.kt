package com.uvindex.app.widget

import com.uvindex.app.data.model.UVForecast
import com.uvindex.app.uv.classifyUvRisk

/** One of the four cells of the 4x1 widget; padding cells carry the placeholder and a neutral tone. */
data class HourlyUvCell(val uvText: String, val timeText: String, val tone: WidgetTone)

/**
 * What the 4x1 hourly widget should draw. [cells] always has [HOURLY_UV_CELL_COUNT] entries; the first
 * cell (with the icon column, which takes its tone) is the current hour and the last is the right end cap.
 */
data class HourlyUvBinding(val cells: List<HourlyUvCell>, val tapTarget: WidgetTapTarget)

const val HOURLY_UV_CELL_COUNT = 4

/** Pure binder: the first four remaining hours of the day, padded with placeholder cells. */
fun bindHourlyUv(forecast: UVForecast?): HourlyUvBinding {
    val filled = forecast?.remainingHours.orEmpty().take(HOURLY_UV_CELL_COUNT).map { hour ->
        val uv = hour.uvIndex.toInt()
        HourlyUvCell(
            uvText = uv.toString(),
            timeText = String.format("%02d:00", hour.hour),
            tone = WidgetTone.Risk(classifyUvRisk(uv.toDouble()))
        )
    }
    val padding = List(HOURLY_UV_CELL_COUNT - filled.size) {
        HourlyUvCell(WIDGET_PLACEHOLDER, WIDGET_PLACEHOLDER, WidgetTone.Neutral)
    }
    return HourlyUvBinding(filled + padding, WidgetTapTarget.MainActivity)
}
