package com.uvindex.app.notification

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Persisted [NotificationHistory] store. Implementations must be safe to call from any coroutine.
 * See CONTEXT.md → "Notification History".
 */
interface NotificationHistoryStore {
    /**
     * Returns the current [NotificationHistory], running one-time migration of legacy keys
     * on the first call on a device that has the old SharedPreferences layout.
     */
    suspend fun snapshot(): NotificationHistory

    /**
     * Records a successful dispatch so the same notification is not re-fired.
     * Must be called only after [NotificationDispatcher.send] returns true.
     */
    suspend fun record(decision: NotificationDecision, now: Instant)

    /**
     * Marks UV Warning as suppressed for the rest of the calendar day.
     * Called when the user taps "Warnungen heute deaktivieren". See CONTEXT.md → "Disabled Today".
     * Exercised in Slice 2.
     */
    suspend fun markUvWarningDisabledToday()
}

/**
 * SharedPreferences-backed [NotificationHistoryStore].
 *
 * Migration pattern (documented for future slices): on first [snapshot] the store reads the legacy
 * `uv_notifications` and `uv_app_settings` SharedPreferences, folds the values into the umbrella
 * prefs (`uv_notification_history`), and sets a `umbrella_migrated` flag so migration runs only
 * once. Legacy keys are left in place — they are cleaned up in a later slice.
 */
