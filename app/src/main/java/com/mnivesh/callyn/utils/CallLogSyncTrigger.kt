package com.mnivesh.callyn.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.work.*
import com.mnivesh.callyn.data.ContactRepository
import com.mnivesh.callyn.workers.PersonalCallLogUploadWorker
import com.mnivesh.callyn.workers.UploadCallLogWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// drop this in MainActivity setContent or dashboard so it checks on mount
@Composable
fun CallLogSyncTrigger(repository: ContactRepository) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val hasUnsyncedWork = repository.getUnsyncedLogs().isNotEmpty()
            val hasUnsyncedPersonal = repository.getPendingPersonalLogs().isNotEmpty()

            if (!hasUnsyncedWork && !hasUnsyncedPersonal) return@withContext

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val workManager = WorkManager.getInstance(context)

            if (hasUnsyncedWork) {
                val workReq = OneTimeWorkRequestBuilder<UploadCallLogWorker>()
                    .setConstraints(constraints)
                    .build()
                workManager.enqueueUniqueWork("SyncWorkLogs", ExistingWorkPolicy.KEEP, workReq)
            }

            if (hasUnsyncedPersonal) {
                val personalReq = OneTimeWorkRequestBuilder<PersonalCallLogUploadWorker>()
                    .setConstraints(constraints)
                    .build()
                workManager.enqueueUniqueWork("SyncPersonalLogs", ExistingWorkPolicy.KEEP, personalReq)
            }
        }
    }
}