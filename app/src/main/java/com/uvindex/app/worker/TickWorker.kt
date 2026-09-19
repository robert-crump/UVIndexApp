package com.uvindex.app.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.uvindex.app.data.repository.WeatherRepository
import com.uvindex.app.notification.NotificationDecider
import com.uvindex.app.notification.NotificationDispatcher
import com.uvindex.app.notification.SharedPreferencesNotificationHistoryStore
import com.uvindex.app.schedule.FetchIntent
import com.uvindex.app.schedule.fetchIntentFor
import com.uvindex.app.util.WidgetUpdateHelper
import java.time.ZonedDateTime

/** The one background worker: fetch per policy, decide and dispatch notifications, refresh widgets. */
class TickWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private companion object {
        const val TAG = "TickWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            val now = ZonedDateTime.now()
            val repository = WeatherRepository(applicationContext)
            val forceRefresh = when (fetchIntentFor(now.toLocalTime())) {
                FetchIntent.Fresh -> true
                FetchIntent.FreshIfStale -> repository.isCacheStale()
                FetchIntent.Cached -> false
            }

            val forecastResult = repository.getUVForecast(forceRefresh = forceRefresh)
            val forecast = forecastResult.getOrNull()
            if (forecast == null) {
                Log.e(TAG, "Failed to get forecast: ${forecastResult.exceptionOrNull()?.message}")
                return Result.retry()
            }

            val historyStore = SharedPreferencesNotificationHistoryStore(applicationContext)
            val dispatcher = NotificationDispatcher(applicationContext)
            val decisions = NotificationDecider.decide(now, forecast, historyStore.snapshot())
            for (decision in decisions) {
                if (dispatcher.send(decision)) {
                    historyStore.record(decision, now.toInstant())
                }
            }

            WidgetUpdateHelper.updateAllWidgets(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker exception", e)
            Result.failure()
        }
    }
}
