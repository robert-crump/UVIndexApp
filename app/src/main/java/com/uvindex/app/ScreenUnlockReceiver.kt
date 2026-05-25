package com.uvindex.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.uvindex.app.notification.SharedPreferencesNotificationHistoryStore
import com.uvindex.app.util.WidgetUpdateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that updates the widget on screen unlock and on boot
 */
class ScreenUnlockReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenUnlockReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_USER_PRESENT -> {
                // Screen was unlocked
                Log.d(TAG, "Screen unlocked - triggering immediate widget update")

                // Trigger immediate widget update (uses cache with reparse)
                // This updates widgets directly without waiting for the worker
                WidgetUpdateHelper.updateAllWidgets(context)

                // Also enqueue a widget update worker for background refresh
                val workRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                    .setInputData(workDataOf("force_refresh" to false))
                    .build()
                WorkManager.getInstance(context).enqueue(workRequest)

                Log.d(TAG, "Widgets updated immediately and background worker enqueued")
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                // Device was rebooted
                Log.d(TAG, "Boot completed - scheduling periodic updates")

                // Restore periodic updates
                WidgetUpdateScheduler.schedulePeriodicUpdates(context)

                // Trigger immediate update with reparse
                WidgetUpdateScheduler.triggerImmediateUpdate(context, forceRefresh = false)

                // Re-schedule daily UV notification if enabled
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val dailyEnabled = SharedPreferencesNotificationHistoryStore(context).snapshot().dailyEnabled
                        if (dailyEnabled) {
                            NotificationScheduler.scheduleDailyNotification(context)
                            Log.d(TAG, "Daily notification re-scheduled after boot")
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}