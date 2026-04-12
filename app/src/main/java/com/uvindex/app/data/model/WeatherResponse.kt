package com.uvindex.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WeatherResponse(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val hourly: HourlyData
)

@Serializable
data class HourlyData(
    val time: List<String>,
    @SerialName("uv_index")
    val uvIndex: List<Double>,
    @SerialName("temperature_2m")
    val temperature: List<Double>
)