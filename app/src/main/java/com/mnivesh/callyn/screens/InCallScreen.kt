package com.mnivesh.callyn.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.mnivesh.callyn.MainActivity
import com.mnivesh.callyn.managers.CallManager
import com.mnivesh.callyn.managers.CallState
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
        onRejectWaiting = { CallManager.rejectCallWaiting() },
        onSaveNote = { note -> CallManager.setCallNote(note) }
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
    onRejectWaiting: () -> Unit,
    onSaveNote: (String) -> Unit
) {
    var showDialpad by remember { mutableStateOf(false) }
    var showConferenceSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showMessageSheet by remember { mutableStateOf(false) }

    // State for Details Popup
    var showDetailsPopup by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // --- Dynamic Size Calculation based on Screen Height ---
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp

    //notes section
    var showNotePopup by remember { mutableStateOf(false) }
    // Initialize draft with what's already saved in CallManager (if any), allows "load what he had written"
    var noteDraft by remember { mutableStateOf(CallManager.getCallNote() ?: "") }

    // Determine sizing strategy based on screen height
    val isSmallScreen = screenHeight < 680

    // Adjusted Sizes
    val controlButtonSize = if (isSmallScreen) 56.sdp() else 62.sdp()
    val hangupButtonSize = if (isSmallScreen) 64.sdp() else 72.sdp()
    val verticalSpacing = if (isSmallScreen) 12.sdp() else 20.sdp()
    val dialerKeySize = if (isSmallScreen) 60.sdp() else 68.sdp()
    val avatarSize = if (isSmallScreen) 60.sdp() else 80.sdp()

    val backgroundBrush = remember(currentState.type) {
        if (currentState.type == "work") WorkGradient else PersonalGradient
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.sdp(), vertical = 12.sdp()), // Reduced vertical padding
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // --- Top Section (Flexible Weight) ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (showDialpad) {
                    DialpadComponent(
                        onDigitClick = onDigitClick,
                        keySize = dialerKeySize,
                        modifier = Modifier
                            .padding(top = 10.sdp())
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    )
                } else {
                    CallerInfo(
                        currentState = currentState,
                        avatarSize = avatarSize,
                        isSmallScreen = isSmallScreen,
                        onShowDetails = { showDetailsPopup = true }
                    )
                }
            }

            // --- Bottom Section (Fixed Controls) ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (currentState.isIncoming && currentState.status == "Ringing") {
                    RingingControls(
                        isPersonal = currentState.type != "work",
                        onAnswer = onAnswer,
                        onReject = onReject,
                        onMessageClick = { showMessageSheet = true }
                    )
                } else {
                    ActiveCallControls(
                        state = currentState,
                        isDialpadVisible = showDialpad,
                        buttonSize = controlButtonSize,
                        hangupSize = hangupButtonSize,
                        verticalSpacing = verticalSpacing,
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

        // --- Work Call Notes Button (Top Right) ---
        if (currentState.type == "work" && currentState.status != "Ended") {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .systemBarsPadding()
                    .padding(top = 30.dp, end = 10.dp)
            ) {
                Surface(
                    onClick = { showNotePopup = true },
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(50), // Pill shape
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.sdp(), vertical = 8.sdp()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.sdp())
                    ) {
                        Text(
                            text = "Add Notes",
                            color = Color.White,
                            fontSize = 12.ssp(),
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Add Notes",
                            tint = Color.White,
                            modifier = Modifier.size(14.sdp())
                        )
                    }
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

        //notes popup
        if (showNotePopup) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { showNotePopup = false }, // Dismiss on outside tap
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .padding(20.sdp())
                        .fillMaxWidth()
                        .clickable(enabled = false) {}, // Consume clicks inside
                    color = Color(0xFF1E293B), // Slate-900 like background
                    shape = RoundedCornerShape(16.sdp()),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    shadowElevation = 12.sdp()
                ) {
                    Column(
                        modifier = Modifier.padding(24.sdp()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.sdp())
                    ) {
                        // 1. Header with Icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.sdp())
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit, // Or EditNote if available
                                contentDescription = null,
                                tint = Color(0xFF60A5FA), // Blue tint
                                modifier = Modifier.size(18.sdp())
                            )
                            Text(
                                "Call Notes",
                                color = TextPrimary,
                                fontSize = 18.ssp(),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 2. Note Taking Area
                        OutlinedTextField(
                            value = noteDraft,
                            onValueChange = { noteDraft = it },
                            placeholder = {
                                Text(
                                    "Type key details here...\n• Client requirements\n• Follow-up items",
                                    color = TextSecondary.copy(alpha = 0.4f),
                                    fontSize = 14.ssp()
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 250.sdp(), max = 350.sdp()), // Bigger area
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF0F172A), // Darker inner bg
                                unfocusedContainerColor = Color(0xFF0F172A),
                                focusedBorderColor = Color(0xFF60A5FA),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                cursorColor = Color(0xFF60A5FA)
                            ),
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 15.ssp(),
                                lineHeight = 22.ssp(),
                                fontWeight = FontWeight.Normal
                            ),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Default
                            ),
                            shape = RoundedCornerShape(12.sdp())
                        )

                        // 3. Helper Text (Affirmation & Warning)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.sdp())
                        ) {
                            Text(
                                "Draft is preserved in memory if you close this box.",
                                color = TextSecondary,
                                fontSize = 11.ssp(),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Nothing is saved permanently unless you tap Save.",
                                color = Color(0xFFFFCC00).copy(alpha = 0.9f), // Amber warning color
                                fontSize = 11.ssp(),
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(4.sdp()))

                        // 4. Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.sdp())
                        ) {
                            // Discard
                            Button(
                                onClick = {
                                    noteDraft = "" // Clear Draft
                                    onSaveNote("")
                                    showNotePopup = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = HangupRed
                                ),
                                border = BorderStroke(1.dp, HangupRed.copy(alpha = 0.3f)),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.sdp())
                            ) {
                                Text("Discard", fontWeight = FontWeight.SemiBold)
                            }

                            // Save
                            Button(
                                onClick = {
                                    onSaveNote(noteDraft) // Save to Manager
                                    showNotePopup = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF60A5FA), // Blue
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.sdp())
                            ) {
                                Text("Save Note", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // --- Details Popup (Glass Blur Like) ---
        if (showDetailsPopup) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)) // Dim background
                    .clickable { showDetailsPopup = false }, // Dismiss on outside tap
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .padding(32.sdp())
                        .fillMaxWidth()
                        .clickable(enabled = false) {}, // Consume clicks inside
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(16.sdp()),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    shadowElevation = 8.sdp()
                ) {
                    Column(
                        modifier = Modifier.padding(24.sdp()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.sdp())
                    ) {
                        Text(
                            "Asset Details",
                            color = TextPrimary,
                            fontSize = 18.ssp(),
                            fontWeight = FontWeight.Bold
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                        if (!currentState.aum.isNullOrEmpty()) {
                            InfoPill(
                                icon = Icons.Default.CurrencyRupee,
                                label = "AUM",
                                value = currentState.aum,
                                color = Color(0xFF10B981)
                            )
                        }
                        if (!currentState.familyAum.isNullOrEmpty()) {
                            InfoPill(
                                icon = Icons.Default.Money,
                                label = "Family AUM",
                                value = currentState.familyAum,
                                color = Color(0xFF10B981)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.sdp()))

                        Button(
                            onClick = { showDetailsPopup = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Close", color = Color.White)
                        }
                    }
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
    if (showMessageSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMessageSheet = false },
            containerColor = Color(0xFF1C1C1E),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
        ) {
            val context = LocalContext.current
            QuickResponseSheet(
                onMessageSelected = { msg ->
                    CallManager.sendQuickResponse(context, currentState.number, msg)
                    showMessageSheet = false
                },
                onDismiss = { showMessageSheet = false }
            )
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
private fun CallerInfo(
    currentState: CallState,
    avatarSize: Dp,
    isSmallScreen: Boolean,
    onShowDetails: () -> Unit
) {
    val spacerHeight = if (isSmallScreen) 10.sdp() else 25.sdp()
    val nameSize = if (isSmallScreen) 26.ssp() else 32.ssp()
    val nameLineHeight = if (isSmallScreen) 30.ssp() else 38.ssp()
    val verticalScrollState = rememberScrollState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .verticalScroll(verticalScrollState)
            .padding(bottom = 12.sdp())
    ) {
        // --- Status & Duration ---
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
                )
            }
        }

        Spacer(modifier = Modifier.height(16.sdp()))

        // 1. Avatar
        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (currentState.isConference) Icons.Default.Groups else Icons.Default.Person,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(avatarSize * 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(spacerHeight))

        // 2. Name
        Text(
            text = currentState.name,
            fontSize = nameSize,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            lineHeight = nameLineHeight
        )

        Spacer(modifier = Modifier.height(16.sdp()))

        // 3. Info Pills (Updated Logic)
        if (currentState.type == "work" && !currentState.isConference) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.sdp())
            ) {
                // Check if the contact is an Employee
                val isEmployee = currentState.rshipManager?.equals("Employee", ignoreCase = true) == true

                // Determine if call is connected/active
                val isActiveOrHold = currentState.status.equals("Active", ignoreCase = true) ||
                        currentState.status.equals("On Hold", ignoreCase = true)

                if (isEmployee) {
                    // --- EMPLOYEE VIEW ---
                    if (!currentState.familyHead.isNullOrEmpty()) {
                        InfoPill(
                            icon = Icons.Default.Apartment,
                            label = "Department",
                            value = currentState.familyHead,
                            color = Color(0xFF60A5FA) // Blue
                        )
                    }
                } else {
                    // --- CLIENT VIEW ---
                    if (isActiveOrHold) {
                        // --- COMPACT VIEW (Active/Hold) ---
                        // Family Head
                        if (!currentState.familyHead.isNullOrEmpty()) {
                            InfoPill(
                                icon = Icons.Default.FamilyRestroom,
                                label = "Family Head",
                                value = currentState.familyHead,
                                color = Color(0xFF60A5FA) // Blue
                            )
                        }

                        // Relationship Manager
                        if (!currentState.rshipManager.isNullOrEmpty()) {
                            InfoPill(
                                icon = Icons.Default.AccountBox,
                                label = "RM",
                                value = currentState.rshipManager,
                                color = Color(0xFFC084FC) // Purple
                            )
                        }

                        // Show More Details Button (only if AUM data exists)
                        if (!currentState.aum.isNullOrEmpty() || !currentState.familyAum.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(8.sdp()))
                            Surface(
                                onClick = onShowDetails,
                                color = Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(20.sdp()),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.sdp(), vertical = 8.sdp()),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Show Asset Details",
                                        fontSize = 13.ssp(),
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF60A5FA) // Blue
                                    )
                                    Spacer(modifier = Modifier.width(6.sdp()))
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = Color(0xFF60A5FA),
                                        modifier = Modifier.size(16.sdp())
                                    )
                                }
                            }
                        }
                    } else {
                        // --- FULL VIEW (Incoming, Dialing, Ringing, Alerting) ---

                        // Family Head
                        if (!currentState.familyHead.isNullOrEmpty()) {
                            InfoPill(
                                icon = Icons.Default.FamilyRestroom,
                                label = "Family Head",
                                value = currentState.familyHead,
                                color = Color(0xFF60A5FA) // Blue
                            )
                        }

                        // Relationship Manager
                        if (!currentState.rshipManager.isNullOrEmpty()) {
                            InfoPill(
                                icon = Icons.Default.AccountBox,
                                label = "RM",
                                value = currentState.rshipManager,
                                color = Color(0xFFC084FC) // Purple
                            )
                        }

                        // AUM
                        if (!currentState.aum.isNullOrEmpty()) {
                            InfoPill(
                                icon = Icons.Default.CurrencyRupee,
                                label = "AUM",
                                value = currentState.aum,
                                color = Color(0xFF10B981) // Green
                            )
                        }

                        // Family AUM
                        if (!currentState.familyAum.isNullOrEmpty()) {
                            InfoPill(
                                icon = Icons.Default.Money,
                                label = "Family AUM",
                                value = currentState.familyAum,
                                color = Color(0xFF10B981) // Green
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.sdp()))
        }

        // 4. Number (Personal Calls only)
        if (currentState.number.isNotEmpty() && !currentState.isConference && currentState.type != "work") {
            Text(
                text = currentState.number,
                fontSize = 18.ssp(),
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.sdp()),
                letterSpacing = 1.ssp()
            )
        }
        Spacer(modifier = Modifier.height(10.sdp()))
    }
}

