package com.uvindex.app.notification

import com.uvindex.app.data.model.UVForecast
import com.uvindex.app.uv.UvRisk
import com.uvindex.app.uv.classifyUvRisk
import com.uvindex.app.uv.isHigh
import com.uvindex.app.uv.isVeryHigh
import java.time.LocalDateTime
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
     */
    fun decide(
        now: ZonedDateTime,
        forecast: UVForecast?,
        history: NotificationHistory,
    ): List<NotificationDecision> {
        val decisions = mutableListOf<NotificationDecision>()
        decideDailyForecast(now, forecast, history)?.let { decisions.add(it) }
        decideUvWarning(now, forecast, history)?.let { decisions.add(it) }
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

    /**
     * Emits a [Channel.UvWarning] decision with [Phase.Prelude] or [Phase.InWindow]:
     * - Prelude: current hour UV < 6 AND next forecast hour UV ≥ 6
     * - InWindow: current hour UV ≥ 6 (fallback for missed Prelude)
     * Once-per-day dedup: [NotificationHistory.lastUvWarningOn] == today blocks re-fire.
     * [NotificationHistory.uvWarningDisabledOn] == today blocks unconditionally.
     *
     * See CONTEXT.md → "UV Warning", "Prelude phase", "In-window phase", "Disabled Today".
     */
    private fun decideUvWarning(
        now: ZonedDateTime,
        forecast: UVForecast?,
        history: NotificationHistory,
    ): NotificationDecision? {
        if (!history.uvWarningEnabled) return null
        if (forecast == null) return null
        val today = now.toLocalDate()
        if (history.uvWarningDisabledOn == today) return null

        val currentHour = now.hour
        val currentUV = forecast.allDayForecasts.find { it.hour == currentHour }?.uvIndex
            ?: forecast.currentHour.uvIndex
        val nextHourUV = forecast.allDayForecasts.find { it.hour == currentHour + 1 }?.uvIndex ?: 0.0

        val currentRisk = classifyUvRisk(currentUV)
        val nextRisk = classifyUvRisk(nextHourUV)
        val phase: Phase = when {
            !currentRisk.isHigh() && nextRisk.isHigh() -> Phase.Prelude
            currentRisk.isHigh() -> Phase.InWindow
            else -> return null
        }

        val highForecasts = forecast.allDayForecasts.filter { classifyUvRisk(it.uvIndex).isHigh() }
        if (highForecasts.isEmpty()) return null

        val warnedAbout = buildWarnedAbout(highForecasts)

        if (history.lastUvWarningOn == today) {
            if (!worseNews(history.lastUvWarning, warnedAbout, now.toLocalDateTime())) return null
        }

        val (title, body) = buildUvWarningContent(phase, forecast, currentHour, nextHourUV)
        return NotificationDecision(
            channel = Channel.UvWarning,
            phase = phase,
            title = title,
            body = body,
            priority = Priority.High,
            actions = listOf(Action.DisableUvWarningsToday),
            warnedAbout = warnedAbout,
        )
    }

    /**
     * Returns true when the new forecast is genuinely worse than what was previously warned about,
     * justifying a same-day re-fire of the UV Warning. [previous] == null always returns true.
     * See CONTEXT.md → "Worse News".
     */
    internal fun worseNews(
        previous: WarnedAbout?,
        current: WarnedAbout,
        now: LocalDateTime,
    ): Boolean {
        if (previous == null) return true
        if (current.peak > previous.peak) return true
        if (current.firstHighHour < previous.firstHighHour) return true
        val newHoursAfterNow = current.highHours.any { h ->
            h >= now.hour && h !in previous.highHours
        }
        if (newHoursAfterNow) return true
        return false
    }

    private fun buildWarnedAbout(highForecasts: List<com.uvindex.app.data.model.HourlyForecast>): WarnedAbout {
        val highHours = highForecasts.map { it.hour }.toSet()
        val firstHighHour = highHours.min()
        val peak = highForecasts.maxOf { it.uvIndex.toFloat() }
        return WarnedAbout(peak = peak, firstHighHour = firstHighHour, highHours = highHours)
    }

    private fun buildUvWarningContent(
        phase: Phase,
        forecast: UVForecast,
        currentHour: Int,
        nextHourUV: Double,
    ): Pair<String, String> = when (phase) {
        Phase.Prelude -> {
            val nextHighHour = currentHour + 1
            val body = "UV steigt ab ${"%02d".format(nextHighHour)}:00 Uhr auf hohe Werte " +
                "(${nextHourUV.toInt()}). Jetzt Sonnenschutz auftragen."
            "UV-Anstieg in Kürze" to body
        }
        Phase.InWindow -> {
            val highHours = forecast.allDayForecasts.filter {
                classifyUvRisk(it.uvIndex).isHigh() && it.hour >= currentHour
            }
            val firstH = highHours.firstOrNull()?.hour ?: currentHour
            val lastH = highHours.lastOrNull()?.hour ?: currentHour
            val veryHigh = highHours.filter { classifyUvRisk(it.uvIndex).isVeryHigh() }
            val sentence1 = "Hohe UV-Strahlung zwischen ${"%02d".format(firstH)}:00 " +
                "und ${"%02d".format(lastH + 1)}:00 Uhr."
            val sentence2 = if (veryHigh.isNotEmpty()) {
                val fvh = veryHigh.first().hour
                val lvh = veryHigh.last().hour
                " Sehr hohe Strahlung (8+) von ${"%02d".format(fvh)}:00 " +
                    "bis ${"%02d".format(lvh + 1)}:00 Uhr."
            } else ""
            "UV-Warnung" to (sentence1 + sentence2)
        }
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
        val veryHighHours = forecast.allDayForecasts.filter { classifyUvRisk(it.uvIndex).isVeryHigh() }.map { it.hour }
        val highHours = forecast.allDayForecasts.filter { classifyUvRisk(it.uvIndex) == UvRisk.High }.map { it.hour }

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
