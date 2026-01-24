package com.mnivesh.callyn.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mnivesh.callyn.managers.AuthManager
import com.mnivesh.callyn.CallynApplication
import com.mnivesh.callyn.api.CallLogRequest
import com.mnivesh.callyn.api.RetrofitInstance

class PersonalUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val appContext = applicationContext as CallynApplication
        val authManager = AuthManager(appContext)
        val token = authManager.getToken()

        if (token.isNullOrBlank()) {
            return Result.failure()
        }

        // 1. Retrieve data passed from CallManager
        val duration = inputData.getLong("duration", 0)
        val timestamp = inputData.getLong("timestamp", 0)
        val direction = inputData.getString("direction") ?: "unknown"
        val simSlot = inputData.getString("simSlot") ?: "Unknown"

        // 2. Build Request (Masking private info)
        // We explicitly set names to "Unknown" and isWork to "false"
        val request = CallLogRequest(
            callerName = "Unknown",
            rshipManagerName = "Unknown",
            familyHead = "",
            type = direction,
            timestamp = timestamp,
            duration = duration,
            simSlot = simSlot,
            isWork = false
        )

        // 3. Execute API Call
        return try {
            val response = RetrofitInstance.api.uploadCallLog("Bearer $token", request)
            if (response.isSuccessful) {
                Result.success()
            } else {
                // Server error? Retry later
                if (response.code() >= 500) Result.retry() else Result.failure()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry() // Network error? Retry later
        }
    }
}