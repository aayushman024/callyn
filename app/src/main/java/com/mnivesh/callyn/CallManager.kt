package com.mnivesh.callyn

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import androidx.annotation.RequiresApi
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mnivesh.callyn.data.ContactRepository
import com.mnivesh.callyn.db.WorkCallLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// --- UPDATED CallState ---
data class CallState(
    val name: String,
    val number: String,
    val status: String,
    val type: String = "unknown",
    val isMuted: Boolean = false,
    val isHolding: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isBluetoothOn: Boolean = false,
    val isIncoming: Boolean = false,
    val availableRoutes: Int = 0,
    val isConference: Boolean = false,
    val canMerge: Boolean = false,
    val canSwap: Boolean = false,
    val participants: List<String> = emptyList(),
    val familyHead: String? = null,
    val rshipManager: String? = null,
    val connectTimeMillis: Long = 0,
    internal val call: Call? = null, // The "Primary" call shown on screen

    // Call Waiting / Background Call
    val secondCallerName: String? = null,
    val secondCallerNumber: String? = null,
    internal val secondIncomingCall: Call? = null // The "Waiting" or "Background" call
)

object CallManager {

    private val _callState = MutableStateFlow<CallState?>(null)
    val callState = _callState.asStateFlow()

    // ✅ NEW: Track all calls to handle multi-call logic properly
    private val registeredCalls = mutableListOf<Call>()

    private var repository: ContactRepository? = null
    private var appContext: Context? = null

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun initialize(repository: ContactRepository, context: Context) {
        this.repository = repository
        this.appContext = context.applicationContext
    }

    private fun normalizeNumber(number: String): String {
        val digitsOnly = number.filter { it.isDigit() }
        return digitsOnly.takeLast(10)
    }

