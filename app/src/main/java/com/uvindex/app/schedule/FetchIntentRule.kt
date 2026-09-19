package com.uvindex.app.schedule

import com.uvindex.app.data.repository.FetchIntent
import java.time.LocalTime

private const val FIRST_FRESH_HOUR = 6
private const val LAST_FRESH_HOUR = 17

/**
 * Pure rule for the tick worker: the top of a daytime hour (06:00..17:00) forces a fresh fetch;
 * every other tick fetches only if the cache is stale.
 */
fun fetchIntentFor(now: LocalTime): FetchIntent = when {
    now.minute == 0 && now.hour in FIRST_FRESH_HOUR..LAST_FRESH_HOUR -> FetchIntent.Fresh
    else -> FetchIntent.FreshIfStale
}
