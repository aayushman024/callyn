package com.mnivesh.callyn

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.runtime.remember
import com.mnivesh.callyn.managers.CallManager
import com.mnivesh.callyn.managers.ThemeManager
import com.mnivesh.callyn.screens.InCallScreen
import com.mnivesh.callyn.ui.theme.CallynTheme
import androidx.lifecycle.lifecycleScope
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
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        keyguardManager.requestDismissKeyguard(this, null)

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
            val themeManager = remember { ThemeManager(this) }
            val isDarkTheme = themeManager.isDarkTheme()
            CallynTheme(darkTheme = isDarkTheme) {
                InCallScreen()
            }
        }
    }
}