package com.example.callyn

import android.annotation.SuppressLint
import android.telecom.Call
import android.telecom.CallAudioState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// 1. Define all possible states for the UI
data class CallState(
    val name: String,
    val number: String,
    val status: String,
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

    @SuppressLint("MissingPermission")
    fun onCallAdded(call: Call) {
        val number = call.details.handle.schemeSpecificPart
        val name = ContactRepository.getNameByNumber(number) ?: "Unknown"
        val isIncoming = call.state == Call.STATE_RINGING

        _callState.value = CallState(
            name = name,
            number = number,
            status = call.getStateString(),
            isIncoming = isIncoming,
            call = call
        )

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

    fun onCallRemoved(call: Call) {
        _callState.value = null
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