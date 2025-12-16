package com.example.callyn

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.telephony.SubscriptionManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch

// --- Data Class to hold SIM info safely ---
data class SimSlot(
    val subId: Int,          // Subscription ID (Needed for calling)
    val slotIndex: Int,      // 0 or 1
    val carrierName: String, // "Jio", "Airtel"
    val number: String       // Optional: Display number
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DialerScreen(
    // Updated callback: Returns Number + Optional SimSlot (null = default)
    onCallClick: (String, SimSlot?) -> Unit
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // State
    var phoneNumber by remember { mutableStateOf("") }
    var availableSims by remember { mutableStateOf<List<SimSlot>>(emptyList()) }

    // Sheets
    var showContactSheet by remember { mutableStateOf(false) }
    var showSimSheet by remember { mutableStateOf(false) }
    val simSheetState = rememberModalBottomSheetState()
    val contactSheetState = rememberModalBottomSheetState()

    // --- 1. Load SIMs Helper ---
    fun refreshSims() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                val activeSims = subManager.activeSubscriptionInfoList
                if (activeSims != null) {
                    availableSims = activeSims.map {
                        SimSlot(it.subscriptionId, it.simSlotIndex, it.displayName.toString(), it.number ?: "")
                    }
                }
            } catch (e: Exception) {
                availableSims = emptyList()
            }
        }
    }

    // Load SIMs on Start and Resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshSims()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        refreshSims() // Initial load
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // --- 2. Handle Call Logic ---
    fun initiateCall() {
        if (phoneNumber.isBlank()) return

        if (availableSims.size > 1) {
            // If Dual SIM -> Show Popup
            showSimSheet = true
        } else {
            // If Single SIM or No SIM detected -> Call directly (Default)
            onCallClick(phoneNumber, availableSims.firstOrNull())
        }
    }

    val buttons = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("*", "0", "#")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(horizontal = 24.dp)
            .padding(bottom = 80.dp) // Space for nav bar
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Display Area ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = phoneNumber.ifEmpty { "Enter Number" },
                color = if (phoneNumber.isEmpty()) Color.Gray else Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // --- Keypad ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            buttons.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEach { label ->
                        DialerButton(
                            label = label,
                            onClick = { phoneNumber += label },
                            onLongClick = if (label == "0") { { phoneNumber += "+" } } else null
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Bottom Control Row ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Add Contact
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .clickable(enabled = phoneNumber.isNotEmpty()) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showContactSheet = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PersonAdd,
                        contentDescription = "Add Contact",
                        tint = if (phoneNumber.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Center: Call Button (One Button Only)
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color(0xFF00C853), CircleShape)
                        .clickable(enabled = phoneNumber.isNotEmpty()) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            initiateCall()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = "Call",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Right: Backspace
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            enabled = phoneNumber.isNotEmpty(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (phoneNumber.isNotEmpty()) phoneNumber = phoneNumber.dropLast(1)
                            },
                            onLongClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                phoneNumber = ""
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Backspace",
                        tint = if (phoneNumber.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }

    // --- SIM SELECTION SHEET ---
    if (showSimSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSimSheet = false },
            sheetState = simSheetState,
            containerColor = Color(0xFF242424)
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    text = "Call using",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )

                availableSims.forEach { sim ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // User Selected a SIM -> Call
                                scope.launch { simSheetState.hide() }.invokeOnCompletion {
                                    showSimSheet = false
                                    onCallClick(phoneNumber, sim)
                                }
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.SimCard, null, tint = Color(0xFF00C853))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(sim.carrierName, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Sim ${sim.slotIndex + 1}", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                    Divider(color = Color.Gray.copy(alpha = 0.2f))
                }
            }
        }
    }

    // --- ADD CONTACT SHEET ---
    if (showContactSheet) {
        var contactName by remember { mutableStateOf("") }
        ModalBottomSheet(
            onDismissRequest = { showContactSheet = false },
            sheetState = contactSheetState,
            containerColor = Color(0xFF242424)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text("Save Contact", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = contactName,
                    onValueChange = { contactName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00C853),
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                            type = ContactsContract.RawContacts.CONTENT_TYPE
                            putExtra(ContactsContract.Intents.Insert.NAME, contactName)
                            putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
                        }
                        try { context.startActivity(intent) } catch (e: Exception) {}
                        scope.launch { contactSheetState.hide() }.invokeOnCompletion { showContactSheet = false }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save")
                }
            }
        }
    }
}

// Helper Composable for Buttons
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialerButton(label: String, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val hapticFeedback = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(72.dp)
            .background(Color(0xFF1E1E1E), CircleShape)
            .combinedClickable(
                onClick = { hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
                onLongClick = onLongClick?.let { longClick -> { hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress); longClick() } }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, fontSize = 32.sp, fontWeight = FontWeight.Medium, color = Color.White)
    }
}