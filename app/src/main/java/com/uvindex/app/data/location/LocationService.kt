package com.uvindex.app.data.location

import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.uvindex.app.permission.AppPermissions
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

class LocationService(private val context: Context) {

    companion object {
        // The fused provider can hang far longer than users are willing to wait for a
        // fresh GPS/network fix (observed >20s with no result). Cap it so we fall back
        // to the last known / stored location quickly instead of stalling the UI.
        private const val FRESH_LOCATION_TIMEOUT_MS = 5_000L
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val geocoder = Geocoder(context, Locale.getDefault())

    suspend fun getCurrentLocation(): Location? {
        if (!hasLocationPermission()) {
            return null
        }

        // Try a fresh fix first. In the background (e.g. WorkManager) this often
        // returns null or fails, so fall back to the device's last known location,
        // which the system / other apps keep current. Returning that lets background
        // workers detect a real location change instead of being stuck re-fetching
        // our own stale stored coordinates.
        return withTimeoutOrNull(FRESH_LOCATION_TIMEOUT_MS) { requestFreshLocation() }
            ?: getLastKnownLocation()
    }

    private suspend fun requestFreshLocation(): Location? {
        return suspendCancellableCoroutine { continuation ->
            val cancellationTokenSource = CancellationTokenSource()

            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location ->
                continuation.resume(location)
            }.addOnFailureListener {
                continuation.resume(null)
            }

            continuation.invokeOnCancellation {
                cancellationTokenSource.cancel()
            }
        }
    }

    private suspend fun getLastKnownLocation(): Location? {
        return suspendCancellableCoroutine { continuation ->
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    continuation.resume(location)
                }
                .addOnFailureListener {
                    continuation.resume(null)
                }
        }
    }

    suspend fun getCityName(latitude: Double, longitude: Double): String? {
        return withContext(Dispatchers.IO) {
            try {
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    address.locality ?: address.subAdminArea ?: address.adminArea
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun getCountryCode(latitude: Double, longitude: Double): String? {
        return withContext(Dispatchers.IO) {
            try {
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    addresses[0].countryCode
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun hasLocationPermission(): Boolean = AppPermissions.hasLocation(context)
}
