package com.example.callyn

import android.app.Activity
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch

// --- Theme Constants ---
private val PersonalGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFF2C2C2C), Color(0xFF121212), Color(0xFF000000))
)
private val WorkGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFF0F172A), Color(0xFF020617), Color(0xFF000000))
)
private val GlassButtonColor = Color.White.copy(alpha = 0.1f)
private val ActiveButtonColor = Color.White
private val TextPrimary = Color.White
private val TextSecondary = Color.White.copy(alpha = 0.6f)
private val HangupRed = Color(0xFFFF453A)
private val AnswerGreen = Color(0xFF30D158)

// 1. STATEFUL COMPOSABLE
@Composable
fun InCallScreen() {
    val context = LocalContext.current
    val callState by CallManager.callState.collectAsState()

    if (callState == null) {
        (context as? Activity)?.finish()
        return
    }

    LaunchedEffect(Unit) {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
    }

    InCallContent(
        currentState = callState!!,
        onDigitClick = { CallManager.playDtmfTone(it) },
        onAnswer = { CallManager.answerCall() },
        onReject = { CallManager.rejectCall() },
        onMute = { CallManager.toggleMute() },
        onSpeaker = { CallManager.toggleSpeaker() },
        onHold = { CallManager.toggleHold() },
        onMerge = { CallManager.mergeCalls() },
        onSwap = { CallManager.swapCalls() },
        onBluetooth = { CallManager.toggleBluetooth() },
        onSplitConference = { index -> CallManager.splitFromConference(index) },
        onAddCall = {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    )
}

// 2. STATELESS COMPOSABLE
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InCallContent(
    currentState: CallState,
    onDigitClick: (Char) -> Unit,
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    onMute: () -> Unit,
    onSpeaker: () -> Unit,
    onHold: () -> Unit,
    onMerge: () -> Unit,
    onSwap: () -> Unit,
    onBluetooth: () -> Unit,
    onSplitConference: (Int) -> Unit,
    onAddCall: () -> Unit
) {
    var showDialpad by remember { mutableStateOf(false) }
    var showConferenceSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

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
            // --- Top Section ---
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                if (showDialpad) {
                    DialpadComponent(
                        onDigitClick = onDigitClick,
                        modifier = Modifier.padding(top = 40.dp)
                    )
                } else {
                    CallerInfo(currentState)
                }
            }

            // --- Bottom Section ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (currentState.isIncoming && currentState.status == "Ringing") {
                    RingingControls(onAnswer, onReject)
                } else {
                    ActiveCallControls(
                        state = currentState,
                        isDialpadVisible = showDialpad,
                        onToggleDialpad = { showDialpad = !showDialpad },
                        onAddCall = onAddCall,
                        onManageConference = { showConferenceSheet = true },
                        onMute = onMute,
                        onSpeaker = onSpeaker,
                        onHold = onHold,
                        onMerge = onMerge,
                        onSwap = onSwap,
                        onBluetooth = onBluetooth,
                        onReject = onReject
                    )
                }
            }
        }
    }

    if (showConferenceSheet && currentState.isConference) {
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
                                    onSplitConference(index)
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

// 3. PREVIEW FUNCTION
@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
fun PreviewInCallScreen() {
    val dummyState = CallState(
        name = "John Doe",
        number = "+1 234 567 8900",
        status = "Ringing",
        type = "work",
        isIncoming = true,
        familyHead = "Jane Doe",
        rshipManager = "Alex Smith"
    )

    InCallContent(
        currentState = dummyState,
        onDigitClick = {},
        onAnswer = {},
        onReject = {},
        onMute = {},
        onSpeaker = {},
        onHold = {},
        onMerge = {},
        onSwap = {},
        onBluetooth = {},
        onSplitConference = {},
        onAddCall = {}
    )
}

// --- Helper Components ---

// *** UPDATED INFO PILL ***
@Composable
fun InfoPill(icon: ImageVector, label: String, value: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f), // Slightly more visible bg
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(8.dp)) // Increased spacing

            // Label (Tag)
            Text(
                text = "$label : ",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = color.copy(alpha = 0.9f)
            )

