package com.uvindex.app.uv

import kotlin.math.roundToInt

enum class SkinType(
    val label: String,
    val description: String,
    val medJoules: Int
) {
    TYPE_I("Typ I", "Sehr hell – verbrennt immer, bräunt nie", 200),
    TYPE_II("Typ II", "Hell – verbrennt meistens, bräunt kaum", 250),
    TYPE_III("Typ III", "Mittel – verbrennt manchmal, bräunt gut", 300),
    TYPE_IV("Typ IV", "Olivfarben – verbrennt selten, bräunt leicht", 450),
    TYPE_V("Typ V", "Dunkel – verbrennt sehr selten, bräunt stark", 600),
    TYPE_VI("Typ VI", "Sehr dunkel – verbrennt kaum nie", 1000);

    // WHO/ICNIRP formula: time = MED / (UV × 0.025 W/m² × 60 s/min)
    fun protectionMinutes(uvIndex: Double): Int {
        val raw = medJoules / (uvIndex * 1.5)
        return ((raw / 5.0).roundToInt() * 5).coerceAtLeast(5)
    }
}

data class ProtectionTimePart(val value: String, val unit: String)

fun protectionTimeParts(minutes: Int): List<ProtectionTimePart> {
    if (minutes < 60) return listOf(ProtectionTimePart("$minutes", "m"))
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0) {
        listOf(ProtectionTimePart("$h", "h"))
    } else {
        listOf(ProtectionTimePart("$h", "h"), ProtectionTimePart("$m", "m"))
    }
}

// Compact single-string rendering for space-constrained UI (e.g. "15m", "1h15m")
fun protectionTimeCompact(minutes: Int): String =
    protectionTimeParts(minutes).joinToString("") { "${it.value}${it.unit}" }
