package com.uvindex.app.worker

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.uvindex.app.MainActivity
import com.uvindex.app.R
import com.uvindex.app.UVIndexApplication
import com.uvindex.app.data.repository.WeatherRepository
import java.time.LocalDateTime

class DailyUVCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repository = WeatherRepository(context)

    companion object {
        private const val TAG = "DailyUVCheckWorker"
        private const val PREFS_NAME = "uv_notifications"
        private const val KEY_DAILY_SENT_DATE = "daily_sent_date"
        private const val KEY_DAILY_SENT_TIMESTAMP = "daily_sent_timestamp"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Daily UV worker started")

        if (!isDailyNotificationEnabled()) {
            Log.d(TAG, "Daily notification is disabled in settings")
            return Result.success()
        }

        if (isDailySentToday()) {
            Log.d(TAG, "Daily notification already sent today")
            return Result.success()
        }

        return try {
            val forecastResult = repository.getUVForecast(forceRefresh = true)

            forecastResult.fold(
                onSuccess = { forecast ->
                    Log.d(TAG, "Forecast loaded. Daily max UV: ${forecast.dailyMax}")
                    sendDailyForecastNotification(forecast)
                    markDailySent()
                    Result.success()
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to get forecast: ${error.message}")
                    Result.retry()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Worker exception", e)
            Result.failure()
        }
    }

    @Suppress("MissingPermission")
    private fun sendDailyForecastNotification(forecast: com.uvindex.app.data.model.UVForecast) {
        if (!hasNotificationPermission()) return

        val maxUV = forecast.dailyMax.toInt()
        val categoryText = when {
            maxUV <= 2 -> "niedrig"
            maxUV <= 5 -> "mittel"
            maxUV <= 7 -> "hoch"
            else -> "sehr hoch"
        }

        val description = buildDailyDescription(forecast)
        val notificationText = if (description.isNotEmpty()) {
            "Tagesmaximum: $maxUV ($categoryText). $description"
        } else {
            "Tagesmaximum: $maxUV ($categoryText)."
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            applicationContext,
            UVIndexApplication.NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("UV: Tagesprognose")
            .setContentText(notificationText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(applicationContext).notify(1, notification)
            Log.d(TAG, "Daily forecast notification sent")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send daily notification", e)
        }
    }

    private fun buildDailyDescription(forecast: com.uvindex.app.data.model.UVForecast): String {
        val veryHigh = mutableListOf<Int>()
        val high = mutableListOf<Int>()

        forecast.allDayForecasts.forEach { hourForecast ->
            val uv = hourForecast.uvIndex.toInt()
            when {
                uv >= 8 -> veryHigh.add(hourForecast.hour)
                uv >= 6 -> high.add(hourForecast.hour)
            }
        }

        val parts = mutableListOf<String>()
        if (veryHigh.isNotEmpty()) parts.add("Sehr hohe Strahlung ${createTimeRanges(veryHigh)}")
        if (high.isNotEmpty()) parts.add("Hohe Strahlung ${createTimeRanges(high)}")
        if (parts.isEmpty()) return ""
        return parts.joinToString(". ") + "."
    }

    private fun createTimeRanges(hours: List<Int>): String {
        if (hours.isEmpty()) return ""
        val sorted = hours.sorted()
        val ranges = mutableListOf<String>()
        var start = sorted[0]
        var end = sorted[0]
        for (i in 1 until sorted.size) {
            if (sorted[i] == end + 1) {
                end = sorted[i]
            } else {
                ranges.add(formatTimeRange(start, end))
                start = sorted[i]
                end = sorted[i]
            }
        }
        ranges.add(formatTimeRange(start, end))
        return "von " + ranges.joinToString(" und ")
    }

    private fun formatTimeRange(start: Int, end: Int): String {
        return if (start == end) "${String.format("%02d:00", start)} Uhr"
        else "${String.format("%02d:00", start)}-${String.format("%02d:00", end + 1)} Uhr"
    }

    private fun isDailySentToday(): Boolean {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSentTimestamp = prefs.getLong(KEY_DAILY_SENT_TIMESTAMP, 0)
        val hoursSinceLastSent = (System.currentTimeMillis() - lastSentTimestamp) / (1000 * 60 * 60)
        if (lastSentTimestamp > 0 && hoursSinceLastSent < 12) return true
        val lastSent = prefs.getString(KEY_DAILY_SENT_DATE, null)
        val today = LocalDateTime.now().toLocalDate().toString()
        return lastSent == today
    }

    private fun markDailySent() {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_DAILY_SENT_DATE, LocalDateTime.now().toLocalDate().toString())
            .putLong(KEY_DAILY_SENT_TIMESTAMP, System.currentTimeMillis())
            .commit()
        Log.d(TAG, "Daily notification marked as sent")
    }

    private fun isDailyNotificationEnabled(): Boolean {
        val prefs = applicationContext.getSharedPreferences("uv_app_settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("daily_notification_enabled", true)
    }

    private fun hasNotificationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            applicationContext, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
