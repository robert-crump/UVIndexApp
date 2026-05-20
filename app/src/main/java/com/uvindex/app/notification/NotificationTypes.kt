package com.uvindex.app.notification

import java.time.Instant
import java.time.LocalDate

/**
 * Persisted state passed into [NotificationDecider.decide] each worker tick.
 * See CONTEXT.md → "Notification History".
 */
data class NotificationHistory(
    val dailyEnabled: Boolean,
    val uvWarningEnabled: Boolean,
    /** Date UV Warning was suppressed by the user ("Warnungen heute deaktivieren"). See CONTEXT.md → "Disabled Today". */
    val uvWarningDisabledOn: LocalDate?,
    val lastDailySent: LocalDate?,
    val lastDailySentAt: Instant?,
    val lastUvWarningOn: LocalDate?,
    val lastUvWarningAt: Instant?,
    /** Snapshot of what the last UV Warning covered. Used to compute Worse News. See CONTEXT.md → "Warned About". */
    val lastUvWarning: WarnedAbout?,
)

/**
 * Snapshot of what a fired UV Warning covered. Used to compute Worse News on the next tick.
 * See CONTEXT.md → "Warned About".
 */
data class WarnedAbout(
    val peak: Float,
    val firstHighHour: Int,
    /** Set of High UV Hours (UV ≥ 6) included in the warning. See CONTEXT.md → "High UV Hour". */
    val highHours: Set<Int>,
)

/**
 * The two user-facing notification channels. See CONTEXT.md → "Daily Forecast Notification" and "UV Warning".
 */
enum class Channel(val notificationId: Int) {
    Daily(1),
    UvWarning(2),
}

/** UV Warning phase. See CONTEXT.md → "Prelude phase" and "In-window phase". */
enum class Phase { Prelude, InWindow }

enum class Priority { Default, High }

/** Actions that can be attached to a notification (e.g., "Warnungen heute deaktivieren"). */
sealed class Action {
    data object DisableUvWarningsToday : Action()
}

/**
 * A resolved decision to send one notification. Produced by [NotificationDecider.decide]
 * and consumed by [NotificationDispatcher].
 */
data class NotificationDecision(
    val channel: Channel,
    /** Null for Daily; set for UV Warning to indicate Prelude vs. In-window. */
    val phase: Phase? = null,
    val title: String,
    val body: String,
    val priority: Priority,
    val actions: List<Action>,
    /** Non-null when [channel] is [Channel.UvWarning]; used to persist Warned About state. */
    val warnedAbout: WarnedAbout? = null,
)
