package com.uvindex.app.notification

import com.uvindex.app.data.model.UVForecast
import com.uvindex.app.uv.UvProtectionRecommendations
import com.uvindex.app.uv.UvRisk
import com.uvindex.app.uv.classifyUvRisk
import com.uvindex.app.uv.germanLabel
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
        // A pure forward shift of the same-length window (an hour drops off the front as it
        // becomes past, one appears on the back) reports a "new" trailing hour without the
        // window actually having grown — that shape must not re-fire (see #27). Only treat a
        // new trailing hour as Worse News when the window is genuinely longer than before.
        if (newHoursAfterNow && current.highHours.size > previous.highHours.size) return true
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
            val body = "UV steigt ab $nextHighHour Uhr auf hohe Werte " +
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
            val sentence1 = "Hohe UV-Strahlung zwischen $firstH und ${lastH + 1} Uhr."
            val sentence2 = if (veryHigh.isNotEmpty()) {
                val fvh = veryHigh.first().hour
                val lvh = veryHigh.last().hour
                " Sehr hohe Strahlung (8+) von $fvh bis ${lvh + 1} Uhr."
            } else ""
            "UV-Warnung" to (sentence1 + sentence2)
        }
    }

    /**
     * Builds the Daily Forecast Notification's title/body. See CONTEXT.md → "Daily Forecast
     * Notification" and issue #28: title carries the location, body carries the daily max and
     * category. Moderate days get a Schutzempfehlung sentence; High-or-above days get both the
     * sun-avoidance window and a Schutzempfehlung sentence (using the High-tier recommendation).
     */
    private fun buildDailyContent(forecast: UVForecast): Pair<String, String> {
        val maxUV = forecast.dailyMax.toInt()
        val risk = classifyUvRisk(forecast.dailyMax)
        val title = if (forecast.locationName != null) {
            "Tagesprognose ${forecast.locationName}"
        } else {
            "Tagesprognose"
        }
        val extra = when {
            risk == UvRisk.Moderate -> " Schutzempfehlung: ${UvProtectionRecommendations.Moderate}."
            risk.isHigh() -> sunAvoidanceSentence(forecast) + " Schutzempfehlung: ${UvProtectionRecommendations.High}."
            else -> ""
        }
        return title to "Max. $maxUV (${risk.germanLabel()}).$extra"
    }

    /**
     * "Zwischen X-Y Uhr direkte Sonne vermeiden." where X is the first High UV Hour and Y is
     * the last High UV Hour plus one. Empty if, unexpectedly, no hour actually reaches High.
     */
    private fun sunAvoidanceSentence(forecast: UVForecast): String {
        val highHours = forecast.allDayForecasts.filter { classifyUvRisk(it.uvIndex).isHigh() }
        val firstH = highHours.minOfOrNull { it.hour } ?: return ""
        val lastH = highHours.maxOf { it.hour }
        return " Zwischen $firstH-${lastH + 1} Uhr direkte Sonne vermeiden."
    }
}
