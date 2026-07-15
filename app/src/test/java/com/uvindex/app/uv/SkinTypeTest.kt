package com.uvindex.app.uv

import org.junit.Assert.assertEquals
import org.junit.Test

class SkinTypeTest {

    // ── protectionTimeCompact ────────────────────────────────────────────────

    @Test
    fun `protectionTimeCompact renders minutes only under an hour`() {
        assertEquals("15m", protectionTimeCompact(15))
        assertEquals("5m", protectionTimeCompact(5))
    }

    @Test
    fun `protectionTimeCompact renders hours only on exact hour boundary`() {
        assertEquals("1h", protectionTimeCompact(60))
        assertEquals("2h", protectionTimeCompact(120))
    }

    @Test
    fun `protectionTimeCompact renders half-hour steps as a decimal hour value`() {
        assertEquals("1.5h", protectionTimeCompact(90))
        assertEquals("2.5h", protectionTimeCompact(150))
    }

    // ── protectionMinutes rounding/clamp feeding into the compact string ──────

    @Test
    fun `protectionMinutes is clamped to a minimum of 5`() {
        val minutes = SkinType.TYPE_I.protectionMinutes(uvIndex = 100.0)
        assertEquals(5, minutes)
        assertEquals("5m", protectionTimeCompact(minutes))
    }

    @Test
    fun `protectionMinutes rounds to the nearest 5 minutes`() {
        // raw = 300 / (7.0 * 1.5) = 28.57 -> rounds to 30
        val minutes = SkinType.TYPE_III.protectionMinutes(uvIndex = 7.0)
        assertEquals(30, minutes)
    }

    @Test
    fun `protectionMinutes rounds values from 55 to 60 up to a full hour`() {
        // raw = 450 / (5.0 * 1.5) = 60.0 -> exactly 1h
        assertEquals(60, SkinType.TYPE_IV.protectionMinutes(uvIndex = 5.0))
        // raw = 300 / (3.4 * 1.5) = 58.8 -> falls in the 55-60 band
        assertEquals(60, SkinType.TYPE_III.protectionMinutes(uvIndex = 3.4))
    }

    @Test
    fun `protectionMinutes rounds to the nearest half hour above an hour`() {
        // raw = 600 / (4.0 * 1.5) = 100.0 -> nearest 30 is 90
        assertEquals(90, SkinType.TYPE_V.protectionMinutes(uvIndex = 4.0))
        // raw = 1000 / (4.0 * 1.5) = 166.67 -> nearest 30 is 180
        assertEquals(180, SkinType.TYPE_VI.protectionMinutes(uvIndex = 4.0))
    }

    @Test
    fun `protectionMinutes feeds protectionTimeCompact as a decimal hour above an hour`() {
        val minutes = SkinType.TYPE_V.protectionMinutes(uvIndex = 4.0)
        assertEquals("1.5h", protectionTimeCompact(minutes))
    }
}
