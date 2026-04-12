package com.uvindex.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AirQualityResponse(
    @SerialName("current")
    val current: CurrentAirQuality
)

@Serializable
data class CurrentAirQuality(
    @SerialName("european_aqi")
    val europeanAqi: Double
)

enum class AirQualityLevel(val label: String, val color: String) {
    GOOD("Good", "#006400"),           // Dark green
    FAIR("Fair", "#90EE90"),           // Light green
    MODERATE("Moderate", "#FFD700"),   // Gelb
    POOR("Poor", "#FF0000")            // Rot
}

fun getAirQualityLevel(aqi: Double): AirQualityLevel {
    return when {
        aqi <= 20 -> AirQualityLevel.GOOD
        aqi <= 40 -> AirQualityLevel.FAIR
        aqi <= 60 -> AirQualityLevel.MODERATE
        else -> AirQualityLevel.POOR
    }
}
