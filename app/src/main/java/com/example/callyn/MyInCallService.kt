package com.example.callyn

import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class MyInCallService : InCallService() {

    companion object {
        var instance: MyInCallService? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        CallManager.onCallAdded(call)

        // Launch our custom UI
        val intent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)

        CallManager.onCallRemoved(call)

        // When the last call is removed, clear the instance
        if (calls.isEmpty()) {
            instance = null
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    // *** CRITICAL: Listen for audio state changes ***
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)

        // Update the CallManager state with current audio routing info
        CallManager.updateAudioState(
            isMuted = audioState.isMuted,
            isSpeakerOn = audioState.route == CallAudioState.ROUTE_SPEAKER,
            isBluetoothOn = audioState.route == CallAudioState.ROUTE_BLUETOOTH,
            availableRoutes = audioState.supportedRouteMask
        )
    }
}