// -------------------------------------------------------------
// Ringing Controls
// -------------------------------------------------------------
@Composable
private fun RingingControls(
    isPersonal: Boolean,
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    onMessageClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.sdp())
    ) {
        // Only show Message button for Personal calls
        if (isPersonal) {
            CallToggleButton(
                icon = Icons.Default.Message,
                text = "Message",
                isActive = false,
                buttonSize = 64.sdp(),
                onClick = onMessageClick
            )
        }

        SwipeableCallControl(
            onSwipeLeft = onReject,
            onSwipeRight = onAnswer
        )
    }
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
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
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
            .padding(bottom = 32.sdp()) // Reduced bottom padding
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
                            val newOffset = (offsetX.value + delta).coerceIn(
                                -maxTravelDistance,
                                maxTravelDistance
                            )
                            offsetX.snapTo(newOffset)

                            // Haptic feedback when crossing threshold
                            if (abs(newOffset) > triggerThreshold * 0.9f && abs(newOffset) < triggerThreshold * 1.1f) {
                                haptic.performHapticFeedback(
                                    HapticFeedbackType.TextHandleMove
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
                                    HapticFeedbackType.LongPress
                                )
                                vibrate(40)

                                // 1. Execute Action IMMEDIATELY
                                onSwipeRight()

                                // 2. Run Animation in Separate Scope (Non-blocking)
                                scope.launch {
                                    offsetX.animateTo(
                                        maxTravelDistance,
                                        spring(stiffness = Spring.StiffnessLow)
                                    )
                                }
                            }

                            // --- REJECT CALL ---
                            x < -triggerThreshold -> {
                                haptic.performHapticFeedback(
                                    HapticFeedbackType.LongPress
                                )
                                vibrate(40)

                                // 1. Execute Action IMMEDIATELY
                                onSwipeLeft()

                                // 2. Run Animation in Separate Scope (Non-blocking)
                                scope.launch {
                                    offsetX.animateTo(
                                        -maxTravelDistance,
                                        spring(stiffness = Spring.StiffnessLow)
                                    )
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
    buttonSize: Dp,
    hangupSize: Dp,
    verticalSpacing: Dp,
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
        verticalArrangement = Arrangement.spacedBy(verticalSpacing)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
            CallToggleButton(if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic, "Mute", state.isMuted, buttonSize, onMute)
            CallToggleButton(Icons.Default.Dialpad, "Keypad", isDialpadVisible, buttonSize, onToggleDialpad)
            CallToggleButton(Icons.Default.VolumeUp, "Speaker", state.isSpeakerOn, buttonSize, onSpeaker)
        }

        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
            CallToggleButton(Icons.Default.Pause, if (state.isHolding) "Unhold" else "Hold", state.isHolding, buttonSize, onHold)

            if (state.canMerge) {
                CallToggleButton(Icons.Default.CallMerge, "Merge", false, buttonSize, onMerge)
            } else if (state.canSwap) {
                CallToggleButton(Icons.Default.SwapCalls, "Swap", false, buttonSize, onSwap)
            } else if (state.isConference) {
                CallToggleButton(Icons.Default.Groups, "Manage", false, buttonSize, onManageConference)
            } else {
                CallToggleButton(Icons.Default.PersonAdd, "Add Call", false, buttonSize, onAddCall)
            }

            if (CallManager.isBluetoothAvailable()) {
                CallToggleButton(Icons.Default.Bluetooth, "Audio", state.isBluetoothOn, buttonSize, onBluetooth)
            } else {
                Spacer(modifier = Modifier.size(buttonSize))
            }
        }

        Spacer(modifier = Modifier.height(verticalSpacing))

        Box(Modifier.fillMaxWidth(), Alignment.Center) {
            CallActionButton(Icons.Default.CallEnd, HangupRed, hangupSize, onReject)
        }
        Spacer(modifier = Modifier.height(16.sdp()))
    }
}

