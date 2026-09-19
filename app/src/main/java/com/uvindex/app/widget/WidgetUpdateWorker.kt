package com.uvindex.app.widget

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.uvindex.app.data.repository.WeatherRepository
import com.uvindex.app.schedule.BackgroundSchedule
import com.uvindex.app.util.WidgetUpdateHelper

class WidgetUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "WidgetUpdateWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "WidgetUpdateWorker started")

            // Get forceRefresh parameter (default: false for reparse)
            val forceRefresh = inputData.getBoolean(BackgroundSchedule.KEY_FORCE_REFRESH, false)
            Log.d(TAG, "Force refresh: $forceRefresh")

            // Hole neue Daten vom Repository
            val repository = WeatherRepository(applicationContext)
            repository.getUVForecast(forceRefresh = forceRefresh).fold(
                onSuccess = {
                    Log.d(TAG, "Data refreshed successfully")
                    // Update widgets with the new data
                    WidgetUpdateHelper.updateAllWidgets(applicationContext)
                    Result.success()
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to refresh data: ${error.message}")
                    // Still update widgets with cached data (with reparse)
                    if (forceRefresh) {
                        // On error with forceRefresh, fall back to cache
                        repository.getUVForecast(forceRefresh = false).fold(
                            onSuccess = {
                                WidgetUpdateHelper.updateAllWidgets(applicationContext)
                                Result.success()
                            },
                            onFailure = {
                                Result.retry()
                            }
                        )
                    } else {
                        Result.retry()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception in WidgetUpdateWorker", e)
            Result.retry()
        }
    }
}