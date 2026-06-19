package com.mnivesh.callyn.managers

import android.content.Context
import android.content.SharedPreferences

class ThemeManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)

    private val KEY_DARK_MODE = "dark_mode"

    // Default to true (Dark Theme) to match original hardcoded behavior
    fun isDarkTheme(): Boolean {
        return prefs.getBoolean(KEY_DARK_MODE, true)
    }

    fun setDarkTheme(isDark: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, isDark).apply()
    }
}
