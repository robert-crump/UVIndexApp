package com.uvindex.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.*
import com.uvindex.app.widget.NotificationScheduler
import com.uvindex.app.widget.WidgetUpdateWorker
import com.uvindex.app.worker.HourlyUpdateWorker
import java.util.concurrent.TimeUnit

class UVIndexApplication : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "uv_index_channel"
        const val NOTIFICATION_CHANNEL_NAME = "UV-Index Benachrichtigungen"
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        scheduleDailyNotifications()
        scheduleHourlyUpdates()
        scheduleWidgetUpdates()
        checkBatteryOptimization()
    }

    private fun scheduleDailyNotifications() {
        NotificationScheduler.scheduleDailyNotification(this)
        android.util.Log.d("UVIndexApplication", "Daily notifications scheduled via WorkManager")
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val packageName = packageName

            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                android.util.Log.w("UVIndexApplication", "⚠️ Battery optimization is enabled - this may prevent background updates!")
                android.util.Log.w("UVIndexApplication", "User should disable battery optimization for this app in Settings")
            } else {
                android.util.Log.d("UVIndexApplication", "✓ Battery optimization is disabled - background updates should work")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Benachrichtigungen über hohe UV-Index Werte"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun scheduleHourlyUpdates() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false)  // Also runs on low battery
            .build()

        // Worker runs every 30 minutes for precise timing (6:30 notification, hourly warnings)
        // No network constraint so the worker also runs offline (falls back to cache)
        val halfHourlyWorkRequest = PeriodicWorkRequestBuilder<HourlyUpdateWorker>(
            30, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.MINUTES)  // Start after 1 minute
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "hourly_uv_update",
            ExistingPeriodicWorkPolicy.UPDATE,
            halfHourlyWorkRequest
        )
    }

    private fun scheduleWidgetUpdates() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false)
            .build()

        val widgetWorkRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
            30, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInitialDelay(2, TimeUnit.MINUTES)  // Start after 2 minutes
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "widget_update",
            ExistingPeriodicWorkPolicy.UPDATE,
            widgetWorkRequest
        )
    }
}