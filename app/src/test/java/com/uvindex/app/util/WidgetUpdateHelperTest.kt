package com.uvindex.app.util

import android.app.Application
import android.appwidget.AppWidgetManager
import androidx.test.core.app.ApplicationProvider
import com.uvindex.app.R
import com.uvindex.app.widget.AirQualityWidget
import com.uvindex.app.widget.SelfProtectionTimeWidget
import com.uvindex.app.widget.UVWidget
import com.uvindex.app.widget.UVWidgetCurrent
import com.uvindex.app.widget.WindWidget
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Verifies that [WidgetUpdateHelper.updateAllWidgets] - the single entry point every trigger
 * site now goes through (see issue #32) - broadcasts ACTION_APPWIDGET_UPDATE with the placed
 * ids for each of the five widget provider classes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class WidgetUpdateHelperTest {

    @Test
    fun `updateAllWidgets broadcasts ACTION_APPWIDGET_UPDATE with placed ids for every provider`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val shadowManager = shadowOf(AppWidgetManager.getInstance(context))

        val expectedIds = mapOf(
            UVWidget::class.java to shadowManager.createWidget(UVWidget::class.java, R.layout.uv_widget),
            UVWidgetCurrent::class.java to shadowManager.createWidget(UVWidgetCurrent::class.java, R.layout.uv_widget_current),
            WindWidget::class.java to shadowManager.createWidget(WindWidget::class.java, R.layout.wind_widget),
            SelfProtectionTimeWidget::class.java to shadowManager.createWidget(SelfProtectionTimeWidget::class.java, R.layout.self_protection_time_widget),
            AirQualityWidget::class.java to shadowManager.createWidget(AirQualityWidget::class.java, R.layout.air_quality_widget),
        )

        WidgetUpdateHelper.updateAllWidgets(context)

        val broadcasts = shadowOf(context).broadcastIntents

        expectedIds.forEach { (widgetClass, id) ->
            val matching = broadcasts.firstOrNull { it.component?.className == widgetClass.name }
            assertNotNull("Expected a broadcast targeting ${widgetClass.simpleName}", matching)
            assertEquals(AppWidgetManager.ACTION_APPWIDGET_UPDATE, matching!!.action)
            assertArrayEquals(
                "Expected ${widgetClass.simpleName} broadcast to carry its placed widget id",
                intArrayOf(id),
                matching.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
            )
        }
    }
}
