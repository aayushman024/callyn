package com.mnivesh.callyn.workers

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mnivesh.callyn.managers.AuthManager
import com.mnivesh.callyn.managers.TokenRefreshManager
import com.mnivesh.callyn.managers.TokenRefreshResult
import java.util.concurrent.TimeUnit

class TokenRefreshWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val authManager = AuthManager(context)
        if (!authManager.isLoggedIn()) {
            Log.d(TAG, "User is not logged in. Skipping periodic token refresh worker.")
            return Result.success()
        }

        Log.d(TAG, "Running periodic TokenRefreshWorker...")
        return when (TokenRefreshManager.performRefresh(context)) {
            is TokenRefreshResult.Success -> {
                Log.d(TAG, "Periodic token refresh successful.")
                Result.success()
            }
            is TokenRefreshResult.InvalidToken -> {
                Log.w(TAG, "Periodic refresh returned invalid token. Logging out user...")
                TokenRefreshManager.logoutAndRedirect(context)
                Result.failure()
            }
            is TokenRefreshResult.NetworkError -> {
                Log.w(TAG, "Periodic refresh failed due to network. Retrying later.")
                Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "TokenRefreshWorker"
        private const val WORK_NAME = "periodic_token_refresh_work"

        fun enqueuePeriodicWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<TokenRefreshWorker>(72, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.d(TAG, "Enqueued periodic TokenRefreshWorker every 24 hours.")
        }
    }
}
