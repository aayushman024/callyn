package com.example.callyn

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages saving and checking the login token using SharedPreferences.
 */
class AuthManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("AuthPrefs", Context.MODE_PRIVATE)

    private val AUTH_TOKEN = "AuthToken"

    fun isLoggedIn(): Boolean = prefs.contains(AUTH_TOKEN)
    fun saveToken(token: String) = prefs.edit().putString(AUTH_TOKEN, token).apply()
    fun getToken(): String? = prefs.getString(AUTH_TOKEN, null)
    fun logout() = prefs.edit().remove(AUTH_TOKEN).apply()
}
