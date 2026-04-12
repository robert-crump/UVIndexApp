package com.uvindex.app.ui.components

import android.graphics.Color
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.uvindex.app.data.model.UVForecast
import com.uvindex.app.ui.theme.UVColorHelper

/**
 * Reusable UV index bar chart component.
 *
 * @param forecast UV forecast data
 * @param modifier Modifier for layout customization
 */
@Composable
fun UVBarChart(
    forecast: UVForecast,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AndroidView(
        factory = { ctx ->
            BarChart(ctx).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                setDragEnabled(true)
                setScaleEnabled(false)
                setPinchZoom(false)
                setDrawGridBackground(false)
                legend.isEnabled = false
                setDrawBarShadow(false)
                setDrawValueAboveBar(true)

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    granularity = 1f
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "${value.toInt()}"
                        }
                    }
                }

                axisLeft.apply {
                    setDrawGridLines(true)
                    axisMinimum = 0f
                    granularity = 1f
                }

                axisRight.isEnabled = false
            }
        },
        update = { chart ->
            val entries = forecast.allDayForecasts.map { hourForecast ->
                BarEntry(
                    hourForecast.hour.toFloat(),
                    hourForecast.uvIndex.toInt().toFloat()
                )
            }

            val dataSet = BarDataSet(entries, "UV-Index").apply {
                valueTextSize = 12f
                setDrawValues(true)
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString()
                    }
                }

                colors = entries.map { entry ->
                    UVColorHelper.getColorInt(entry.y.toDouble(), context)
                }
            }

            val barData = BarData(dataSet).apply {
                barWidth = 0.8f
            }

            chart.data = barData
            chart.setVisibleXRangeMaximum(8f)
            chart.setVisibleXRangeMinimum(8f)

            val currentHour = forecast.currentHour.hour
            val maxHour = forecast.allDayForecasts.maxOfOrNull { it.hour } ?: 23

            if (currentHour + 7 <= maxHour) {
                chart.moveViewToX(currentHour.toFloat() - 0.4f)
            } else {
                val startHour = maxOf(0, maxHour - 7)
                chart.moveViewToX(startHour.toFloat() - 0.4f)
            }

            chart.invalidate()
        },
        modifier = modifier
    )
}

/**
 * Reusable clear-sky UV index bar chart component.
 * Displays hourly clear-sky UV values for the entire day.
 *
 * @param forecast UV forecast data (contains clearSkyHourly)
 * @param modifier Modifier for layout customization
 */
@Composable
fun ClearSkyBarChart(
    forecast: UVForecast,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AndroidView(
        factory = { ctx ->
            BarChart(ctx).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                setDragEnabled(true)
                setScaleEnabled(false)
                setPinchZoom(false)
                setDrawGridBackground(false)
                legend.isEnabled = false
                setDrawBarShadow(false)
                setDrawValueAboveBar(true)

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    granularity = 1f
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "${value.toInt()}"
                        }
                    }
                }

                axisLeft.apply {
                    setDrawGridLines(true)
                    axisMinimum = 0f
                    granularity = 1f
                }

                axisRight.isEnabled = false
            }
        },
        update = { chart ->
            // Use allDayForecasts hours as X-axis, clearSkyHourly as Y values
            val hours = forecast.allDayForecasts.map { it.hour }
            val entries = hours.map { hour ->
                val clearSkyValue = if (hour in forecast.clearSkyHourly.indices) {
                    forecast.clearSkyHourly[hour]
                } else {
                    0.0
                }
                BarEntry(hour.toFloat(), clearSkyValue.toInt().toFloat())
            }

            val dataSet = BarDataSet(entries, "Clear-Sky UV").apply {
                valueTextSize = 12f
                setDrawValues(true)
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString()
                    }
                }

                colors = entries.map { entry ->
                    UVColorHelper.getColorInt(entry.y.toDouble(), context)
                }
            }

            val barData = BarData(dataSet).apply {
                barWidth = 0.8f
            }

            chart.data = barData
            chart.setVisibleXRangeMaximum(8f)
            chart.setVisibleXRangeMinimum(8f)

            val currentHour = forecast.currentHour.hour
            val maxHour = forecast.allDayForecasts.maxOfOrNull { it.hour } ?: 23

            if (currentHour + 7 <= maxHour) {
                chart.moveViewToX(currentHour.toFloat() - 0.4f)
            } else {
                val startHour = maxOf(0, maxHour - 7)
                chart.moveViewToX(startHour.toFloat() - 0.4f)
            }

            chart.invalidate()
        },
        modifier = modifier
    )
}

/**
 * Reusable temperature line chart component.
 *
 * @param forecast UV forecast data (also contains temperatures)
 * @param modifier Modifier for layout customization
 */
@Composable
fun TemperatureLineChart(
    forecast: UVForecast,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            LineChart(ctx).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                setDragEnabled(true)
                setScaleEnabled(false)
                setPinchZoom(false)
                setDrawGridBackground(false)
                legend.isEnabled = false

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    granularity = 1f
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "${value.toInt()}"
                        }
                    }
                }

                axisLeft.apply {
                    setDrawGridLines(true)
                    granularity = 5f  // Y-axis in 5°C steps
                }

                axisRight.isEnabled = false
            }
        },
        update = { chart ->
            val entries = forecast.allDayForecasts.map { hourForecast ->
                Entry(
                    hourForecast.hour.toFloat(),
                    hourForecast.temperature.toInt().toFloat()  // Rounded for consistent data points
                )
            }

            val dataSet = LineDataSet(entries, "Temperatur").apply {
                color = Color.parseColor("#FF6200EE")
                lineWidth = 2.5f
                setDrawCircles(true)
                setDrawValues(true)
                valueTextSize = 10f
                circleRadius = 4f
                setCircleColor(Color.parseColor("#FF6200EE"))
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "${value.toInt()}°"
                    }
                }
            }

            val lineData = LineData(dataSet)
            chart.data = lineData

            // Calculate min/max for Y-axis scaling
            val minTemp = entries.minOfOrNull { it.y }?.toInt() ?: 0
            val maxTemp = entries.maxOfOrNull { it.y }?.toInt() ?: 30

            // Round to nearest 5-step intervals (no extra step)
            // For minTemp: round DOWN (floor) - e.g. 12°C → 10°C, -4°C → -5°C
            val yAxisMin = if (minTemp >= 0) {
                (minTemp / 5) * 5
            } else {
                ((minTemp - 4) / 5) * 5  // Correctly floors negative numbers
            }

            // For maxTemp: round UP (ceil) - e.g. 22°C → 25°C, 33°C → 35°C
            val yAxisMax = if (maxTemp >= 0) {
                ((maxTemp + 4) / 5) * 5
            } else {
                (maxTemp / 5) * 5
            }

            chart.axisLeft.apply {
                axisMinimum = yAxisMin.toFloat()
                axisMaximum = yAxisMax.toFloat()
                granularity = 5f  // Y-axis in 5°C steps
                setLabelCount((yAxisMax - yAxisMin) / 5 + 1, true)
            }

            chart.setVisibleXRangeMaximum(8f)
            chart.setVisibleXRangeMinimum(8f)

            val currentHour = forecast.currentHour.hour
            val maxHour = forecast.allDayForecasts.maxOfOrNull { it.hour } ?: 23

            if (currentHour + 7 <= maxHour) {
                chart.moveViewToX(currentHour.toFloat() - 0.4f)
            } else {
                val startHour = maxOf(0, maxHour - 7)
                chart.moveViewToX(startHour.toFloat() - 0.4f)
            }

            chart.invalidate()
        },
        modifier = modifier
    )
}