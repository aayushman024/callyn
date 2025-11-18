package com.example.callyn

import android.content.Context
import android.content.SharedPreferences

class AuthManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("AuthPrefs", Context.MODE_PRIVATE)

    private val AUTH_TOKEN = "AuthToken"
    private val USER_NAME = "UserName"

    fun isLoggedIn(): Boolean = prefs.contains(AUTH_TOKEN)

    fun saveToken(token: String) = prefs.edit().putString(AUTH_TOKEN, token).apply()
    fun getToken(): String? = prefs.getString(AUTH_TOKEN, null)

    fun saveUserName(name: String) = prefs.edit().putString(USER_NAME, name).apply()
    fun getUserName(): String? = prefs.getString(USER_NAME, null)

    // Clears all saved data (token and username)
    fun logout() = prefs.edit().clear().apply()
}