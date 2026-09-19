package com.uvindex.app.schedule

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.uvindex.app.notification.SharedPreferencesNotificationHistoryStore
import com.uvindex.app.widget.WidgetUpdateWorker
import com.uvindex.app.worker.DailyUVCheckWorker
import com.uvindex.app.worker.HourlyUpdateWorker
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Owns every WorkManager schedule in the app. No other file builds a WorkRequest. */
object BackgroundSchedule {

    private const val TAG = "BackgroundSchedule"

    const val HOURLY_UPDATE_WORK_NAME = "hourly_uv_update"
    const val WIDGET_PERIODIC_WORK_NAME = "widget_periodic_update"
    const val WIDGET_MIDNIGHT_WORK_NAME = "widget_midnight_update"
    const val DAILY_NOTIFICATION_WORK_NAME = "daily_uv_notification"

    const val KEY_FORCE_REFRESH = "force_refresh"

    private const val HOURLY_UPDATE_INTERVAL_MINUTES = 30L
    private const val HOURLY_UPDATE_INITIAL_DELAY_MINUTES = 1L

    private const val WIDGET_INTERVAL_MINUTES = 15L
    private const val WIDGET_FLEX_MINUTES = 5L

    private const val DAILY_NOTIFICATION_INTERVAL_HOURS = 24L
    private const val DAILY_NOTIFICATION_FLEX_HOURS = 2L

    val DAILY_NOTIFICATION_TIME: LocalTime = LocalTime.of(6, 30)
    private val WIDGET_MIDNIGHT_TIME: LocalTime = LocalTime.of(0, 1)

    /** [DAILY_NOTIFICATION_TIME] as user-facing text, e.g. "06:30". */
    val dailyNotificationTimeText: String
        get() = DAILY_NOTIFICATION_TIME.format(DateTimeFormatter.ofPattern("HH:mm"))

    // Also runs on low battery; no network constraint so workers fall back to the cache offline.
    private val constraints = Constraints.Builder()
        .setRequiresBatteryNotLow(false)
        .build()

    /** Idempotent: registers every schedule, cancelling the daily notification if the user turned it off. */
    suspend fun ensureScheduled(context: Context) {
        val dailyEnabled = SharedPreferencesNotificationHistoryStore(context).snapshot().dailyEnabled
        val workManager = WorkManager.getInstance(context)
        scheduleHourlyUpdate(workManager)
        scheduleWidgetPeriodic(workManager)
        scheduleWidgetMidnight(workManager)
        if (dailyEnabled) scheduleDailyNotification(workManager)
        else workManager.cancelUniqueWork(DAILY_NOTIFICATION_WORK_NAME)
        Log.d(TAG, "Schedules ensured (dailyNotification=$dailyEnabled)")
    }

    /** One-shot widget worker run. [forceRefresh]: true = API fetch, false = re-derive from cache. */
    fun refreshWidgetsNow(context: Context, forceRefresh: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            .setInputData(forceRefreshData(forceRefresh))
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    private fun forceRefreshData(forceRefresh: Boolean): Data = workDataOf(KEY_FORCE_REFRESH to forceRefresh)

    private fun scheduleHourlyUpdate(workManager: WorkManager) {
        val request = PeriodicWorkRequestBuilder<HourlyUpdateWorker>(HOURLY_UPDATE_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInitialDelay(HOURLY_UPDATE_INITIAL_DELAY_MINUTES, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(HOURLY_UPDATE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun scheduleWidgetPeriodic(workManager: WorkManager) {
        val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
            WIDGET_INTERVAL_MINUTES, TimeUnit.MINUTES,
            WIDGET_FLEX_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInputData(forceRefreshData(false))
            .build()
        workManager.enqueueUniquePeriodicWork(WIDGET_PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun scheduleWidgetMidnight(workManager: WorkManager) {
        val delay = delayUntilNext(ZonedDateTime.now(), WIDGET_MIDNIGHT_TIME)
        val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(forceRefreshData(false))
            .build()
        workManager.enqueueUniqueWork(WIDGET_MIDNIGHT_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private fun scheduleDailyNotification(workManager: WorkManager) {
        val delay = delayUntilNext(ZonedDateTime.now(), DAILY_NOTIFICATION_TIME)
        val request = PeriodicWorkRequestBuilder<DailyUVCheckWorker>(
            DAILY_NOTIFICATION_INTERVAL_HOURS, TimeUnit.HOURS,
            DAILY_NOTIFICATION_FLEX_HOURS, TimeUnit.HOURS
        )
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(DAILY_NOTIFICATION_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
