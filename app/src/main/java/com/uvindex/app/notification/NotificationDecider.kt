package com.uvindex.app.notification

import com.uvindex.app.data.model.UVForecast
import java.time.ZonedDateTime

/**
 * Pure decision engine: no Android imports, fully unit-testable.
 * See CONTEXT.md → "Daily Forecast Notification", "UV Warning", "Dedup Rules".
 */
object NotificationDecider {

    /** Hour range in which the Daily Forecast Notification may fire. Generous to handle Doze delays. */
    private val MORNING_WINDOW = 5..11

    /**
     * Evaluates [history] and [forecast] at [now] and returns the list of
     * [NotificationDecision]s that should be dispatched this tick.
     *
     * UV Warning channel is not implemented in this slice — only Daily decisions are emitted.
     */
    fun decide(
        now: ZonedDateTime,
        forecast: UVForecast?,
        history: NotificationHistory,
    ): List<NotificationDecision> {
        val decisions = mutableListOf<NotificationDecision>()
        decideDailyForecast(now, forecast, history)?.let { decisions.add(it) }
        return decisions
    }

    /**
     * Emits a [Channel.Daily] decision when:
     * - [NotificationHistory.dailyEnabled] is true
     * - [now] is within [MORNING_WINDOW]
     * - [NotificationHistory.lastDailySent] is not today (no re-fire even if forecast changes)
     * - a [forecast] is available
     *
     * See CONTEXT.md → "Daily Forecast Notification".
     */
    private fun decideDailyForecast(
        now: ZonedDateTime,
        forecast: UVForecast?,
        history: NotificationHistory,
    ): NotificationDecision? {
        if (!history.dailyEnabled) return null
        if (forecast == null) return null
        if (now.hour !in MORNING_WINDOW) return null

        val today = now.toLocalDate()
        if (history.lastDailySent == today) return null

        val (title, body) = buildDailyContent(forecast)
        return NotificationDecision(
            channel = Channel.Daily,
            title = title,
            body = body,
            priority = Priority.Default,
            actions = emptyList(),
        )
    }

    private fun buildDailyContent(forecast: UVForecast): Pair<String, String> {
        val maxUV = forecast.dailyMax.toInt()
        val category = when {
            maxUV <= 2 -> "niedrig"
            maxUV <= 5 -> "mittel"
            maxUV <= 7 -> "hoch"
            else -> "sehr hoch"
        }
        val description = buildHighUVDescription(forecast)
        val body = if (description.isNotEmpty()) {
            "Tagesmaximum: $maxUV ($category). $description"
        } else {
            "Tagesmaximum: $maxUV ($category)."
        }
        return "UV: Tagesprognose" to body
    }

    /**
     * Describes the High UV Window(s) for today. See CONTEXT.md → "High UV Window".
     */
    private fun buildHighUVDescription(forecast: UVForecast): String {
        val veryHighHours = forecast.allDayForecasts.filter { it.uvIndex >= 8 }.map { it.hour }
        val highHours = forecast.allDayForecasts.filter { it.uvIndex in 6.0..<8.0 }.map { it.hour }

        val parts = buildList {
            if (veryHighHours.isNotEmpty()) add("Sehr hohe Strahlung ${formatRanges(veryHighHours)}")
            if (highHours.isNotEmpty()) add("Hohe Strahlung ${formatRanges(highHours)}")
        }
        return if (parts.isEmpty()) "" else parts.joinToString(". ") + "."
    }

    private fun formatRanges(hours: List<Int>): String {
        val sorted = hours.sorted()
        val ranges = mutableListOf<String>()
        var start = sorted[0]
        var end = sorted[0]
        for (i in 1 until sorted.size) {
            if (sorted[i] == end + 1) end = sorted[i]
            else {
                ranges.add(formatRange(start, end))
                start = sorted[i]; end = sorted[i]
            }
        }
        ranges.add(formatRange(start, end))
        return "von " + ranges.joinToString(" und ")
    }

    private fun formatRange(start: Int, end: Int): String =
        if (start == end) "${"%02d".format(start)}:00 Uhr"
        else "${"%02d".format(start)}:00-${"%02d".format(end + 1)}:00 Uhr"
}
