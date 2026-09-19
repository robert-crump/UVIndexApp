package com.uvindex.app.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.uvindex.app.R
import com.uvindex.app.uv.UvRisk
import com.uvindex.app.uv.classifyUvRisk
import com.uvindex.app.uv.germanLabel

/**
 * Helper object for UV index related colors.
 * Consolidates all UV color functions in one place.
 */
object UVColorHelper {
    
    enum class ColorType {
        FOREGROUND,
        BACKGROUND
    }
    
    /**
     * Returns the Android Color Int for the given UV index.
     *
     * @param uvIndex UV index value
     * @param context Android Context
     * @param type Foreground or background color
     * @return Android Color Int
     */
    fun getColorInt(uvIndex: Double, context: Context, type: ColorType = ColorType.FOREGROUND): Int =
        getColorInt(classifyUvRisk(uvIndex), context, type)

    fun getColorInt(risk: UvRisk, context: Context, type: ColorType = ColorType.FOREGROUND): Int {
        val foreground = type == ColorType.FOREGROUND
        val colorRes = when (risk) {
            UvRisk.None -> if (foreground) R.color.green_good_uv else R.color.green_good_uv_background
            UvRisk.Moderate -> if (foreground) R.color.yellow_moderate_uv else R.color.yellow_moderate_uv_background
            UvRisk.High -> if (foreground) R.color.orange_high_uv else R.color.orange_high_uv_background
            UvRisk.VeryHigh -> if (foreground) R.color.red_very_high_uv else R.color.red_very_high_uv_background
        }
        return ContextCompat.getColor(context, colorRes)
    }

    /**
     * Returns the Compose Color for the given UV index.
     */
    fun getColor(uvIndex: Double, context: Context, type: ColorType = ColorType.FOREGROUND): Color {
        return Color(getColorInt(uvIndex, context, type))
    }

    /**
     * Returns the category label (e.g. "Niedrig", "Mittel", "Hoch", "Sehr hoch").
     */
    fun getCategoryText(uvIndex: Double): String = classifyUvRisk(uvIndex).germanLabel()
}
