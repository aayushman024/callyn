package com.mnivesh.callyn.workers

import android.content.ContentValues
import android.content.Context
import android.provider.CallLog
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay

class DeleteCallLogWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val number = inputData.getString("number") ?: return Result.failure()

        // Wait a few seconds to let Telecom framework write the log before we try to delete it
        delay(4000)

        return try {
            val contentResolver = applicationContext.contentResolver
            val numberToQuery = number.filter { it.isDigit() }.let {
                if (it.length > 10) it.takeLast(10) else it
            }

            val queryUri = CallLog.Calls.CONTENT_URI
            val projection = arrayOf(CallLog.Calls._ID, CallLog.Calls.TYPE)
            val selection = "${CallLog.Calls.NUMBER} LIKE ?"
            val selectionArgs = arrayOf("%$numberToQuery")
            val sortOrder = "${CallLog.Calls.DATE} DESC"

            var entryId: String? = null
            var currentType: Int = -1

            contentResolver.query(queryUri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    entryId = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls._ID))
                    currentType = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                }
            }

            if (entryId != null) {
                if (currentType == CallLog.Calls.MISSED_TYPE) {
                    try {
                        val values = ContentValues().apply {
                            put(CallLog.Calls.TYPE, CallLog.Calls.INCOMING_TYPE)
                            put(CallLog.Calls.IS_READ, 1)
                            put(CallLog.Calls.NEW, 0)
                        }
                        contentResolver.update(queryUri, values, "${CallLog.Calls._ID} = ?", arrayOf(entryId))
                        delay(400)
                    } catch (e: Exception) {}
                }

                val rows = contentResolver.delete(queryUri, "${CallLog.Calls._ID} = ?", arrayOf(entryId))
                if (rows > 0) Result.success() else Result.retry()
            } else {
                // If log isn't there yet, try again
                Result.retry()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}