@Composable
private fun CallToggleButton(
    icon: ImageVector,
    text: String,
    isActive: Boolean,
    buttonSize: Dp,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(if (isActive) ActiveButtonColor else GlassButtonColor, label = "bg")
    val contentColor by animateColorAsState(if (isActive) Color.Black else Color.White, label = "content")
    val iconSize = buttonSize * 0.44f // Dynamically scale icon

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .background(backgroundColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = Color.White),
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, text, tint = contentColor, modifier = Modifier.size(iconSize))
        }
        Spacer(modifier = Modifier.height(8.sdp()))
        Text(text, color = TextSecondary, fontSize = 13.ssp(), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CallActionButton(icon: ImageVector, backgroundColor: Color, size: Dp, onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.9f else 1f, label = "scale")
    val iconSize = size * 0.5f

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
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(iconSize))
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
            Box(Modifier
                .size(44.sdp())
                .clip(CircleShape)
                .background(Color(0xFF3A3A3C)), Alignment.Center) {
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
    keySize: Dp,
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
        verticalArrangement = Arrangement.spacedBy(16.sdp()) // Reduced spacing
    ) {
        buttons.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.sdp()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { digit ->
                    DialerKey(digit = digit, size = keySize, onClick = { onDigitClick(digit) })
                }
            }
        }
    }
}

