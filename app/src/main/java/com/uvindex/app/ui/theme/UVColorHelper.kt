package com.uvindex.app.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.uvindex.app.R

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
    fun getColorInt(uvIndex: Double, context: Context, type: ColorType = ColorType.FOREGROUND): Int {
        val uv = uvIndex.toInt()
        val colorRes = when {
            uv <= 2 -> if (type == ColorType.FOREGROUND) 
                R.color.green_good_uv else R.color.green_good_uv_background
            uv <= 5 -> if (type == ColorType.FOREGROUND) 
                R.color.yellow_moderate_uv else R.color.yellow_moderate_uv_background
            uv <= 7 -> if (type == ColorType.FOREGROUND) 
                R.color.orange_high_uv else R.color.orange_high_uv_background
            else -> if (type == ColorType.FOREGROUND) 
                R.color.red_very_high_uv else R.color.red_very_high_uv_background
        }
        return ContextCompat.getColor(context, colorRes)
    }
    
    /**
     * Returns the Compose Color for the given UV index.
     *
     * @param uvIndex UV index value
     * @param context Android Context
     * @param type Foreground or background color
     * @return Compose Color
     */
    fun getColor(uvIndex: Double, context: Context, type: ColorType = ColorType.FOREGROUND): Color {
        return Color(getColorInt(uvIndex, context, type))
    }
    
    /**
     * Returns the category label for the given UV index.
     *
     * @param uvIndex UV index value
     * @return Category as a String (e.g. "Niedrig", "Mittel", "Hoch", "Sehr hoch")
     */
    fun getCategoryText(uvIndex: Double): String {
        val uv = uvIndex.toInt()
        return when {
            uv <= 2 -> "Niedrig"
            uv <= 5 -> "Mittel"
            uv <= 7 -> "Hoch"
            else -> "Sehr hoch"
        }
    }
}
