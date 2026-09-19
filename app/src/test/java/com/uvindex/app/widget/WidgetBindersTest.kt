package com.uvindex.app.widget

import com.uvindex.app.data.model.HourlyForecast
import com.uvindex.app.data.model.UVForecast
import com.uvindex.app.uv.SkinType
import com.uvindex.app.uv.UvRisk
import com.uvindex.app.wind.CompassOctant
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetBindersTest {

    private fun row(hour: Int, uv: Double, wind: Double = 0.0, dir: Double = 0.0) =
        HourlyForecast("2026-09-19T%02d:00".format(hour), hour, uv, 20.0, wind, dir)

    private fun forecast(
        rows: List<HourlyForecast>,
        currentHour: Int = rows.first().hour,
        aqi: Double? = null
    ) = UVForecast(
        currentHour = rows.first { it.hour == currentHour }, nextHours = emptyList(), dailyMax = 0.0,
        dailyMaxRemaining = 0.0, maxHourToday = currentHour, locationName = null, allDayForecasts = rows,
        airQuality = aqi, lastUpdateTime = null, countryCode = null
    )

    // Hourly 4x1

    @Test
    fun `hourly with four or more remaining hours fills four cells from the current hour`() {
        val rows = (8..16).map { row(it, uv = (it - 8).toDouble()) }
        val binding = bindHourlyUv(forecast(rows, currentHour = 10))
        assertEquals(listOf("10:00", "11:00", "12:00", "13:00"), binding.cells.map { it.timeText })
        assertEquals(listOf("2", "3", "4", "5"), binding.cells.map { it.uvText })
    }

    @Test
    fun `hourly with fewer than four remaining hours pads with neutral placeholder cells`() {
        val rows = (8..20).map { row(it, uv = 1.0) }
        val binding = bindHourlyUv(forecast(rows, currentHour = 19))
        assertEquals(4, binding.cells.size)
        assertEquals(listOf("19:00", "20:00", WIDGET_PLACEHOLDER, WIDGET_PLACEHOLDER), binding.cells.map { it.timeText })
        assertEquals(WidgetTone.Neutral, binding.cells[2].tone)
        assertEquals(WidgetTone.Neutral, binding.cells[3].tone)
        assertEquals(WIDGET_PLACEHOLDER, binding.cells[3].uvText)
    }

    @Test
    fun `hourly end cells take the risk category of their hour`() {
        val rows = listOf(row(10, 3.0), row(11, 4.0), row(12, 6.0), row(13, 8.0))
        val cells = bindHourlyUv(forecast(rows)).cells
        assertEquals(WidgetTone.Risk(UvRisk.Moderate), cells.first().tone)
        assertEquals(WidgetTone.Risk(UvRisk.VeryHigh), cells.last().tone)
    }

    @Test
    fun `hourly null forecast is four placeholder cells`() {
        val binding = bindHourlyUv(null)
        assertEquals(4, binding.cells.size)
        binding.cells.forEach {
            assertEquals(HourlyUvCell(WIDGET_PLACEHOLDER, WIDGET_PLACEHOLDER, WidgetTone.Neutral), it)
        }
    }

    // Wind

    @Test
    fun `wind shows rounded speed and travel octant`() {
        val binding = bindWind(forecast(listOf(row(12, 1.0, wind = 12.4, dir = 0.0))))
        assertEquals("12 km/h", binding.speedText)
        assertEquals(CompassOctant.S, binding.arrow)
    }

    @Test
    fun `wind null forecast yields placeholder`() {
        assertEquals(WIDGET_PLACEHOLDER, bindWind(null).speedText)
    }

    // Air quality

    @Test
    fun `air quality shows the truncated value`() {
        val binding = bindAirQuality(forecast(listOf(row(12, 1.0)), aqi = 34.7))
        assertEquals("34", binding.valueText)
        assertEquals(34.7, binding.aqi!!, 0.0)
    }

    @Test
    fun `air quality null forecast or null reading yields placeholder`() {
        assertEquals(WIDGET_PLACEHOLDER, bindAirQuality(null).valueText)
        val noReading = bindAirQuality(forecast(listOf(row(12, 1.0)), aqi = null))
        assertEquals(WIDGET_PLACEHOLDER, noReading.valueText)
        assertEquals(null, noReading.aqi)
    }

    // Self protection

    @Test
    fun `self protection shows compact protection time`() {
        val binding = bindSelfProtection(forecast(listOf(row(12, 8.0))), SkinType.TYPE_II)
        assertEquals("20m", binding.valueText)
        assertEquals(WidgetTone.Risk(UvRisk.VeryHigh), binding.tone)
        assertEquals(WidgetTapTarget.MainActivity, binding.tapTarget)
    }

    @Test
    fun `self protection null forecast yields placeholder`() {
        val binding = bindSelfProtection(null, SkinType.TYPE_II)
        assertEquals(WIDGET_PLACEHOLDER, binding.valueText)
        assertEquals(WidgetTone.Neutral, binding.tone)
    }

    @Test
    fun `self protection without skin type prompts and opens settings`() {
        val binding = bindSelfProtection(forecast(listOf(row(12, 8.0))), null)
        assertEquals(SKIN_TYPE_PROMPT, binding.valueText)
        assertEquals(WidgetTapTarget.Settings, binding.tapTarget)
        assertEquals(WidgetTapTarget.Settings, bindSelfProtection(null, null).tapTarget)
    }

    @Test
    fun `self protection with UV below 1 shows placeholder`() {
        val binding = bindSelfProtection(forecast(listOf(row(12, 0.9))), SkinType.TYPE_II)
        assertEquals(WIDGET_PLACEHOLDER, binding.valueText)
        assertEquals(WidgetTone.Risk(UvRisk.None), binding.tone)
    }
}
