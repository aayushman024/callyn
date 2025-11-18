package com.example.callyn

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.util.Log
import com.example.callyn.data.ContactRepository
import com.example.callyn.db.WorkCallLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ... CallState data class (Remains unchanged) ...
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
    internal val call: Call? = null
)

object CallManager {

    private val _callState = MutableStateFlow<CallState?>(null)
    val callState = _callState.asStateFlow()

    private var repository: ContactRepository? = null
    private var appContext: Context? = null
    private var callRecorder: CallRecorder? = null

    // Flag to prevent multiple start recording attempts
    private var isRecording = false

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun initialize(repository: ContactRepository, context: Context) {
        this.repository = repository
        this.appContext = context.applicationContext
        this.callRecorder = CallRecorder(context.applicationContext)
    }

    private fun normalizeNumber(number: String): String {
        val digitsOnly = number.filter { it.isDigit() }
        return digitsOnly.takeLast(10)
    }

    @SuppressLint("MissingPermission")
    fun onCallAdded(call: Call) {
        isRecording = false // Reset flag on new call
        registerCallCallback(call)
        updateCallState(call)
    }

    fun onCallRemoved(call: Call) {
        val lastState = _callState.value

        // --- Safe Stop Recording ---
        // Launch in IO scope so it doesn't block UI thread
        coroutineScope.launch {
            if (isRecording) {
                callRecorder?.stopRecording()
                isRecording = false
            }
        }

        val remainingCall = MyInCallService.instance?.calls?.firstOrNull {
            it != call && it.state != Call.STATE_DISCONNECTED
        }

        if (remainingCall == null) {
            _callState.value = null
        } else {
            onCallAdded(remainingCall)
        }

        // --- WORK CALL LOGIC ---
        if (lastState != null && lastState.type == "work") {
            val now = System.currentTimeMillis()

            val durationSeconds = if (call.details.connectTimeMillis > 0) {
                (now - call.details.connectTimeMillis) / 1000
            } else {
                0
            }

            coroutineScope.launch {
                repository?.insertWorkLog(
                    WorkCallLog(
                        name = lastState.name,
                        number = lastState.number,
                        duration = durationSeconds,
                        timestamp = now,
//                        isIncoming = lastState.isIncoming,
//                        isUploaded = false
                    )
                )
                deleteLastCallLogEntry(lastState.number)
            }
        }
    }

