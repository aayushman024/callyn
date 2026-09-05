package com.mnivesh.callyn.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mnivesh.callyn.BuildConfig
import com.mnivesh.callyn.MainActivity
import com.mnivesh.callyn.R
import com.mnivesh.callyn.api.RetrofitInstance
import com.mnivesh.callyn.managers.AuthManager
import com.mnivesh.callyn.managers.CallManager
import com.mnivesh.callyn.managers.DialerManager
import com.mnivesh.callyn.managers.HandsFreeConnectionState
import com.mnivesh.callyn.managers.HandsFreeManager
import com.mnivesh.callyn.managers.PermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import android.os.Handler
import android.os.Looper
import java.util.concurrent.TimeUnit

class HandsFreeService : Service() {

    companion object {
        const val ACTION_START = "com.mnivesh.callyn.action.START_HANDS_FREE"
        const val ACTION_STOP = "com.mnivesh.callyn.action.STOP_HANDS_FREE"

        private const val TAG = "HandsFreeService"
        private const val CHANNEL_ID = "callyn_hands_free"
        private const val NOTIFICATION_ID = 23450
        private const val END_CALL_RETRY_DELAY_MS = 100L
        private const val END_CALL_MAX_RETRIES = 20
        private const val END_CALL_CONFIRMATION_TIMEOUT_MS = 5_000L
    }

    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private var webSocket: WebSocket? = null
    private var intentionalStop = false
    private var preserveUnavailableState = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeSessionId: String? = null
    private var activePhoneNumber: String? = null
    private var lastReportedCallStatus: String? = null
    private var endCallRequestedSessionId: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        notificationBuilder = createNotificationBuilder("Connecting to hands-free service")
        startForegroundNotification()
        observeNativeCallState()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSession("user disabled hands-free mode")
            ACTION_START, null -> startSession()
            else -> Log.w(TAG, "Unknown service action: ${intent.action}")
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "Task removed; ending hands-free session")
        stopSession("app task removed")
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        mainHandler.removeCallbacksAndMessages(null)
        webSocket?.close(1000, "Service destroyed")
        webSocket = null
        httpClient.dispatcher.executorService.shutdown()
        serviceScope.cancel()
        activeSessionId = null
        activePhoneNumber = null
        endCallRequestedSessionId = null
        if (!preserveUnavailableState) {
            HandsFreeManager.setConnectionState(HandsFreeConnectionState.DISABLED)
        }
        super.onDestroy()
    }

    private fun startSession() {
        if (webSocket != null) {
            Log.d(TAG, "Start ignored; WebSocket already exists")
            return
        }

        intentionalStop = false
        preserveUnavailableState = false
        val token = AuthManager(applicationContext).getToken()
        if (token.isNullOrBlank()) {
            Log.e(TAG, "Cannot connect: no authentication token")
            preserveUnavailableState = true
            HandsFreeManager.setConnectionState(HandsFreeConnectionState.UNAVAILABLE)
            stopSelf()
            return
        }

        HandsFreeManager.setConnectionState(HandsFreeConnectionState.CONNECTING)
        Log.d(TAG, "Opening WebSocket: ${RetrofitInstance.handsFreeWebSocketUrl()}")
        val request = Request.Builder()
            .url(RetrofitInstance.handsFreeWebSocketUrl())
            .header("Authorization", "Bearer $token")
            .build()

        webSocket = httpClient.newWebSocket(request, listener)
    }

    private fun observeNativeCallState() {
        serviceScope.launch {
            CallManager.callState.collectLatest { state ->
                val sessionId = activeSessionId ?: return@collectLatest
                val phoneNumber = activePhoneNumber ?: return@collectLatest
                if (state == null) {
                    sendCallStatus(sessionId, phoneNumber, "ENDED")
                    clearActiveCall(sessionId)
                    return@collectLatest
                }

                val normalizedStateNumber = state.number.filter(Char::isDigit)
                val normalizedTargetNumber = phoneNumber.filter(Char::isDigit)
                if (normalizedStateNumber.isNotEmpty() &&
                    !normalizedStateNumber.endsWith(normalizedTargetNumber.takeLast(10)) &&
                    !normalizedTargetNumber.endsWith(normalizedStateNumber.takeLast(10))) {
                    return@collectLatest
                }

                when (state.status.lowercase()) {
                    "active" -> sendCallStatus(sessionId, phoneNumber, "ACTIVE")
                    "disconnected" -> {
                        sendCallStatus(sessionId, phoneNumber, "ENDED")
                        clearActiveCall(sessionId)
                    }
                }
            }
        }
    }

    private fun handleInitiateCall(message: JSONObject) {
        val sessionId = message.optString("sessionId")
        val phoneNumber = message.optString("phoneNumber")
        if (sessionId.isBlank() || phoneNumber.isBlank()) {
            return
        }
        if (activeSessionId != null) {
            sendCallStatus(sessionId, phoneNumber, "FAILED", "CALL_ALREADY_ACTIVE")
            return
        }

        activeSessionId = sessionId
        activePhoneNumber = phoneNumber
        lastReportedCallStatus = null
        endCallRequestedSessionId = null
        sendCallStatus(sessionId, phoneNumber, "INITIATING")

        mainHandler.post {
            if (activeSessionId != sessionId || activePhoneNumber != phoneNumber) return@post

            val result = DialerManager(applicationContext, PermissionManager(applicationContext))
                .dialHandsFree(phoneNumber, isWorkCall = true)
            if (result != DialerManager.HandsFreeDialResult.STARTED) {
                sendCallStatus(sessionId, phoneNumber, "FAILED", result.name)
                clearActiveCall(sessionId)
            }
        }
    }

    private fun handleEndCall(message: JSONObject) {
        val sessionId = message.optString("sessionId")
        val currentSessionId = activeSessionId
        val currentPhoneNumber = activePhoneNumber
        if (sessionId.isBlank()) {
            return
        }
        if (currentSessionId != sessionId || currentPhoneNumber.isNullOrBlank()) {
            return
        }

        if (endCallRequestedSessionId == sessionId) {
            return
        }

        endCallRequestedSessionId = sessionId
        mainHandler.post {
            requestDisconnect(sessionId, currentPhoneNumber, 0)
        }
    }

    private fun requestDisconnect(sessionId: String, phoneNumber: String, attempt: Int) {
        if (activeSessionId != sessionId || activePhoneNumber != phoneNumber) return

        if (CallManager.disconnectHandsFreeCall(phoneNumber)) {
            mainHandler.postDelayed({
                if (activeSessionId == sessionId && endCallRequestedSessionId == sessionId) {
                    sendCallStatus(sessionId, phoneNumber, "FAILED", "DISCONNECT_TIMEOUT")
                    clearActiveCall(sessionId)
                }
            }, END_CALL_CONFIRMATION_TIMEOUT_MS)
            return
        }

        if (attempt < END_CALL_MAX_RETRIES) {
            mainHandler.postDelayed({
                requestDisconnect(sessionId, phoneNumber, attempt + 1)
            }, END_CALL_RETRY_DELAY_MS)
            return
        }

        sendCallStatus(sessionId, phoneNumber, "FAILED", "ACTIVE_CALL_NOT_FOUND")
        clearActiveCall(sessionId)
    }

    private fun sendCallStatus(sessionId: String, phoneNumber: String, status: String, failureCode: String? = null) {
        if (lastReportedCallStatus == status) return
        lastReportedCallStatus = status
        val payload = JSONObject()
            .put("type", "CALL_STATUS")
            .put("sessionId", sessionId)
            .put("phoneNumber", phoneNumber)
            .put("status", status)
        failureCode?.let { payload.put("failureCode", it) }
        Log.d(TAG, "Sending call status: $payload")
        webSocket?.send(payload.toString())
    }

    private fun clearActiveCall(sessionId: String) {
        if (activeSessionId == sessionId) {
            activeSessionId = null
            activePhoneNumber = null
            lastReportedCallStatus = null
            endCallRequestedSessionId = null
        }
    }

    private fun stopSession(reason: String) {
        Log.d(TAG, "Stopping session: $reason")
        intentionalStop = true
        preserveUnavailableState = false
        mainHandler.removeCallbacksAndMessages(null)

        webSocket?.let { socket ->
            val message = JSONObject().put("type", "HANDS_FREE_DISABLED").toString()
            Log.d(TAG, "Sending: $message")
            socket.send(message)
            socket.close(1000, reason)
        }
        webSocket = null
        HandsFreeManager.setConnectionState(HandsFreeConnectionState.DISABLED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleUnexpectedDisconnect(reason: String) {
        if (intentionalStop) {
            return
        }

        Log.w(TAG, "Hands-free unavailable: $reason")
        webSocket = null
        preserveUnavailableState = true
        HandsFreeManager.setConnectionState(HandsFreeConnectionState.UNAVAILABLE)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket opened: ${response.code}")
            val connectMessage = JSONObject()
                .put("type", "CONNECT")
                .put("handsFreeEnabled", true)
                .put(
                    "device",
                    JSONObject()
                        .put("appVersion", BuildConfig.VERSION_NAME)
                        .put("os", "android")
                )
                .toString()
            Log.d(TAG, "Sending: $connectMessage")
            webSocket.send(connectMessage)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "Received: $text")
            val message = runCatching { JSONObject(text) }.getOrNull()
            val type = message?.optString("type")
            when (type) {
                "CONNECTED" -> {
                    HandsFreeManager.setConnectionState(HandsFreeConnectionState.CONNECTED)
                    updateForegroundNotification("Hands-free active")
                    Log.d(TAG, "Hands-free connection established")
                }
                "INITIATE_CALL" -> message?.let(::handleInitiateCall)
                "END_CALL" -> message?.let(::handleEndCall)
                "ERROR" -> Log.e(TAG, "Server error: $text")
                "DISABLED_ACK" -> Log.d(TAG, "Server acknowledgement: $type")
                else -> Log.w(TAG, "Unknown server message: $text")
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing: code=$code reason=$reason")
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: code=$code reason=$reason")
            handleUnexpectedDisconnect("closed by server")
        }

        override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure: ${error.message}", error)
            handleUnexpectedDisconnect(error.message ?: "connection failure")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hands-free calling",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotificationBuilder(contentText: String): NotificationCompat.Builder {
        val launchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_headset)
            .setContentTitle("Callyn")
            .setContentText(contentText)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(launchIntent)
    }

    private fun startForegroundNotification() {
        val notification = notificationBuilder.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateForegroundNotification(contentText: String) {
        notificationBuilder.setContentText(contentText)
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notificationBuilder.build())
    }
}