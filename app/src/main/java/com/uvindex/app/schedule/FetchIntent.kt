package com.uvindex.app.schedule

import java.time.LocalTime

/** How a background tick wants its forecast: see [fetchIntentFor]. */
enum class FetchIntent {
    /** Always go to the network (falls back to cache on failure). */
    Fresh,

    /** Go to the network only if the repository's fetch policy says the cache is stale. */
    FreshIfStale,

    /** Use whatever the cache holds, re-derived for the current time. */
    Cached,
}

private const val FIRST_FRESH_HOUR = 6
private const val LAST_FRESH_HOUR = 17

/**
 * Pure rule for the tick worker. At the top of an hour (minute 0) daytime hours (6..17) force a
 * fresh fetch and other hours fetch if stale; every other tick reads the cache.
 */
fun fetchIntentFor(now: LocalTime): FetchIntent = when {
    now.minute != 0 -> FetchIntent.Cached
    now.hour in FIRST_FRESH_HOUR..LAST_FRESH_HOUR -> FetchIntent.Fresh
    else -> FetchIntent.FreshIfStale
}
