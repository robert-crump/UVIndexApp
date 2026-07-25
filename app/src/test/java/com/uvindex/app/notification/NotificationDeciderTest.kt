package com.uvindex.app.notification

import com.uvindex.app.data.model.HourlyForecast
import com.uvindex.app.data.model.UVForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
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
        lastUvWarning: WarnedAbout? = null,
    ) = NotificationHistory(
        dailyEnabled = false,
        uvWarningEnabled = uvWarningEnabled,
        uvWarningDisabledOn = uvWarningDisabledOn,
        lastDailySent = null,
        lastDailySentAt = null,
        lastUvWarningOn = lastUvWarningOn,
        lastUvWarningAt = null,
        lastUvWarning = lastUvWarning,
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

    /** Forecast for Daily-content tests: dailyMax + allDayForecasts control the category/window math. */
    private fun dailyForecast(
        dailyMax: Double,
        locationName: String? = "Aachen",
        allDayForecasts: List<HourlyForecast> = listOf(HourlyForecast("2026-05-20T12:00", 12, dailyMax, 22.0)),
    ): UVForecast {
        val hour = allDayForecasts.first()
        return UVForecast(
            currentHour = hour,
            nextHours = emptyList(),
            dailyMax = dailyMax,
            dailyMaxRemaining = dailyMax,
            clearSkyMax = dailyMax,
            clearSkyHourly = emptyList(),
            maxHourToday = hour.hour,
            highUVTimeSlots = emptyList(),
            locationName = locationName,
            allDayForecasts = allDayForecasts,
            airQuality = null,
            lastUpdateTime = null,
            countryCode = null,
        )
    }

    // ── Daily channel content tests (#28) ──────────────────────────────────

    @Test
    fun `Daily title includes location name`() {
        val history = historyWith(lastDailySent = today.minusDays(1))
        val result = NotificationDecider.decide(morningNow, dailyForecast(dailyMax = 2.0), history)
        assertEquals("Tagesprognose Aachen", result[0].title)
    }

    @Test
    fun `Daily title falls back to generic when locationName is null`() {
        val history = historyWith(lastDailySent = today.minusDays(1))
        val forecast = dailyForecast(dailyMax = 2.0, locationName = null)
        val result = NotificationDecider.decide(morningNow, forecast, history)
        assertEquals("Tagesprognose", result[0].title)
    }

    @Test
    fun `Daily content for niedrig category has no appended text`() {
        val history = historyWith(lastDailySent = today.minusDays(1))
        val result = NotificationDecider.decide(morningNow, dailyForecast(dailyMax = 2.0), history)
        assertEquals("Max. 2 (niedrig).", result[0].body)
    }

    @Test
    fun `Daily content for mittel category appends Schutzempfehlung`() {
        val history = historyWith(lastDailySent = today.minusDays(1))
        val result = NotificationDecider.decide(morningNow, dailyForecast(dailyMax = 4.0), history)
        assertEquals("Max. 4 (mittel). Schutzempfehlung: Sonnenbrille, Sonnencreme.", result[0].body)
    }

    @Test
    fun `Daily content for hoch category appends sun-avoidance window and Schutzempfehlung`() {
        val history = historyWith(lastDailySent = today.minusDays(1))
        val forecast = dailyForecast(
            dailyMax = 7.0,
            allDayForecasts = listOf(
                HourlyForecast("2026-05-20T12:00", 12, 6.0, 22.0),
                HourlyForecast("2026-05-20T13:00", 13, 7.0, 22.0),
                HourlyForecast("2026-05-20T14:00", 14, 6.0, 22.0),
            ),
        )
        val result = NotificationDecider.decide(morningNow, forecast, history)
        assertEquals(
            "Max. 7 (hoch). Zwischen 12-15 Uhr direkte Sonne vermeiden." +
                " Schutzempfehlung: Schatten, Sonnenbrille, Sonnencreme.",
            result[0].body,
        )
    }

    @Test
    fun `Daily content for sehr hoch category also appends sun-avoidance window and Schutzempfehlung`() {
        val history = historyWith(lastDailySent = today.minusDays(1))
        val result = NotificationDecider.decide(morningNow, dailyForecast(dailyMax = 9.0), history)
        assertEquals(
            "Max. 9 (sehr hoch). Zwischen 12-13 Uhr direkte Sonne vermeiden." +
                " Schutzempfehlung: Schatten, Sonnenbrille, Sonnencreme.",
            result[0].body,
        )
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
    fun `UV Warning Prelude body uses bare hour formatting, no leading zeros or minutes`() {
        val now = morningNow.withHour(10)
        val forecast = forecastWithUV(atHour = 10, currentUV = 3.0, nextHourUV = 7.0)
        val result = NotificationDecider.decide(now, forecast, historyForUvWarning())
        val body = result.find { it.channel == Channel.UvWarning }!!.body
        assertTrue("Expected bare hour 'ab 11 Uhr', got: $body", body.contains("ab 11 Uhr"))
        assertFalse("Expected no ':00' minutes in body: $body", body.contains(":00"))
    }

    @Test
    fun `UV Warning InWindow body uses bare hour formatting, no leading zeros or minutes`() {
        val now = morningNow.withHour(11)
        val forecast = forecastWithHoursAt(now.hour, mapOf(11 to 7.0, 12 to 9.0))
        val result = NotificationDecider.decide(now, forecast, historyForUvWarning())
        val body = result.find { it.channel == Channel.UvWarning }!!.body
        assertTrue("Expected bare hours 'zwischen 11 und 13 Uhr', got: $body", body.contains("zwischen 11 und 13 Uhr"))
        assertFalse("Expected no ':00' minutes in body: $body", body.contains(":00"))
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

    /** Forecast where [hourUVs] maps each hour to its UV index; [nowHour] is the current hour. */
    private fun forecastWithHoursAt(nowHour: Int, hourUVs: Map<Int, Double>): UVForecast {
        val entries = hourUVs.entries.sortedBy { it.key }.map { (h, uv) ->
            HourlyForecast("2026-05-20T${"%02d".format(h)}:00", h, uv, 22.0)
        }
        val maxUV = entries.maxOf { it.uvIndex }
        val currentEntry = entries.find { it.hour == nowHour } ?: entries.first()
        return UVForecast(
            currentHour = currentEntry,
            nextHours = entries.filter { it.hour > nowHour },
            dailyMax = maxUV,
            dailyMaxRemaining = maxUV,
            clearSkyMax = 10.0,
            clearSkyHourly = emptyList(),
            maxHourToday = entries.maxByOrNull { it.uvIndex }?.hour ?: nowHour,
            highUVTimeSlots = emptyList(),
            locationName = "Test",
            allDayForecasts = entries,
            airQuality = null,
            lastUpdateTime = null,
            countryCode = null,
        )
    }

    @Test
    fun `decide is silent for UV Warning when lastUvWarningOn is today and forecast unchanged`() {
        val now = morningNow.withHour(11)
        val forecast = forecastWithUV(atHour = 11, currentUV = 7.0)
        // lastUvWarning matches current forecast exactly → worseNews returns false → no re-fire
        val previousWarning = WarnedAbout(peak = 7.0f, firstHighHour = 11, highHours = setOf(11))
        val history = historyForUvWarning(lastUvWarningOn = now.toLocalDate(), lastUvWarning = previousWarning)
        val result = NotificationDecider.decide(now, forecast, history)
        assertTrue("Expected no UV Warning when already sent today with unchanged forecast", result.none { it.channel == Channel.UvWarning })
    }

    // ── worseNews unit tests ──────────────────────────────────────────────────

    private fun nowAt(hour: Int): LocalDateTime =
        LocalDateTime.now().withHour(hour).withMinute(0).withSecond(0).withNano(0)

    @Test
    fun `worseNews returns true when previous is null`() {
        val current = WarnedAbout(peak = 7.0f, firstHighHour = 10, highHours = setOf(10, 11))
        assertTrue(NotificationDecider.worseNews(null, current, nowAt(9)))
    }

    @Test
    fun `worseNews returns false for identical content`() {
        val w = WarnedAbout(peak = 7.0f, firstHighHour = 10, highHours = setOf(10, 11, 12))
        assertFalse(NotificationDecider.worseNews(w, w, nowAt(9)))
    }

    @Test
    fun `worseNews returns true when current peak is higher`() {
        val prev = WarnedAbout(peak = 6.5f, firstHighHour = 10, highHours = setOf(10, 11))
        val curr = WarnedAbout(peak = 8.0f, firstHighHour = 10, highHours = setOf(10, 11))
        assertTrue(NotificationDecider.worseNews(prev, curr, nowAt(9)))
    }

    @Test
    fun `worseNews returns true when current has new high hour at or after now`() {
        val prev = WarnedAbout(peak = 7.0f, firstHighHour = 10, highHours = setOf(10, 11, 12))
        val curr = WarnedAbout(peak = 7.0f, firstHighHour = 10, highHours = setOf(10, 11, 12, 13))
        assertTrue(NotificationDecider.worseNews(prev, curr, nowAt(11)))
    }

    @Test
    fun `worseNews returns true when current firstHighHour is earlier`() {
        val prev = WarnedAbout(peak = 7.0f, firstHighHour = 11, highHours = setOf(11, 12))
        val curr = WarnedAbout(peak = 7.0f, firstHighHour = 10, highHours = setOf(10, 11, 12))
        assertTrue(NotificationDecider.worseNews(prev, curr, nowAt(9)))
    }

    @Test
    fun `worseNews returns false when forecast shrinks`() {
        val prev = WarnedAbout(peak = 8.0f, firstHighHour = 10, highHours = setOf(10, 11, 12, 13))
        val curr = WarnedAbout(peak = 7.0f, firstHighHour = 10, highHours = setOf(10, 11))
        // curr has no new hours >= 11 not in prev; peak is lower
        assertFalse(NotificationDecider.worseNews(prev, curr, nowAt(11)))
    }

    @Test
    fun `worseNews returns false when same-length window shifts forward one hour`() {
        // #27: 14-15 (2-4pm) shifts to 15-16 (3-5pm) as hour 14 becomes past and drops out.
        // Same length window, no genuine growth -> must not count as Worse News.
        val prev = WarnedAbout(peak = 7.0f, firstHighHour = 14, highHours = setOf(14, 15))
        val curr = WarnedAbout(peak = 7.0f, firstHighHour = 15, highHours = setOf(15, 16))
        assertFalse(NotificationDecider.worseNews(prev, curr, nowAt(15)))
    }

    @Test
    fun `worseNews returns true when window shifts forward and also grows`() {
        val prev = WarnedAbout(peak = 7.0f, firstHighHour = 14, highHours = setOf(14, 15))
        val curr = WarnedAbout(peak = 7.0f, firstHighHour = 15, highHours = setOf(15, 16, 17))
        assertTrue(NotificationDecider.worseNews(prev, curr, nowAt(15)))
    }

    // ── UV Warning re-fire (Worse News) decide() tests ─────────────────────────

    @Test
    fun `decide re-fires UV Warning when sent today but peak is higher`() {
        val now = morningNow.withHour(11)
        val forecast = forecastWithHoursAt(now.hour, mapOf(11 to 7.0, 12 to 8.5))
        val previousWarning = WarnedAbout(peak = 6.5f, firstHighHour = 11, highHours = setOf(11, 12))
        val history = historyForUvWarning(lastUvWarningOn = today, lastUvWarning = previousWarning)
        val result = NotificationDecider.decide(now, forecast, history)
        val uvDecision = result.find { it.channel == Channel.UvWarning }
        assertNotNull("Expected UV Warning re-fire on higher peak", uvDecision)
        assertEquals(8.5f, uvDecision!!.warnedAbout!!.peak)
    }

    @Test
    fun `decide re-fires UV Warning when sent today but new high hour discovered after now`() {
        val now = morningNow.withHour(11)
        val forecast = forecastWithHoursAt(now.hour, mapOf(10 to 7.0, 11 to 7.0, 12 to 7.0, 13 to 7.0))
        val previousWarning = WarnedAbout(peak = 7.0f, firstHighHour = 10, highHours = setOf(10, 11, 12))
        val history = historyForUvWarning(lastUvWarningOn = today, lastUvWarning = previousWarning)
        val result = NotificationDecider.decide(now, forecast, history)
        assertNotNull("Expected UV Warning re-fire on new high hour after now", result.find { it.channel == Channel.UvWarning })
    }

    @Test
    fun `decide re-fires UV Warning when sent today but firstHighHour moved earlier`() {
        val now = morningNow.withHour(10)
        val forecast = forecastWithHoursAt(now.hour, mapOf(10 to 7.0, 11 to 7.0, 12 to 7.0))
        val previousWarning = WarnedAbout(peak = 7.0f, firstHighHour = 11, highHours = setOf(11, 12))
        val history = historyForUvWarning(lastUvWarningOn = today, lastUvWarning = previousWarning)
        val result = NotificationDecider.decide(now, forecast, history)
        assertNotNull("Expected UV Warning re-fire when firstHighHour moved earlier", result.find { it.channel == Channel.UvWarning })
    }

    @Test
    fun `decide stays silent when sent today and forecast window shrunk`() {
        val now = morningNow.withHour(11)
        val forecast = forecastWithHoursAt(now.hour, mapOf(10 to 6.5, 11 to 7.0))
        val previousWarning = WarnedAbout(peak = 8.0f, firstHighHour = 10, highHours = setOf(10, 11, 12, 13))
        val history = historyForUvWarning(lastUvWarningOn = today, lastUvWarning = previousWarning)
        val result = NotificationDecider.decide(now, forecast, history)
        assertTrue("Expected no re-fire on shrunk window", result.none { it.channel == Channel.UvWarning })
    }

    @Test
    fun `decide stays silent when sent today and window merely slid forward one hour`() {
        // Reproduces #27: first fire covered 14-15 (2-4pm); by the time hour 14 is past,
        // the forecast reclassifies it away while hour 16 newly crosses the threshold,
        // reporting 15-16 (3-5pm). Same-length shift, not new information -> no re-fire.
        val now = morningNow.withHour(15)
        val forecast = forecastWithHoursAt(now.hour, mapOf(15 to 7.0, 16 to 7.0))
        val previousWarning = WarnedAbout(peak = 7.0f, firstHighHour = 14, highHours = setOf(14, 15))
        val history = historyForUvWarning(lastUvWarningOn = today, lastUvWarning = previousWarning)
        val result = NotificationDecider.decide(now, forecast, history)
        assertTrue("Expected no re-fire when window merely slid forward", result.none { it.channel == Channel.UvWarning })
    }

    @Test
    fun `decide stays silent when disabled today even if forecast worsened`() {
        val now = morningNow.withHour(11)
        val forecast = forecastWithHoursAt(now.hour, mapOf(11 to 9.0, 12 to 9.0, 13 to 9.0))
        val previousWarning = WarnedAbout(peak = 6.5f, firstHighHour = 11, highHours = setOf(11))
        val history = historyForUvWarning(
            uvWarningDisabledOn = today,
            lastUvWarningOn = today,
            lastUvWarning = previousWarning,
        )
        val result = NotificationDecider.decide(now, forecast, history)
        assertTrue("Expected UV Warning blocked when disabled today, even with worse news", result.none { it.channel == Channel.UvWarning })
    }
}