@Composable
private fun DialerKey(digit: Char, size: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
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

//Quick Response Sheet
@Composable
fun QuickResponseSheet(
    onMessageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val messages = listOf(
        "Can't talk now. What's up?",
        "I'll call you back later.",
        "I'm in a meeting.",
        "Please text me."
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.sdp())
    ) {
        Text(
            text = "Quick Response",
            fontSize = 20.ssp(),
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(24.sdp())
        )

        LazyColumn {
            items(messages.size) { index ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMessageSelected(messages[index]) }
                        .padding(horizontal = 24.sdp(), vertical = 16.sdp()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Message, null, tint = TextSecondary, modifier = Modifier.size(20.sdp()))
                    Spacer(modifier = Modifier.width(16.sdp()))
                    Text(messages[index], fontSize = 16.ssp(), color = Color.White)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// PREVIEWS
// -------------------------------------------------------------

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun PreviewPersonalActiveCall() {
    val mockState = CallState(
        name = "Aayushman",
        number = "+91 98765 43210",
        status = "Active",
        type = "personal",
        isMuted = false,
        isSpeakerOn = true,
        isBluetoothOn = false,
        isIncoming = false,
        connectTimeMillis = System.currentTimeMillis() - 125000
    )

    InCallContent(
        currentState = mockState,
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
        onAddCall = {},
        onAnswerWaiting = {},
        onRejectWaiting = {},
        onSaveNote = {}
    )
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun PreviewWorkIncomingCall() {
    val mockState = CallState(
        name = "Rahul Sharma",
        number = "+91 11223 34455",
        status = "Ringing",
        type = "work",
        isIncoming = true,
        rshipManager = "Employee",
        familyHead = "Tech Team",
        aum = null
    )

    InCallContent(
        currentState = mockState,
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
        onAddCall = {},
        onAnswerWaiting = {},
        onRejectWaiting = {},
        onSaveNote = {}
    )
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun PreviewClientWorkCall() {
    val mockState = CallState(
        name = "Suresh Raina",
        number = "+91 55667 78899",
        status = "Active",
        type = "work",
        isIncoming = false,
        connectTimeMillis = System.currentTimeMillis() - 60000,
        rshipManager = "Amit Verma",
        familyHead = "Raina Family",
        aum = "₹5,00,000",
        familyAum = "₹20,00,000"
    )

    InCallContent(
        currentState = mockState,
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
        onAddCall = {},
        onAnswerWaiting = {},
        onRejectWaiting = {},
        onSaveNote = {}
    )
}