package com.example.callyn

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat

class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- Critical for Lock Screen ---
        // These flags allow the activity to show over the lock screen
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        // Deprecated in API 30, but good for < 30
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )
        // ---------------------------------

        // Make the app edge-to-edge (draw behind status/nav bars)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            // Set the Compose UI
            InCallScreen()
        }
    }
}