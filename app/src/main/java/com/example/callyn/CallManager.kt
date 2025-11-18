package com.example.callyn

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.util.Log
import com.example.callyn.data.ContactRepository // <-- FIX: Import real repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.provider.CallLog
import kotlinx.coroutines.delay

// 1. Define all possible states for the UI
data class CallState(
    val name: String,
    val number: String,
    val status: String,
    val type: String = "unknown", // <-- FIX: Add type to distinguish contact source
    val isMuted: Boolean = false,
    val isHolding: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isBluetoothOn: Boolean = false,
    val isIncoming: Boolean = false,
    val availableRoutes: Int = 0,
    internal val call: Call? = null
)

// 2. A singleton object to hold and manage the call state
object CallManager {

    private val _callState = MutableStateFlow<CallState?>(null)
    val callState = _callState.asStateFlow()

    // --- FIX: Add fields for dependencies ---
    private var repository: ContactRepository? = null
    private var appContext: Context? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Called from CallynApplication to inject dependencies.
     */
    fun initialize(repository: ContactRepository, context: Context) {
        this.repository = repository
        this.appContext = context.applicationContext
    }
    // ----------------------------------------

    // --- FIX: Add normalization function ---
    /**
     * Normalizes a phone number to its last 10 digits for reliable lookup.
     * Handles formats like +91, 91, or 0.
     */
    private fun normalizeNumber(number: String): String {
        // Remove all non-digit characters
        val digitsOnly = number.filter { it.isDigit() }
        // Return the last 10 digits, or fewer if the number is shorter
        return digitsOnly.takeLast(10)
    }
    // -------------------------------------