class SharedPreferencesNotificationHistoryStore(
    private val context: Context,
) : NotificationHistoryStore {

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_UMBRELLA, Context.MODE_PRIVATE)
    }

    override suspend fun snapshot(): NotificationHistory = withContext(Dispatchers.IO) {
        if (!prefs.getBoolean(KEY_MIGRATED, false)) migrate()
        readHistory()
    }

    private fun migrate() {
        val legacyNotif = context.getSharedPreferences(PREFS_LEGACY_NOTIFICATIONS, Context.MODE_PRIVATE)
        val legacySettings = context.getSharedPreferences(PREFS_LEGACY_SETTINGS, Context.MODE_PRIVATE)
        val today = LocalDate.now()

        prefs.edit().also { editor ->
            editor.putBoolean(KEY_MIGRATED, true)

            // Settings
            editor.putBoolean(KEY_DAILY_ENABLED, legacySettings.getBoolean(LEGACY_DAILY_ENABLED, true))
            editor.putBoolean(KEY_UV_WARNING_ENABLED, legacySettings.getBoolean(LEGACY_UV_WARNING_ENABLED, true))

            // Daily channel
            legacyNotif.getString(LEGACY_DAILY_SENT_DATE, null)
                ?.let { editor.putString(KEY_LAST_DAILY_SENT, it) }
            legacyNotif.getLong(LEGACY_DAILY_SENT_TIMESTAMP, 0L).takeIf { it > 0L }
                ?.let { editor.putLong(KEY_LAST_DAILY_SENT_AT, it) }

            // UV Warning: hourly_disabled_date → uvWarningDisabledOn
            legacyNotif.getString(LEGACY_HOURLY_DISABLED_DATE, null)
                ?.let { editor.putString(KEY_UV_WARNING_DISABLED_ON, it) }

            // UV Warning: last_hourly_sent_time → lastUvWarningAt + lastUvWarningOn
            val lastHourlySentMs = legacyNotif.getLong(LEGACY_LAST_HOURLY_SENT_TIME, 0L)
            if (lastHourlySentMs > 0L) {
                editor.putLong(KEY_LAST_UV_WARNING_AT, lastHourlySentMs)
                val sentDate = Instant.ofEpochMilli(lastHourlySentMs)
                    .atZone(ZoneId.systemDefault()).toLocalDate()
                editor.putString(KEY_LAST_UV_WARNING_ON, sentDate.toString())
            }

            // UV Warning: last_transition_warned_hour + last_transition_warned_date → lastUvWarning
            // peak is conservatively 6.0f (we only know it was ≥ threshold at the time)
            val transitionDate = legacyNotif.getString(LEGACY_LAST_TRANSITION_WARNED_DATE, null)
            val transitionHour = legacyNotif.getInt(LEGACY_LAST_TRANSITION_WARNED_HOUR, -1)
            if (transitionDate != null && transitionHour >= 0) {
                editor.putFloat(KEY_WARNED_ABOUT_PEAK, 6.0f)
                editor.putInt(KEY_WARNED_ABOUT_FIRST_HIGH_HOUR, transitionHour)
                editor.putString(KEY_WARNED_ABOUT_HIGH_HOURS, transitionHour.toString())
                // If transition was today, block immediate UV Warning re-fire
                if (transitionDate == today.toString()) {
                    editor.putString(KEY_LAST_UV_WARNING_ON, today.toString())
                }
            }
        }.apply()
    }

    private fun readHistory(): NotificationHistory {
        fun String?.toDate() = this?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        fun Long.toInstantOrNull() = if (this > 0L) Instant.ofEpochMilli(this) else null

        return NotificationHistory(
            dailyEnabled = prefs.getBoolean(KEY_DAILY_ENABLED, true),
            uvWarningEnabled = prefs.getBoolean(KEY_UV_WARNING_ENABLED, true),
            uvWarningDisabledOn = prefs.getString(KEY_UV_WARNING_DISABLED_ON, null).toDate(),
            lastDailySent = prefs.getString(KEY_LAST_DAILY_SENT, null).toDate(),
            lastDailySentAt = prefs.getLong(KEY_LAST_DAILY_SENT_AT, 0L).toInstantOrNull(),
            lastUvWarningOn = prefs.getString(KEY_LAST_UV_WARNING_ON, null).toDate(),
            lastUvWarningAt = prefs.getLong(KEY_LAST_UV_WARNING_AT, 0L).toInstantOrNull(),
            lastUvWarning = readWarnedAbout(),
        )
    }

    private fun readWarnedAbout(): WarnedAbout? {
        val peak = prefs.getFloat(KEY_WARNED_ABOUT_PEAK, -1f)
        if (peak < 0f) return null
        val firstHigh = prefs.getInt(KEY_WARNED_ABOUT_FIRST_HIGH_HOUR, -1)
        if (firstHigh < 0) return null
        val highHours = prefs.getString(KEY_WARNED_ABOUT_HIGH_HOURS, null)
            ?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toSet()
            ?: return null
        return WarnedAbout(peak, firstHigh, highHours)
    }

    override suspend fun record(decision: NotificationDecision, now: Instant) =
        withContext(Dispatchers.IO) {
            val localDate = now.atZone(ZoneId.systemDefault()).toLocalDate().toString()
            prefs.edit().apply {
                when (decision.channel) {
                    Channel.Daily -> {
                        putString(KEY_LAST_DAILY_SENT, localDate)
                        putLong(KEY_LAST_DAILY_SENT_AT, now.toEpochMilli())
                    }
                    Channel.UvWarning -> {
                        putString(KEY_LAST_UV_WARNING_ON, localDate)
                        putLong(KEY_LAST_UV_WARNING_AT, now.toEpochMilli())
                        decision.warnedAbout?.let { wa ->
                            putFloat(KEY_WARNED_ABOUT_PEAK, wa.peak)
                            putInt(KEY_WARNED_ABOUT_FIRST_HIGH_HOUR, wa.firstHighHour)
                            putString(KEY_WARNED_ABOUT_HIGH_HOURS, wa.highHours.joinToString(","))
                        }
                    }
                }
            }.apply()
        }

    override suspend fun markUvWarningDisabledToday() = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_UV_WARNING_DISABLED_ON, LocalDate.now().toString())
            .apply()
    }

    companion object {
        private const val PREFS_UMBRELLA = "uv_notification_history"
        private const val KEY_MIGRATED = "umbrella_migrated"
        private const val KEY_DAILY_ENABLED = "daily_enabled"
        private const val KEY_UV_WARNING_ENABLED = "uv_warning_enabled"
        private const val KEY_UV_WARNING_DISABLED_ON = "uv_warning_disabled_on"
        private const val KEY_LAST_DAILY_SENT = "last_daily_sent"
        private const val KEY_LAST_DAILY_SENT_AT = "last_daily_sent_at"
        private const val KEY_LAST_UV_WARNING_ON = "last_uv_warning_on"
        private const val KEY_LAST_UV_WARNING_AT = "last_uv_warning_at"
        private const val KEY_WARNED_ABOUT_PEAK = "warned_about_peak"
        private const val KEY_WARNED_ABOUT_FIRST_HIGH_HOUR = "warned_about_first_high_hour"
        private const val KEY_WARNED_ABOUT_HIGH_HOURS = "warned_about_high_hours"

        // Legacy SharedPreferences locations (left in place; cleaned up in a later slice)
        private const val PREFS_LEGACY_NOTIFICATIONS = "uv_notifications"
        private const val PREFS_LEGACY_SETTINGS = "uv_app_settings"
        private const val LEGACY_DAILY_SENT_DATE = "daily_sent_date"
        private const val LEGACY_DAILY_SENT_TIMESTAMP = "daily_sent_timestamp"
        private const val LEGACY_DAILY_ENABLED = "daily_notification_enabled"
        private const val LEGACY_UV_WARNING_ENABLED = "hourly_notification_enabled"
        private const val LEGACY_LAST_HOURLY_SENT_TIME = "last_hourly_sent_time"
        private const val LEGACY_HOURLY_DISABLED_DATE = "hourly_disabled_date"
        private const val LEGACY_LAST_TRANSITION_WARNED_HOUR = "last_transition_warned_hour"
        private const val LEGACY_LAST_TRANSITION_WARNED_DATE = "last_transition_warned_date"
    }
}
