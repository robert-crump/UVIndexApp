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
