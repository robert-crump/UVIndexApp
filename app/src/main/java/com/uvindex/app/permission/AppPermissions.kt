package com.uvindex.app.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** The runtime permission the app should ask the user for next. */
enum class PermissionRequest {
    Location,
    Notification,
    None,
}

/** True if the result map of a location request contains a coarse or fine grant. */
fun isLocationGranted(result: Map<String, Boolean>): Boolean =
    result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
        result[Manifest.permission.ACCESS_FINE_LOCATION] == true

/** POST_NOTIFICATIONS is a runtime permission only on API 33+. */
fun notificationPermissionRequired(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU

/**
 * Pure decision for the next permission to request at startup: location first, then
 * notifications (API 33+ only). No Android state is read so it can be unit tested on the JVM.
 */
fun nextPermissionToRequest(
    sdkInt: Int,
    locationGranted: Boolean,
    notificationGranted: Boolean,
): PermissionRequest = when {
    !locationGranted -> PermissionRequest.Location
    notificationPermissionRequired(sdkInt) && !notificationGranted -> PermissionRequest.Notification
    else -> PermissionRequest.None
}

/** The location grants the settings screen shows; an interface so the settings view model can use a fake. */
interface LocationPermissionState {
    fun hasForegroundLocation(): Boolean
    fun hasBackgroundLocation(): Boolean
}

/** Thin Android edge: the single place that reads the OS permission state. */
object AppPermissions {

    /** ACCESS_BACKGROUND_LOCATION is a separate permission only on API 29+; before that a foreground grant covers it. */
    fun hasBackgroundLocation(context: Context): Boolean =
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            hasLocation(context)
        } else {
            isGranted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

    fun locationPermissionState(context: Context): LocationPermissionState {
        val appContext = context.applicationContext
        return object : LocationPermissionState {
            override fun hasForegroundLocation() = hasLocation(appContext)
            override fun hasBackgroundLocation() = hasBackgroundLocation(appContext)
        }
    }

    fun hasLocation(context: Context): Boolean =
        isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION) ||
            isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasNotifications(context: Context): Boolean =
        !notificationPermissionRequired(Build.VERSION.SDK_INT) ||
            isGranted(context, Manifest.permission.POST_NOTIFICATIONS)

    fun nextToRequest(context: Context): PermissionRequest =
        nextPermissionToRequest(Build.VERSION.SDK_INT, hasLocation(context), hasNotifications(context))

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
