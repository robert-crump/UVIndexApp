package com.uvindex.app.uv

/**
 * Shared protection-recommendation text, used by both the Info screen and the Daily
 * Forecast Notification so they never drift out of sync. Plain Kotlin (no Android
 * dependency) so [com.uvindex.app.notification.NotificationDecider] stays pure/testable.
 */
object UvProtectionRecommendations {
    const val Moderate = "Sonnenbrille, Sonnencreme"
    const val High = "Schatten, Sonnenbrille, Sonnencreme"
}
