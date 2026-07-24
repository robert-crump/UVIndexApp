package com.uvindex.app.uv

enum class UvRisk { None, Moderate, High, VeryHigh }

fun classifyUvRisk(uvIndex: Double): UvRisk = when {
    uvIndex >= 8.0 -> UvRisk.VeryHigh
    uvIndex >= 6.0 -> UvRisk.High
    uvIndex >= 3.0 -> UvRisk.Moderate
    else -> UvRisk.None
}

fun UvRisk.isHigh(): Boolean = this >= UvRisk.High
fun UvRisk.isVeryHigh(): Boolean = this == UvRisk.VeryHigh

/** German category label shown to users (e.g. in the Daily Forecast Notification). */
fun UvRisk.germanLabel(): String = when (this) {
    UvRisk.None -> "niedrig"
    UvRisk.Moderate -> "mittel"
    UvRisk.High -> "hoch"
    UvRisk.VeryHigh -> "sehr hoch"
}
