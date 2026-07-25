package com.mnivesh.callyn.managers

import android.content.Context
import android.content.SharedPreferences

class AuthManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("AuthPrefs", Context.MODE_PRIVATE)

    private val AUTH_TOKEN = "AuthToken"
    private val REFRESH_TOKEN = "RefreshToken"
    private val USER_NAME = "UserName"

    private val USER_EMAIL = "UserEmail"

    private val KEY_DEPARTMENT = "user_department"
    private val WORK_PHONE = "work_phone"

    private val KEY_LAST_REFRESH_TIME = "last_token_refresh_time"

    fun isLoggedIn(): Boolean = prefs.contains(AUTH_TOKEN) && !getToken().isNullOrEmpty()

    fun saveToken(token: String) {
        prefs.edit().putString(AUTH_TOKEN, token).apply()
        updateLastRefreshTime()
    }
    fun getToken(): String? = prefs.getString(AUTH_TOKEN, null)
    fun saveRefreshToken(token: String?) = prefs.edit().putString(REFRESH_TOKEN, token).apply()
    fun getRefreshToken(): String? = prefs.getString(REFRESH_TOKEN, null)

    fun updateLastRefreshTime(timestampMs: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_REFRESH_TIME, timestampMs).apply()
    }

    fun getLastRefreshTime(): Long = prefs.getLong(KEY_LAST_REFRESH_TIME, 0L)

    /**
     * Checks whether the token should be proactively refreshed.
     * Default threshold is 12 hours.
     */
    fun shouldRefreshToken(thresholdMillis: Long = 12 * 60 * 60 * 1000L): Boolean {
        if (!isLoggedIn()) return false
        val lastRefresh = getLastRefreshTime()
        if (lastRefresh == 0L) return true
        return (System.currentTimeMillis() - lastRefresh) >= thresholdMillis
    }

    fun saveUserName(name: String?) = prefs.edit().putString(USER_NAME, name).apply()
    fun getUserName(): String? = prefs.getString(USER_NAME, null)

    fun saveUserEmail(email: String?) = prefs.edit().putString(USER_EMAIL, email).apply()

    fun getUserEmail(): String? = prefs.getString(USER_EMAIL, null)

    fun saveDepartment(department: String) {
        prefs.edit().putString(KEY_DEPARTMENT, department).apply()
    }
    fun getDepartment(): String? {
        return prefs.getString(KEY_DEPARTMENT, null)
    }

    fun saveWorkPhone(workPhone: String?) {
        prefs.edit().putString(WORK_PHONE, workPhone).apply()
    }
    fun getWorkPhone(): String? {
        return prefs.getString(WORK_PHONE, null)
    }

    private val KEY_SETUP_COMPLETED = "setup_completed"

    fun isSetupCompleted(): Boolean = prefs.getBoolean(KEY_SETUP_COMPLETED, false)

    fun setSetupCompleted(isCompleted: Boolean) {
        prefs.edit().putBoolean(KEY_SETUP_COMPLETED, isCompleted).apply()
    }
    // Clears all saved data (token and username)
    fun logout() = prefs.edit().clear().apply()
}