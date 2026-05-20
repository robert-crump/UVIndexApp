package com.uvindex.app.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.uvindex.app.notification.Channel
import com.uvindex.app.notification.SharedPreferencesNotificationHistoryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotificationActionRx"
        const val ACTION_DISABLE_UV_WARNINGS = "com.uvindex.app.DISABLE_UV_WARNINGS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DISABLE_UV_WARNINGS -> {
                Log.d(TAG, "Disabling UV warnings for today")
                NotificationManagerCompat.from(context).cancel(Channel.UvWarning.notificationId)
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        SharedPreferencesNotificationHistoryStore(context).markUvWarningDisabledToday()
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
