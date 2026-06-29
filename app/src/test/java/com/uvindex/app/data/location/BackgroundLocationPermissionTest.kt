package com.uvindex.app.data.location

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundLocationPermissionTest {

    @Test
    fun `already granted short-circuits regardless of sdk`() {
        assertEquals(
            BackgroundLocationStep.AlreadyGranted,
            nextBackgroundLocationStep(sdkInt = 33, foregroundGranted = true, backgroundGranted = true)
        )
        // Even if foreground is somehow false, an existing background grant wins.
        assertEquals(
            BackgroundLocationStep.AlreadyGranted,
            nextBackgroundLocationStep(sdkInt = 29, foregroundGranted = false, backgroundGranted = true)
        )
    }

    @Test
    fun `foreground missing is requested first`() {
        assertEquals(
            BackgroundLocationStep.RequestForegroundFirst,
            nextBackgroundLocationStep(sdkInt = 33, foregroundGranted = false, backgroundGranted = false)
        )
        assertEquals(
            BackgroundLocationStep.RequestForegroundFirst,
            nextBackgroundLocationStep(sdkInt = 28, foregroundGranted = false, backgroundGranted = false)
        )
    }

    @Test
    fun `api 28 and below grant background via foreground`() {
        assertEquals(
            BackgroundLocationStep.AlreadyGranted,
            nextBackgroundLocationStep(sdkInt = 28, foregroundGranted = true, backgroundGranted = false)
        )
        assertEquals(
            BackgroundLocationStep.AlreadyGranted,
            nextBackgroundLocationStep(sdkInt = 23, foregroundGranted = true, backgroundGranted = false)
        )
    }

    @Test
    fun `api 29 requests background inline`() {
        assertEquals(
            BackgroundLocationStep.RequestBackgroundInline,
            nextBackgroundLocationStep(sdkInt = 29, foregroundGranted = true, backgroundGranted = false)
        )
    }

    @Test
    fun `api 30 and above direct to system settings`() {
        assertEquals(
            BackgroundLocationStep.DirectToSystemSettings,
            nextBackgroundLocationStep(sdkInt = 30, foregroundGranted = true, backgroundGranted = false)
        )
        assertEquals(
            BackgroundLocationStep.DirectToSystemSettings,
            nextBackgroundLocationStep(sdkInt = 33, foregroundGranted = true, backgroundGranted = false)
        )
    }
}
