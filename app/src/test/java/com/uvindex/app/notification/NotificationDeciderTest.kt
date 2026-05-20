package com.uvindex.app.notification

import com.uvindex.app.data.model.HourlyForecast
import com.uvindex.app.data.model.UVForecast
import org.junit.Assert.assertEquals
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

    private fun historyWith(
        dailyEnabled: Boolean = true,
        lastDailySent: LocalDate? = null,
    ) = NotificationHistory(
        dailyEnabled = dailyEnabled,
        uvWarningEnabled = true,
        uvWarningDisabledOn = null,
        lastDailySent = lastDailySent,
        lastDailySentAt = null,
        lastUvWarningOn = null,
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
}
