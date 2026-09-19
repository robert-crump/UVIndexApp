package com.uvindex.app.widget

import com.uvindex.app.data.model.UVForecast
import com.uvindex.app.wind.CompassOctant
import com.uvindex.app.wind.travelOctant
import kotlin.math.roundToInt

/** What the wind widget should draw. */
data class WindBinding(val speedText: String, val arrow: CompassOctant, val tapTarget: WidgetTapTarget)

/** Pure binder: null [forecast] yields the placeholder with a north arrow. */
fun bindWind(forecast: UVForecast?): WindBinding {
    if (forecast == null) {
        return WindBinding(WIDGET_PLACEHOLDER, CompassOctant.N, WidgetTapTarget.MainActivity)
    }
    return WindBinding(
        speedText = "${forecast.currentHour.windSpeed.roundToInt()} km/h",
        arrow = travelOctant(forecast.currentHour.windDirection),
        tapTarget = WidgetTapTarget.MainActivity
    )
}
