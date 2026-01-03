package com.mnivesh.callyn.workers

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mnivesh.callyn.AuthManager
import com.mnivesh.callyn.api.RetrofitInstance
import com.mnivesh.callyn.api.UserDetailsRequest
import com.mnivesh.callyn.api.version

class SyncUserDetailsWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val authManager = AuthManager(applicationContext)

        // 1. Get required data
        val token = authManager.getToken() ?: return Result.failure()
        val username = authManager.getUserName() ?: return Result.failure()
        val department = authManager.getDepartment() ?: "N/A"
        val email = authManager.getUserEmail() ?: "N/A"


        // 2. Prepare Request
        val request = UserDetailsRequest(
            username = username,
            email = email,
            phoneModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            osLevel = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            appVersion = version,
            department = department,
            lastSeen = System.currentTimeMillis()
        )

        // 3. Send to API
        return try {
            val response = RetrofitInstance.api.syncUserDetails("Bearer $token", request)
            if (response.isSuccessful) {
                Log.d("SyncWorker", "User details synced successfully.")
                Result.success()
            } else {
                Log.e("SyncWorker", "Failed to sync: ${response.code()}")
                // Retry if it's a server error (5xx), fail if client error (4xx)
                if (response.code() in 500..599) Result.retry() else Result.failure()
            }
        } catch (e: Exception) {
            Log.e("SyncWorker", "Network error, retrying...", e)
            Result.retry() // Automatically retry later if network is down
        }
    }
}