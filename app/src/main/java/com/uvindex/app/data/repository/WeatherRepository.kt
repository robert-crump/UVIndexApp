package com.uvindex.app.data.repository

import android.content.Context
import android.util.Log
import com.uvindex.app.data.api.RetrofitClient
import com.uvindex.app.data.local.DataStoreManager
import com.uvindex.app.data.location.LocationService
import com.uvindex.app.data.model.CachedWeatherData
import com.uvindex.app.data.model.HourlyForecast
import com.uvindex.app.data.model.UVForecast
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
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Reads the UV forecast according to [intent]; the repository alone decides whether that
     * means a network call.
     *
     * - [FetchIntent.Fresh]: always fetch, falling back to cache on failure
     * - [FetchIntent.FreshIfStale]: fetch only if [shouldFetch] says so (cache older than
     *   [CACHE_TTL], location moved more than [MOVE_THRESHOLD_KM], or cache not from today)
     * - [FetchIntent.CachedOnly]: cache only, never touches network or location
     */
    suspend fun getUVForecast(intent: FetchIntent): Result<UVForecast> {
        Log.d(TAG, "getUVForecast called (intent=$intent)")
        val now = LocalDateTime.now()
        if (intent == FetchIntent.CachedOnly) return cachedOnly(now)
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

            val shouldFetchNew = intent == FetchIntent.Fresh || shouldFetchNewData(
                currentLocation.latitude,
                currentLocation.longitude,
                now
            )
            Log.d(TAG, "shouldFetchNew=$shouldFetchNew (intent=$intent)")

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

    private suspend fun cachedOnly(now: LocalDateTime): Result<UVForecast> = try {
        val cached = getCachedForecast(now)
        if (cached != null) {
            Log.d(TAG, "Cache hit: location=${cached.locationName}, currentHourUV=${cached.currentHour.uvIndex}, dailyMax=${cached.dailyMax}")
            Result.success(cached)
        } else {
            Log.w(TAG, "Cache miss: no cached data available")
            Result.failure(Exception("Keine gecachten Daten verfÃ¼gbar"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun shouldFetchNewData(currentLat: Double?, currentLon: Double?, now: LocalDateTime): Boolean {
        val cache = readCache()?.let {
            val rowDate = it.rows.firstOrNull()?.let { row ->
                LocalDateTime.parse(row.time, DateTimeFormatter.ISO_DATE_TIME).toLocalDate()
            }
            rowDate?.let { date -> CacheMetadata(fetchedAt(it.timestamp), it.latitude, it.longitude, date) }
        }
        return shouldFetch(cache, currentLat, currentLon, now)
    }

    private suspend fun readCache(): CachedWeatherData? {
        val cachedJson = dataStore.getCachedWeatherData().first() ?: return null
        return try {
            json.decodeFromString<CachedWeatherData>(cachedJson)
        } catch (e: Exception) {
            // Includes caches written by older builds (missing fields): treated as a miss
            Log.e(TAG, "readCache: failed to parse cached data: ${e.message}", e)
            null
        }
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
