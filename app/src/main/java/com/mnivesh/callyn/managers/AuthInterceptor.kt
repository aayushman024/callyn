package com.mnivesh.callyn.managers

import android.content.Context
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

class AuthInterceptor(private val context: Context) : Interceptor {
    private val authManager = AuthManager(context)

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // 1. Dynamic Token Injection: Strip any callsite Authorization header
        // and inject the latest valid token saved in AuthManager.
        val latestToken = authManager.getToken()
        val authenticatedRequest = if (!latestToken.isNullOrEmpty()) {
            newRequestWithToken(originalRequest, latestToken)
        } else {
            originalRequest
        }

        var response = chain.proceed(authenticatedRequest)

        // 2. Catch 401 Unauthorized
        if (response.code == 401) {
            synchronized(this) {
                val currentToken = authManager.getToken()
                val previousToken = authenticatedRequest.header("Authorization")?.removePrefix("Bearer ")?.trim()

                // Check if another thread already refreshed the token while we waited
                if (currentToken != null && currentToken != previousToken) {
                    response.close()
                    return chain.proceed(newRequestWithToken(originalRequest, currentToken))
                }

                val savedToken = authManager.getToken()
                val refreshToken = authManager.getRefreshToken()

                // If user has no saved access or refresh token, let 401 propagate
                if (savedToken.isNullOrEmpty() || refreshToken.isNullOrEmpty()) {
                    return response
                }

                val refreshResult = TokenRefreshManager.performRefreshBlocking(context)
                when (refreshResult) {
                    is TokenRefreshResult.Success -> {
                        response.close()
                        return chain.proceed(newRequestWithToken(originalRequest, refreshResult.accessToken))
                    }
                    is TokenRefreshResult.NetworkError -> {
                        // Network error or transient failure: session is still valid. Propagate 401.
                        return response
                    }
                    is TokenRefreshResult.InvalidToken -> {
                        // Session expired / revoked: logout and redirect.
                        TokenRefreshManager.logoutAndRedirect(context)
                    }
                }
            }
        }

        return response
    }

    private fun newRequestWithToken(request: Request, token: String): Request {
        return request.newBuilder()
            .removeHeader("Authorization")
            .addHeader("Authorization", "Bearer $token")
            .build()
    }
}