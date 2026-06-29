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

fun formatProtectionTime(minutes: Int): String {
    if (minutes < 60) return "$minutes min"
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0) "$h h" else "$h h ${String.format("%02d", m)} min"
}
