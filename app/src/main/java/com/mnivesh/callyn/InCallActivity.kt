package com.mnivesh.callyn

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.mnivesh.callyn.managers.CallManager
import com.mnivesh.callyn.screens.InCallScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        // 1. Handle Lock Screen Visibility
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        // 2. Request Keyguard Dismissal (Optional but smoother for incoming calls)
        // This allows the user to interact without swiping the lock screen away first
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        keyguardManager.requestDismissKeyguard(this, null)

        //window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 4. Edge-to-Edge UI
        WindowCompat.setDecorFitsSystemWindows(window, false)

        lifecycleScope.launch {
            CallManager.callState.collectLatest { state ->
                if (state == null || state.status == "Disconnected") {
                    finishAndRemoveTask()
                }
            }
        }

        setContent {
            InCallScreen()
        }
    }
}