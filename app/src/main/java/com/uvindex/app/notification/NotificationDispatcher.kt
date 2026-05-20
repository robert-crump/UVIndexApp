package com.uvindex.app.notification

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.uvindex.app.MainActivity
import com.uvindex.app.R
import com.uvindex.app.UVIndexApplication
import com.uvindex.app.worker.NotificationActionReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders a [NotificationDecision] into an Android [Notification] and posts it.
 * History is recorded by the caller only when this returns true.
 */
class NotificationDispatcher(private val context: Context) {

    /**
     * Posts the notification for [decision].
     * Returns true on success; returns false (without throwing) when the POST_NOTIFICATIONS
     * permission is missing or the system rejects the call.
     */
    suspend fun send(decision: NotificationDecision): Boolean = withContext(Dispatchers.IO) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "POST_NOTIFICATIONS permission missing — skipping ${decision.channel}")
            return@withContext false
        }
        return@withContext try {
            val notification = buildNotification(decision)
            NotificationManagerCompat.from(context).notify(decision.channel.notificationId, notification)
            Log.d(TAG, "Notification dispatched: ${decision.channel} (id=${decision.channel.notificationId})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch ${decision.channel}", e)
            false
        }
    }

    private fun buildNotification(decision: NotificationDecision): Notification {
        val tapIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        val androidPriority = when (decision.priority) {
            Priority.High -> NotificationCompat.PRIORITY_HIGH
            Priority.Default -> NotificationCompat.PRIORITY_DEFAULT
        }

        val builder = NotificationCompat.Builder(context, UVIndexApplication.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(decision.title)
            .setContentText(decision.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(decision.body))
            .setPriority(androidPriority)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)

        for (action in decision.actions) {
            when (action) {
                is Action.DisableUvWarningsToday -> {
                    val disableIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                        this.action = NotificationActionReceiver.ACTION_DISABLE_UV_WARNINGS
                    }
                    val disablePendingIntent = PendingIntent.getBroadcast(
                        context, 0, disableIntent, PendingIntent.FLAG_IMMUTABLE,
                    )
                    builder.addAction(0, "Warnungen heute deaktivieren", disablePendingIntent)
                }
            }
        }

        return builder.build()
    }

    private fun hasNotificationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "NotificationDispatcher"
    }
}
