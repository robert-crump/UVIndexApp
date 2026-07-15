package com.uvindex.app.uv

import java.util.Locale
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
        return when {
            raw < 55 -> ((raw / 5.0).roundToInt() * 5).coerceAtLeast(5)
            raw <= 60 -> 60
            else -> (raw / 30.0).roundToInt() * 30
        }
    }
}

data class ProtectionTimePart(val value: String, val unit: String)

// Above 1h, protectionMinutes() only ever produces 30-minute steps, so hours
// are rendered as a single decimal value (e.g. "1.5h") instead of "1h30m" to
// keep the widget text on one line.
fun protectionTimeParts(minutes: Int): List<ProtectionTimePart> {
    if (minutes < 60) return listOf(ProtectionTimePart("$minutes", "m"))
    val wholeHours = minutes / 60
    val remainderMinutes = minutes % 60
    val value = when (remainderMinutes) {
        0 -> "$wholeHours"
        30 -> "$wholeHours.5"
        else -> String.format(Locale.US, "%.1f", minutes / 60.0)
    }
    return listOf(ProtectionTimePart(value, "h"))
}

// Compact single-string rendering for space-constrained UI (e.g. "15m", "1.5h")
fun protectionTimeCompact(minutes: Int): String =
    protectionTimeParts(minutes).joinToString("") { "${it.value}${it.unit}" }
