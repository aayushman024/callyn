package com.mnivesh.callyn.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mnivesh.callyn.managers.CallManager

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            "ANSWER_CALL" -> {
                CallManager.answerCall()
                if (context != null) {
                    val activityIntent = Intent(context, com.mnivesh.callyn.InCallActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    try {
                        context.startActivity(activityIntent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            "END_CALL" -> {
                CallManager.rejectCall()
            }
        }
    }
}
