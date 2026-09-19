package com.uvindex.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class UVForecastDerivationTest {

    private fun row(day: String, hour: Int, uv: Double) =
        HourlyForecast("${day}T${"%02d".format(hour)}:00", hour, uv, 20.0)

    private fun snapshot(rows: List<HourlyForecast>) = ForecastSnapshot(
        rows = rows,
        locationName = "Berlin",
        airQuality = 30.0,
        countryCode = "DE",
        fetchedAt = LocalDateTime.of(2026, 5, 20, 6, 5)
    )

    private val day = "2026-05-20"
    private fun now(hour: Int, minute: Int = 30) = LocalDateTime.of(2026, 5, 20, hour, minute)
    private fun dayRows(vararg uv: Double) = uv.mapIndexed { i, v -> row(day, 8 + i, v) }

    @Test
    fun currentHourMatchesClockHour() {
        val f = deriveUVForecast(snapshot(dayRows(1.0, 3.0, 5.0, 4.0, 2.0)), now(10))!!
        assertEquals(10, f.currentHour.hour)
        assertEquals(listOf(11, 12), f.nextHours.map { it.hour })
        assertEquals("06:05", f.lastUpdateTime)
    }

    @Test
    fun noMatchFallsBackToFirstRowOfToday() {
        val f = deriveUVForecast(snapshot(dayRows(1.0, 3.0)), now(22))!!
        assertEquals(8, f.currentHour.hour)
    }

    @Test
    fun endOfDayHasNoNextHours() {
        val f = deriveUVForecast(snapshot(dayRows(1.0, 3.0, 2.0)), now(10))!!
        assertEquals(emptyList<HourlyForecast>(), f.nextHours)
        assertEquals(2.0, f.dailyMaxRemaining, 0.0)
    }

    @Test
    fun nextHoursCappedAtThree() {
        val f = deriveUVForecast(snapshot(dayRows(1.0, 1.0, 1.0, 1.0, 1.0, 1.0)), now(8))!!
        assertEquals(3, f.nextHours.size)
    }

    @Test
    fun remainingMaxTieUsesEarliestHour() {
        val f = deriveUVForecast(snapshot(dayRows(9.0, 2.0, 6.0, 4.0, 6.0)), now(9))!!
        assertEquals(6.0, f.dailyMaxRemaining, 0.0)
        assertEquals(10, f.maxHourToday)
        assertEquals(9.0, f.dailyMax, 0.0)
    }

    @Test
    fun rowsFromPreviousDayYieldNull() {
        val rows = listOf(row("2026-05-19", 12, 5.0))
        assertNull(deriveUVForecast(snapshot(rows), now(12)))
    }

    @Test
    fun onlyTodaysRowsAreUsed() {
        val rows = listOf(row("2026-05-19", 10, 9.0)) + dayRows(1.0, 2.0, 3.0)
        val f = deriveUVForecast(snapshot(rows), now(10))!!
        assertNotNull(f)
        assertEquals(3, f.allDayForecasts.size)
        assertEquals(3.0, f.dailyMax, 0.0)
    }
}
