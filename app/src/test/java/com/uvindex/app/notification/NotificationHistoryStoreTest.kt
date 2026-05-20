package com.uvindex.app.notification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * Store migration tests.
 *
 * Pattern for future slices: annotate with @RunWith(RobolectricTestRunner::class) and obtain
 * a real Context via ApplicationProvider.getApplicationContext(). SharedPreferences operations
 * behave identically to a real device inside Robolectric's in-memory storage.
 *
 * @Config(application = Application::class) prevents UVIndexApplication.onCreate() from running,
 * which would fail because WorkManager is not initialized in the Robolectric test environment.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application = android.app.Application::class)
class NotificationHistoryStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Wipe all prefs before each test for isolation
        listOf("uv_notification_history", "uv_notifications", "uv_app_settings").forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun `snapshot migrates legacy daily keys on first call`() = runTest {
        val sentDate = LocalDate.now().minusDays(1).toString()
        val sentTimestamp = System.currentTimeMillis() - 86_400_000L

        context.getSharedPreferences("uv_notifications", Context.MODE_PRIVATE).edit()
            .putString("daily_sent_date", sentDate)
            .putLong("daily_sent_timestamp", sentTimestamp)
            .commit()

        context.getSharedPreferences("uv_app_settings", Context.MODE_PRIVATE).edit()
            .putBoolean("daily_notification_enabled", false)
            .putBoolean("hourly_notification_enabled", true)
            .commit()

        val store = SharedPreferencesNotificationHistoryStore(context)
        val history = store.snapshot()

        assertFalse("dailyEnabled should reflect legacy setting", history.dailyEnabled)
        assertTrue("uvWarningEnabled should reflect legacy setting", history.uvWarningEnabled)
        assertEquals("lastDailySent should carry over legacy date", LocalDate.parse(sentDate), history.lastDailySent)
        assertNotNull("lastDailySentAt should carry over legacy timestamp", history.lastDailySentAt)
    }

    @Test
    fun `snapshot migration does not re-fire daily on same morning`() = runTest {
        val today = LocalDate.now().toString()

        context.getSharedPreferences("uv_notifications", Context.MODE_PRIVATE).edit()
            .putString("daily_sent_date", today)
            .putLong("daily_sent_timestamp", System.currentTimeMillis())
            .commit()

        val store = SharedPreferencesNotificationHistoryStore(context)
        val history = store.snapshot()

        // lastDailySent == today means decide() will return no Daily decision
        assertEquals("lastDailySent should be today after migration", LocalDate.parse(today), history.lastDailySent)
    }

    @Test
    fun `snapshot returns defaults when no legacy data exists`() = runTest {
        val store = SharedPreferencesNotificationHistoryStore(context)
        val history = store.snapshot()

        assertTrue(history.dailyEnabled)
        assertTrue(history.uvWarningEnabled)
        assertNull(history.lastDailySent)
        assertNull(history.lastDailySentAt)
    }

    @Test
    fun `migration runs only once even if called multiple times`() = runTest {
        context.getSharedPreferences("uv_notifications", Context.MODE_PRIVATE).edit()
            .putString("daily_sent_date", "2026-01-01")
            .commit()

        val store = SharedPreferencesNotificationHistoryStore(context)
        store.snapshot()

        // Simulate legacy prefs changing after first snapshot (should be ignored)
        context.getSharedPreferences("uv_notifications", Context.MODE_PRIVATE).edit()
            .putString("daily_sent_date", "2026-06-01")
            .commit()

        val history2 = store.snapshot()
        assertEquals(
            "Second snapshot should not re-migrate",
            LocalDate.parse("2026-01-01"),
            history2.lastDailySent,
        )
    }
}
