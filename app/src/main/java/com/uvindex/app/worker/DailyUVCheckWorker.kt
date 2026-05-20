package com.uvindex.app.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.uvindex.app.data.repository.WeatherRepository
import com.uvindex.app.notification.NotificationDecider
import com.uvindex.app.notification.NotificationDispatcher
import com.uvindex.app.notification.SharedPreferencesNotificationHistoryStore
import java.time.Instant
import java.time.ZoneId

class DailyUVCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val repository = WeatherRepository(context)
    private val historyStore = SharedPreferencesNotificationHistoryStore(context)
    private val dispatcher = NotificationDispatcher(context)

    companion object {
        private const val TAG = "DailyUVCheckWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Daily UV worker started")
        return try {
            val forecastResult = repository.getUVForecast(forceRefresh = true)
            if (forecastResult.isFailure) {
                Log.e(TAG, "Failed to get forecast: ${forecastResult.exceptionOrNull()?.message}")
                return Result.retry()
            }

            val forecast = forecastResult.getOrNull()
            val now = Instant.now().atZone(ZoneId.systemDefault())
            val history = historyStore.snapshot()
            val decisions = NotificationDecider.decide(now, forecast, history)

            for (decision in decisions) {
                if (dispatcher.send(decision)) {
                    historyStore.record(decision, now.toInstant())
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker exception", e)
            Result.failure()
        }
    }
}
