package com.mnivesh.callyn.managers

import com.mnivesh.callyn.api.RetrofitInstance
import com.mnivesh.callyn.api.ViewLimitResponse
import retrofit2.Response

object ViewLimitManager {

    // helper to get the token formatted for headers
    private fun getAuthHeader(authManager: AuthManager): String {
        return "Bearer ${authManager.getToken()}"
    }

    suspend fun getStatus(authManager: AuthManager): Response<ViewLimitResponse> {
        return RetrofitInstance.api.getViewLimitStatus(getAuthHeader(authManager))
    }

    suspend fun increment(authManager: AuthManager): Response<ViewLimitResponse> {
        return RetrofitInstance.api.incrementViewCount(getAuthHeader(authManager))
    }
}