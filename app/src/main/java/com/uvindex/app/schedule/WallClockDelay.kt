package com.uvindex.app.schedule

import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Time from [now] until the next occurrence of [target] on the wall clock of [now]'s zone.
 * A target that is already reached (including exactly now) resolves to tomorrow. Works on wall-clock
 * dates, so a DST day yields the real elapsed time rather than a fixed 24h.
 */
fun delayUntilNext(now: ZonedDateTime, target: LocalTime): Duration {
    var next = now.toLocalDate().atTime(target).atZone(now.zone)
    if (!next.isAfter(now)) {
        next = now.toLocalDate().plusDays(1).atTime(target).atZone(now.zone)
    }
    return Duration.between(now, next)
}
