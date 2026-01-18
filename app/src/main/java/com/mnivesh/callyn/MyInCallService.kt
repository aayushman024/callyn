package com.mnivesh.callyn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.mnivesh.callyn.managers.CallManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.content.BroadcastReceiver

@RequiresApi(Build.VERSION_CODES.N)
class MyInCallService : InCallService() {

    companion object {
        var instance: MyInCallService? = null
        private const val NOTIFICATION_ID = 12345
        private const val CHANNEL_ID = "callyn_ongoing_calls"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startStateObserver()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.onCallAdded(call)

        // Immediate notification update to ensure chip appears ASAP
        showNotification()

        val intent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.onCallRemoved(call)

        if (calls.isEmpty()) {
            stopForegroundService()
            instance = null
        } else {
            showNotification()
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        CallManager.updateAudioState(
            isMuted = audioState.isMuted,
            isSpeakerOn = audioState.route == CallAudioState.ROUTE_SPEAKER,
            isBluetoothOn = audioState.route == CallAudioState.ROUTE_BLUETOOTH,
            availableRoutes = audioState.supportedRouteMask
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForegroundService()
        serviceScope.cancel()
        instance = null
    }

    private fun startStateObserver() {
        serviceScope.launch {
            CallManager.callState.collectLatest { state ->
                if (state != null && !calls.isEmpty()) {
                    showNotification()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val name = "Ongoing Calls"
        val descriptionText = "Active call status"
        val importance = NotificationManager.IMPORTANCE_DEFAULT // Default allows visual chip
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
            setSound(null, null)
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun showNotification() {
        val currentState = CallManager.callState.value ?: return

        // 1. Prepare Data
        val callerName = if (currentState.name.isNotBlank() && currentState.name != "Unknown") {
            currentState.name
        } else {
            currentState.number
        }

        val callStatus = currentState.status

        // 2. Prepare Intent (Tapping notification/chip goes to Activity)
        val activityIntent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Prepare End Call Action
        val endCallIntent = PendingIntent.getBroadcast(
            this, 1,
            Intent(this, NotificationActionReceiver::class.java).apply { action = "END_CALL" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder: android.app.Notification.Builder?
        val notificationCompatBuilder: NotificationCompat.Builder?

        // --- ANDROID 12+ (API 31) NATIVE CHIP LOGIC ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val person = Person.Builder()
                .setName(callerName)
                .setIcon(Icon.createWithResource(this, android.R.drawable.ic_menu_call))
                .setImportant(true)
                .build()

            // CallStyle is REQUIRED for the system green chip
            val callStyle = Notification.CallStyle.forOngoingCall(
                person,
                endCallIntent
            )

            notificationBuilder = Notification.Builder(this, CHANNEL_ID)
                .setStyle(callStyle)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle(callerName)
                .setContentText(callStatus)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_CALL)
                .setColor(getColor(android.R.color.holo_green_dark))
                .setContentIntent(pendingIntent)
                // Critical for timer in status bar:
                .setUsesChronometer(true)
                .setWhen(currentState.connectTimeMillis.takeIf { it > 0 } ?: System.currentTimeMillis())

            startForeground(NOTIFICATION_ID, notificationBuilder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)

        } else {
            // --- ANDROID 11 AND BELOW FALLBACK ---
            notificationCompatBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle(callerName)
                .setContentText(callStatus)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setColor(Color.parseColor("#4CAF50"))
                .setColorized(true)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End", endCallIntent)
                .setUsesChronometer(true)
                .setWhen(currentState.connectTimeMillis.takeIf { it > 0 } ?: System.currentTimeMillis())

            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notificationCompatBuilder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
            } else {
                startForeground(NOTIFICATION_ID, notificationCompatBuilder.build())
            }
        }
    }

    private fun stopForegroundService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "END_CALL") {
            CallManager.rejectCall() // This calls disconnect() in your manager
        }
    }
}