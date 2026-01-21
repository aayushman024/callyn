package com.mnivesh.callyn

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.mnivesh.callyn.screens.InCallScreen

class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Handle Lock Screen Visibility
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        // 2. Request Keyguard Dismissal (Optional but smoother for incoming calls)
        // This allows the user to interact without swiping the lock screen away first
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        keyguardManager.requestDismissKeyguard(this, null)

        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 4. Edge-to-Edge UI
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            InCallScreen()
        }
    }
}