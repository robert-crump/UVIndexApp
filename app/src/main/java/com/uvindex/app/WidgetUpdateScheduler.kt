package com.uvindex.app.widget

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Scheduler for periodic widget updates
 */
object WidgetUpdateScheduler {

    private const val TAG = "WidgetUpdateScheduler"
    private const val PERIODIC_UPDATE_WORK_NAME = "widget_periodic_update"
    private const val MIDNIGHT_UPDATE_WORK_NAME = "widget_midnight_update"

    /**
     * Schedules periodic updates every 15 minutes
     */
    fun schedulePeriodicUpdates(context: Context) {
        Log.d(TAG, "Scheduling periodic widget updates")

        val periodicWork = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES // Flex-Intervall
        )
            .setInputData(workDataOf("force_refresh" to false))
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_UPDATE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWork
        )

        Log.d(TAG, "Periodic updates scheduled (every 15 minutes)")

        // Schedule midnight update
        scheduleMidnightUpdate(context)
    }

    /**
     * Schedules an update at midnight (new day)
     */
    private fun scheduleMidnightUpdate(context: Context) {
        val currentTime = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = currentTime
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If midnight has already passed, schedule for tomorrow
            if (timeInMillis <= currentTime) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val delay = calendar.timeInMillis - currentTime

        Log.d(TAG, "Scheduling midnight update for: ${calendar.time}")

        val midnightWork = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("force_refresh" to false)) // Reparse only at midnight
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            MIDNIGHT_UPDATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            midnightWork
        )

        Log.d(TAG, "Midnight update scheduled")
    }

    /**
     * Triggers an immediate update (e.g. on app start or screen unlock)
     * @param forceRefresh true = API fetch, false = reparse only
     */
    fun triggerImmediateUpdate(context: Context, forceRefresh: Boolean = false) {
        Log.d(TAG, "Triggering immediate widget update (forceRefresh=$forceRefresh)")

        val workRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            .setInputData(workDataOf("force_refresh" to forceRefresh))
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }

}