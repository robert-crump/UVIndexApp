package com.uvindex.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.uvindex.app.notification.NotificationScheduler
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
                // Screen was unlocked. Push the cache straight to the widgets - no need to
                // also enqueue WidgetUpdateWorker, since getCachedForecast() already
                // re-parses with the current time on every read, so this alone is enough
                // to show up-to-date values without a network round trip.
                Log.d(TAG, "Screen unlocked - triggering immediate widget update")
                WidgetUpdateHelper.updateAllWidgets(context)
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