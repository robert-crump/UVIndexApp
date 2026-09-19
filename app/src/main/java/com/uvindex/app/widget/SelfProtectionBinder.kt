package com.uvindex.app.widget

import com.uvindex.app.data.model.UVForecast
import com.uvindex.app.uv.SkinType
import com.uvindex.app.uv.classifyUvRisk
import com.uvindex.app.uv.isNoUvRisk
import com.uvindex.app.uv.protectionTimeCompact

/** What the self-protection-time widget should draw. */
data class SelfProtectionBinding(val valueText: String, val tone: WidgetTone, val tapTarget: WidgetTapTarget)

const val SKIN_TYPE_PROMPT = "?"

/** Pure binder. No skin type asks for one and opens settings; UV below 1 needs no protection. */
fun bindSelfProtection(forecast: UVForecast?, skinType: SkinType?): SelfProtectionBinding {
    if (skinType == null) {
        return SelfProtectionBinding(SKIN_TYPE_PROMPT, WidgetTone.Neutral, WidgetTapTarget.Settings)
    }
    if (forecast == null) {
        return SelfProtectionBinding(WIDGET_PLACEHOLDER, WidgetTone.Neutral, WidgetTapTarget.MainActivity)
    }
    val uv = forecast.currentHour.uvIndex
    val text = if (isNoUvRisk(uv)) WIDGET_PLACEHOLDER else protectionTimeCompact(skinType.protectionMinutes(uv))
    return SelfProtectionBinding(text, WidgetTone.Risk(classifyUvRisk(uv)), WidgetTapTarget.MainActivity)
}
