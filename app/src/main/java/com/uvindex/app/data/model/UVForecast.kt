package com.uvindex.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UVForecast(
    val currentHour: HourlyForecast,
    val nextHours: List<HourlyForecast>,
    val dailyMax: Double,
    val dailyMaxRemaining: Double,
    val maxHourToday: Int,    // Hour of the daily UV peak
    val locationName: String?,
    val allDayForecasts: List<HourlyForecast>,
    val airQuality: Double?,
    val lastUpdateTime: String?,
    val countryCode: String?
)

@Serializable
data class HourlyForecast(
    val time: String,
    val hour: Int,
    val uvIndex: Double,
    val temperature: Double,
    val windSpeed: Double = 0.0,      // km/h, rounded; 0.0 default keeps old caches deserializable
    val windDirection: Double = 0.0   // source bearing in degrees (where the wind comes FROM)
)

@Serializable
data class CachedWeatherData(
    val rows: List<HourlyForecast>,
    val locationName: String?,
    val countryCode: String?,
    val airQuality: Double?,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)
