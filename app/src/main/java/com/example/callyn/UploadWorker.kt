package com.example.callyn

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.callyn.RetrofitInstance
import com.example.callyn.api.CallLogRequest
import com.example.callyn.api.ApiService

class UploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val appContext = applicationContext as CallynApplication
        val repository = appContext.repository
        val authManager = AuthManager(appContext)

        val token = authManager.getToken()
        if (token.isNullOrBlank()) {
            return Result.failure()
        }

        // 1. Fetch unsynced logs
        val unsyncedLogs = repository.getUnsyncedLogs()
        if (unsyncedLogs.isEmpty()) {
            return Result.success()
        }

        // 2. Upload Loop
        var allSuccess = true
        for (log in unsyncedLogs) {
            try {
                // Optional: Fetch fresh RM info from DB using the number
                val contactInfo = repository.findWorkContactByNumber(log.number.takeLast(10))
                val rmName = contactInfo?.rshipManager ?: "Unknown"

                val request = CallLogRequest(
                    callerName = log.name,
                    rshipManagerName = rmName,
                    type = log.direction,
                    timestamp = log.timestamp,
                    duration = log.duration
                )

                val response = RetrofitInstance.api.uploadCallLog("Bearer $token", request)

                if (response.isSuccessful) {
                    repository.markLogSynced(log.id)
                    Log.d("UploadWorker", "Uploaded log ID: ${log.id}")
                } else {
                    Log.e("UploadWorker", "Failed log ID: ${log.id} Code: ${response.code()}")
                    allSuccess = false
                }
            } catch (e: Exception) {
                Log.e("UploadWorker", "Exception log ID: ${log.id}", e)
                allSuccess = false
            }
        }

        // If any failed, return retry (WorkManager handles backoff automatically)
        return if (allSuccess) Result.success() else Result.retry()
    }
}