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

/** UV below 1 carries no meaningful sunburn risk (no protection time is shown). */
fun isNoUvRisk(uvIndex: Double): Boolean = uvIndex < 1.0

/** German category label; apply casing (e.g. `lowercase()`) at the display site. */
fun UvRisk.germanLabel(): String = when (this) {
    UvRisk.None -> "Niedrig"
    UvRisk.Moderate -> "Mittel"
    UvRisk.High -> "Hoch"
    UvRisk.VeryHigh -> "Sehr hoch"
}
