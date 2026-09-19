package com.uvindex.app.widget

import com.uvindex.app.data.model.UVForecast

/** What the air-quality widget should draw; a null [aqi] means the neutral "no data" look. */
data class AirQualityBinding(val valueText: String, val aqi: Double?, val tapTarget: WidgetTapTarget)

/** Pure binder: a missing forecast and a missing air-quality reading both yield the placeholder. */
fun bindAirQuality(forecast: UVForecast?): AirQualityBinding {
    val aqi = forecast?.airQuality
        ?: return AirQualityBinding(WIDGET_PLACEHOLDER, null, WidgetTapTarget.MainActivity)
    return AirQualityBinding(aqi.toInt().toString(), aqi, WidgetTapTarget.MainActivity)
}
