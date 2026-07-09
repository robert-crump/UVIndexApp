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
    fun `protectionTimeCompact renders hours and minutes joined without spaces`() {
        assertEquals("1h15m", protectionTimeCompact(75))
        assertEquals("2h5m", protectionTimeCompact(125))
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
}
