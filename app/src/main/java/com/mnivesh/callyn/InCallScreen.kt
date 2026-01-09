package com.mnivesh.callyn

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import androidx.annotation.RequiresPermission
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

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
        },
        // Call Waiting
        onAnswerWaiting = { CallManager.acceptCallWaiting() },
        onRejectWaiting = { CallManager.rejectCallWaiting() }
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
    onAddCall: () -> Unit,
    onAnswerWaiting: () -> Unit,
    onRejectWaiting: () -> Unit
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
                .padding(horizontal = 24.sdp(), vertical = 20.sdp()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // --- Top Section (Flexible Weight) ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                if (showDialpad) {
                    DialpadComponent(
                        onDigitClick = onDigitClick,
                        modifier = Modifier
                            .padding(top = 20.sdp())
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    )
                } else {
                    CallerInfo(currentState)
                }
            }

            // --- Bottom Section (Fixed Controls) ---
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

        // --- Call Waiting Popup ---
        AnimatedVisibility(
            visible = currentState.secondIncomingCall != null,
            enter = slideInVertically { -it },
            exit = slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.sdp(), start = 16.sdp(), end = 16.sdp())
        ) {
            CallWaitingPopup(
                name = currentState.secondCallerName ?: "Unknown",
                number = currentState.secondCallerNumber ?: "",
                onAccept = onAnswerWaiting,
                onDecline = onRejectWaiting
            )
        }
    }

    if (showConferenceSheet && currentState.isConference) {
        ModalBottomSheet(
            onDismissRequest = { showConferenceSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF1C1C1E),
            contentColor = Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) },
            shape = RoundedCornerShape(topStart = 24.sdp(), topEnd = 24.sdp())
        ) {
            Column(modifier = Modifier.padding(bottom = 48.sdp())) {
                Text(
                    text = "Conference Participants",
                    fontSize = 20.ssp(),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.sdp(), vertical = 16.sdp())
                )

                if (currentState.participants.isEmpty()) {
                    Text(
                        "No participant info available",
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 24.sdp())
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

// --- Helper Components ---

@Composable
fun CallWaitingPopup(name: String, number: String, onAccept: () -> Unit, onDecline: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E)),
        shape = RoundedCornerShape(16.sdp()),
        elevation = CardDefaults.cardElevation(8.sdp())
    ) {
        Row(
            modifier = Modifier
                .padding(16.sdp())
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Incoming Call", color = Color(0xFF30D158), fontSize = 12.ssp(), fontWeight = FontWeight.Bold)
                Text(name, color = Color.White, fontSize = 16.ssp(), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (number.isNotEmpty() && name != number) {
                    Text(number, color = Color.Gray, fontSize = 14.ssp())
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.sdp())) {
                // Reject
                IconButton(onClick = onDecline, modifier = Modifier
                    .size(44.sdp())
                    .background(HangupRed, CircleShape)) {
                    Icon(Icons.Default.CallEnd, null, tint = Color.White)
                }
                // Accept
                IconButton(onClick = onAccept, modifier = Modifier
                    .size(44.sdp())
                    .background(AnswerGreen, CircleShape)) {
                    Icon(Icons.Default.Call, null, tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun CallDurationTimer(connectTimeMillis: Long, color: Color) {
    var timeText by remember { mutableStateOf("00:00") }
    LaunchedEffect(connectTimeMillis) {
        if (connectTimeMillis > 0) {
            while (true) {
                val durationMs = System.currentTimeMillis() - connectTimeMillis
                val seconds = durationMs / 1000
                val m = seconds / 60
                val s = seconds % 60
                timeText = "%02d:%02d".format(m, s)
                delay(1000L)
            }
        }
    }
    Text(
        text = timeText,
        fontSize = 14.ssp(),
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.padding(horizontal = 16.sdp(), vertical = 6.sdp()),
        letterSpacing = 1.ssp()
    )
}

@Composable
fun InfoPill(icon: ImageVector, label: String, value: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.sdp(), color.copy(alpha = 0.3f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.sdp(), vertical = 6.sdp())
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.sdp())
            )
            Spacer(modifier = Modifier.width(8.sdp()))
            Text(
                text = "$label : ",
                fontSize = 14.ssp(),
                fontWeight = FontWeight.Normal,
                color = color.copy(alpha = 0.9f)
            )
            Text(
                text = value,
                fontSize = 14.ssp(),
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
        modifier = Modifier
            .padding(top = 18.sdp())
            .verticalScroll(rememberScrollState())
    ) {
    //call status and duration
        Surface(
            color = Color.White.copy(alpha = 0.1f),
            shape = RoundedCornerShape(50),
        ) {
            if (currentState.status.equals("Active", ignoreCase = true) && currentState.connectTimeMillis > 0) {
                CallDurationTimer(
                    connectTimeMillis = currentState.connectTimeMillis,
                    color = TextSecondary
                )
            } else {
                Text(
                    text = currentState.status.uppercase(),
                    fontSize = 10.ssp(),
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.sdp(), vertical = 6.sdp()),
                    //  letterSpacing = 1.5.ssp()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.sdp()))

        // 1. Avatar
        Box(
            modifier = Modifier
                .size(100.sdp())
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if(currentState.isConference) Icons.Default.Groups else Icons.Default.Person,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(48.sdp())
            )
        }

        Spacer(modifier = Modifier.height(20.sdp()))

        // 2. Name
        Text(
            text = currentState.name,
            fontSize = 32.ssp(),
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            lineHeight = 38.ssp()
        )

        Spacer(modifier = Modifier.height(16.sdp()))

        // 3. Info Pills (Family Head & RM)
        if (currentState.type == "work" && !currentState.isConference) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.sdp())
            ) {
                if (currentState.familyHead?.isNotEmpty() == true) {
                    InfoPill(Icons.Default.FamilyRestroom, "Family Head", currentState.familyHead, Color(0xFF60A5FA))
                }
                if (currentState.rshipManager?.isNotEmpty() == true) {
                    InfoPill(Icons.Default.AccountBox, "RM", currentState.rshipManager, Color(0xFFC084FC))
                }
            }
            Spacer(modifier = Modifier.height(24.sdp()))
            Surface(
                color = Color(0xFF3B82F6).copy(alpha = 0.2f),
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    text = "WORK CALL",
                    fontSize = 11.ssp(),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF60A5FA),
                    modifier = Modifier.padding(horizontal = 12.sdp(), vertical = 4.sdp()),
                    letterSpacing = 1.ssp()
                )
            }
        }

        // 4. Number
        if (currentState.number.isNotEmpty() && !currentState.isConference && currentState.type != "work") {
            Text(
                text = currentState.number,
                fontSize = 18.ssp(),
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.sdp()),
                letterSpacing = 1.ssp()
            )
        }
        Spacer(modifier = Modifier.height(16.sdp()))

    }
}

