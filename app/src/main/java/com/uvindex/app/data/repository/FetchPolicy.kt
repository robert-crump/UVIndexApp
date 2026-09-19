package com.uvindex.app.data.repository

import com.uvindex.app.data.location.haversineDistanceKm
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** Cache age at which a read goes back to the network. */
val CACHE_TTL: Duration = Duration.ofHours(3)

/** Distance moved from the cached coordinates beyond which a read goes back to the network. */
const val MOVE_THRESHOLD_KM = 15.0

/** Metadata of the cached snapshot the fetch decision depends on. */
data class CacheMetadata(
    val fetchedAt: LocalDateTime,
    val latitude: Double,
    val longitude: Double,
    val forecastDate: LocalDate
)

/**
 * The single fetch-policy predicate: should this read go to the network?
 * Pure: the clock is passed in. A null [current] location skips the move check.
 */
fun shouldFetch(
    cache: CacheMetadata?,
    currentLatitude: Double?,
    currentLongitude: Double?,
    now: LocalDateTime
): Boolean {
    if (cache == null) return true
    if (cache.forecastDate != now.toLocalDate()) return true
    if (Duration.between(cache.fetchedAt, now) >= CACHE_TTL) return true
    if (currentLatitude != null && currentLongitude != null) {
        val movedKm = haversineDistanceKm(cache.latitude, cache.longitude, currentLatitude, currentLongitude)
        if (movedKm > MOVE_THRESHOLD_KM) return true
    }
    return false
}
