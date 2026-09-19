package com.uvindex.app.data.model

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Raw, cache-persisted forecast inputs. Derived fields are never stored. */
data class ForecastSnapshot(
    val rows: List<HourlyForecast>,
    val locationName: String?,
    val airQuality: Double?,
    val countryCode: String?,
    val fetchedAt: LocalDateTime
)

/**
 * The single place that decides what "today", "current hour", "next hours" and "remaining hours" mean.
 * Pure: the clock is passed in. Returns null when the rows contain nothing for [now]'s date.
 */
fun deriveUVForecast(snapshot: ForecastSnapshot, now: LocalDateTime): UVForecast? {
    val today = now.toLocalDate()
    val todayRows = snapshot.rows.filter {
        LocalDateTime.parse(it.time, DateTimeFormatter.ISO_DATE_TIME).toLocalDate() == today
    }
    if (todayRows.isEmpty()) return null

    val currentHour = todayRows.firstOrNull { it.hour == now.hour } ?: todayRows.first()
    val remaining = todayRows.filter { it.hour >= currentHour.hour }

    return UVForecast(
        currentHour = currentHour,
        nextHours = todayRows.filter { it.hour > currentHour.hour }.take(3),
        dailyMax = todayRows.maxOf { it.uvIndex },
        dailyMaxRemaining = remaining.maxOf { it.uvIndex },
        // maxByOrNull keeps the first maximum, so ties resolve to the earliest hour
        maxHourToday = remaining.maxByOrNull { it.uvIndex }!!.hour,
        locationName = snapshot.locationName,
        allDayForecasts = todayRows,
        airQuality = snapshot.airQuality,
        lastUpdateTime = snapshot.fetchedAt.format(DateTimeFormatter.ofPattern("HH:mm")),
        countryCode = snapshot.countryCode
    )
}
