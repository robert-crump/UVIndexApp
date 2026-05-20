package com.uvindex.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.uvindex.app.UVIndexApplication
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Verifies that [NotificationDispatcher] builds the expected Android [android.app.Notification]
 * for a [Channel.Daily] decision and returns false gracefully when permission is missing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class NotificationDispatcherTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create the channel so the notification is accepted
        notificationManager.createNotificationChannel(
            NotificationChannel(
                UVIndexApplication.NOTIFICATION_CHANNEL_ID,
                "Test channel",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
    }

    private fun dailyDecision(
        title: String = "UV: Tagesprognose",
        body: String = "Tagesmaximum: 7 (hoch).",
    ) = NotificationDecision(
        channel = Channel.Daily,
        title = title,
        body = body,
        priority = Priority.Default,
        actions = emptyList(),
    )

    @Test
    fun `send returns false when POST_NOTIFICATIONS permission is missing`() = runTest {
        // Robolectric denies POST_NOTIFICATIONS by default on SDK 33 unless explicitly granted
        val dispatcher = NotificationDispatcher(context)
        val result = dispatcher.send(dailyDecision())
        assertFalse("Expected false when POST_NOTIFICATIONS is not granted", result)
    }

    @Test
    fun `send posts notification with correct ID title and body for Daily decision`() = runTest {
        // Grant permission via Robolectric's ShadowApplication
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val decision = dailyDecision()
        val dispatcher = NotificationDispatcher(context)
        val result = dispatcher.send(decision)

        assertTrue("Expected true on successful dispatch", result)

        val shadow = Shadows.shadowOf(notificationManager)
        val posted = shadow.allNotifications
        assertFalse("Expected at least one notification posted", posted.isEmpty())

        val notification = shadow.getNotification(null, Channel.Daily.notificationId)
        assertNotNull("Expected notification with ID ${Channel.Daily.notificationId}", notification)
        assertEquals(decision.title, notification!!.extras.getString("android.title"))
        assertEquals(decision.body, notification.extras.getString("android.text"))
    }

    @Test
    fun `send posts UV Warning notification with correct ID and action button`() = runTest {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val decision = NotificationDecision(
            channel = Channel.UvWarning,
            phase = Phase.InWindow,
            title = "UV-Warnung",
            body = "Hohe UV-Strahlung zwischen 10:00 und 14:00 Uhr.",
            priority = Priority.High,
            actions = listOf(Action.DisableUvWarningsToday),
            warnedAbout = WarnedAbout(peak = 7.0f, firstHighHour = 10, highHours = setOf(10, 11, 12, 13)),
        )

        val dispatcher = NotificationDispatcher(context)
        val result = dispatcher.send(decision)

        assertTrue("Expected true on successful dispatch", result)

        val shadow = Shadows.shadowOf(notificationManager)
        val notification = shadow.getNotification(null, Channel.UvWarning.notificationId)
        assertNotNull("Expected notification with ID ${Channel.UvWarning.notificationId}", notification)
        assertEquals(decision.title, notification!!.extras.getString("android.title"))
        assertNotNull("Expected at least one action (Disable)", notification.actions)
        assertTrue("Expected Disable action button", notification.actions.isNotEmpty())
    }
}
