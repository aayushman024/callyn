package com.mnivesh.callyn.managers

import android.content.Context
import android.content.Intent
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mnivesh.callyn.MainActivity
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AuthInterceptor(private val context: Context) : Interceptor {
    private val authManager = AuthManager(context)

    private sealed class RefreshResult {
        data class Success(val accessToken: String) : RefreshResult()
        object InvalidToken : RefreshResult()
        object NetworkError : RefreshResult()
    }

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

                val savedToken = authManager.getToken()
                val refreshToken = authManager.getRefreshToken()

                // If the user has no saved access token or refresh token, they are already logged out.
                // We should let the 401 propagate naturally rather than redirecting/restarting the app.
                if (savedToken.isNullOrEmpty() || refreshToken.isNullOrEmpty()) {
                    return response
                }

                val refreshResult = performRefresh(refreshToken)
                when (refreshResult) {
                    is RefreshResult.Success -> {
                        response.close()
                        return chain.proceed(newRequestWithToken(originalRequest, refreshResult.accessToken))
                    }
                    is RefreshResult.NetworkError -> {
                        // Network error or 5xx: user session is still valid.
                        // Propagate the 401 without logging out or redirecting.
                        return response
                    }
                    is RefreshResult.InvalidToken -> {
                        // Backend explicitly rejected the refresh token (4xx): session expired.
                        logoutAndRedirect()
                    }
                }
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

    private fun performRefresh(refreshToken: String): RefreshResult {
        // Fresh client to avoid infinite interceptor loops
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val json = JSONObject().apply {
            put("refreshToken", refreshToken)
        }

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("https://app-store-dqg8bnf4d8cberf7.centralindia-01.azurewebsites.net/mobile/refresh")
            .post(body)
            .build()

        return try {
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
                        RefreshResult.Success(newAccessToken)
                    } else {
                        RefreshResult.InvalidToken
                    }
                } else {
                    RefreshResult.InvalidToken
                }
            } else {
                val errorMsg = "Token refresh failed with HTTP code ${response.code}: ${response.message}"
                FirebaseCrashlytics.getInstance().log(errorMsg)
                FirebaseCrashlytics.getInstance().recordException(Exception("Token refresh failed: HTTP ${response.code}"))

                // 4xx errors except transient rate limits mean invalid token/session expired.
                // 5xx or other status codes represent backend/network-level errors.
                if (response.code in 400..404) {
                    RefreshResult.InvalidToken
                } else {
                    RefreshResult.NetworkError
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            FirebaseCrashlytics.getInstance().recordException(e)
            RefreshResult.NetworkError
        }
    }

    private fun logoutAndRedirect() {
        authManager.logout()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }
}