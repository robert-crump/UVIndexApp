package com.uvindex.app.data.location

import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceTest {
    @Test
    fun samePointIsZero() {
        assertEquals(0.0, haversineDistanceKm(52.52, 13.405, 52.52, 13.405), 1e-9)
    }

    @Test
    fun berlinToMunichWithinOnePercent() {
        val expected = 504.0
        val actual = haversineDistanceKm(52.5200, 13.4050, 48.1351, 11.5820)
        assertEquals(expected, actual, expected * 0.01)
    }
}
