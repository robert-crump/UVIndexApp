package com.uvindex.app.widget

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.uvindex.app.worker.DailyUVCheckWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

object NotificationScheduler {

    private const val TAG = "NotificationScheduler"
    const val WORK_NAME = "daily_uv_notification"

    fun scheduleDailyNotification(context: Context) {
        val initialDelay = calculateDelayTo630AM()

        val workRequest = PeriodicWorkRequestBuilder<DailyUVCheckWorker>(
            24, TimeUnit.HOURS,
            2, TimeUnit.HOURS   // flex window: fires within 2h before 6:30 AM
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )

        Log.d(TAG, "Daily notification scheduled via WorkManager (delay: ${initialDelay / 60000} min)")
    }

    fun cancelDailyNotification(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        Log.d(TAG, "Daily notification cancelled")
    }

    private fun calculateDelayTo630AM(): Long {
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return target.timeInMillis - System.currentTimeMillis()
    }
}
