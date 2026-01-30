package com.mnivesh.callyn.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mnivesh.callyn.api.RetrofitInstance
import com.mnivesh.callyn.api.SmsLogRequest
import com.mnivesh.callyn.managers.AuthManager

class SmsUploadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val TAG = "SmsWorkerDebug"

    override suspend fun doWork(): Result {
        Log.d(TAG, "Worker STARTED. Fetching input data...")

        val sender = inputData.getString("sender")
        val message = inputData.getString("message")
        val timestamp = inputData.getLong("timestamp", 0L)

        if (sender == null || message == null) {
            Log.e(TAG, "FAILURE: Missing sender or message in input data.")
            return Result.failure()
        }

        val authManager = AuthManager(applicationContext)
        val token = authManager.getToken()

        // 1. Check Token
        if (token.isNullOrEmpty()) {
            Log.e(TAG, "FAILURE: No Auth Token found. User might be logged out.")
            return Result.failure()
        }
        Log.d(TAG, "Token found. Attempting upload for SMS from: $sender")

        return try {
            // 2. Network Call
            val payload = SmsLogRequest(sender, message, timestamp)
            val response = RetrofitInstance.api.uploadSms("Bearer $token", payload)

            if (response.isSuccessful) {
                Log.d(TAG, "SUCCESS! Upload complete. Server Code: ${response.code()}")
                Result.success()
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown Error"
                Log.e(TAG, "FAILURE: Server rejected request. Code: ${response.code()}, Error: $errorBody")

                // If 500+ error, retry. If 400 (Client Error), fail permanently.
                if (response.code() >= 500) Result.retry() else Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL FAILURE: Exception during network call", e)
            Result.retry()
        }
    }
}