    @SuppressLint("MissingPermission")
    fun onCallAdded(call: Call) {
        val number = call.details.handle.schemeSpecificPart
        val isIncoming = call.state == Call.STATE_RINGING

        // --- FIX: Normalize the number immediately ---
        val normalizedNumber = normalizeNumber(number)
        // -------------------------------------------

        // Set initial state (showing original number as name temporarily)
        _callState.value = CallState(
            name = number, // Show original number initially
            number = number, // Store original number
            status = call.getStateString(),
            isIncoming = isIncoming,
            call = call
        )

        // --- FIX: Use normalizedNumber for all lookups ---
        coroutineScope.launch {
            // 1. Check Work Contacts (from our DB)
            val workContact = repository?.findWorkContactByNumber(normalizedNumber) // Use normalized
            if (workContact != null) {
                _callState.value = _callState.value?.copy(
                    name = workContact.name,
                    type = workContact.type // This will be "work"
                )
            } else {
                // 2. Check Personal Contacts (from phone)
                val personalContactName = findPersonalContactName(normalizedNumber) // Use normalized
                if (personalContactName != null) {
                    _callState.value = _callState.value?.copy(
                        name = personalContactName,
                        type = "personal" // Mark as personal
                    )
                } else {
                    // 3. No match, set name to "Unknown"
                    _callState.value = _callState.value?.copy(
                        name = "Unknown"
                    )
                }
            }
        }
        // -----------------------------------------------

        // Register callback for state changes
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                _callState.value = _callState.value?.copy(
                    status = call.getStateString(),
                    isIncoming = state == Call.STATE_RINGING,
                    isHolding = state == Call.STATE_HOLDING
                )
            }
        })
    }

    /**
     * FIX: Helper function to query the phone's native contact list.
     * Now accepts the normalized number.
     */
    @SuppressLint("Range")
    private fun findPersonalContactName(normalizedNumber: String): String? { // Use normalized
        val context = appContext ?: return null
        var contactName: String? = null
        // Use the normalized number to query the phone's contact lookup
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(normalizedNumber) // Use normalized
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

    fun onCallRemoved(call: Call) {
        // --- MODIFIED: Capture state BEFORE clearing it ---
        val lastState = _callState.value
        val remainingCall = MyInCallService.instance?.calls?.firstOrNull { it != call }
        //updateMultiCallState(remainingCall) // This will set _callState.value to null if no calls are left

        // Check if the call that just ended was a work call
        if (lastState != null && lastState.type == "work") {
            Log.d("CallManager", "Work call ended. Deleting from call log...")
            // Launch a coroutine to handle the deletion
            coroutineScope.launch {
                deleteLastCallLogEntry(lastState.number)
            }
        }
        // --------------------------------------------------
    }

    // --- NEW: Function to delete the call log entry ---
    @SuppressLint("MissingPermission")
    private suspend fun deleteLastCallLogEntry(number: String) {
        val context = appContext ?: return

        // We must delay slightly. The InCallService is notified of a call's
        // removal *before* the system's CallLog provider has written the entry.
        // 1-2 seconds is usually safe.
        delay(1500) // Wait 1.5 seconds

        try {
            val contentResolver = context.contentResolver
            val numberToQuery = normalizeNumber(number)

            // 1. Find the most recent call log entry for this number
            val queryUri = CallLog.Calls.CONTENT_URI
            val projection = arrayOf(CallLog.Calls._ID)
            // Use LIKE to match normalized number, just in case
            val selection = "${CallLog.Calls.NUMBER} LIKE ?"
            val selectionArgs = arrayOf("%$numberToQuery")
            val sortOrder = "${CallLog.Calls.DATE} DESC LIMIT 1"

            var entryId: String? = null

            contentResolver.query(queryUri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    entryId = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls._ID))
                }
            }

            // 2. If we found an entry, delete it by its ID
            if (entryId != null) {
                val deleteUri = CallLog.Calls.CONTENT_URI
                val deleteSelection = "${CallLog.Calls._ID} = ?"
                val deleteSelectionArgs = arrayOf(entryId)

                val rowsDeleted = contentResolver.delete(deleteUri, deleteSelection, deleteSelectionArgs)
                if (rowsDeleted > 0) {
                    Log.d("CallManager", "Successfully deleted call log entry $entryId for number $number")
                } else {
                    Log.w("CallManager", "Could not delete call log entry, 0 rows affected.")
                }
            } else {
                Log.w("CallManager", "Could not find call log entry for number $number to delete.")
            }

        } catch (e: SecurityException) {
            Log.e("CallManager", "SecurityException: Missing WRITE_CALL_LOG permission.", e)
        } catch (e: Exception) {
            Log.e("CallManager", "Failed to delete call log entry", e)
        }
    }

    // *** NEW: Update audio state from InCallService ***
    fun updateAudioState(
        isMuted: Boolean,
        isSpeakerOn: Boolean,
        isBluetoothOn: Boolean,
        availableRoutes: Int
    ) {
        _callState.value = _callState.value?.copy(
            isMuted = isMuted,
            isSpeakerOn = isSpeakerOn,
            isBluetoothOn = isBluetoothOn,
            availableRoutes = availableRoutes
        )
    }

    // --- Actions from the UI ---

    fun answerCall() {
        _callState.value?.call?.answer(0)
    }

    fun rejectCall() {
        _callState.value?.call?.let {
            if (it.state == Call.STATE_RINGING) {
                it.reject(false, "")
            } else {
                it.disconnect()
            }
        }
    }

    fun toggleSpeaker() {
        val currentState = _callState.value ?: return
        val newRoute = if (currentState.isSpeakerOn) {
            CallAudioState.ROUTE_EARPIECE
        } else {
            CallAudioState.ROUTE_SPEAKER
        }
        MyInCallService.instance?.setAudioRoute(newRoute)
    }

    fun toggleMute() {
        val currentState = _callState.value ?: return
        val newMuteState = !currentState.isMuted
        MyInCallService.instance?.setMuted(newMuteState)
    }

    fun toggleBluetooth() {
        val currentState = _callState.value ?: return

        // Check if Bluetooth is available
        val hasBluetoothRoute = (currentState.availableRoutes and CallAudioState.ROUTE_BLUETOOTH) != 0

        if (!hasBluetoothRoute) {
            // No Bluetooth device available
            return
        }

        val newRoute = if (currentState.isBluetoothOn) {
            // If Bluetooth is on, switch to earpiece
            CallAudioState.ROUTE_EARPIECE
        } else {
            // If Bluetooth is off, switch to Bluetooth
            CallAudioState.ROUTE_BLUETOOTH
        }

        MyInCallService.instance?.setAudioRoute(newRoute)
    }

    fun cycleAudioRoute() {
        val currentState = _callState.value ?: return
        val availableRoutes = currentState.availableRoutes

        // Determine next route based on current route and available routes
        val nextRoute = when {
            // Currently on earpiece
            !currentState.isSpeakerOn && !currentState.isBluetoothOn -> {
                when {
                    // If speaker available, go to speaker
                    (availableRoutes and CallAudioState.ROUTE_SPEAKER) != 0 ->
                        CallAudioState.ROUTE_SPEAKER
                    // Else if Bluetooth available, go to Bluetooth
                    (availableRoutes and CallAudioState.ROUTE_BLUETOOTH) != 0 ->
                        CallAudioState.ROUTE_BLUETOOTH
                    // Else stay on earpiece
                    else -> CallAudioState.ROUTE_EARPIECE
                }
            }

            // Currently on speaker
            currentState.isSpeakerOn -> {
                when {
                    // If Bluetooth available, go to Bluetooth
                    (availableRoutes and CallAudioState.ROUTE_BLUETOOTH) != 0 ->
                        CallAudioState.ROUTE_BLUETOOTH
                    // Else go back to earpiece
                    else -> CallAudioState.ROUTE_EARPIECE
                }
            }

            // Currently on Bluetooth
            currentState.isBluetoothOn -> {
                // Go back to earpiece
                CallAudioState.ROUTE_EARPIECE
            }

            else -> CallAudioState.ROUTE_EARPIECE
        }

        MyInCallService.instance?.setAudioRoute(nextRoute)
    }

    fun isBluetoothAvailable(): Boolean {
        val currentState = _callState.value ?: return false
        return (currentState.availableRoutes and CallAudioState.ROUTE_BLUETOOTH) != 0
    }

    fun toggleHold() {
        val currentState = _callState.value ?: return
        if (currentState.isHolding) {
            _callState.value?.call?.unhold()
        } else {
            _callState.value?.call?.hold()
        }
    }

    // --- NEW DTMF FUNCTION ---
    fun playDtmfTone(digit: Char) {
        _callState.value?.call?.playDtmfTone(digit)
    }
    // -------------------------

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