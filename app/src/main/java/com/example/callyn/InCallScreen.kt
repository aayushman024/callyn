package com.example.callyn

import android.app.Activity
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch

// --- Theme Constants ---

// 1. Personal Call Gradient (Grey/Black)
private val PersonalGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF2C2C2C), // Dark Grey Top
        Color(0xFF121212), // Mid Black
        Color(0xFF000000)  // Pure Black Bottom
    )
)

// 2. Work Call Gradient (Navy Blue/Black)
private val WorkGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF0F172A), // Navy Blue Top
        Color(0xFF020617), // Darker Navy
        Color(0xFF000000)  // Pure Black Bottom
    )
)

private val GlassButtonColor = Color.White.copy(alpha = 0.1f)
private val ActiveButtonColor = Color.White
private val TextPrimary = Color.White
private val TextSecondary = Color.White.copy(alpha = 0.6f)
private val HangupRed = Color(0xFFFF453A)
private val AnswerGreen = Color(0xFF30D158)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InCallScreen() {
    val context = LocalContext.current
    val callState by CallManager.callState.collectAsState()
    val currentState = callState

    // UI States
    var showDialpad by remember { mutableStateOf(false) }
    var showConferenceSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    if (currentState == null) {
        (context as? Activity)?.finish()
        return
    }

    // Immersive Mode
    LaunchedEffect(Unit) {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
    }

    // --- Conditional Background Logic ---
    val backgroundBrush = if (currentState.type == "work") WorkGradient else PersonalGradient

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            // --- Top Section: Info or Dialpad ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                if (showDialpad) {
                    DialpadComponent(
                        onDigitClick = { CallManager.playDtmfTone(it) },
                        modifier = Modifier.padding(top = 40.dp)
                    )
                } else {
                    CallerInfo(currentState)
                }
            }

            // --- Bottom Section: Controls ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (currentState.isIncoming && currentState.status == "Ringing") {
                    RingingControls()
                } else {
                    ActiveCallControls(
                        state = currentState,
                        isDialpadVisible = showDialpad,
                        onToggleDialpad = { showDialpad = !showDialpad },
                        onAddCall = {
                            val intent = Intent(context, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        },
                        onManageConference = { showConferenceSheet = true }
                    )
                }
            }
        }
    }

    // --- Conference Sheet ---
    if (showConferenceSheet && currentState != null && currentState.isConference) {
        ModalBottomSheet(
            onDismissRequest = { showConferenceSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF1C1C1E),
            contentColor = Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(modifier = Modifier.padding(bottom = 48.dp)) {
                Text(
                    text = "Conference Participants",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )

                if (currentState.participants.isEmpty()) {
                    Text(
                        "No participant info available",
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                } else {
                    LazyColumn {
                        itemsIndexed(currentState.participants) { index, participant ->
                            ConferenceParticipantRow(
                                name = participant,
                                onSplit = {
                                    CallManager.splitFromConference(index)
                                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                                        showConferenceSheet = false
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallerInfo(currentState: CallState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 60.dp)
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if(currentState.isConference) Icons.Default.Groups else Icons.Default.Person,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = currentState.name,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            lineHeight = 38.sp
        )

        // Show Work/Personal Tag
        if (currentState.type == "work") {
            Surface(
                color = Color(0xFF3B82F6).copy(alpha = 0.2f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text(
                    text = "WORK CALL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF60A5FA),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    letterSpacing = 1.sp
                )
            }
        }

        // *** FIX: Hide number if it is a work call ***
        if (currentState.number.isNotEmpty() && !currentState.isConference && currentState.type != "work") {
            Text(
                text = currentState.number,
                fontSize = 18.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
                letterSpacing = 1.sp
            )
        }

        Surface(
            color = Color.White.copy(alpha = 0.1f),
            shape = RoundedCornerShape(50),
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(
                text = currentState.status.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                letterSpacing = 1.5.sp
            )
        }
    }
}

@Composable
private fun RingingControls() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 48.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CallActionButton(
            icon = Icons.Default.CallEnd,
            backgroundColor = HangupRed,
            size = 72.dp,
            onClick = { CallManager.rejectCall() }
        )
        CallActionButton(
            icon = Icons.Default.Call,
            backgroundColor = AnswerGreen,
            size = 72.dp,
            onClick = { CallManager.answerCall() }
        )
    }
}

@Composable
private fun ActiveCallControls(
    state: CallState,
    isDialpadVisible: Boolean,
    onToggleDialpad: () -> Unit,
    onAddCall: () -> Unit,
    onManageConference: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CallToggleButton(
                icon = if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                text = "Mute",
                isActive = state.isMuted,
                onClick = { CallManager.toggleMute() }
            )
            CallToggleButton(
                icon = Icons.Default.Dialpad,
                text = "Keypad",
                isActive = isDialpadVisible,
                onClick = onToggleDialpad
            )
            CallToggleButton(
                icon = Icons.Default.VolumeUp,
                text = "Speaker",
                isActive = state.isSpeakerOn,
                onClick = { CallManager.toggleSpeaker() }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CallToggleButton(
                icon = Icons.Default.Pause,
                text = if (state.isHolding) "Unhold" else "Hold",
                isActive = state.isHolding,
                onClick = { CallManager.toggleHold() }
            )

            if (state.canMerge) {
                CallToggleButton(
                    icon = Icons.Default.CallMerge,
                    text = "Merge",
                    isActive = false,
                    onClick = { CallManager.mergeCalls() }
                )
            } else if (state.canSwap) {
                CallToggleButton(
                    icon = Icons.Default.SwapCalls,
                    text = "Swap",
                    isActive = false,
                    onClick = { CallManager.swapCalls() }
                )
            } else if (state.isConference) {
                CallToggleButton(
                    icon = Icons.Default.Groups,
                    text = "Manage",
                    isActive = false,
                    onClick = onManageConference
                )
            } else {
                CallToggleButton(
                    icon = Icons.Default.PersonAdd,
                    text = "Add Call",
                    isActive = false,
                    onClick = onAddCall
                )
            }

            if (CallManager.isBluetoothAvailable()) {
                CallToggleButton(
                    icon = Icons.Default.Bluetooth,
                    text = "Audio",
                    isActive = state.isBluetoothOn,
                    onClick = { CallManager.toggleBluetooth() }
                )
            } else {
                Spacer(modifier = Modifier.size(72.dp))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            CallActionButton(
                icon = Icons.Default.CallEnd,
                backgroundColor = HangupRed,
                size = 72.dp,
                onClick = { CallManager.rejectCall() }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CallToggleButton(
    icon: ImageVector,
    text: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        if (isActive) ActiveButtonColor else GlassButtonColor,
        label = "bg"
    )
    val contentColor by animateColorAsState(
        if (isActive) Color.Black else Color.White,
        label = "content"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(backgroundColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = Color.White),
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = contentColor,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = text,
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CallActionButton(
    icon: ImageVector,
    backgroundColor: Color,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.9f else 1f, label = "scale")

    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = Color.Black),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
fun ConferenceParticipantRow(
    name: String,
    onSplit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3A3A3C)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = name,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Button(
            onClick = onSplit,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF3A3A3C),
                contentColor = Color(0xFF0A84FF)
            ),
            shape = RoundedCornerShape(50),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Text(
                "Private",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

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
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        buttons.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { digit ->
                    DialerKey(digit = digit, onClick = { onDigitClick(digit) })
                }
            }
        }
    }
}

@Composable
private fun DialerKey(digit: Char, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = Color.White),
                onClick = { onClick() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit.toString(),
            fontSize = 32.sp,
            fontWeight = FontWeight.Normal,
            color = Color.White
        )
    }
}