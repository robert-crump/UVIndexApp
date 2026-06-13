package com.uvindex.app.wind

import kotlin.math.roundToInt

/**
 * The eight compass points an arrow can travel toward, ordered clockwise from North.
 * Ordinal matches the 45° octant index (N = 0, NE = 1, … NW = 7).
 */
enum class CompassOctant { N, NE, E, SE, S, SW, W, NW }

/**
 * Converts a meteorological wind direction — the bearing the wind comes **FROM**, as
 * supplied by Open-Meteo's `winddirection_10m` — into the octant the wind is travelling
 * **TOWARD**. The widget arrow points in the direction of travel, so a wind "from North"
 * (source bearing 0°) yields a southward arrow ([CompassOctant.S]).
 *
 * Pure function: bucket the travel bearing to the nearest of 8 compass points (45° each).
 */
fun travelOctant(sourceBearingDegrees: Double): CompassOctant {
    // Direction of travel is opposite the source bearing.
    val travel = sourceBearingDegrees + 180.0
    // Normalize into [0, 360) so negative or >360 inputs are handled.
    val normalized = ((travel % 360.0) + 360.0) % 360.0
    val index = (normalized / 45.0).roundToInt() % 8
    return CompassOctant.entries[index]
}
