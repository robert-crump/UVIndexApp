package com.uvindex.app.schedule

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.uvindex.app.worker.TickWorker
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Owns every WorkManager schedule in the app. No other file builds a WorkRequest. */
object BackgroundSchedule {

    private const val TAG = "BackgroundSchedule"

    const val TICK_WORK_NAME = "background_tick"
    const val TICK_MIDNIGHT_WORK_NAME = "background_tick_midnight"

    // Work names of the retired hourly, widget and daily workers; cancelled so old installs drop them.
    private val LEGACY_WORK_NAMES = listOf(
        "hourly_uv_update", "widget_periodic_update", "widget_midnight_update", "daily_uv_notification",
    )

    private const val TICK_INTERVAL_MINUTES = 30L
    private const val TICK_INITIAL_DELAY_MINUTES = 1L

    /** When the Daily Forecast Notification is announced to the user; the decider's window opens at this time. */
    val DAILY_NOTIFICATION_TIME: LocalTime = LocalTime.of(6, 30)
    private val MIDNIGHT_TICK_TIME: LocalTime = LocalTime.of(0, 1)

    /** [DAILY_NOTIFICATION_TIME] as user-facing text, e.g. "06:30". */
    val dailyNotificationTimeText: String
        get() = DAILY_NOTIFICATION_TIME.format(DateTimeFormatter.ofPattern("HH:mm"))

    // Also runs on low battery; no network constraint so the tick falls back to the cache offline.
    private val constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(false)
        .build()

    /** Idempotent: registers the periodic tick and the midnight one-shot, dropping retired schedules. */
    fun ensureScheduled(context: Context) {
        val workManager = WorkManager.getInstance(context)
        LEGACY_WORK_NAMES.forEach { workManager.cancelUniqueWork(it) }
        scheduleTick(workManager)
        scheduleMidnightTick(workManager)
        Log.d(TAG, "Schedules ensured")
    }

    private fun scheduleTick(workManager: WorkManager) {
        val request = PeriodicWorkRequestBuilder<TickWorker>(TICK_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInitialDelay(TICK_INITIAL_DELAY_MINUTES, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(TICK_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun scheduleMidnightTick(workManager: WorkManager) {
        val delay = delayUntilNext(ZonedDateTime.now(), MIDNIGHT_TICK_TIME)
        val request = OneTimeWorkRequestBuilder<TickWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(TICK_MIDNIGHT_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
