package com.uvindex.app.widget

import com.uvindex.app.uv.UvRisk

/** The one "no data" text shared by every widget. */
const val WIDGET_PLACEHOLDER = "–"

/** The one "no data" foreground color (#999999) shared by every widget. */
const val WIDGET_ERROR_COLOR = 0xFF999999.toInt()

/** Color/background category of a widget: a UV risk, or the neutral "no data" look. */
sealed interface WidgetTone {
    data class Risk(val risk: UvRisk) : WidgetTone
    data object Neutral : WidgetTone
}

/** Which activity a widget tap opens. */
enum class WidgetTapTarget { MainActivity, Settings }
