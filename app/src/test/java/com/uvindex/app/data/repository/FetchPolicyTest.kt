package com.uvindex.app.data.repository

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FetchPolicyTest {
    private val now = LocalDateTime.of(2026, 9, 19, 12, 0)
    private val lat = 52.52
    private val lon = 13.405

    private fun cache(
        fetchedAt: LocalDateTime = now.minusHours(1),
        forecastDate: LocalDate = now.toLocalDate()
    ) = CacheMetadata(fetchedAt, lat, lon, forecastDate)

    @Test
    fun noCacheFetches() = assertTrue(shouldFetch(null, lat, lon, now))

    @Test
    fun freshAndNearUsesCache() = assertFalse(shouldFetch(cache(), lat + 0.01, lon, now))

    @Test
    fun olderThanTtlFetches() =
        assertTrue(shouldFetch(cache(fetchedAt = now.minus(CACHE_TTL)), lat, lon, now))

    @Test
    fun movedBeyondThresholdFetches() = assertTrue(shouldFetch(cache(), lat + 0.2, lon, now))

    @Test
    fun fromYesterdayFetches() =
        assertTrue(shouldFetch(cache(forecastDate = now.toLocalDate().minusDays(1)), lat, lon, now))

    @Test
    fun unknownCurrentLocationSkipsMoveCheck() {
        assertFalse(shouldFetch(cache(), null, null, now))
        assertTrue(shouldFetch(cache(fetchedAt = now.minusHours(4)), null, null, now))
    }
}
