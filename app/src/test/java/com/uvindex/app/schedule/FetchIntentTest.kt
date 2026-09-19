package com.uvindex.app.schedule

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class FetchIntentTest {

    @Test
    fun `top of a daytime hour forces a fresh fetch`() {
        assertEquals(FetchIntent.Fresh, fetchIntentFor(LocalTime.of(6, 0)))
        assertEquals(FetchIntent.Fresh, fetchIntentFor(LocalTime.of(12, 0)))
        assertEquals(FetchIntent.Fresh, fetchIntentFor(LocalTime.of(17, 0)))
    }

    @Test
    fun `top of a night hour fetches only if stale`() {
        assertEquals(FetchIntent.FreshIfStale, fetchIntentFor(LocalTime.of(5, 0)))
        assertEquals(FetchIntent.FreshIfStale, fetchIntentFor(LocalTime.of(18, 0)))
        assertEquals(FetchIntent.FreshIfStale, fetchIntentFor(LocalTime.of(0, 0)))
    }

    @Test
    fun `any other minute reads the cache`() {
        assertEquals(FetchIntent.Cached, fetchIntentFor(LocalTime.of(12, 30)))
        assertEquals(FetchIntent.Cached, fetchIntentFor(LocalTime.of(12, 1)))
        assertEquals(FetchIntent.Cached, fetchIntentFor(LocalTime.of(3, 30)))
    }
}
