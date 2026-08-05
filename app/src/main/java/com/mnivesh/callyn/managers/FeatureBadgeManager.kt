package com.mnivesh.callyn.managers

import android.content.Context
import android.content.SharedPreferences

enum class FeatureBadgeKey(val prefKey: String) {
    QUICK_REPLIES("feature_quick_replies_visited")
    // Easily add future features here, e.g.:
    // CALL_LOGS("feature_call_logs_visited")
}

class FeatureBadgeManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("FeatureBadgePrefs", Context.MODE_PRIVATE)

    /**
     * Returns true if the user has NOT visited the feature yet.
     */
    fun isFeatureUnvisited(feature: FeatureBadgeKey): Boolean {
        return !prefs.getBoolean(feature.prefKey, false)
    }

    /**
     * Marks the feature as visited so the red badge will disappear.
     */
    fun markFeatureVisited(feature: FeatureBadgeKey) {
        prefs.edit().putBoolean(feature.prefKey, true).apply()
    }

    /**
     * Checks if ANY feature is still unvisited.
     * Used for displaying the red dot indicator on the app drawer hamburger icon.
     */
    fun hasAnyUnvisitedFeature(): Boolean {
        return FeatureBadgeKey.entries.any { isFeatureUnvisited(it) }
    }
}
