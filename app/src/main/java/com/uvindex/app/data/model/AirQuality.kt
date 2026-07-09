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

/**
 * European Air Quality Index (EAQI) levels.
 * Breakpoints follow the EEA's official 6-level scale (0-20-40-60-80-100+).
 */
enum class AirQualityLevel(val label: String) {
    GOOD("Gut"),
    FAIR("Zufriedenstellend"),
    MODERATE("Mäßig"),
    POOR("Schlecht"),
    VERY_POOR("Sehr schlecht"),
    EXTREMELY_POOR("Extrem schlecht")
}

fun getAirQualityLevel(aqi: Double): AirQualityLevel {
    return when {
        aqi <= 20 -> AirQualityLevel.GOOD
        aqi <= 40 -> AirQualityLevel.FAIR
        aqi <= 60 -> AirQualityLevel.MODERATE
        aqi <= 80 -> AirQualityLevel.POOR
        aqi <= 100 -> AirQualityLevel.VERY_POOR
        else -> AirQualityLevel.EXTREMELY_POOR
    }
}