//            Text(
//                text = ": ",
//                fontSize = 12.sp,
//                color = color.copy(alpha = 0.9f)
//            )

            // Value (Name)
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun CallerInfo(currentState: CallState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 60.dp)
    ) {
        // 1. Avatar
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

        Spacer(modifier = Modifier.height(20.dp))

        // 2. Name
        Text(
            text = currentState.name,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            lineHeight = 38.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Info Pills (Family Head & RM)
        // *** LOGIC UPDATE: Only show if type is "work" ***
        if (currentState.type == "work" && !currentState.isConference) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (currentState.familyHead?.isNotEmpty() == true) {
                    InfoPill(
                        icon = Icons.Default.FamilyRestroom,
                        label = "Family Head",
                        value = currentState.familyHead,
                        color = Color(0xFF60A5FA) // Blue
                    )
                }

                if (currentState.rshipManager?.isNotEmpty() == true) {
                    InfoPill(
                        icon = Icons.Default.AccountBox,
                        label = "RM",
                        value = currentState.rshipManager,
                        color = Color(0xFFC084FC) // Purple
                    )
                }
            }

            // 4. Work Tag (Moved here since it's only for work)
            Spacer(modifier = Modifier.height(45.dp))
            Surface(
                color = Color(0xFF3B82F6).copy(alpha = 0.2f),
                shape = RoundedCornerShape(50),
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

        // 5. Number (Only show if NOT work and NOT conference)
        if (currentState.number.isNotEmpty() && !currentState.isConference && currentState.type != "work") {
            Text(
                text = currentState.number,
                fontSize = 18.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
                letterSpacing = 1.sp
            )
        }

        // 6. Status
        Spacer(modifier = Modifier.height(26.dp))
        Surface(
            color = Color.White.copy(alpha = 0.1f),
            shape = RoundedCornerShape(50),
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

// ... (RingingControls, ActiveCallControls, CallToggleButton, CallActionButton, ConferenceParticipantRow remain unchanged) ...

@Composable
private fun RingingControls(onAnswer: () -> Unit, onReject: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 48.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CallActionButton(Icons.Default.CallEnd, HangupRed, 72.dp, onReject)
        CallActionButton(Icons.Default.Call, AnswerGreen, 72.dp, onAnswer)
    }
}

@Composable
private fun ActiveCallControls(
    state: CallState,
    isDialpadVisible: Boolean,
    onToggleDialpad: () -> Unit,
    onAddCall: () -> Unit,
    onManageConference: () -> Unit,
    onMute: () -> Unit,
    onSpeaker: () -> Unit,
    onHold: () -> Unit,
    onMerge: () -> Unit,
    onSwap: () -> Unit,
    onBluetooth: () -> Unit,
    onReject: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
            CallToggleButton(if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic, "Mute", state.isMuted, onMute)
            CallToggleButton(Icons.Default.Dialpad, "Keypad", isDialpadVisible, onToggleDialpad)
            CallToggleButton(Icons.Default.VolumeUp, "Speaker", state.isSpeakerOn, onSpeaker)
        }

        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
            CallToggleButton(Icons.Default.Pause, if (state.isHolding) "Unhold" else "Hold", state.isHolding, onHold)

            if (state.canMerge) {
                CallToggleButton(Icons.Default.CallMerge, "Merge", false, onMerge)
            } else if (state.canSwap) {
                CallToggleButton(Icons.Default.SwapCalls, "Swap", false, onSwap)
            } else if (state.isConference) {
                CallToggleButton(Icons.Default.Groups, "Manage", false, onManageConference)
            } else {
                CallToggleButton(Icons.Default.PersonAdd, "Add Call", false, onAddCall)
            }

            if (CallManager.isBluetoothAvailable()) {
                CallToggleButton(Icons.Default.Bluetooth, "Audio", state.isBluetoothOn, onBluetooth)
            } else {
                Spacer(modifier = Modifier.size(72.dp))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(Modifier.fillMaxWidth(), Alignment.Center) {
            CallActionButton(Icons.Default.CallEnd, HangupRed, 72.dp, onReject)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CallToggleButton(icon: ImageVector, text: String, isActive: Boolean, onClick: () -> Unit) {
    val backgroundColor by animateColorAsState(if (isActive) ActiveButtonColor else GlassButtonColor, label = "bg")
    val contentColor by animateColorAsState(if (isActive) Color.Black else Color.White, label = "content")

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
            Icon(icon, text, tint = contentColor, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CallActionButton(icon: ImageVector, backgroundColor: Color, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
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
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(36.dp))
    }
}

@Composable
fun ConferenceParticipantRow(name: String, onSplit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF3A3A3C)), Alignment.Center) {
                Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(16.dp))
            Text(name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Button(
            onClick = onSplit,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3A3C), contentColor = Color(0xFF0A84FF)),
            shape = RoundedCornerShape(50),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Text("Private", fontSize = 13.sp, fontWeight = FontWeight.Bold)
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