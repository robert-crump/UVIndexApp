package com.uvindex.app.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.uvindex.app.data.local.DataStoreManager
import com.uvindex.app.data.location.LocationService
import com.uvindex.app.data.repository.WeatherRepository
import com.uvindex.app.notification.NotificationDecider
import com.uvindex.app.notification.NotificationDispatcher
import com.uvindex.app.notification.SharedPreferencesNotificationHistoryStore
import com.uvindex.app.util.CacheManager
import com.uvindex.app.util.WidgetUpdateHelper
import java.time.ZonedDateTime

class HourlyUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val repository = WeatherRepository(context)
    private val historyStore = SharedPreferencesNotificationHistoryStore(context)
    private val dispatcher = NotificationDispatcher(context)

    companion object {
        private const val TAG = "HourlyUpdateWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "=== Hourly worker started ===")
        return try {
            val now = ZonedDateTime.now()
            val currentHour = now.hour
            val currentMinute = now.minute

            val shouldFetchNew = when {
                currentHour in 6..17 && currentMinute == 0 -> true
                currentHour !in 6..17 && currentMinute == 0 -> shouldFetchByTime()
                else -> false
            }

            val forecastResult = repository.getUVForecast(forceRefresh = shouldFetchNew)

            forecastResult.fold(
                onSuccess = { forecast ->
                    Log.d(TAG, "Forecast loaded successfully")

                    val history = historyStore.snapshot()
                    val decisions = NotificationDecider.decide(now, forecast, history)
                    for (decision in decisions) {
                        if (dispatcher.send(decision)) {
                            historyStore.record(decision, now.toInstant())
                        }
                    }

                    if (shouldFetchNew) {
                        Log.d(TAG, "Updating widgets")
                        WidgetUpdateHelper.updateAllWidgets(applicationContext)
                    }

                    Log.d(TAG, "=== Worker completed successfully ===")
                    Result.success()
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to get forecast: ${error.message}")
                    Result.retry()
                },
            )
        } catch (e: Exception) {
            Log.e(TAG, "Worker exception", e)
            Result.failure()
        }
    }

    private suspend fun shouldFetchByTime(): Boolean {
        val dataStore = DataStoreManager(applicationContext)
        val locationService = LocationService(applicationContext)
        val cacheManager = CacheManager(dataStore, locationService)
        return cacheManager.isStale(maxAgeHours = 3)
    }
}
