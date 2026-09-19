package com.uvindex.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.uvindex.app.schedule.BackgroundSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UVIndexApplication : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "uv_index_channel"
        const val NOTIFICATION_CHANNEL_NAME = "UV-Index Benachrichtigungen"
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        CoroutineScope(Dispatchers.IO).launch { BackgroundSchedule.ensureScheduled(this@UVIndexApplication) }
        checkBatteryOptimization()
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
}
