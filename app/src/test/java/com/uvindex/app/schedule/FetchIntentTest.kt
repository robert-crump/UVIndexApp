package com.uvindex.app.schedule

import com.uvindex.app.data.repository.FetchIntent
import java.time.LocalTime
import org.junit.Test
import org.junit.Assert.assertEquals

class FetchIntentTest {
    @Test
    fun `top of daytime hours is Fresh`() {
        assertEquals(FetchIntent.Fresh, fetchIntentFor(LocalTime.of(6, 0)))
        assertEquals(FetchIntent.Fresh, fetchIntentFor(LocalTime.of(12, 0)))
        assertEquals(FetchIntent.Fresh, fetchIntentFor(LocalTime.of(17, 0)))
    }

    @Test
    fun `outside daytime window is FreshIfStale`() {
        assertEquals(FetchIntent.FreshIfStale, fetchIntentFor(LocalTime.of(5, 59)))
        assertEquals(FetchIntent.FreshIfStale, fetchIntentFor(LocalTime.of(18, 0)))
        assertEquals(FetchIntent.FreshIfStale, fetchIntentFor(LocalTime.of(0, 0)))
    }

    @Test
    fun `half past the hour is FreshIfStale`() {
        assertEquals(FetchIntent.FreshIfStale, fetchIntentFor(LocalTime.of(6, 30)))
        assertEquals(FetchIntent.FreshIfStale, fetchIntentFor(LocalTime.of(12, 30)))
    }
}
