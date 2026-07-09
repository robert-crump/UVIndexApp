package com.uvindex.app.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.uvindex.app.R
import com.uvindex.app.data.model.AirQualityLevel
import com.uvindex.app.data.model.getAirQualityLevel

/**
 * Helper object for European Air Quality Index (EAQI) related colors.
 * Mirrors UVColorHelper's structure for consistency.
 */
object AQIColorHelper {

    enum class ColorType {
        FOREGROUND,
        BACKGROUND
    }

    /**
     * Returns the Android Color Int for the given EAQI value.
     *
     * @param aqi European AQI value
     * @param context Android Context
     * @param type Foreground or background color
     * @return Android Color Int
     */
    fun getColorInt(aqi: Double, context: Context, type: ColorType = ColorType.FOREGROUND): Int {
        val colorRes = when (getAirQualityLevel(aqi)) {
            AirQualityLevel.GOOD -> if (type == ColorType.FOREGROUND)
                R.color.aqi_good else R.color.aqi_good_background
            AirQualityLevel.FAIR -> if (type == ColorType.FOREGROUND)
                R.color.aqi_fair else R.color.aqi_fair_background
            AirQualityLevel.MODERATE -> if (type == ColorType.FOREGROUND)
                R.color.aqi_moderate else R.color.aqi_moderate_background
            AirQualityLevel.POOR -> if (type == ColorType.FOREGROUND)
                R.color.aqi_poor else R.color.aqi_poor_background
            AirQualityLevel.VERY_POOR -> if (type == ColorType.FOREGROUND)
                R.color.aqi_very_poor else R.color.aqi_very_poor_background
            AirQualityLevel.EXTREMELY_POOR -> if (type == ColorType.FOREGROUND)
                R.color.aqi_extremely_poor else R.color.aqi_extremely_poor_background
        }
        return ContextCompat.getColor(context, colorRes)
    }

    /**
     * Returns the Compose Color for the given EAQI value.
     *
     * @param aqi European AQI value
     * @param context Android Context
     * @param type Foreground or background color
     * @return Compose Color
     */
    fun getColor(aqi: Double, context: Context, type: ColorType = ColorType.FOREGROUND): Color {
        return Color(getColorInt(aqi, context, type))
    }
}
