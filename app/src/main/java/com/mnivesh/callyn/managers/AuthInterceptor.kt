package com.mnivesh.callyn.api

import android.content.Context
import android.content.Intent
import com.mnivesh.callyn.MainActivity
import com.mnivesh.callyn.managers.AuthManager
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

class AuthInterceptor(private val context: Context) : Interceptor {
    private val authManager = AuthManager(context)

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Let normal requests pass first
        var response = chain.proceed(originalRequest)

        // Catch 401 Unauthorized
        if (response.code == 401) {
            synchronized(this) {
                val currentToken = authManager.getToken()
                val previousToken = originalRequest.header("Authorization")?.removePrefix("Bearer ")?.trim()

                // Check if another thread already refreshed the token while we waited
                if (currentToken != null && currentToken != previousToken) {
                    response.close()
                    return chain.proceed(newRequestWithToken(originalRequest, currentToken))
                }

                // Actually try the refresh
                val refreshToken = authManager.getRefreshToken()
                if (!refreshToken.isNullOrEmpty()) {
                    val refreshedToken = performRefresh(refreshToken)
                    if (refreshedToken != null) {
                        response.close()
                        return chain.proceed(newRequestWithToken(originalRequest, refreshedToken))
                    }
                }

                // If everything fails, kick them out
                logoutAndRedirect()
            }
        }

        return response
    }

    private fun newRequestWithToken(request: Request, token: String): Request {
        // Strip the old auth header and slap the new one on
        return request.newBuilder()
            .removeHeader("Authorization")
            .addHeader("Authorization", "Bearer $token")
            .build()
    }

    private fun performRefresh(refreshToken: String): String? {
        // Fresh client to avoid infinite interceptor loops
        val client = OkHttpClient()

        val json = JSONObject().apply {
            put("refreshToken", refreshToken)
        }

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("https://app-store-dqg8bnf4d8cberf7.centralindia-01.azurewebsites.net/mobile/refresh")
            .post(body)
            .build()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val jsonObject = JSONObject(responseBody)
                    val newAccessToken = jsonObject.optString("accessToken")
                    val newRefreshToken = jsonObject.optString("refreshToken")

                    if (newAccessToken.isNotEmpty()) {
                        authManager.saveToken(newAccessToken)

                        // Update refresh token if backend rotates it
                        if (newRefreshToken.isNotEmpty()) {
                            authManager.saveRefreshToken(newRefreshToken)
                        }
                        return newAccessToken
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun logoutAndRedirect() {
        authManager.logout()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }
}