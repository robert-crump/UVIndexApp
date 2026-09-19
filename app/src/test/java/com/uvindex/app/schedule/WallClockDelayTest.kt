package com.uvindex.app.schedule

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class WallClockDelayTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val target = LocalTime.of(6, 30)

    private fun at(dateTime: String) = LocalDateTime.parse(dateTime).atZone(berlin)

    @Test
    fun targetLaterToday() {
        assertEquals(Duration.ofHours(2), delayUntilNext(at("2026-06-10T04:30:00"), target))
    }

    @Test
    fun targetAlreadyPassedToday_resolvesToTomorrow() {
        assertEquals(Duration.ofHours(23), delayUntilNext(at("2026-06-10T07:30:00"), target))
    }

    @Test
    fun exactlyAtTarget_resolvesToTomorrow() {
        assertEquals(Duration.ofHours(24), delayUntilNext(at("2026-06-10T06:30:00"), target))
    }

    @Test
    fun springForwardDay_isOneHourShorter() {
        // 2026-03-29: 02:00 -> 03:00 in Europe/Berlin
        assertEquals(Duration.ofHours(5), delayUntilNext(at("2026-03-29T00:30:00"), target))
    }

    @Test
    fun fallBackDay_isOneHourLonger() {
        // 2026-10-25: 03:00 -> 02:00 in Europe/Berlin
        assertEquals(Duration.ofHours(7), delayUntilNext(at("2026-10-25T00:30:00"), target))
    }
}
