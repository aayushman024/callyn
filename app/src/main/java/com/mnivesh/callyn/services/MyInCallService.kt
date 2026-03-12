package com.mnivesh.callyn.services

import android.R
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.PowerManager // REQUIRED IMPORT
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.mnivesh.callyn.InCallActivity
import com.mnivesh.callyn.managers.CallManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.DisconnectCause
import androidx.core.app.NotificationManagerCompat
import com.mnivesh.callyn.CallynApplication
import com.mnivesh.callyn.MainActivity

@RequiresApi(Build.VERSION_CODES.N)
class MyInCallService : InCallService() {

    companion object {
        var instance: MyInCallService? = null
        private const val NOTIFICATION_ID = 12345
        private const val MISSED_CALL_NOTIF_ID = 12346 // NEW
        private const val CHANNEL_ID = "callyn_ongoing_calls"
        private const val MISSED_CHANNEL_ID = "callyn_missed_calls" // NEW
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // --- PROXIMITY WAKELOCK VARIABLE ---
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        createMissedCallChannel()
        startStateObserver()

        // 1. INITIALIZE WAKELOCK
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "Callyn:Proximity")
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.onCallAdded(call)

        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire()
        }

        showNotification()

        // Handles screen-ON case — fullScreenIntent alone doesn't launch when screen is on
        val intent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.onCallRemoved(call)

        if (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                call.details.state == Call.STATE_DISCONNECTED &&
                        call.details.disconnectCause.code == DisconnectCause.MISSED
            } else {
                TODO("VERSION.SDK_INT < S")
            }
        ) {
            val rawNumber = call.details.handle?.schemeSpecificPart ?: "Unknown"
            showMissedCallNotification(rawNumber)
        }

        if (calls.isEmpty()) {
            // 3. RELEASE SENSOR ON CALL END
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            stopForegroundService()
            instance = null
        } else {
            showNotification()
        }
    }

    @SuppressLint("MissingPermission")
    private fun showMissedCallNotification(number: String) {
        serviceScope.launch(Dispatchers.IO) {
            var resolvedName: String? = null
            val numberStr = number.filter { it.isDigit() }.takeLast(10)

            // 1 & 2. Employee / Work (AppContact DB)
            try {
                val app = applicationContext as CallynApplication
                val workContact = app.repository.findWorkContactByNumber(numberStr)

                if (workContact != null) {
                    resolvedName = workContact.name
                } else {
                    // 3. CRM (CrmContact DB)
                    val crmContact = app.repository.findCrmContactByNumber(numberStr)
                    if (crmContact != null) {
                        resolvedName = crmContact.name
                    }
                }
            } catch (e: Exception) {
                // Ignore DB lookup failures
            }

            // 4. Personal (Device Contacts)
            if (resolvedName == null) {
                try {
                    val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
                    contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) resolvedName = it.getString(0)
                    }
                } catch (e: Exception) {
                    // Ignore local contact lookup failures
                }
            }

            val displayName = resolvedName ?: number

            // Get unread missed call count for the badge
            var unreadCount = 1
            try {
                contentResolver.query(
                    android.provider.CallLog.Calls.CONTENT_URI, null,
                    "${android.provider.CallLog.Calls.TYPE} = ? AND ${android.provider.CallLog.Calls.IS_READ} = ?",
                    arrayOf(android.provider.CallLog.Calls.MISSED_TYPE.toString(), "0"), null
                )?.use { unreadCount = it.count.takeIf { c -> c > 0 } ?: 1 }
            } catch (e: Exception) {
                // Ignore call log query failures
            }

            // Build and trigger notification
            val pendingIntent = PendingIntent.getActivity(
                this@MyInCallService, 0,
                Intent(this@MyInCallService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(this@MyInCallService, MISSED_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_missed_call) // Or your app's custom icon
                .setContentTitle("Missed call")
                .setContentText(displayName)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setNumber(unreadCount)

            NotificationManagerCompat.from(this@MyInCallService).notify(MISSED_CALL_NOTIF_ID, builder.build())
        }
    }

    private fun createMissedCallChannel() {
        val name = "Missed Calls"
        val descriptionText = "Notifications for missed calls"
        val importance = NotificationManager.IMPORTANCE_MAX
        val channel = NotificationChannel(MISSED_CHANNEL_ID, name, importance).apply {
            description = descriptionText
            setShowBadge(true) // Ensures the launcher icon badge appears
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        CallManager.updateAudioState(
            isMuted = audioState.isMuted,
            isSpeakerOn = audioState.route == CallAudioState.ROUTE_SPEAKER,
            isBluetoothOn = audioState.route == CallAudioState.ROUTE_BLUETOOTH,
            availableRoutes = audioState.supportedRouteMask
        )

        // 4. SMART LOGIC: Disable sensor if on Speaker/Bluetooth
        if (audioState.route == CallAudioState.ROUTE_SPEAKER || audioState.route == CallAudioState.ROUTE_BLUETOOTH) {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } else {
            // Re-enable if back to Earpiece
            if (wakeLock?.isHeld == false && !calls.isEmpty()) wakeLock?.acquire()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
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
        val importance = NotificationManager.IMPORTANCE_MAX // FIX 3: was IMPORTANCE_DEFAULT, too low for full-screen intents
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

        val callerName = if (currentState.name.isNotBlank() && currentState.name != "Unknown") {
            currentState.name
        } else {
            currentState.number
        }

        val callStatus = currentState.status

        val isActive = callStatus.equals("Active", ignoreCase = true)
        val displayTime = if (isActive && currentState.connectTimeMillis > 0) currentState.connectTimeMillis else System.currentTimeMillis()

        val activityIntent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val fullScreenIntent = PendingIntent.getActivity(
            this, 2, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pendingIntent = PendingIntent.getActivity( // FIX 1: was missing, caused compile error
            this, 0, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val endCallIntent = PendingIntent.getBroadcast(
            this, 1,
            Intent(this, NotificationActionReceiver::class.java).apply { action = "END_CALL" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder: Notification.Builder?
        val notificationCompatBuilder: NotificationCompat.Builder?

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val person = Person.Builder()
                .setName(callerName)
                .setIcon(Icon.createWithResource(this, R.drawable.ic_menu_call))
                .setImportant(true)
                .build()

            val callStyle = Notification.CallStyle.forOngoingCall(
                person,
                endCallIntent
            )

            notificationBuilder = Notification.Builder(this, CHANNEL_ID)
                .setStyle(callStyle)
                .setSmallIcon(R.drawable.ic_menu_call)
                .setContentTitle(callerName)
                .setContentText(callStatus)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_CALL)
                .setColor(getColor(R.color.holo_green_dark))
                .setFullScreenIntent(fullScreenIntent, true)
                .setContentIntent(pendingIntent)
                .setUsesChronometer(isActive)
                .setWhen(if (isActive && currentState.connectTimeMillis > 0) currentState.connectTimeMillis else 0L)
            startForeground(NOTIFICATION_ID, notificationBuilder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)

        } else {
            notificationCompatBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_menu_call)
                .setContentTitle(callerName)
                .setContentText(callStatus)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setColor(Color.parseColor("#4CAF50"))
                .setFullScreenIntent(fullScreenIntent, true)
                .setColorized(true)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_menu_close_clear_cancel, "End", endCallIntent)
                .setUsesChronometer(true)
                .setWhen(currentState.connectTimeMillis.takeIf { it > 0 } ?: 0L)

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
            CallManager.rejectCall()
        }
    }
}