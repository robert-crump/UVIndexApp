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
import com.uvindex.app.util.WidgetUpdateHelper
import com.uvindex.app.util.CacheManager
import java.time.LocalDateTime

class HourlyUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repository = WeatherRepository(context)

    companion object {
        private const val TAG = "HourlyUpdateWorker"
        const val ACTION_DISABLE_HOURLY = "com.uvindex.app.DISABLE_HOURLY"
        private const val PREFS_NAME = "uv_notifications"
        private const val KEY_HOURLY_DISABLED_DATE = "hourly_disabled_date"
        private const val KEY_LAST_HOURLY_SENT_TIME = "last_hourly_sent_time"
        private const val KEY_LAST_TRANSITION_WARNED_HOUR = "last_transition_warned_hour"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "=== Hourly worker started ===")
        Log.d(TAG, "Current time: ${LocalDateTime.now()}")

        val now = LocalDateTime.now()
        val currentHour = now.hour
        val currentMinute = now.minute

        Log.d(TAG, "Current hour: $currentHour, Current minute: $currentMinute")

        return try {
            val shouldFetchNew = when {
                currentHour in 6..17 && currentMinute == 0 -> {
                    Log.d(TAG, "Zwischen 6-18 Uhr zur vollen Stunde -> Hole neue Daten")
                    true
                }
                currentHour !in 6..17 && currentMinute == 0 -> {
                    val timeCheck = shouldFetchByTime()
                    Log.d(TAG, "Außerhalb 6-18 Uhr zur vollen Stunde -> TimeCheck: $timeCheck")
                    timeCheck
                }
                else -> {
                    Log.d(TAG, "Keine Bedingung erfüllt -> Keine neuen Daten")
                    false
                }
            }

            val forecastResult = repository.getUVForecast(forceRefresh = shouldFetchNew)

            forecastResult.fold(
                onSuccess = { forecast ->
                    Log.d(TAG, "Forecast loaded successfully")

                    // UV warning notifications
                    Log.d(TAG, "Hourly warning check - Minute: $currentMinute")
                    if (currentMinute >= 30) {
                        val hourlyDisabled = isHourlyDisabledToday()
                        val hourlyEnabled = isHourlyNotificationEnabled()
                        Log.d(TAG, "Hourly warnings disabled for today: $hourlyDisabled, Enabled in settings: $hourlyEnabled")
                        if (!hourlyDisabled && hourlyEnabled) {
                            checkAndSendHourlyWarning(forecast, currentHour)
                            checkAndSendTransitionWarning(forecast, currentHour)
                        } else if (!hourlyEnabled) {
                            Log.d(TAG, "Hourly warnings are disabled in settings")
                        } else {
                            Log.d(TAG, "Hourly warnings are disabled for today")
                        }
                    } else {
                        Log.d(TAG, "Not time for hourly warning (minute < 30)")
                    }

                    if (shouldFetchNew) {
                        Log.d(TAG, "Updating widgets")
                        updateWidgets()
                    }

                    Log.d(TAG, "=== Worker completed successfully ===")
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

    private suspend fun shouldFetchByTime(): Boolean {
        val dataStore = com.uvindex.app.data.local.DataStoreManager(applicationContext)
        val locationService = com.uvindex.app.data.location.LocationService(applicationContext)
        val cacheManager = CacheManager(dataStore, locationService)
        return cacheManager.isStale(maxAgeHours = 3)
    }

    private fun updateWidgets() {
        WidgetUpdateHelper.updateAllWidgets(applicationContext)
        Log.d(TAG, "Widgets updated")
    }

    @Suppress("MissingPermission")
    private fun checkAndSendTransitionWarning(forecast: com.uvindex.app.data.model.UVForecast, currentHour: Int) {
        Log.d(TAG, "=== checkAndSendTransitionWarning called ===")

        val currentUV = forecast.currentHour.uvIndex.toInt()
        if (currentUV >= 6) {
            Log.d(TAG, "Current UV already high ($currentUV) -> No transition warning needed")
            return
        }

        val nextHourUV = forecast.allDayForecasts.find { it.hour == currentHour + 1 }?.uvIndex?.toInt() ?: 0
        Log.d(TAG, "Current UV: $currentUV, Next hour UV: $nextHourUV")
        if (nextHourUV < 6) {
            Log.d(TAG, "No transition in next hour -> No transition warning")
            return
        }

        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastWarnedHour = prefs.getInt(KEY_LAST_TRANSITION_WARNED_HOUR, -1)
        val today = java.time.LocalDate.now().toString()
        val lastWarnedDate = prefs.getString("last_transition_warned_date", null)

        if (lastWarnedDate == today && lastWarnedHour == currentHour) {
            Log.d(TAG, "Transition warning already sent for hour $currentHour today -> Skipping")
            return
        }

        if (!hasNotificationPermission()) {
            Log.w(TAG, "No notification permission -> Cannot send transition warning")
            return
        }

        val nextHighHour = currentHour + 1
        val notificationText = "UV steigt ab ${String.format("%02d:00", nextHighHour)} Uhr auf hohe Werte (${nextHourUV}). Jetzt Sonnenschutz auftragen."

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
            .setContentTitle("UV-Anstieg in Kürze")
            .setContentText(notificationText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(applicationContext).notify(3, notification)
            prefs.edit()
                .putInt(KEY_LAST_TRANSITION_WARNED_HOUR, currentHour)
                .putString("last_transition_warned_date", today)
                .apply()
            Log.d(TAG, "Transition warning sent: UV rises to $nextHourUV at ${String.format("%02d:00", nextHighHour)}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send transition warning", e)
        }
    }

    private fun checkAndSendHourlyWarning(forecast: com.uvindex.app.data.model.UVForecast, currentHour: Int) {
        Log.d(TAG, "=== checkAndSendHourlyWarning called ===")

        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSentTime = prefs.getLong(KEY_LAST_HOURLY_SENT_TIME, 0)
        val now = System.currentTimeMillis()
        val minutesSinceLastSent = (now - lastSentTime) / (1000 * 60)

        Log.d(TAG, "Minutes since last hourly warning: $minutesSinceLastSent")

        if (minutesSinceLastSent < 60 && lastSentTime > 0) {
            Log.d(TAG, "Hourly warning already sent in the last 60 minutes -> Skipping")
            return
        }

        val remainingHighUVHours = forecast.allDayForecasts.filter {
            it.uvIndex.toInt() >= 6 && it.hour >= currentHour
        }

        Log.d(TAG, "Remaining high UV hours (>= 6): ${remainingHighUVHours.size}")

        if (remainingHighUVHours.isEmpty()) {
            Log.d(TAG, "No remaining high UV hours -> No warning needed")
            return
        }

        val currentMinute = LocalDateTime.now().minute
        Log.d(TAG, "Current minute: $currentMinute")

        if (currentMinute >= 30) {
            Log.d(TAG, "Time is >= 30 minutes -> Sending hourly warning")
            sendHourlyWarning(forecast, currentHour)
            prefs.edit().putLong(KEY_LAST_HOURLY_SENT_TIME, now).apply()
        } else {
            Log.d(TAG, "Time is < 30 minutes -> Not sending warning yet")
        }
    }

    @Suppress("MissingPermission")
    private fun sendHourlyWarning(forecast: com.uvindex.app.data.model.UVForecast, currentHour: Int) {
        Log.d(TAG, "=== sendHourlyWarning called ===")

        if (!hasNotificationPermission()) {
            Log.w(TAG, "No notification permission -> Cannot send warning")
            return
        }

        val highUVHours = forecast.allDayForecasts.filter {
            it.uvIndex.toInt() >= 6 && it.hour >= currentHour
        }

        if (highUVHours.isEmpty()) {
            Log.d(TAG, "No high UV hours found -> Not sending warning")
            return
        }

        val firstHighHour = highUVHours.first().hour
        val lastHighHour = highUVHours.last().hour

        val veryHighUVHours = forecast.allDayForecasts.filter {
            it.uvIndex.toInt() >= 8 && it.hour >= currentHour
        }

        val sentence1 = "Hohe UV-Strahlung (6-7) zwischen ${String.format("%02d:00", firstHighHour)} und ${String.format("%02d:00", lastHighHour)} Uhr."
        val sentence2 = if (veryHighUVHours.isNotEmpty()) {
            val firstVeryHighHour = veryHighUVHours.first().hour
            val lastVeryHighHour = veryHighUVHours.last().hour
            " Sehr hohe UV-Strahlung (8+) von ${String.format("%02d:00", firstVeryHighHour)} bis ${String.format("%02d:00", lastVeryHighHour)} Uhr."
        } else ""

        val notificationText = sentence1 + sentence2

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val disableIntent = Intent(applicationContext, NotificationActionReceiver::class.java).apply {
            action = ACTION_DISABLE_HOURLY
        }
        val disablePendingIntent = PendingIntent.getBroadcast(
            applicationContext, 0, disableIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            applicationContext,
            UVIndexApplication.NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("UV-Warnung")
            .setContentText(notificationText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .addAction(0, "Warnungen heute deaktivieren", disablePendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .build()

        try {
            NotificationManagerCompat.from(applicationContext).notify(2, notification)
            Log.d(TAG, "✓ Hourly warning notification sent at ${LocalDateTime.now()}")
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to send hourly warning notification", e)
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            applicationContext, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isHourlyDisabledToday(): Boolean {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val disabledDate = prefs.getString(KEY_HOURLY_DISABLED_DATE, null)
        val today = LocalDateTime.now().toLocalDate().toString()
        val isDisabled = disabledDate == today
        Log.d(TAG, "isHourlyDisabledToday: disabledDate=$disabledDate, today=$today, result=$isDisabled")
        return isDisabled
    }

    private fun isHourlyNotificationEnabled(): Boolean {
        val prefs = applicationContext.getSharedPreferences("uv_app_settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("hourly_notification_enabled", true)
    }
}
