package com.example.callyn

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable // <-- Add this import
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf // <-- Add this import
import androidx.compose.runtime.remember // <-- Add this import
import androidx.compose.runtime.setValue // <-- Add this import
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

@Composable
fun InCallScreen() {
    val context = LocalContext.current
    val callState by CallManager.callState.collectAsState()
    val currentState = callState

    // State for dialpad visibility
    var showDialpad by remember { mutableStateOf(false) }

    if (currentState == null) {
        (context as? Activity)?.finish()
        return
    }

    // Make status bar transparent & unified with background
    (context as? Activity)?.window?.let { window ->
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0B))
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            // --- Top Section: Caller Info or Dialpad ---
            if (showDialpad) {
                DialpadComponent(
                    onDigitClick = { digit ->
                        CallManager.playDtmfTone(digit)
                    },
                    modifier = Modifier.padding(top = 64.dp)
                )
            } else {
                CallerInfo(currentState)
            }

            // --- Call Controls ---
            if (currentState.isIncoming && currentState.status == "Ringing") {
                RingingControls()
            } else {
                ActiveCallControls(
                    state = currentState,
                    isDialpadVisible = showDialpad,
                    onToggleDialpad = { showDialpad = !showDialpad }
                )
            }
        }
    }
}

@Composable
private fun CallerInfo(currentState: CallState) {

    // The CallManager now provides the correct name and type
    val displayName = currentState.name

    // Only show the number if it's NOT a work contact
    val displayNumber = if (currentState.type != "work") {
        currentState.number
    } else {
        null // Hide number for work contacts
    }
    // ----------------------------------------------------

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 64.dp)
    ) {
        Text(
            text = displayName,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        // This logic now correctly hides the number for work contacts
        displayNumber?.let {
            Text(
                text = it,
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Text(
            text = currentState.status,
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun RingingControls() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 64.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        CallControlButton(
            icon = Icons.Default.CallEnd,
            backgroundColor = Color(0xFFD32F2F),
            contentColor = Color.White,
            onClick = { CallManager.rejectCall() }
        )
        CallControlButton(
            icon = Icons.Default.Phone,
            backgroundColor = Color(0xFF2E7D32),
            contentColor = Color.White,
            onClick = { CallManager.answerCall() }
        )
    }
}

@Composable
private fun ActiveCallControls(
    state: CallState,
    isDialpadVisible: Boolean, // <-- New
    onToggleDialpad: () -> Unit // <-- New
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Row 1
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CallToggleButton(
                icon = if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                text = "Mute",
                isSelected = state.isMuted,
                onClick = { CallManager.toggleMute() }
            )
            CallToggleButton(
                icon = Icons.Default.VolumeUp,
                text = "Speaker",
                isSelected = state.isSpeakerOn,
                onClick = { CallManager.toggleSpeaker() }
            )
            if (CallManager.isBluetoothAvailable()) {
                CallToggleButton(
                    icon = Icons.Default.Bluetooth,
                    text = "Bluetooth",
                    isSelected = state.isBluetoothOn,
                    onClick = { CallManager.toggleBluetooth() }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp)) // <-- Space between rows

        // Row 2
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CallToggleButton(
                icon = if (state.isHolding) Icons.Default.Pause else Icons.Default.PlayArrow, // Changed from Check
                text = "Hold",
                isSelected = state.isHolding,
                onClick = { CallManager.toggleHold() }
            )
            CallToggleButton(
                icon = Icons.Default.Dialpad, // <-- New Keypad Button
                text = "Keypad",
                isSelected = isDialpadVisible,
                onClick = onToggleDialpad
            )
            // You can add a 3rd button here like "Add Call" if you want
        }


        Spacer(modifier = Modifier.height(40.dp))

        // End Call
        CallControlButton(
            icon = Icons.Default.CallEnd,
            backgroundColor = Color(0xFFD32F2F),
            contentColor = Color.White,
            onClick = { CallManager.rejectCall() }
        )
    }
}

@Composable
private fun CallToggleButton(
    icon: ImageVector,
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) Color.White else Color.White.copy(alpha = 0.15f)
    val iconTint = if (isSelected) Color.Black else Color.White

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() } // Make the whole column clickable
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(bg),
            contentAlignment = Alignment.Center // Center the icon
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(28.dp),
                tint = iconTint
            )
        }
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun CallControlButton(
    icon: ImageVector,
    backgroundColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(84.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        colors = IconButtonDefaults.iconButtonColors(contentColor = contentColor)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp))
    }
}

// --- NEW DIALPAD COMPOSABLE ---

@Composable
private fun DialpadComponent(
    onDigitClick: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val buttons = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('*', '0', '#')
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        buttons.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { digit ->
                    KeypadButton(digit = digit, onClick = { onDigitClick(digit) })
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(digit: Char, onClick: () -> Unit) {
    // Map for dialer button letters (optional, but good UI)
    val subtext = mapOf(
        '1' to "", '2' to "ABC", '3' to "DEF",
        '4' to "GHI", '5' to "JKL", '6' to "MNO",
        '7' to "PQRS", '8' to "TUV", '9' to "WXYZ",
        '*' to "", '0' to "+", '#' to ""
    )[digit] ?: ""

    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(Color(0xFF1E1E1E))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit.toString(),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (subtext.isNotEmpty()) {
                Text(
                    text = subtext,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.LightGray,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}