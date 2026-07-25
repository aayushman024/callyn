package com.mnivesh.callyn.managers

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mnivesh.callyn.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class TokenRefreshResult {
    data class Success(val accessToken: String) : TokenRefreshResult()
    object InvalidToken : TokenRefreshResult()
    object NetworkError : TokenRefreshResult()
}

object TokenRefreshManager {
    private const val TAG = "TokenRefreshManager"
    private const val REFRESH_URL = "https://app-store-dqg8bnf4d8cberf7.centralindia-01.azurewebsites.net/auth/mobile/refresh"
    private val mutex = Mutex()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Thread-safe token refresh procedure.
     * Retries on transient network errors, 5xx server errors, AND HTTP 400 errors with backoff.
     * Fails fast ONLY on deterministic HTTP 401 or 403 responses.
     */
    suspend fun performRefresh(context: Context, maxRetries: Int = 3): TokenRefreshResult = mutex.withLock {
        val authManager = AuthManager(context)
        val refreshToken = authManager.getRefreshToken()

        if (refreshToken.isNullOrEmpty()) {
            Log.w(TAG, "No refresh token available.")
            return TokenRefreshResult.InvalidToken
        }

        withContext(Dispatchers.IO) {
            var attempt = 0
            var delayMs = 1000L

            while (attempt < maxRetries) {
                attempt++
                Log.d(TAG, "Executing token refresh attempt $attempt/$maxRetries...")

                try {
                    val json = JSONObject().apply {
                        put("refreshToken", refreshToken)
                    }
                    val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

                    val request = Request.Builder()
                        .url(REFRESH_URL)
                        .post(body)
                        .build()

                    val response = client.newCall(request).execute()

                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        if (!responseBody.isNullOrEmpty()) {
                            val jsonObject = JSONObject(responseBody)
                            val newAccessToken = jsonObject.optString("accessToken")
                            val newRefreshToken = jsonObject.optString("refreshToken")

                            if (newAccessToken.isNotEmpty()) {
                                authManager.saveToken(newAccessToken)
                                if (newRefreshToken.isNotEmpty()) {
                                    authManager.saveRefreshToken(newRefreshToken)
                                }
                                authManager.updateLastRefreshTime()
                                Log.d(TAG, "Token refreshed successfully on attempt $attempt.")
                                return@withContext TokenRefreshResult.Success(newAccessToken)
                            }
                        }
                    } else {
                        val code = response.code
                        val errorBody = response.body?.string()
                        val errorMsg = "Token refresh failed with HTTP code $code: ${response.message} - Body: $errorBody"
                        Log.e(TAG, errorMsg)
                        FirebaseCrashlytics.getInstance().log(errorMsg)

                        // 401 or 403 explicitly mean token/session revoked or invalid
                        if (code == 401 || code == 403) {
                            FirebaseCrashlytics.getInstance().recordException(Exception("Token refresh rejected: HTTP $code"))
                            return@withContext TokenRefreshResult.InvalidToken
                        }

                        // HTTP 400, 5xx or transient status codes -> retry with backoff
                        Log.w(TAG, "HTTP $code encountered on refresh attempt $attempt. Retrying in ${delayMs}ms...")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Network exception on token refresh attempt $attempt: ${e.message}", e)
                    FirebaseCrashlytics.getInstance().recordException(e)
                }

                if (attempt < maxRetries) {
                    delay(delayMs)
                    delayMs *= 2
                }
            }

            Log.e(TAG, "Token refresh exhausted all $maxRetries attempts.")
            return@withContext TokenRefreshResult.NetworkError
        }
    }

    /**
     * Synchronous blocking wrapper for OkHttp Interceptors running on worker threads.
     */
    fun performRefreshBlocking(context: Context, maxRetries: Int = 3): TokenRefreshResult {
        return runBlocking {
            performRefresh(context, maxRetries)
        }
    }

    fun logoutAndRedirect(context: Context) {
        val authManager = AuthManager(context)
        authManager.logout()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }
}
