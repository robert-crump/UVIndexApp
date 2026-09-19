package com.uvindex.app.widget

import com.uvindex.app.data.model.HourlyForecast
import com.uvindex.app.data.model.UVForecast
import com.uvindex.app.uv.UvRisk
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrentUvBinderTest {

    private fun forecast(uv: Double, hour: Int = 13): UVForecast {
        val current = HourlyForecast("2026-09-19T%02d:00".format(hour), hour, uv, 20.0)
        return UVForecast(
            currentHour = current, nextHours = emptyList(), dailyMax = uv, dailyMaxRemaining = uv,
            maxHourToday = hour, locationName = null, allDayForecasts = listOf(current),
            airQuality = null, lastUpdateTime = null, countryCode = null
        )
    }

    @Test
    fun `UV 0, 3, 6, 8 pick the expected risk category`() {
        val expected = mapOf(0.0 to UvRisk.None, 3.0 to UvRisk.Moderate, 6.0 to UvRisk.High, 8.0 to UvRisk.VeryHigh)
        expected.forEach { (uv, risk) ->
            val binding = bindCurrentUv(forecast(uv))
            assertEquals(WidgetTone.Risk(risk), binding.tone)
            assertEquals(uv.toInt().toString(), binding.valueText)
        }
    }

    @Test
    fun `formats the hour`() {
        assertEquals("09:00", bindCurrentUv(forecast(2.0, hour = 9)).timeText)
    }

    @Test
    fun `null forecast yields error placeholder and neutral tone`() {
        val binding = bindCurrentUv(null)
        assertEquals(WIDGET_PLACEHOLDER, binding.valueText)
        assertEquals(WIDGET_PLACEHOLDER, binding.timeText)
        assertEquals(WidgetTone.Neutral, binding.tone)
    }

    @Test
    fun `tap target is the main activity`() {
        assertEquals(WidgetTapTarget.MainActivity, bindCurrentUv(forecast(5.0)).tapTarget)
        assertEquals(WidgetTapTarget.MainActivity, bindCurrentUv(null).tapTarget)
    }
}
