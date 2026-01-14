package com.mnivesh.callyn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
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

@RequiresApi(Build.VERSION_CODES.N)
class MyInCallService : InCallService() {

    companion object {
        var instance: MyInCallService? = null
    }

    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "callyn_call_channel"

    // Scope for observing CallManager updates
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()

        // Start observing CallManager state changes to update notification
        startStateObserver()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        CallManager.onCallAdded(call)

        // Show initial notification
        showNotification()

        // Launch UI
        val intent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.onCallRemoved(call)

        if (calls.isEmpty()) {
            cancelNotification()
            instance = null
            stopSelf()
        } else {
            showNotification()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelNotification()
        serviceScope.cancel() // Cancel coroutines
        instance = null
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

    private fun startStateObserver() {
        serviceScope.launch {
            CallManager.callState.collectLatest { state ->
                if (state != null) {
                    showNotification()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Ongoing Calls"
            val descriptionText = "Notifications for active calls"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification() {
        val currentState = CallManager.callState.value ?: return

        // --- Logic: Name if available, otherwise Number + (Unknown) ---
        val contentTitle = if (currentState.name == "Unknown" || currentState.name.isBlank()) {
            "${currentState.number} (Unknown)"
        } else {
            currentState.name
        }

        val contentText = currentState.status

        val intent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun cancelNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(NOTIFICATION_ID)
    }
}