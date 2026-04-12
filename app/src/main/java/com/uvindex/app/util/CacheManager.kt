package com.uvindex.app.util

import com.uvindex.app.data.local.DataStoreManager
import com.uvindex.app.data.location.LocationService
import kotlinx.coroutines.flow.first

/**
 * Manager class for cache management.
 * Consolidates cache validation logic in one place.
 */
class CacheManager(
    private val dataStore: DataStoreManager,
    private val locationService: LocationService
) {
    
    companion object {
        private const val LOCATION_CHANGE_THRESHOLD_KM = 15.0
        private const val DEFAULT_CACHE_VALIDITY_HOURS = 3
    }
    
    /**
     * Checks whether a cache refresh is needed.
     *
     * @param currentLat Current latitude (optional, for location check)
     * @param currentLon Current longitude (optional, for location check)
     * @param maxAgeHours Maximum cache age in hours (default: 3)
     * @param checkLocation Whether to check for location change (default: true)
     * @return true if a refresh is needed, false otherwise
     */
    suspend fun shouldRefresh(
        currentLat: Double? = null,
        currentLon: Double? = null,
        maxAgeHours: Int = DEFAULT_CACHE_VALIDITY_HOURS,
        checkLocation: Boolean = true
    ): Boolean {
        val lastLocation = dataStore.getLastLocation().first() ?: return true
        
        // Zeit-Check
        val hoursSinceUpdate = (System.currentTimeMillis() - lastLocation.timestamp) / (1000 * 60 * 60)
        if (hoursSinceUpdate >= maxAgeHours) {
            return true
        }
        
        // Optional: location check
        if (checkLocation && currentLat != null && currentLon != null) {
            val distance = locationService.calculateDistance(
                lastLocation.latitude,
                lastLocation.longitude,
                currentLat,
                currentLon
            )
            
            if (distance > LOCATION_CHANGE_THRESHOLD_KM) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Checks only the cache age (without location check).
     *
     * @param maxAgeHours Maximum cache age in hours
     * @return true if the cache is too old, false otherwise
     */
    suspend fun isStale(maxAgeHours: Int = DEFAULT_CACHE_VALIDITY_HOURS): Boolean {
        return shouldRefresh(maxAgeHours = maxAgeHours, checkLocation = false)
    }
}
