package com.mnivesh.callyn.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mnivesh.callyn.CallynApplication
import com.mnivesh.callyn.managers.AuthManager

class UploadCallLogWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val appContext = applicationContext as CallynApplication
        val repository = appContext.repository
        val authManager = AuthManager(appContext)
        val token = authManager.getToken()
        val department = authManager.getDepartment()

        if (token.isNullOrBlank()) return Result.failure()
        if (department == "GUEST") return Result.success()

        // handle all batching and DB updates in repository
        val success = repository.batchUploadUnsyncedWorkLogs(token)

        return if (success) Result.success() else Result.retry()
    }
}