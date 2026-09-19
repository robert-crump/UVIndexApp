package com.uvindex.app.widget

import com.uvindex.app.data.model.UVForecast
import com.uvindex.app.uv.UvRisk
import com.uvindex.app.uv.classifyUvRisk

/** Color/background category of a widget: a UV risk, or the neutral "no data" look. */
sealed interface WidgetTone {
    data class Risk(val risk: UvRisk) : WidgetTone
    data object Neutral : WidgetTone
}

/** Which activity a widget tap opens. */
enum class WidgetTapTarget { MainActivity }

/** What the 1x1 current-UV widget should draw; the widget host turns this into RemoteViews. */
data class CurrentUvBinding(
    val valueText: String,
    val timeText: String,
    val tone: WidgetTone,
    val tapTarget: WidgetTapTarget
)

/** Pure binder: null [forecast] means "no cached data" and yields the error placeholder. */
fun bindCurrentUv(forecast: UVForecast?): CurrentUvBinding {
    if (forecast == null) {
        return CurrentUvBinding("-", "--:--", WidgetTone.Neutral, WidgetTapTarget.MainActivity)
    }
    val uv = forecast.currentHour.uvIndex.toInt()
    return CurrentUvBinding(
        valueText = uv.toString(),
        timeText = String.format("%02d:00", forecast.currentHour.hour),
        tone = WidgetTone.Risk(classifyUvRisk(uv.toDouble())),
        tapTarget = WidgetTapTarget.MainActivity
    )
}
