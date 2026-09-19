package com.uvindex.app.permission

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPermissionsTest {

    private val coarse = Manifest.permission.ACCESS_COARSE_LOCATION
    private val fine = Manifest.permission.ACCESS_FINE_LOCATION

    @Test
    fun `coarse-only result counts as location granted`() {
        assertTrue(isLocationGranted(mapOf(coarse to true)))
        assertTrue(isLocationGranted(mapOf(coarse to true, fine to false)))
    }

    @Test
    fun `fine-only result counts as location granted`() {
        assertTrue(isLocationGranted(mapOf(fine to true, coarse to false)))
    }

    @Test
    fun `both granted counts as location granted`() {
        assertTrue(isLocationGranted(mapOf(fine to true, coarse to true)))
    }

    @Test
    fun `none granted or empty result is not granted`() {
        assertFalse(isLocationGranted(mapOf(fine to false, coarse to false)))
        assertFalse(isLocationGranted(emptyMap()))
    }

    @Test
    fun `location is requested first when missing`() {
        assertEquals(PermissionRequest.Location, nextPermissionToRequest(33, false, false))
        assertEquals(PermissionRequest.Location, nextPermissionToRequest(28, false, true))
    }

    @Test
    fun `notification is requested after location on SDK 33`() {
        assertEquals(PermissionRequest.Notification, nextPermissionToRequest(33, true, false))
    }

    @Test
    fun `notification is never requested below SDK 33`() {
        assertEquals(PermissionRequest.None, nextPermissionToRequest(32, true, false))
    }

    @Test
    fun `nothing to request when everything is granted`() {
        assertEquals(PermissionRequest.None, nextPermissionToRequest(33, true, true))
    }
}
