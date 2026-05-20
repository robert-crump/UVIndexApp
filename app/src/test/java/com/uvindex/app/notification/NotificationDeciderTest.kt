package com.uvindex.app.notification

import com.uvindex.app.data.model.HourlyForecast
import com.uvindex.app.data.model.UVForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class NotificationDeciderTest {

    private val zone = ZoneId.systemDefault()

    /** Fixed morning time (07:30) within the trigger window. */
    private val morningNow: ZonedDateTime =
        ZonedDateTime.now(zone).withHour(7).withMinute(30).withSecond(0).withNano(0)

    private val today: LocalDate = morningNow.toLocalDate()

    // ── helpers ──────────────────────────────────────────────────────────────

    /** History for Daily-channel tests — UV Warning disabled to keep tests isolated. */
    private fun historyWith(
        dailyEnabled: Boolean = true,
        lastDailySent: LocalDate? = null,
    ) = NotificationHistory(
        dailyEnabled = dailyEnabled,
        uvWarningEnabled = false,
        uvWarningDisabledOn = null,
        lastDailySent = lastDailySent,
        lastDailySentAt = null,
        lastUvWarningOn = null,
        lastUvWarningAt = null,
        lastUvWarning = null,
    )

    private fun historyForUvWarning(
        uvWarningEnabled: Boolean = true,
        uvWarningDisabledOn: LocalDate? = null,
        lastUvWarningOn: LocalDate? = null,
    ) = NotificationHistory(
        dailyEnabled = false,
        uvWarningEnabled = uvWarningEnabled,
        uvWarningDisabledOn = uvWarningDisabledOn,
        lastDailySent = null,
        lastDailySentAt = null,
        lastUvWarningOn = lastUvWarningOn,
        lastUvWarningAt = null,
        lastUvWarning = null,
    )

    private fun fakeForecast(dailyMax: Double = 7.0): UVForecast {
        val hour = HourlyForecast("2026-05-20T08:00", 8, 7.0, 22.0)
        return UVForecast(
            currentHour = hour,
            nextHours = emptyList(),
            dailyMax = dailyMax,
            dailyMaxRemaining = dailyMax,
            clearSkyMax = 8.0,
            clearSkyHourly = emptyList(),
            maxHourToday = 12,
            highUVTimeSlots = emptyList(),
            locationName = "Test",
            allDayForecasts = listOf(hour),
            airQuality = null,
            lastUpdateTime = null,
            countryCode = null,
        )
    }

    /** Forecast with specific UV at [atHour] and optionally a different UV at [atHour]+1. */
    private fun forecastWithUV(
        atHour: Int,
        currentUV: Double,
        nextHourUV: Double = 0.0,
    ): UVForecast {
        val entries = buildList {
            add(HourlyForecast("2026-05-20T${"%02d".format(atHour)}:00", atHour, currentUV, 22.0))
            if (nextHourUV > 0.0) {
                add(HourlyForecast("2026-05-20T${"%02d".format(atHour + 1)}:00", atHour + 1, nextHourUV, 22.0))
            }
        }
        val maxUV = entries.maxOf { it.uvIndex }
        return UVForecast(
            currentHour = entries[0],
            nextHours = entries.drop(1),
            dailyMax = maxUV,
            dailyMaxRemaining = maxUV,
            clearSkyMax = 10.0,
            clearSkyHourly = emptyList(),
            maxHourToday = atHour,
            highUVTimeSlots = emptyList(),
            locationName = "Test",
            allDayForecasts = entries,
            airQuality = null,
            lastUpdateTime = null,
            countryCode = null,
        )
    }

    // ── Daily channel decide() tests ──────────────────────────────────────

    @Test
    fun `decide returns nothing when dailyEnabled is false`() {
        val result = NotificationDecider.decide(morningNow, fakeForecast(), historyWith(dailyEnabled = false))
        assertTrue("Expected no decisions when daily is disabled", result.isEmpty())
    }

    @Test
    fun `decide emits Daily decision when not sent today and in morning window`() {
        val history = historyWith(lastDailySent = today.minusDays(1))
        val result = NotificationDecider.decide(morningNow, fakeForecast(), history)
        assertEquals("Expected exactly one Daily decision", 1, result.size)
        assertEquals(Channel.Daily, result[0].channel)
    }

    @Test
    fun `decide is silent when lastDailySent is today`() {
        val history = historyWith(lastDailySent = today)
        val result = NotificationDecider.decide(morningNow, fakeForecast(), history)
        assertTrue("Expected no decisions when already sent today", result.isEmpty())
    }

    @Test
    fun `decide is silent on forecast change after send today`() {
        val history = historyWith(lastDailySent = today)
        // Simulate a forecast update (higher UV) arriving after the notification was already sent
        val updatedForecast = fakeForecast(dailyMax = 11.0)
        val result = NotificationDecider.decide(morningNow, updatedForecast, history)
        assertTrue("Expected no re-fire even when forecast changed after send", result.isEmpty())
    }

    @Test
    fun `decide returns nothing when forecast is null`() {
        val history = historyWith(lastDailySent = null)
        val result = NotificationDecider.decide(morningNow, null, history)
        assertTrue("Expected no decisions when forecast is unavailable", result.isEmpty())
    }

    @Test
    fun `decide returns nothing outside morning window`() {
        val afternoon = morningNow.withHour(14)
        val history = historyWith(lastDailySent = null)
        val result = NotificationDecider.decide(afternoon, fakeForecast(), history)
        assertTrue("Expected no decisions outside morning window", result.isEmpty())
    }

    // ── UV Warning channel decide() tests ────────────────────────────────────

    @Test
    fun `decide emits UV Warning Prelude when currentUV below 6 and next hour UV at or above 6`() {
        val now = morningNow.withHour(10)
        val forecast = forecastWithUV(atHour = 10, currentUV = 3.0, nextHourUV = 7.0)
        val result = NotificationDecider.decide(now, forecast, historyForUvWarning())
        val uvDecision = result.find { it.channel == Channel.UvWarning }
        assertNotNull("Expected UV Warning decision for Prelude", uvDecision)
        assertEquals(Phase.Prelude, uvDecision!!.phase)
    }

    @Test
    fun `decide emits UV Warning InWindow when currentUV at or above 6`() {
        val now = morningNow.withHour(11)
        val forecast = forecastWithUV(atHour = 11, currentUV = 7.0)
        val result = NotificationDecider.decide(now, forecast, historyForUvWarning())
        val uvDecision = result.find { it.channel == Channel.UvWarning }
        assertNotNull("Expected UV Warning decision for InWindow", uvDecision)
        assertEquals(Phase.InWindow, uvDecision!!.phase)
    }

    @Test
    fun `decide is silent for UV Warning when uvWarningEnabled is false`() {
        val now = morningNow.withHour(11)
        val forecast = forecastWithUV(atHour = 11, currentUV = 7.0)
        val result = NotificationDecider.decide(now, forecast, historyForUvWarning(uvWarningEnabled = false))
        assertTrue("Expected no UV Warning when disabled in settings", result.none { it.channel == Channel.UvWarning })
    }

    @Test
    fun `decide is silent for UV Warning when uvWarningDisabledOn is today`() {
        val now = morningNow.withHour(11)
        val forecast = forecastWithUV(atHour = 11, currentUV = 7.0)
        val history = historyForUvWarning(uvWarningDisabledOn = now.toLocalDate())
        val result = NotificationDecider.decide(now, forecast, history)
        assertTrue("Expected no UV Warning after user disabled today", result.none { it.channel == Channel.UvWarning })
    }

    @Test
    fun `decide is silent for UV Warning when lastUvWarningOn is today`() {
        val now = morningNow.withHour(11)
        val forecast = forecastWithUV(atHour = 11, currentUV = 7.0)
        val history = historyForUvWarning(lastUvWarningOn = now.toLocalDate())
        val result = NotificationDecider.decide(now, forecast, history)
        assertTrue("Expected no UV Warning when already sent today", result.none { it.channel == Channel.UvWarning })
    }
}