    private fun updateCallState(call: Call) {
        val details = call.details
        val isConference = details.hasProperty(Call.Details.PROPERTY_CONFERENCE) ||
                (call.children != null && call.children.isNotEmpty())

        val children = call.children ?: emptyList()

        var displayNumber = ""
        var finalName = ""

        if (isConference) {
            val count = children.size
            finalName = if (count > 0) "Conference Call ($count)" else "Merging Calls..."
            displayNumber = ""
        } else {
            val handle = details.handle
            if (handle == null) {
                finalName = "Connecting..."
                displayNumber = ""
            } else {
                displayNumber = handle.schemeSpecificPart ?: ""
                finalName = displayNumber
            }
        }

        val currentState = _callState.value
        if (!isConference && displayNumber.isNotEmpty() && currentState != null) {
            if (currentState.number == displayNumber && currentState.name != displayNumber) {
                finalName = currentState.name
            }
        }

        val status = call.getStateString()
        val isIncoming = call.state == Call.STATE_RINGING

        // --- CRITICAL FIX: SAFE RECORDING TRIGGER ---
        // 1. Check if active
        // 2. Check if it is work type
        // 3. Check if we are NOT already recording (prevent loops)
        if (call.state == Call.STATE_ACTIVE && currentState?.type == "work" && !isRecording) {
            isRecording = true // Mark as recording immediately
            coroutineScope.launch {
                val success = callRecorder?.startRecording(displayNumber) == true
                if (!success) isRecording = false // Reset if failed
            }
        }
        // -------------------------------------------

        val canMerge = call.conferenceableCalls.isNotEmpty() ||
                (details.callCapabilities and Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0
        val canSwap = (details.callCapabilities and Call.Details.CAPABILITY_SWAP_CONFERENCE) != 0

        val newState = CallState(
            name = finalName,
            number = displayNumber,
            status = status,
            isIncoming = isIncoming,
            isConference = isConference,
            canMerge = canMerge,
            canSwap = canSwap,
            participants = children.map { it.details.handle?.schemeSpecificPart ?: "Unknown" },
            call = call
        )

        if (currentState != null) {
            _callState.value = newState.copy(
                isMuted = currentState.isMuted,
                isSpeakerOn = currentState.isSpeakerOn,
                isBluetoothOn = currentState.isBluetoothOn,
                availableRoutes = currentState.availableRoutes,
                type = currentState.type
            )
        } else {
            _callState.value = newState
        }

        if (!isConference && displayNumber.isNotEmpty() && finalName == displayNumber) {
            resolveContactInfo(displayNumber)
        }
    }

    private fun resolveContactInfo(number: String) {
        if (number.isBlank()) return

        val normalized = normalizeNumber(number)
        coroutineScope.launch {
            val workContact = repository?.findWorkContactByNumber(normalized)
            if (workContact != null) {
                if (_callState.value?.number == number) {
                    _callState.value = _callState.value?.copy(
                        name = workContact.name,
                        type = "work"
                    )
                    // Re-check recording logic in case status became ACTIVE while resolving
                    val currentCall = _callState.value?.call
                    if (currentCall?.state == Call.STATE_ACTIVE && !isRecording) {
                        isRecording = true
                        val success = callRecorder?.startRecording(number) == true
                        if (!success) isRecording = false
                    }
                }
            } else {
                val personalName = findPersonalContactName(normalized)
                if (personalName != null) {
                    if (_callState.value?.number == number) {
                        _callState.value = _callState.value?.copy(
                            name = personalName,
                            type = "personal"
                        )
                    }
                }
            }
        }
    }

    // ... (Keep registerCallCallback, Actions, Audio Logic, Bluetooth Logic, DTMF, Helpers) ...
    // These remain exactly the same as previous working versions.

    private fun registerCallCallback(call: Call) {
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) { updateCallState(call) }
            override fun onDetailsChanged(call: Call, details: Call.Details) { updateCallState(call) }
            override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: List<Call>) { updateCallState(call) }
            override fun onChildrenChanged(call: Call, children: List<Call>) { updateCallState(call) }
        })
    }

    fun mergeCalls() {
        val currentCall = _callState.value?.call ?: return
        val conferenceable = currentCall.conferenceableCalls
        if (conferenceable.isNotEmpty()) {
            currentCall.conference(conferenceable.first())
        } else {
            if ((currentCall.details.callCapabilities and Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0) {
                currentCall.mergeConference()
            }
        }
    }

    fun swapCalls() {
        val currentCall = _callState.value?.call ?: return
        if ((currentCall.details.callCapabilities and Call.Details.CAPABILITY_SWAP_CONFERENCE) != 0) {
            currentCall.swapConference()
        }
    }

    fun splitFromConference(childIndex: Int) {
        val currentCall = _callState.value?.call ?: return
        val children = currentCall.children
        if (children != null && childIndex in children.indices) {
            children[childIndex].splitFromConference()
        }
    }

    fun updateAudioState(isMuted: Boolean, isSpeakerOn: Boolean, isBluetoothOn: Boolean, availableRoutes: Int) {
        _callState.value = _callState.value?.copy(
            isMuted = isMuted,
            isSpeakerOn = isSpeakerOn,
            isBluetoothOn = isBluetoothOn,
            availableRoutes = availableRoutes
        )
    }

    fun answerCall() { _callState.value?.call?.answer(0) }

    fun rejectCall() {
        _callState.value?.call?.let {
            if (it.state == Call.STATE_RINGING) it.reject(false, "") else it.disconnect()
        }
    }

    fun toggleSpeaker() {
        val current = _callState.value ?: return
        val route = if (current.isSpeakerOn) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER
        MyInCallService.instance?.setAudioRoute(route)
    }

    fun toggleMute() {
        val current = _callState.value ?: return
        MyInCallService.instance?.setMuted(!current.isMuted)
    }

    fun toggleHold() {
        val current = _callState.value ?: return
        if (current.isHolding) current.call?.unhold() else current.call?.hold()
    }

    fun isBluetoothAvailable(): Boolean {
        val currentState = _callState.value ?: return false
        return (currentState.availableRoutes and CallAudioState.ROUTE_BLUETOOTH) != 0
    }

    fun toggleBluetooth() {
        val currentState = _callState.value ?: return
        if (!isBluetoothAvailable()) return

        val newRoute = if (currentState.isBluetoothOn) {
            CallAudioState.ROUTE_EARPIECE
        } else {
            CallAudioState.ROUTE_BLUETOOTH
        }
        MyInCallService.instance?.setAudioRoute(newRoute)
    }

    fun playDtmfTone(digit: Char) {
        _callState.value?.call?.playDtmfTone(digit)
    }

    @SuppressLint("Range")
    private fun findPersonalContactName(normalizedNumber: String): String? {
        val context = appContext ?: return null
        var contactName: String? = null
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(normalizedNumber)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    contactName = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME))
                }
            }
        } catch (e: Exception) {
            Log.e("CallManager", "Failed to query personal contacts", e)
        }
        return contactName
    }

    @SuppressLint("MissingPermission")
    private suspend fun deleteLastCallLogEntry(number: String) {
        val context = appContext ?: return
        delay(1500)
        try {
            val contentResolver = context.contentResolver
            val numberToQuery = normalizeNumber(number)
            val queryUri = CallLog.Calls.CONTENT_URI
            val projection = arrayOf(CallLog.Calls._ID)
            val selection = "${CallLog.Calls.NUMBER} LIKE ?"
            val selectionArgs = arrayOf("%$numberToQuery")
            val sortOrder = "${CallLog.Calls.DATE} DESC LIMIT 1"
            var entryId: String? = null
            contentResolver.query(queryUri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    entryId = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls._ID))
                }
            }
            if (entryId != null) {
                val deleteUri = CallLog.Calls.CONTENT_URI
                val deleteSelection = "${CallLog.Calls._ID} = ?"
                val deleteSelectionArgs = arrayOf(entryId)
                contentResolver.delete(deleteUri, deleteSelection, deleteSelectionArgs)
            }
        } catch (e: Exception) {
            Log.e("CallManager", "Failed to delete call log entry", e)
        }
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
}