package com.uvindex.app.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDateTime

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotificationActionRx"
        private const val PREFS_NAME = "uv_notifications"
        private const val KEY_HOURLY_DISABLED_DATE = "hourly_disabled_date"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            HourlyUpdateWorker.ACTION_DISABLE_HOURLY -> {
                Log.d(TAG, "Disabling hourly warnings for today")
                disableHourlyForToday(context)
                // Dismiss the notification
                NotificationManagerCompat.from(context).cancel(2)
            }
        }
    }

    private fun disableHourlyForToday(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = LocalDateTime.now().toLocalDate().toString()
        prefs.edit()
            .putString(KEY_HOURLY_DISABLED_DATE, today)
            .apply()
    }
}