    @SuppressLint("MissingPermission")
    fun onCallAdded(call: Call) {
        if (!registeredCalls.contains(call)) {
            registeredCalls.add(call)
            registerCallCallback(call)
        }
        // Instead of blindly updating, we recalculate the whole picture
        recalculateGlobalState()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun onCallRemoved(call: Call) {
        registeredCalls.remove(call)

        // 1. Handle Logging (Existing logic)
        handleCallLogging(call)

        // 2. Update UI
        if (registeredCalls.isEmpty()) {
            _callState.value = null
        } else {
            recalculateGlobalState()
        }
    }

    // --- LOGIC: The Brain of Multi-Call Management ---
    private fun recalculateGlobalState() {
        if (registeredCalls.isEmpty()) return

        // 1. Identify the "Primary" call (Active > Dialing > Ringing > Holding)
        // If we are in a conference, the conference parent is primary
        val conferenceCall = registeredCalls.find { it.details.hasProperty(Call.Details.PROPERTY_CONFERENCE) }

        var primaryCall: Call = conferenceCall
            ?: registeredCalls.find { it.state == Call.STATE_ACTIVE }
            ?: registeredCalls.find { it.state == Call.STATE_DIALING }
            ?: registeredCalls.find { it.state == Call.STATE_RINGING } // Only if it's the ONLY call
            ?: registeredCalls.first()

        // 2. Identify "Secondary" call (Waiting or Held)
        // It's any call that isn't the primary one
        val secondaryCall = registeredCalls.find { it != primaryCall }

        // 3. Determine if we have a "Waiting" call (Ringing while another is Active)
        var waitingCall: Call? = null
        if (primaryCall.state == Call.STATE_ACTIVE || primaryCall.state == Call.STATE_HOLDING) {
            waitingCall = registeredCalls.find { it.state == Call.STATE_RINGING }
        }

        // If we have a waiting call, we stay on the Active call but show the popup info
        // If we swapped calls, 'primaryCall' is now the new active one.

        updateStateForPrimary(primaryCall, waitingCall ?: secondaryCall)
    }

    private fun updateStateForPrimary(primary: Call, secondary: Call?) {
        val details = primary.details
        val isConference = details.hasProperty(Call.Details.PROPERTY_CONFERENCE) ||
                (!primary.children.isNullOrEmpty())

        val children = primary.children ?: emptyList()

        // Name & Number Resolution
        var displayNumber = ""
        var finalName = ""

        if (isConference) {
            val count = children.size
            finalName = "Conference ($count)"
        } else {
            val handle = details.handle
            displayNumber = handle?.schemeSpecificPart ?: ""
            finalName = displayNumber
        }

        // Check if we need to resolve name
        val currentState = _callState.value
        if (!isConference && displayNumber.isNotEmpty()) {
            if (currentState?.number == displayNumber && currentState.name != displayNumber) {
                finalName = currentState.name // Keep existing name if already resolved
            } else {
                resolveContactInfo(displayNumber) // Trigger resolution
            }
        }

        // Status String
        val status = primary.getStateString()
        val isIncoming = (primary.state == Call.STATE_RINGING)

        // Capabilities
        val canMerge = (details.callCapabilities and Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0 ||
                (registeredCalls.size > 1 && registeredCalls.none { it.state == Call.STATE_RINGING })

        val canSwap = (details.callCapabilities and Call.Details.CAPABILITY_SWAP_CONFERENCE) != 0 ||
                (registeredCalls.size > 1 && secondary?.state == Call.STATE_HOLDING)

        // Prepare Secondary Info (For Popup)
        var secName: String? = null
        var secNumber: String? = null
        val isSecondRinging = secondary?.state == Call.STATE_RINGING

        if (secondary != null) {
            val rawSec = secondary.details.handle?.schemeSpecificPart ?: ""
            secNumber = rawSec
            // We'll let the UI or a coroutine resolve this name if needed,
            // for now pass number or existing state name
            secName = if (currentState?.secondCallerNumber == rawSec) currentState.secondCallerName else rawSec

            if (isSecondRinging && secName == rawSec) {
                resolveSecondaryContactInfo(rawSec)
            }
        }

        val newState = CallState(
            name = finalName,
            number = displayNumber,
            status = status,
            isIncoming = isIncoming,
            isConference = isConference,
            canMerge = canMerge,
            canSwap = canSwap,
            participants = children.map { it.details.handle?.schemeSpecificPart ?: "Unknown" },
            call = primary,
            type = currentState?.type ?: "unknown",
            isMuted = currentState?.isMuted ?: false,
            isHolding = (primary.state == Call.STATE_HOLDING),
            isSpeakerOn = currentState?.isSpeakerOn ?: false,
            isBluetoothOn = currentState?.isBluetoothOn ?: false,
            availableRoutes = currentState?.availableRoutes ?: 0,
            familyHead = currentState?.familyHead,
            rshipManager = currentState?.rshipManager,
            connectTimeMillis = details.connectTimeMillis,

            // ✅ Second Call Data
            secondIncomingCall = if (isSecondRinging) secondary else null,
            secondCallerName = secName,
            secondCallerNumber = secNumber
        )

        _callState.value = newState
    }

    // --- Actions ---

    fun acceptCallWaiting() {
        val waiting = _callState.value?.secondIncomingCall ?: return
        waiting.answer(0)
        // System automatically holds the active call.
        // Our 'recalculateGlobalState' will catch the state change and update UI.
    }

    fun rejectCallWaiting() {
        val waiting = _callState.value?.secondIncomingCall ?: return
        waiting.reject(false, "")
    }

    fun mergeCalls() {
        // Try generic merge
        val primary = _callState.value?.call ?: return
        val secondary = registeredCalls.find { it != primary }

        if (primary.details.callCapabilities and Call.Details.CAPABILITY_MERGE_CONFERENCE != 0) {
            primary.mergeConference()
        } else if (secondary != null) {
            primary.conference(secondary)
        }
    }

    fun swapCalls() {
        // Robust Swap: If generic fails, do manual hold/unhold
        val primary = _callState.value?.call ?: return
        val secondary = registeredCalls.find { it != primary } ?: return

        if (primary.details.callCapabilities and Call.Details.CAPABILITY_SWAP_CONFERENCE != 0) {
            primary.swapConference()
        } else {
            // Manual Swap
            if (primary.state == Call.STATE_ACTIVE) {
                primary.hold()
                secondary.unhold()
            } else if (primary.state == Call.STATE_HOLDING) {
                secondary.hold()
                primary.unhold()
            }
        }
    }

    // --- Resolution Logic ---

    private fun resolveSecondaryContactInfo(number: String) {
        if (number.isBlank()) return
        val normalized = normalizeNumber(number)
        coroutineScope.launch {
            var name = findPersonalContactName(normalized)
            if (name == null) {
                val workContact = repository?.findWorkContactByNumber(normalized)
                if (workContact != null) name = workContact.name
            }
            if (name != null) {
                val current = _callState.value
                if (current != null && current.secondCallerNumber == number) {
                    _callState.value = current.copy(secondCallerName = name)
                }
            }
        }
    }

    // ... (resolveContactInfo for primary call remains unchanged from previous) ...
    private fun resolveContactInfo(number: String) {
        if (number.isBlank()) return
        val normalized = normalizeNumber(number)
        coroutineScope.launch {
            val personalName = findPersonalContactName(normalized)
            if (personalName != null) {
                if (_callState.value?.number == number) {
                    _callState.value = _callState.value?.copy(name = personalName, type = "personal")
                }
            } else {
                if (normalized.length > 9) {
                    val workContact = repository?.findWorkContactByNumber(normalized)
                    if (workContact != null) {
                        if (_callState.value?.number == number) {
                            val updatedState = _callState.value?.copy(
                                name = workContact.name,
                                type = "work",
                                familyHead = workContact.familyHead,
                                rshipManager = workContact.rshipManager
                            )
                            _callState.value = updatedState
                        }
                    }
                }
            }
        }
    }

    // --- Logging & Deletion (Preserved) ---
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun handleCallLogging(call: Call) {
        val lastState = _callState.value ?: return // Only log if we had a state
        // Use the call details directly, as lastState might be pointing to the OTHER call
        val rawNumber = call.details.handle?.schemeSpecificPart ?: ""

        coroutineScope.launch {
            // Re-check work status for safety
            var isWork = false
            var name = ""
            if (rawNumber.isNotEmpty()) {
                val normalized = normalizeNumber(rawNumber)
                val workContact = repository?.findWorkContactByNumber(normalized)
                if (workContact != null) {
                    isWork = true
                    name = workContact.name
                }
            }

            if (isWork) {
                val now = System.currentTimeMillis()
                val durationSeconds = if (call.details.connectTimeMillis > 0) {
                    (now - call.details.connectTimeMillis) / 1000
                } else {
                    0
                }

                var direction = "outgoing"
                val wasIncoming = (call.details.callDirection == Call.Details.DIRECTION_INCOMING)
                if (wasIncoming) {
                    direction = if (call.details.connectTimeMillis > 0) "incoming" else "missed"
                }

                repository?.insertWorkLog(
                    WorkCallLog(
                        name = name.ifBlank { rawNumber },
                        number = rawNumber,
                        duration = durationSeconds,
                        timestamp = now,
                        type = "work",
                        direction = direction,
                        isSynced = false
                    )
                )

                // [!code ++] Check department before deleting logs
                val context = appContext
                val department = if (context != null) AuthManager(context).getDepartment() else null

                if (department != "Management") {
                    setupLogDeletionObserver(rawNumber)
                }

                // Sync
                if (appContext != null) {
                    val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .build()
                    WorkManager.getInstance(appContext!!).enqueue(uploadWorkRequest)
                }
            }
        }
    }

    // --- Boilerplate (Callbacks, Audio, etc - Unchanged) ---
    private fun registerCallCallback(call: Call) {
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) { recalculateGlobalState() }
            override fun onDetailsChanged(call: Call, details: Call.Details) { recalculateGlobalState() }
            override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: List<Call>) { recalculateGlobalState() }
            override fun onChildrenChanged(call: Call, children: List<Call>) { recalculateGlobalState() }
        })
    }

    fun splitFromConference(childIndex: Int) {
        val children = _callState.value?.call?.children
        if (children != null && childIndex in children.indices) children[childIndex].splitFromConference()
    }
    fun updateAudioState(isMuted: Boolean, isSpeakerOn: Boolean, isBluetoothOn: Boolean, availableRoutes: Int) {
        _callState.value = _callState.value?.copy(isMuted = isMuted, isSpeakerOn = isSpeakerOn, isBluetoothOn = isBluetoothOn, availableRoutes = availableRoutes)
    }
    fun answerCall() { _callState.value?.call?.answer(0) }
    fun rejectCall() {
        _callState.value?.call?.let { if (it.state == Call.STATE_RINGING) it.reject(false, "") else it.disconnect() }
    }
    fun toggleSpeaker() {
        val current = _callState.value ?: return
        val route = if (current.isSpeakerOn) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER
        MyInCallService.instance?.setAudioRoute(route)
    }
    fun toggleMute() { MyInCallService.instance?.setMuted(!(_callState.value?.isMuted ?: false)) }
    fun toggleHold() {
        val current = _callState.value ?: return
        if (current.isHolding) current.call?.unhold() else current.call?.hold()
    }
    fun isBluetoothAvailable(): Boolean { return ((_callState.value?.availableRoutes ?: 0) and CallAudioState.ROUTE_BLUETOOTH) != 0 }
    fun toggleBluetooth() {
        val current = _callState.value ?: return
        if (!isBluetoothAvailable()) return
        val newRoute = if (current.isBluetoothOn) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_BLUETOOTH
        MyInCallService.instance?.setAudioRoute(newRoute)
    }
    fun playDtmfTone(digit: Char) { _callState.value?.call?.playDtmfTone(digit) }

    @SuppressLint("Range")
    private fun findPersonalContactName(normalizedNumber: String): String? {
        val context = appContext ?: return null
        var contactName: String? = null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(normalizedNumber))
        try {
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) contactName = it.getString(it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME))
            }
        } catch (e: Exception) { e.printStackTrace() }
        return contactName
    }

    private fun Call.getStateString(): String = when (this.state) {
        Call.STATE_RINGING -> "Ringing"
        Call.STATE_DIALING -> "Dialing"
        Call.STATE_ACTIVE -> "Active"
        Call.STATE_HOLDING -> "On Hold"
        Call.STATE_DISCONNECTED -> "Disconnected"
        Call.STATE_CONNECTING -> "Connecting"
        else -> "Unknown"
    }

    private fun setupLogDeletionObserver(number: String) {
        val context = appContext ?: return
        val handler = Handler(Looper.getMainLooper())
        var observer: ContentObserver? = null
        observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                coroutineScope.launch {
                    val deleted = maskAndDeleteLog(context, number)
                    if (deleted) try { observer?.let { context.contentResolver.unregisterContentObserver(it) } } catch (e: Exception) {}
                }
            }
        }
        try { context.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer) } catch (e: Exception) {}
        handler.postDelayed({ try { observer?.let { context.contentResolver.unregisterContentObserver(it) } } catch (e: Exception) {} }, 5000)
        coroutineScope.launch {
            val deleted = maskAndDeleteLog(context, number)
            if (deleted) try { observer?.let { context.contentResolver.unregisterContentObserver(it) } } catch (e: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun maskAndDeleteLog(context: Context, number: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val numberToQuery = normalizeNumber(number)
                val contentResolver = context.contentResolver
                val queryUri = CallLog.Calls.CONTENT_URI
                val projection = arrayOf(CallLog.Calls._ID, CallLog.Calls.TYPE)
                val selection = "${CallLog.Calls.NUMBER} LIKE ?"
                val selectionArgs = arrayOf("%$numberToQuery")
                val sortOrder = "${CallLog.Calls.DATE} DESC LIMIT 1"
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
                    if (rows > 0) return@withContext true
                }
            } catch (e: Exception) {}
            return@withContext false
        }
    }
}