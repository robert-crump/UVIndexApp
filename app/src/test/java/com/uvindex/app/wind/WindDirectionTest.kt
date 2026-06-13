package com.uvindex.app.wind

import org.junit.Assert.assertEquals
import org.junit.Test

class WindDirectionTest {

    // ── Cardinal sources: arrow points the opposite way (direction of travel) ──

    @Test
    fun `wind from North travels South`() {
        assertEquals(CompassOctant.S, travelOctant(0.0))
    }

    @Test
    fun `wind from East travels West`() {
        assertEquals(CompassOctant.W, travelOctant(90.0))
    }

    @Test
    fun `wind from South travels North`() {
        assertEquals(CompassOctant.N, travelOctant(180.0))
    }

    @Test
    fun `wind from West travels East`() {
        assertEquals(CompassOctant.E, travelOctant(270.0))
    }

    // ── Intercardinal sources ──────────────────────────────────────────────────

    @Test
    fun `wind from NorthEast travels SouthWest`() {
        assertEquals(CompassOctant.SW, travelOctant(45.0))
    }

    @Test
    fun `wind from SouthWest travels NorthEast`() {
        assertEquals(CompassOctant.NE, travelOctant(225.0))
    }

    // ── Rounding to the nearest 45° bucket ──────────────────────────────────────

    @Test
    fun `bearing rounds down to nearest octant below the midpoint`() {
        // source 22° → travel 202° → 202/45 = 4.49 → octant 4 (S)
        assertEquals(CompassOctant.S, travelOctant(22.0))
    }

    @Test
    fun `bearing rounds up to nearest octant above the midpoint`() {
        // source 23° → travel 203° → 203/45 = 4.51 → octant 5 (SW)
        assertEquals(CompassOctant.SW, travelOctant(23.0))
    }

    // ── Normalization: wrap-around and out-of-range inputs ──────────────────────

    @Test
    fun `bearing near 360 wraps correctly`() {
        // source 350° → travel 530° → 170° → octant 4 (S)
        assertEquals(CompassOctant.S, travelOctant(350.0))
    }

    @Test
    fun `exactly 360 degrees is treated as North source`() {
        assertEquals(CompassOctant.S, travelOctant(360.0))
    }

    @Test
    fun `negative bearing is normalized`() {
        // -90° ≡ 270° (from West) → travels East
        assertEquals(CompassOctant.E, travelOctant(-90.0))
    }
}
