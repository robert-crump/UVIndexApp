package com.uvindex.app.data.repository

import android.content.Context
import android.util.Log
import com.uvindex.app.data.api.RetrofitClient
import com.uvindex.app.data.local.DataStoreManager
import com.uvindex.app.data.location.LocationService
import com.uvindex.app.data.model.CachedWeatherData
import com.uvindex.app.data.model.HourlyForecast
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
            aqiResponse.current.europeanAqi.roundToInt().toDouble()
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
            // Re-parse with current time so that currentHour and nextHours are up to date
            reParseWithCurrentTime(cached.forecast)
        } catch (e: Exception) {
            Log.e(TAG, "getCachedForecast: failed to parse cached data: ${e.message}", e)
            null
        }
    }

    private fun reParseWithCurrentTime(oldForecast: UVForecast): UVForecast? {
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
            maxHourToday = maxHourToday,
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
                uvIndex = response.hourly.uvIndex[index].roundToInt().toDouble(),
                temperature = response.hourly.temperature[index].roundToInt().toDouble(),
                windSpeed = response.hourly.windSpeed.getOrNull(index)?.roundToInt()?.toDouble() ?: 0.0,
                windDirection = response.hourly.windDirection.getOrNull(index) ?: 0.0
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

        // Current timestamp
        val currentTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())

        return UVForecast(
            currentHour = currentHour,
            nextHours = nextHours,
            dailyMax = dailyMax,
            dailyMaxRemaining = dailyMaxRemaining,
            maxHourToday = maxHourToday,
            locationName = locationName,
            allDayForecasts = todayForecasts,
            airQuality = airQuality,
            lastUpdateTime = currentTime,
            countryCode = countryCode
        )
    }

}