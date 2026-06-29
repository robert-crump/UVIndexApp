package com.uvindex.app.data.location

/**
 * The next action needed to obtain the [android.Manifest.permission.ACCESS_BACKGROUND_LOCATION]
 * permission, given the device's API level and the current grant state.
 *
 * Android's permission model makes this multi-step: background location requires a foreground
 * location grant first, can only be requested via a runtime dialog on API 29, and on API 30+
 * must be granted by the user in system settings ("Allow all the time").
 */
enum class BackgroundLocationStep {
    /** Background location is already available — nothing to do. */
    AlreadyGranted,

    /** Foreground location must be granted before background can be requested. */
    RequestForegroundFirst,

    /** API 29: background location can be requested inline via a runtime dialog. */
    RequestBackgroundInline,

    /** API 30+: background location can only be granted from the app's system settings. */
    DirectToSystemSettings,
}

/**
 * Pure decision for the next background-location step. No Android imports so it can be unit
 * tested on the JVM (same pattern as `travelOctant` and `classifyUvRisk`).
 *
 * @param sdkInt the device API level (`Build.VERSION.SDK_INT`)
 * @param foregroundGranted whether ACCESS_FINE or ACCESS_COARSE location is granted
 * @param backgroundGranted whether ACCESS_BACKGROUND_LOCATION is granted
 */
fun nextBackgroundLocationStep(
    sdkInt: Int,
    foregroundGranted: Boolean,
    backgroundGranted: Boolean,
): BackgroundLocationStep = when {
    backgroundGranted -> BackgroundLocationStep.AlreadyGranted
    !foregroundGranted -> BackgroundLocationStep.RequestForegroundFirst
    // On API <= 28 background location does not exist as a separate permission; holding a
    // foreground grant means background access is already available.
    sdkInt <= 28 -> BackgroundLocationStep.AlreadyGranted
    sdkInt == 29 -> BackgroundLocationStep.RequestBackgroundInline
    else -> BackgroundLocationStep.DirectToSystemSettings
}