// -------------------------------------------------------------
// REPLACED: RingingControls with Swipeable Implementation
// -------------------------------------------------------------
@Composable
private fun RingingControls(onAnswer: () -> Unit, onReject: () -> Unit) {
    SwipeableCallControl(
        onSwipeLeft = onReject,
        onSwipeRight = onAnswer
    )
}

@Composable
private fun SwipeableCallControl(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(android.os.VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }
    }

    fun vibrate(ms: Long = 25L) {
        vibrator?.vibrate(
            VibrationEffect.createOneShot(
                ms,
                VibrationEffect.DEFAULT_AMPLITUDE
            )
        )
    }

    // 1. Defined Sizes
    val trackHeight = 86.sdp()
    val thumbSize = 76.sdp()

    // 2. State
    val offsetX = remember { Animatable(0f) }
    var trackWidth by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    // 3. New Math Calculation
    val thumbPx = with(density) { thumbSize.toPx() }

    // Calculate the actual physical limit the thumb can move from center
    val maxTravelDistance = ((trackWidth - thumbPx) / 2).coerceAtLeast(0f)

    // Trigger Threshold: 50% of the actual travel distance
    val triggerThreshold = if (maxTravelDistance > 0) maxTravelDistance * 0.5f else trackWidth * 0.25f

    val targetColor = when {
        offsetX.value < -triggerThreshold -> HangupRed.copy(alpha = 0.35f)
        offsetX.value > triggerThreshold -> AnswerGreen.copy(alpha = 0.35f)
        else -> Color.White.copy(alpha = 0.10f)
    }

    val backgroundColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(300),
        label = "bg"
    )

    val leftIconAlpha = (1f - (offsetX.value / -triggerThreshold)).coerceIn(0f, 1f)
    val rightIconAlpha = (1f - (offsetX.value / triggerThreshold)).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 52.sdp())
            .height(trackHeight)
            .padding(horizontal = 20.sdp())
            .onSizeChanged { trackWidth = it.width.toFloat() }
            .clip(RoundedCornerShape(60))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        HangupRed.copy(alpha = 0.18f),
                        backgroundColor,
                        AnswerGreen.copy(alpha = 0.18f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {

        // Static Text Layer
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.sdp()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(leftIconAlpha)
            ) {
                Icon(Icons.Default.CallEnd, null, tint = HangupRed, modifier = Modifier.size(22.sdp()))
                Spacer(Modifier.width(6.sdp()))
                Text("Reject", color = HangupRed, fontWeight = FontWeight.Bold, fontSize = 13.ssp())
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(rightIconAlpha)
            ) {
                Text("Accept", color = AnswerGreen, fontWeight = FontWeight.Bold, fontSize = 13.ssp())
                Spacer(Modifier.width(6.sdp()))
                Icon(Icons.Default.Call, null, tint = AnswerGreen, modifier = Modifier.size(22.sdp()))
            }
        }

        // Draggable Thumb
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .size(thumbSize)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            val newOffset = (offsetX.value + delta).coerceIn(-maxTravelDistance, maxTravelDistance)
                            offsetX.snapTo(newOffset)

                            // Haptic feedback when crossing threshold
                            if (abs(newOffset) > triggerThreshold * 0.9f && abs(newOffset) < triggerThreshold * 1.1f) {
                                haptic.performHapticFeedback(
                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                                )
                            }
                        }
                    },
                    onDragStopped = {
                        val x = offsetX.value

                        when {
                            // --- ACCEPT CALL ---
                            x > triggerThreshold -> {
                                haptic.performHapticFeedback(
                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                                )
                                vibrate(40)

                                // 1. Execute Action IMMEDIATELY
                                onSwipeRight()

                                // 2. Run Animation in Separate Scope (Non-blocking)
                                scope.launch {
                                    offsetX.animateTo(maxTravelDistance, spring(stiffness = Spring.StiffnessLow))
                                }
                            }

                            // --- REJECT CALL ---
                            x < -triggerThreshold -> {
                                haptic.performHapticFeedback(
                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                                )
                                vibrate(40)

                                // 1. Execute Action IMMEDIATELY
                                onSwipeLeft()

                                // 2. Run Animation in Separate Scope (Non-blocking)
                                scope.launch {
                                    offsetX.animateTo(-maxTravelDistance, spring(stiffness = Spring.StiffnessLow))
                                }
                            }

                            // --- RESET (Not triggered) ---
                            else -> {
                                scope.launch {
                                    offsetX.animateTo(
                                        0f,
                                        spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                    )
                                }
                            }
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            val icon = when {
                offsetX.value < -10f -> Icons.Default.CallEnd
                offsetX.value > 10f -> Icons.Default.Call
                else -> Icons.Default.Call
            }

            val tint = when {
                offsetX.value < -10f -> HangupRed
                offsetX.value > 10f -> AnswerGreen
                else -> Color.Black
            }

            Icon(icon, null, tint = tint, modifier = Modifier.size(30.sdp()))
        }
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
        verticalArrangement = Arrangement.spacedBy(24.sdp())
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
                Spacer(modifier = Modifier.size(72.sdp()))
            }
        }

        Spacer(modifier = Modifier.height(24.sdp()))

        Box(Modifier.fillMaxWidth(), Alignment.Center) {
            CallActionButton(Icons.Default.CallEnd, HangupRed, 72.sdp(), onReject)
        }
        Spacer(modifier = Modifier.height(16.sdp()))
    }
}

