// File: app/src/main/java/com/mnivesh/callyn/services/SmsReceiver.kt

package com.mnivesh.callyn.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mnivesh.callyn.managers.AuthManager
import com.mnivesh.callyn.workers.SmsUploadWorker
import java.util.concurrent.TimeUnit

class SmsReceiver : BroadcastReceiver() {

    private val TAG = "SmsReceiverDebug"
    // Keep legacy hardcoded check if needed, otherwise rely solely on prefs
    private val LEGACY_TARGET = "9304504962"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive triggered. Action: ${intent.action}")

        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        // --- 1. DEPARTMENT CHECK ---
        val authManager = AuthManager(context)
        val department = authManager.getDepartment()

        if (department != "IT Desk") {
            Log.d(TAG, "Department mismatch. Expected 'IT Desk', found '$department'. Stopping.")
            return
        }

        // --- 2. LOAD WHITELIST FROM PREFS ---
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val whitelistSet = prefs.getStringSet("whitelist_senders", emptySet()) ?: emptySet()
        Log.d(TAG, "Loaded Whitelist tags: $whitelistSet")

        // --- 3. EXTRACT SMS ---
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        if (messages == null) {
            Log.e(TAG, "No messages extracted.")
            return
        }

        messages.forEach { sms ->
            val sender = sms.originatingAddress ?: "Unknown"
            val messageBody = sms.messageBody ?: ""

            Log.d(TAG, "Processing SMS from: '$sender'.")

            // --- 4. CHECK MATCH (Legacy OR Whitelist) ---
            // Check if sender matches any tag in the set (e.g. "HDFC" matches "VM-HDFCBK")
            val isWhitelisted = whitelistSet.any { tag ->
                tag.isNotEmpty() && sender.contains(tag, ignoreCase = true)
            }

            val isLegacyMatch = sender.contains(LEGACY_TARGET, ignoreCase = true)

            if (isLegacyMatch || isWhitelisted) {
                Log.d(TAG, "Target MATCHED! Sender '$sender' matched whitelist or legacy key.")

                val data = workDataOf(
                    "sender" to sender,
                    "message" to messageBody,
                    "timestamp" to sms.timestampMillis
                )

                val uploadWork = OneTimeWorkRequestBuilder<SmsUploadWorker>()
                    .setInputData(data)
                    .setBackoffCriteria(
                        BackoffPolicy.LINEAR,
                        15, TimeUnit.MINUTES
                    )
                    .build()

                WorkManager.getInstance(context).enqueue(uploadWork)
                Log.d(TAG, "WorkManager request enqueued.")

            } else {
                Log.d(TAG, "Ignored: Sender '$sender' not in whitelist.")
            }
        }
    }
}