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
import com.uvindex.app.data.model.ForecastSnapshot
import com.uvindex.app.data.model.WeatherResponse
import com.uvindex.app.data.model.deriveUVForecast
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
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
        val now = LocalDateTime.now()
        return try {
            Log.d(TAG, "Requesting current location...")
            val currentLocation = locationService.getCurrentLocation()
            if (currentLocation == null) {
                Log.w(TAG, "Location unavailable - trying stored coordinates")
                val storedLocation = dataStore.getLastLocation().first()
                if (storedLocation != null) {
                    Log.d(TAG, "Falling back to stored coordinates: lat=${storedLocation.latitude}, lon=${storedLocation.longitude}")
                    return fetchWithCoordinates(storedLocation.latitude, storedLocation.longitude, now)
                }
                Log.w(TAG, "No stored coordinates - falling back to cache")
                val cached = getCachedForecast(now)
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
                currentLocation.longitude,
                now
            )
            Log.d(TAG, "shouldFetchNew=$shouldFetchNew (forceRefresh=$forceRefresh)")

            if (!shouldFetchNew) {
                val cached = getCachedForecast(now)
                if (cached != null) {
                    Log.d(TAG, "Using cached data (location=${cached.locationName}, currentHourUV=${cached.currentHour.uvIndex})")
                    return Result.success(cached)
                }
                Log.d(TAG, "Cache miss despite shouldFetchNew=false, fetching from API")
            }

            return fetchWithCoordinates(currentLocation.latitude, currentLocation.longitude, now)
        } catch (e: Exception) {
            Log.e(TAG, "Exception in getUVForecast: ${e.message}", e)
            val cached = getCachedForecast(now)
            if (cached != null) {
                Log.d(TAG, "Exception recovery: using cached data (location=${cached.locationName})")
                Result.success(cached)
            } else {
                Log.e(TAG, "Exception recovery failed: no cache available")
                Result.failure(e)
            }
        }
    }

    private suspend fun fetchWithCoordinates(latitude: Double, longitude: Double, now: LocalDateTime): Result<UVForecast> {
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

        val timestamp = System.currentTimeMillis()
        val rows = parseRows(response)
        val cachedData = CachedWeatherData(
            rows = rows,
            locationName = locationName,
            countryCode = countryCode,
            airQuality = airQuality,
            latitude = latitude,
            longitude = longitude,
            timestamp = timestamp
        )
        dataStore.saveCachedWeatherData(json.encodeToString(cachedData))
        dataStore.saveLastLocation(latitude, longitude)
        Log.d(TAG, "Data cached successfully")

        val forecast = deriveUVForecast(
            ForecastSnapshot(rows, locationName, airQuality, countryCode, fetchedAt(timestamp)),
            now
        ) ?: return Result.failure(Exception("Keine Prognosedaten für heute"))
        Log.d(TAG, "Forecast derived: dailyMax=${forecast.dailyMax}, currentHourUV=${forecast.currentHour.uvIndex}")

        return Result.success(forecast)
    }

    /**
     * Fetches cached UV forecast without accessing location.
     * Ideal for widgets running in the background.
     */
    suspend fun getCachedForecastForWidget(): Result<UVForecast> {
        val now = LocalDateTime.now()
        Log.d(TAG, "getCachedForecastForWidget called")
        return try {
            val cached = getCachedForecast(now)
            if (cached != null) {
                Log.d(TAG, "Widget cache hit: location=${cached.locationName}, currentHourUV=${cached.currentHour.uvIndex}, dailyMax=${cached.dailyMax}")
                Result.success(cached)
            } else {
                Log.w(TAG, "Widget cache miss: no cached data available")
                Result.failure(Exception("Keine gecachten Daten verfÃ¼gbar"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun shouldFetchNewData(currentLat: Double, currentLon: Double, now: LocalDateTime): Boolean {
        // Use CacheManager for location- and time-based validation
        val needsRefresh = cacheManager.shouldRefresh(
            currentLat = currentLat,
            currentLon = currentLon,
            maxAgeHours = CACHE_VALIDITY_HOURS,
            checkLocation = true
        )
        // Cache with no rows for today derives to null, which also means "fetch"
        return needsRefresh || getCachedForecast(now) == null
    }

    private suspend fun getCachedForecast(now: LocalDateTime): UVForecast? {
        val cachedJson = dataStore.getCachedWeatherData().first()
        if (cachedJson == null) {
            Log.d(TAG, "getCachedForecast: no cached JSON in DataStore")
            return null
        }
        return try {
            val cached = json.decodeFromString<CachedWeatherData>(cachedJson)
            Log.d(TAG, "getCachedForecast: cache loaded (lat=${cached.latitude}, lon=${cached.longitude}, age=${(System.currentTimeMillis() - cached.timestamp) / 60000}min)")
            deriveUVForecast(
                ForecastSnapshot(
                    rows = cached.rows,
                    locationName = cached.locationName,
                    airQuality = cached.airQuality,
                    countryCode = cached.countryCode,
                    fetchedAt = fetchedAt(cached.timestamp)
                ),
                now
            )
        } catch (e: Exception) {
            // Includes caches written by older builds (missing fields): treated as a miss
            Log.e(TAG, "getCachedForecast: failed to parse cached data: ${e.message}", e)
            null
        }
    }

    private fun fetchedAt(epochMillis: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())

    private fun parseRows(response: WeatherResponse): List<HourlyForecast> =
        response.hourly.time.mapIndexed { index, timeStr ->
            HourlyForecast(
                time = timeStr,
                hour = LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_DATE_TIME).hour,
                uvIndex = response.hourly.uvIndex[index].roundToInt().toDouble(),
                temperature = response.hourly.temperature[index].roundToInt().toDouble(),
                windSpeed = response.hourly.windSpeed.getOrNull(index)?.roundToInt()?.toDouble() ?: 0.0,
                windDirection = response.hourly.windDirection.getOrNull(index) ?: 0.0
            )
        }
}
