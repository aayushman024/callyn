package com.mnivesh.callyn.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mnivesh.callyn.CallynApplication
import com.mnivesh.callyn.managers.AuthManager
import com.mnivesh.callyn.api.CallLogRequest
import com.mnivesh.callyn.api.RetrofitInstance

class PersonalCallLogUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val appContext = applicationContext as CallynApplication
        val repository = appContext.repository
        val token = AuthManager(appContext).getToken()

        if (token.isNullOrBlank()) return Result.failure()

        val pendingLogs = repository.getPendingPersonalLogs()
        if (pendingLogs.isEmpty()) return Result.success()

        val successfulIds = mutableListOf<Int>()
        var allSuccess = true

        for (log in pendingLogs) {
            val request = CallLogRequest(
                callerName = "Unknown",
                rshipManagerName = "Unknown",
                familyHead = "",
                notes = "",
                type = log.direction,
                timestamp = log.timestamp,
                duration = log.duration,
                simSlot = log.simSlot,
                isWork = false
            )

            try {
                val response = RetrofitInstance.api.uploadCallLog("Bearer $token", request)
                if (response.isSuccessful) {
                    successfulIds.add(log.id)
                } else {
                    allSuccess = false
                }
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("Crash uploading personal log ID: ${log.id}")
                FirebaseCrashlytics.getInstance().recordException(e)
                allSuccess = false
            }
        }

        // nuke the successful ones from local db immediately
        repository.deleteSyncedPersonalLogs(successfulIds)

        // workmanager handles exponential backoff natively if we return retry
        return if (allSuccess) Result.success() else Result.retry()
    }
}