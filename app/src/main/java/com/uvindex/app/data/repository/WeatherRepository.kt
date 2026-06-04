package com.uvindex.app.data.repository

import android.content.Context
import android.util.Log
import com.uvindex.app.data.api.RetrofitClient
import com.uvindex.app.data.local.DataStoreManager
import com.uvindex.app.data.location.LocationService
import com.uvindex.app.data.model.CachedWeatherData
import com.uvindex.app.data.model.HourlyForecast
import com.uvindex.app.data.model.TimeSlot
import com.uvindex.app.data.model.UVForecast
import com.uvindex.app.util.CacheManager
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class WeatherRepository(context: Context) {

    companion object {
        private const val TAG = "WeatherRepository"
    }

    private val api = RetrofitClient.api
    private val dataStore = DataStoreManager(context)
    private val locationService = LocationService(context)
    private val cacheManager = CacheManager(dataStore, locationService)
    private val json = Json { ignoreUnknownKeys = true }

    private val CACHE_VALIDITY_HOURS = 3

    /**
     * Fetches UV forecast from cache or API.
     *
     * Cache logic:
     * - Uses cache if < 3 hours old AND location has not changed by > 20km
     * - Makes API call if cache is > 3 hours old OR location is > 20km away
     * - forceRefresh = true bypasses cache and always fetches fresh data
     *
     * This reduces API calls and makes optimal use of cached data.
     */
    suspend fun getUVForecast(forceRefresh: Boolean = false): Result<UVForecast> {
        Log.d(TAG, "getUVForecast called (forceRefresh=$forceRefresh)")
        return try {
            Log.d(TAG, "Requesting current location...")
            val currentLocation = locationService.getCurrentLocation()
            if (currentLocation == null) {
                Log.w(TAG, "Location unavailable - trying stored coordinates")
                val storedLocation = dataStore.getLastLocation().first()
                if (storedLocation != null) {
                    Log.d(TAG, "Falling back to stored coordinates: lat=${storedLocation.latitude}, lon=${storedLocation.longitude}")
                    return fetchWithCoordinates(storedLocation.latitude, storedLocation.longitude)
                }
                Log.w(TAG, "No stored coordinates - falling back to cache")
                val cached = getCachedForecast()
                return if (cached != null) {
                    Result.success(cached)
                } else {
                    Log.e(TAG, "No cache available and no location - returning failure")
                    Result.failure(Exception("Standort konnte nicht ermittelt werden"))
                }
            }
            Log.d(TAG, "Location obtained: lat=${currentLocation.latitude}, lon=${currentLocation.longitude}")

            // Check cache age and location change (except when forceRefresh is true)
            val shouldFetchNew = forceRefresh || shouldFetchNewData(
                currentLocation.latitude,
                currentLocation.longitude
            )
            Log.d(TAG, "shouldFetchNew=$shouldFetchNew (forceRefresh=$forceRefresh)")

            if (!shouldFetchNew) {
                val cached = getCachedForecast()
                if (cached != null) {
                    Log.d(TAG, "Using cached data (location=${cached.locationName}, currentHourUV=${cached.currentHour.uvIndex})")
                    return Result.success(cached)
                }
                Log.d(TAG, "Cache miss despite shouldFetchNew=false, fetching from API")
            }

            return fetchWithCoordinates(currentLocation.latitude, currentLocation.longitude)
        } catch (e: Exception) {
            Log.e(TAG, "Exception in getUVForecast: ${e.message}", e)
            val cached = getCachedForecast()
            if (cached != null) {
                Log.d(TAG, "Exception recovery: using cached data (location=${cached.locationName})")
                Result.success(cached)
            } else {
                Log.e(TAG, "Exception recovery failed: no cache available")
                Result.failure(e)
            }
        }
    }

    private suspend fun fetchWithCoordinates(latitude: Double, longitude: Double): Result<UVForecast> {
        Log.d(TAG, "Fetching weather data from API...")
        val response = api.getWeatherForecast(
            latitude = latitude,
            longitude = longitude
        )
        Log.d(TAG, "API response received: ${response.hourly.time.size} hourly entries")

        val airQuality = try {
            val aqiResponse = api.getAirQuality(
                latitude = latitude,
                longitude = longitude
            )
            Log.d(TAG, "AQI fetched: ${aqiResponse.current.europeanAqi}")
            aqiResponse.current.europeanAqi
        } catch (e: Exception) {
            Log.w(TAG, "AQI fetch failed: ${e.message}")
            null
        }

        val locationName = locationService.getCityName(latitude, longitude)
        Log.d(TAG, "Location name: $locationName")

        val countryCode = locationService.getCountryCode(latitude, longitude)

        val forecast = parseWeatherResponse(response, locationName, airQuality, countryCode)
        Log.d(TAG, "Forecast parsed: dailyMax=${forecast.dailyMax}, currentHourUV=${forecast.currentHour.uvIndex}")

        val cachedData = CachedWeatherData(
            forecast = forecast,
            latitude = latitude,
            longitude = longitude,
            timestamp = System.currentTimeMillis()
        )
        dataStore.saveCachedWeatherData(json.encodeToString(cachedData))
        dataStore.saveLastLocation(latitude, longitude)
        Log.d(TAG, "Data cached successfully")

        return Result.success(forecast)
    }

    /**
     * Fetches cached UV forecast without accessing location.
     * Ideal for widgets running in the background.
     */
    suspend fun getCachedForecastForWidget(): Result<UVForecast> {
        Log.d(TAG, "getCachedForecastForWidget called")
        return try {
            val cached = getCachedForecast()
            if (cached != null) {
                Log.d(TAG, "Widget cache hit: location=${cached.locationName}, currentHourUV=${cached.currentHour.uvIndex}, dailyMax=${cached.dailyMax}")
                Result.success(cached)
            } else {
                Log.w(TAG, "Widget cache miss: no cached data available")
                Result.failure(Exception("Keine gecachten Daten verfügbar"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun shouldFetchNewData(currentLat: Double, currentLon: Double): Boolean {
        // Use CacheManager for location- and time-based validation
        val needsRefresh = cacheManager.shouldRefresh(
            currentLat = currentLat,
            currentLon = currentLon,
            maxAgeHours = CACHE_VALIDITY_HOURS,
            checkLocation = true
        )

        if (needsRefresh) {
            return true
        }

        // Additional check: is the cache from today?
        val cachedForecast = getCachedForecast()
        if (cachedForecast != null) {
            val now = LocalDateTime.now()
            val firstForecastTime = cachedForecast.allDayForecasts.firstOrNull()?.time
            if (firstForecastTime != null) {
                val forecastDate = LocalDateTime.parse(firstForecastTime, DateTimeFormatter.ISO_DATE_TIME)
                // If not from the same day, fetch new data
                if (forecastDate.dayOfYear != now.dayOfYear || forecastDate.year != now.year) {
                    return true
                }
            }
        }

        return false
    }

    private suspend fun getCachedForecast(): UVForecast? {
        val cachedJson = dataStore.getCachedWeatherData().first()
        if (cachedJson == null) {
            Log.d(TAG, "getCachedForecast: no cached JSON in DataStore")
            return null
        }
        return try {
            val cached = json.decodeFromString<CachedWeatherData>(cachedJson)
            Log.d(TAG, "getCachedForecast: cache loaded (lat=${cached.latitude}, lon=${cached.longitude}, age=${(System.currentTimeMillis() - cached.timestamp) / 60000}min)")
            // Re-parse with current time and latitude so that currentHour, nextHours and clearSkyMax are up to date
            reParseWithCurrentTime(cached.forecast, cached.latitude)
        } catch (e: Exception) {
            Log.e(TAG, "getCachedForecast: failed to parse cached data: ${e.message}", e)
            null
        }
    }

    private fun reParseWithCurrentTime(oldForecast: UVForecast, latitude: Double): UVForecast? {
        val now = LocalDateTime.now()

        // Check if data is from the same day
        val firstForecastTime = oldForecast.allDayForecasts.firstOrNull()?.time
        if (firstForecastTime != null) {
            val forecastDate = LocalDateTime.parse(firstForecastTime, DateTimeFormatter.ISO_DATE_TIME)
            // If data is not from today, return null (triggers a new API call)
            if (forecastDate.dayOfYear != now.dayOfYear || forecastDate.year != now.year) {
                return null
            }
        }

        // Find the current hour in allDayForecasts
        val currentHourForecast = oldForecast.allDayForecasts.find {
            it.hour == now.hour
        } ?: oldForecast.allDayForecasts.firstOrNull() ?: return oldForecast

        // Find next hours (after current hour)
        val nextHours = oldForecast.allDayForecasts
            .filter { it.hour > currentHourForecast.hour }
            .take(3)

        // Maximum of all hours of the day (for app display)
        val dailyMax = oldForecast.allDayForecasts.maxOf { it.uvIndex }

        // Calculate maximum of remaining hours (for widget)
        val remainingHours = oldForecast.allDayForecasts.filter { it.hour >= currentHourForecast.hour }
        val dailyMaxRemaining = if (remainingHours.isNotEmpty()) {
            remainingHours.maxOf { it.uvIndex }
        } else {
            currentHourForecast.uvIndex
        }

        // Find the hour with the actual (float) maximum in the remaining hours
        // In case of a tie: prefer the earliest hour
        val maxHourToday = remainingHours.maxByOrNull { it.uvIndex }?.hour
            ?: currentHourForecast.hour

        // Recalculate hourly clear-sky UV values
        // This is important because the solar elevation changes every hour
        val clearSkyHourly = calculateClearSkyHourly(
            latitude = latitude,
            dateTime = now,
            todayForecasts = oldForecast.allDayForecasts
        )

        // Clear-sky value for the current hour
        val clearSkyMax = if (now.hour in clearSkyHourly.indices) {
            clearSkyHourly[now.hour]
        } else {
            0.0
        }

        // If no lastUpdateTime is present (old cache), generate one
        val updateTime = oldForecast.lastUpdateTime ?: run {
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date())
        }

        return UVForecast(
            currentHour = currentHourForecast,
            nextHours = nextHours,
            dailyMax = dailyMax,
            dailyMaxRemaining = dailyMaxRemaining,
            clearSkyMax = clearSkyMax,
            clearSkyHourly = clearSkyHourly,
            maxHourToday = maxHourToday,
            highUVTimeSlots = oldForecast.highUVTimeSlots,
            locationName = oldForecast.locationName,
            allDayForecasts = oldForecast.allDayForecasts,
            airQuality = oldForecast.airQuality,
            lastUpdateTime = updateTime,
            countryCode = oldForecast.countryCode
        )
    }

    private fun parseWeatherResponse(response: com.uvindex.app.data.model.WeatherResponse, locationName: String?, airQuality: Double?, countryCode: String?): UVForecast {
        val now = LocalDateTime.now()
        val currentHourIndex = response.hourly.time.indexOfFirst { timeStr ->
            val hour = LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_DATE_TIME).hour
            hour == now.hour
        }

        val hourlyForecasts = response.hourly.time.mapIndexed { index, timeStr ->
            val dateTime = LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_DATE_TIME)
            HourlyForecast(
                time = timeStr,
                hour = dateTime.hour,
                uvIndex = response.hourly.uvIndex[index],
                temperature = response.hourly.temperature[index].roundToInt().toDouble()
            )
        }

        val todayForecasts = hourlyForecasts.filter {
            LocalDateTime.parse(it.time, DateTimeFormatter.ISO_DATE_TIME).dayOfYear == now.dayOfYear
        }

        val currentHour = if (currentHourIndex >= 0) {
            hourlyForecasts[currentHourIndex]
        } else {
            todayForecasts.first()
        }

        val nextHours = todayForecasts
            .filter { it.hour > currentHour.hour }
            .take(3)

        // Maximum of all hours of the day (for app display)
        val dailyMax = todayForecasts.maxOf { it.uvIndex }

        // Maximum of remaining hours (for widget and new tile)
        val remainingHours = todayForecasts.filter { it.hour >= currentHour.hour }
        val dailyMaxRemaining = if (remainingHours.isNotEmpty()) {
            remainingHours.maxOf { it.uvIndex }
        } else {
            currentHour.uvIndex
        }

        // Find the hour with the actual (float) maximum in the remaining hours
        // In case of a tie: prefer the earliest hour
        val maxHourToday = remainingHours.maxByOrNull { it.uvIndex }?.hour
            ?: currentHour.hour

        val highUVTimeSlots = findHighUVTimeSlots(todayForecasts)

        // Current timestamp
        val currentTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())

        // Calculate hourly clear-sky UV values for the entire day
        val clearSkyHourly = calculateClearSkyHourly(
            latitude = response.latitude ?: 0.0,
            dateTime = now,
            todayForecasts = todayForecasts
        )

        // Clear-sky value for the current hour
        val clearSkyMax = if (now.hour in clearSkyHourly.indices) {
            clearSkyHourly[now.hour]
        } else {
            0.0
        }

        return UVForecast(
            currentHour = currentHour,
            nextHours = nextHours,
            dailyMax = dailyMax,
            dailyMaxRemaining = dailyMaxRemaining,
            clearSkyMax = clearSkyMax,
            clearSkyHourly = clearSkyHourly,
            maxHourToday = maxHourToday,
            highUVTimeSlots = highUVTimeSlots,
            locationName = locationName,
            allDayForecasts = todayForecasts,
            airQuality = airQuality,
            lastUpdateTime = currentTime,
            countryCode = countryCode
        )
    }

    private fun findHighUVTimeSlots(forecasts: List<HourlyForecast>): List<TimeSlot> {
        val slots = mutableListOf<TimeSlot>()
        var startHour: Int? = null

        forecasts.sortedBy { it.hour }.forEach { forecast ->
            if (forecast.uvIndex >= 4.0) {
                if (startHour == null) {
                    startHour = forecast.hour
                }
            } else {
                if (startHour != null) {
                    slots.add(TimeSlot(startHour!!, forecast.hour - 1))
                    startHour = null
                }
            }
        }

        if (startHour != null) {
            val lastHour = forecasts.filter { it.uvIndex >= 4.0 }.maxOfOrNull { it.hour } ?: forecasts.last().hour
            slots.add(TimeSlot(startHour!!, lastHour))
        }

        return slots
    }

    /**
     * Calculates hourly clear-sky UV values for all hours of the day.
     *
     * @param latitude Location latitude
     * @param dateTime Current date/time
     * @param todayForecasts Hourly forecasts for today
     * @return List of 24 clear-sky UV values (index = hour 0–23)
     */
    private fun calculateClearSkyHourly(
        latitude: Double,
        dateTime: LocalDateTime,
        todayForecasts: List<HourlyForecast>
    ): List<Double> {
        val dayOfYear = dateTime.dayOfYear
        val month = dateTime.monthValue

        val seasonalModifier = when (month) {
            12, 1, 2 -> 0.8
            3, 4, 5 -> 1.0
            6, 7, 8 -> 1.2
            else -> 1.0
        }

        return (0..23).map { hour ->
            val solarElevation = calculateSolarElevation(latitude, dayOfYear, hour)
            if (solarElevation <= 0) {
                0.0
            } else {
                val clearSkyBase = when {
                    solarElevation < 10 -> 0.5
                    solarElevation < 20 -> 2.0
                    solarElevation < 30 -> 4.0
                    solarElevation < 45 -> 6.0
                    solarElevation < 60 -> 9.0
                    solarElevation < 75 -> 12.0
                    else -> 15.0
                }

                val estimatedClearSky = clearSkyBase * seasonalModifier
                val forecastedUV = todayForecasts.find { it.hour == hour }?.uvIndex ?: 0.0

                maxOf(estimatedClearSky, forecastedUV * 1.3).coerceIn(0.0, 16.0)
            }
        }
    }

    /**
     * Calculates the solar elevation angle for a given hour.
     *
     * @param latitude Latitude in degrees
     * @param dayOfYear Day of the year (1–365/366)
     * @param hour Hour of the day (0–23)
     * @return Solar elevation in degrees (0 = horizon, 90 = zenith)
     */
    private fun calculateSolarElevation(latitude: Double, dayOfYear: Int, hour: Int): Double {
        // Solar declination (angle of the sun above/below the equator)
        val declination = calculateSolarDeclinationFactor(dayOfYear)

        // DST correction: during daylight saving time the solar noon is at ~13:00 instead of 12:00
        val isDST = java.time.ZonedDateTime.now().zone.rules
            .isDaylightSavings(java.time.Instant.now())
        val solarHour = if (isDST) hour - 1.0 else hour.toDouble()

        // Hour angle (0° = solar noon, 15° per hour)
        // 12:00 = 0°, 13:00 = 15°, 11:00 = -15°
        val hourAngle = (solarHour - 12.0) * 15.0

        // Convert to radians
        val latRad = Math.toRadians(latitude)
        val decRad = Math.toRadians(declination)
        val hourRad = Math.toRadians(hourAngle)

        // Calculate solar elevation using the formula:
        // sin(elevation) = sin(lat) * sin(dec) + cos(lat) * cos(dec) * cos(hourAngle)
        val sinElevation = Math.sin(latRad) * Math.sin(decRad) +
                Math.cos(latRad) * Math.cos(decRad) * Math.cos(hourRad)

        val elevation = Math.toDegrees(Math.asin(sinElevation.coerceIn(-1.0, 1.0)))

        return elevation
    }

    /**
     * Calculates the solar declination factor for a given day.
     *
     * @param dayOfYear Day of the year (1–365/366)
     * @return Declination angle in degrees
     */
    private fun calculateSolarDeclinationFactor(dayOfYear: Int): Double {
        // Simplified calculation of solar declination
        // Maximum (~23.44°) around summer solstice (day 172)
        // Minimum (~-23.44°) around winter solstice (day 355)
        val angle = 2.0 * Math.PI * (dayOfYear - 81) / 365.0
        return 23.44 * Math.sin(angle)
    }
}