package com.uvindex.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.uvindex.app.data.repository.WeatherRepository

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
            val forceRefresh = inputData.getBoolean("force_refresh", false)
            Log.d(TAG, "Force refresh: $forceRefresh")

            // Hole neue Daten vom Repository
            val repository = WeatherRepository(applicationContext)
            repository.getUVForecast(forceRefresh = forceRefresh).fold(
                onSuccess = {
                    Log.d(TAG, "Data refreshed successfully")
                    // Update widgets with the new data
                    updateAllWidgets()
                    Result.success()
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to refresh data: ${error.message}")
                    // Still update widgets with cached data (with reparse)
                    if (forceRefresh) {
                        // On error with forceRefresh, fall back to cache
                        repository.getUVForecast(forceRefresh = false).fold(
                            onSuccess = {
                                updateAllWidgets()
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

    private fun updateAllWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)

        // Update 4x1 Widget (UVWidget)
        val uvWidgetIntent = Intent(applicationContext, UVWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        }
        val uvWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(applicationContext, UVWidget::class.java)
        )
        uvWidgetIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, uvWidgetIds)
        applicationContext.sendBroadcast(uvWidgetIntent)
        Log.d(TAG, "UVWidget updated (${uvWidgetIds.size} instances)")

        // Update 2x2 Max Widget (falls vorhanden)
        try {
            val maxWidgetIntent = Intent(applicationContext, UVWidgetMax::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val maxWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(applicationContext, UVWidgetMax::class.java)
            )
            maxWidgetIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, maxWidgetIds)
            applicationContext.sendBroadcast(maxWidgetIntent)
            Log.d(TAG, "UVWidgetMax updated (${maxWidgetIds.size} instances)")
        } catch (e: Exception) {
            Log.w(TAG, "UVWidgetMax not found or update failed: ${e.message}")
        }

        // Update 1x1 Wind Widget (falls vorhanden)
        try {
            val windWidgetIntent = Intent(applicationContext, WindWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val windWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(applicationContext, WindWidget::class.java)
            )
            windWidgetIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, windWidgetIds)
            applicationContext.sendBroadcast(windWidgetIntent)
            Log.d(TAG, "WindWidget updated (${windWidgetIds.size} instances)")
        } catch (e: Exception) {
            Log.w(TAG, "WindWidget not found or update failed: ${e.message}")
        }

        // Update 1x1 Self-protection Time Widget (falls vorhanden)
        try {
            val selfProtectionWidgetIntent = Intent(applicationContext, SelfProtectionTimeWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val selfProtectionWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(applicationContext, SelfProtectionTimeWidget::class.java)
            )
            selfProtectionWidgetIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, selfProtectionWidgetIds)
            applicationContext.sendBroadcast(selfProtectionWidgetIntent)
            Log.d(TAG, "SelfProtectionTimeWidget updated (${selfProtectionWidgetIds.size} instances)")
        } catch (e: Exception) {
            Log.w(TAG, "SelfProtectionTimeWidget not found or update failed: ${e.message}")
        }

        Log.d(TAG, "All widgets updated")
    }
}