@Composable
private fun CallToggleButton(icon: ImageVector, text: String, isActive: Boolean, onClick: () -> Unit) {
    val backgroundColor by animateColorAsState(if (isActive) ActiveButtonColor else GlassButtonColor, label = "bg")
    val contentColor by animateColorAsState(if (isActive) Color.Black else Color.White, label = "content")

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.sdp())
                .clip(CircleShape)
                .background(backgroundColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = Color.White),
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, text, tint = contentColor, modifier = Modifier.size(32.sdp()))
        }
        Spacer(modifier = Modifier.height(8.sdp()))
        Text(text, color = TextSecondary, fontSize = 13.ssp(), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CallActionButton(icon: ImageVector, backgroundColor: Color, size: Dp, onClick: () -> Unit) {
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
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(36.sdp()))
    }
}

@Composable
fun ConferenceParticipantRow(name: String, onSplit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.sdp(), vertical = 12.sdp()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(Modifier.size(44.sdp()).clip(CircleShape).background(Color(0xFF3A3A3C)), Alignment.Center) {
                Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.ssp())
            }
            Spacer(Modifier.width(16.sdp()))
            Text(name, color = Color.White, fontSize = 17.ssp(), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Button(
            onClick = onSplit,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3A3C), contentColor = Color(0xFF0A84FF)),
            shape = RoundedCornerShape(50),
            contentPadding = PaddingValues(horizontal = 16.sdp(), vertical = 8.sdp()),
            modifier = Modifier.height(36.sdp())
        ) {
            Text("Private", fontSize = 13.ssp(), fontWeight = FontWeight.Bold)
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
        verticalArrangement = Arrangement.spacedBy(20.sdp())
    ) {
        buttons.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.sdp()),
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
            .size(72.sdp())
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
            fontSize = 32.ssp(),
            fontWeight = FontWeight.Normal,
            color = Color.White
        )
    }
}