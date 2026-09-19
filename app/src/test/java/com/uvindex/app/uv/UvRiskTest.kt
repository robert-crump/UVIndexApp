package com.uvindex.app.uv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UvRiskTest {

    // ── classifyUvRisk ────────────────────────────────────────────────────────

    @Test
    fun `classifyUvRisk returns None for values below 3`() {
        assertEquals(UvRisk.None, classifyUvRisk(0.0))
        assertEquals(UvRisk.None, classifyUvRisk(2.9))
    }

    @Test
    fun `classifyUvRisk returns Moderate for values from 3 below 6`() {
        assertEquals(UvRisk.Moderate, classifyUvRisk(3.0))
        assertEquals(UvRisk.Moderate, classifyUvRisk(5.9))
    }

    @Test
    fun `classifyUvRisk returns High at boundary 6_0`() {
        assertEquals(UvRisk.High, classifyUvRisk(6.0))
    }

    @Test
    fun `classifyUvRisk returns High for values between 6_0 and 8_0`() {
        assertEquals(UvRisk.High, classifyUvRisk(7.0))
        assertEquals(UvRisk.High, classifyUvRisk(7.9))
    }

    @Test
    fun `classifyUvRisk returns VeryHigh at boundary 8_0`() {
        assertEquals(UvRisk.VeryHigh, classifyUvRisk(8.0))
    }

    @Test
    fun `classifyUvRisk returns VeryHigh for values above 8_0`() {
        assertEquals(UvRisk.VeryHigh, classifyUvRisk(9.0))
        assertEquals(UvRisk.VeryHigh, classifyUvRisk(11.0))
    }

    // ── isHigh ────────────────────────────────────────────────────────────────

    @Test
    fun `isHigh returns true for High and VeryHigh`() {
        assertTrue(UvRisk.High.isHigh())
        assertTrue(UvRisk.VeryHigh.isHigh())
    }

    @Test
    fun `isHigh returns false for None and Moderate`() {
        assertFalse(UvRisk.None.isHigh())
        assertFalse(UvRisk.Moderate.isHigh())
    }

    // ── isVeryHigh ───────────────────────────────────────────────────────────

    @Test
    fun `isVeryHigh returns true only for VeryHigh`() {
        assertTrue(UvRisk.VeryHigh.isVeryHigh())
        assertFalse(UvRisk.High.isVeryHigh())
        assertFalse(UvRisk.Moderate.isVeryHigh())
        assertFalse(UvRisk.None.isVeryHigh())
    }

    // ── germanLabel / isNoUvRisk at band boundaries ──────────────────────────

    @Test
    fun `category label per band boundary`() {
        val expected = mapOf(
            0.0 to "Niedrig", 1.0 to "Niedrig", 2.0 to "Niedrig",
            3.0 to "Mittel", 5.0 to "Mittel",
            6.0 to "Hoch", 7.0 to "Hoch",
            8.0 to "Sehr hoch"
        )
        expected.forEach { (uv, label) -> assertEquals("UV $uv", label, classifyUvRisk(uv).germanLabel()) }
    }

    @Test
    fun `isNoUvRisk per band boundary`() {
        assertTrue(isNoUvRisk(0.0))
        assertTrue(isNoUvRisk(0.9))
        listOf(1.0, 2.0, 3.0, 5.0, 6.0, 7.0, 8.0).forEach { assertFalse("UV $it", isNoUvRisk(it)) }
    }
}
