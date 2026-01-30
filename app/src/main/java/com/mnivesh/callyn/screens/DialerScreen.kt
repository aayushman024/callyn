package com.mnivesh.callyn.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mnivesh.callyn.CallynApplication
import com.mnivesh.callyn.components.DeviceContact
import com.mnivesh.callyn.components.DeviceNumber
import com.mnivesh.callyn.db.AppContact
import com.mnivesh.callyn.managers.AuthManager
import com.mnivesh.callyn.managers.SimManager
// Ensure these managers exist in your project or comment them out if testing
import com.mnivesh.callyn.managers.ViewLimitManager
import com.mnivesh.callyn.sheets.ModernBottomSheet
import com.mnivesh.callyn.sheets.ModernDeviceBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// --- SEARCH RESULT SEALED CLASS ---
sealed class DialerSearchResult {
    data class Work(val contact: AppContact) : DialerSearchResult()
    data class Device(val contact: DeviceContact) : DialerSearchResult()
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DialerScreen(
    onCallClick: (String, Boolean, Int?) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val application = context.applicationContext as CallynApplication

    // Auth & Work Contacts Setup
    val authManager = remember { AuthManager(context) }
    val department = remember { authManager.getDepartment() }
    val workContacts by application.repository.allContacts.collectAsState(initial = emptyList<AppContact>())

    var phoneNumber by remember { mutableStateOf("") }

    // Updated to use Sealed Class
    var searchResults by remember { mutableStateOf<List<DialerSearchResult>>(emptyList()) }

    // --- Bottom Sheet States ---
    val workSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedWorkContact by remember { mutableStateOf<AppContact?>(null) }

    val deviceSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedDeviceContact by remember { mutableStateOf<DeviceContact?>(null) }

    // SIM sheet (Manual Dial)
    var showSimSheet by remember { mutableStateOf(false) }
    val simSheetState = rememberModalBottomSheetState()

    // Sim Count State
    var isDualSim by remember { mutableStateOf(false) }

    // Check SIM Status
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                val activeSims = subManager.activeSubscriptionInfoCount
                isDualSim = activeSims > 1
            } catch (e: Exception) {
                isDualSim = false
            }
        }
    }

    // ---------------- SEARCH LOGIC ----------------
    LaunchedEffect(phoneNumber, workContacts) {
        if (phoneNumber.isEmpty()) {
            searchResults = emptyList()
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            val combinedResults = mutableListOf<DialerSearchResult>()
            val seenNumbers = mutableSetOf<String>()

            // Helper to normalize phone numbers
            fun normalizeNumber(number: String): String {
                return number.filter { it.isDigit() }.takeLast(10)
            }

            // 1. Search Work Contacts First (If Management)
            if (department == "Management" || department == "IT Desk") {
                val matches = workContacts.filter {
                    it.name.contains(phoneNumber, ignoreCase = true) ||
                            it.number.contains(phoneNumber)
                }

                matches.forEach { appContact ->
                    val normalized = normalizeNumber(appContact.number)
                    if (normalized !in seenNumbers) {
                        seenNumbers.add(normalized)
                        combinedResults.add(DialerSearchResult.Work(appContact))
                    }
                }
            }

            // 2. Search Device Contacts
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                try {
                    // Added CONTACT_ID projection to support Edit/View actions
                    val cursor = context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID
                        ),
                        "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                        arrayOf("%$phoneNumber%", "%$phoneNumber%"),
                        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
                    )

                    cursor?.use {
                        val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)

                        while (it.moveToNext()) {
                            val number = it.getString(numIdx) ?: ""
                            val normalized = normalizeNumber(number)

                            if (normalized !in seenNumbers) {
                                seenNumbers.add(normalized)

                                // Create DeviceContact object on the fly
                                val contactId = it.getLong(idIdx).toString()
                                val name = it.getString(nameIdx) ?: "Unknown"

                                val deviceContact = DeviceContact(
                                    id = contactId,
                                    name = name,
                                    numbers = listOf(DeviceNumber(number, true))
                                )
                                combinedResults.add(DialerSearchResult.Device(deviceContact))
                            }
                        }
                    }
                } catch (_: Exception) { }
            }

            // Update State - limit to top 6 results
            searchResults = combinedResults.take(6)
        }
    }

    // ---------------- CALL LOGIC ----------------
    fun initiateCall() {
        if (phoneNumber.isBlank()) return

        // 1. Check if the typed number exists in the Work Database
        val cleanInput = phoneNumber.filter { it.isDigit() }
        val isWorkMatch = workContacts.any { contact ->
            val cleanContact = contact.number.filter { it.isDigit() }
            if (cleanContact.length >= 10 && cleanInput.length >= 10) {
                cleanContact.takeLast(10) == cleanInput.takeLast(10)
            } else {
                cleanContact == cleanInput
            }
        }

        // 2. Logic: If Work Match + Work SIM -> Auto Dial. Else if Dual SIM -> Show Sheet.
        if (isWorkMatch && SimManager.workSimSlot != null) {
            onCallClick(phoneNumber, true, SimManager.workSimSlot)
        } else if (isDualSim) {
            showSimSheet = true
        } else {
            onCallClick(phoneNumber, false, null)
        }
    }

    // ---------------- UI ----------------
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 94.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // -------- SEARCH RESULTS --------
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(searchResults) { result ->
                    // Extract display data
                    val (name, number, isWork) = when(result) {
                        is DialerSearchResult.Work -> Triple(result.contact.name, result.contact.number, true)
                        is DialerSearchResult.Device -> Triple(result.contact.name, result.contact.numbers.first().number, false)
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                // 1. Save to History
                                scope.launch(Dispatchers.IO) {
                                    SearchHistoryManager.addSearch(context, number)
                                }

                                // 2. Open Sheet OR Populate Number
                                when (result) {
                                    is DialerSearchResult.Work -> {
                                        selectedWorkContact = result.contact
                                    }
                                    is DialerSearchResult.Device -> {
                                        selectedDeviceContact = result.contact
                                    }
                                }

                                // Optional: Populate the keypad too for consistency
                                phoneNumber = number
                            },
                        color = if (isWork) Color(0xFF1E293B) else Color(0xFF1A1A1A),
                        tonalElevation = if (isWork) 4.dp else 2.dp,
                        shadowElevation = if (isWork) 2.dp else 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            // Avatar
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        if (isWork) {
                                            Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF2563EB)))
                                        } else {
                                            Brush.linearGradient(listOf(Color(0xFF2D2D2D), Color(0xFF242424)))
                                        },
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = name.firstOrNull()?.uppercase() ?: "?",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }

                            Spacer(Modifier.width(14.dp))

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = name,
                                        fontSize = 16.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    // Work Badge
                                    if (isWork) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            color = Color(0xFF3B82F6).copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "Work",
                                                fontSize = 10.sp,
                                                color = Color(0xFF60A5FA),
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = number,
                                    fontSize = 14.sp,
                                    color = Color(0xFF9CA3AF)
                                )
                            }

                            Icon(
                                Icons.Filled.ChevronRight,
                                null,
                                tint = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // -------- EDITABLE NUMBER DISPLAY --------
            BasicTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                ),
                readOnly = true,
                cursorBrush = SolidColor(Color(0xFF3B82F6)),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (phoneNumber.isEmpty()) {
                            Text(
                                text = "Enter Number",
                                color = Color.Gray,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // -------- KEYPAD --------
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("*", "0", "#")
                )

                keys.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        row.forEach {
                            DialerButton(
                                label = it,
                                onClick = { phoneNumber += it },
                                onLongClick = if (it == "0") {
                                    { phoneNumber += "+" }
                                } else null
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // -------- ACTION ROW --------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    // Add Contact Button
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .shadow(
                                elevation = if (phoneNumber.isNotEmpty()) 4.dp else 0.dp,
                                shape = CircleShape,
                                ambientColor = Color(0xFF3B82F6).copy(alpha = 0.3f)
                            )
                            .background(
                                if (phoneNumber.isNotEmpty()) Color(0xFF1E293B) else Color(0xFF1A1A1A),
                                CircleShape
                            )
                            .clickable(enabled = phoneNumber.isNotEmpty()) {
                                try {
                                    val intent = Intent(Intent.ACTION_INSERT).apply {
                                        type = ContactsContract.Contacts.CONTENT_TYPE
                                        putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PersonAdd,
                            null,
                            modifier = Modifier.size(28.dp),
                            tint = if (phoneNumber.isNotEmpty()) Color(0xFF60A5FA) else Color(0xFF4A4A4A)
                        )
                    }

                    // Call Button
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = CircleShape,
                                ambientColor = Color(0xFF00C853),
                                spotColor = Color(0xFF00C853)
                            )
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF00E676), Color(0xFF00C853))
                                ),
                                CircleShape
                            )
                            .clickable(enabled = phoneNumber.isNotEmpty()) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                initiateCall()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Call,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    // Backspace Button
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .shadow(
                                elevation = if (phoneNumber.isNotEmpty()) 4.dp else 0.dp,
                                shape = CircleShape,
                                ambientColor = Color(0xFFEF4444).copy(alpha = 0.3f)
                            )
                            .background(
                                if (phoneNumber.isNotEmpty()) Color(0xFF1E293B) else Color(0xFF1A1A1A),
                                CircleShape
                            )
                            .clip(CircleShape)
                            .combinedClickable(
                                enabled = phoneNumber.isNotEmpty(),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    phoneNumber = phoneNumber.dropLast(1)
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    phoneNumber = ""
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Backspace,
                            null,
                            modifier = Modifier.size(28.dp),
                            tint = if (phoneNumber.isNotEmpty()) Color(0xFFF87171) else Color(0xFF4A4A4A)
                        )
                    }
                }
            }
        }

        // ---------------- SHEET: WORK CONTACT ----------------
        if (selectedWorkContact != null) {
            ModernBottomSheet(
                contact = selectedWorkContact!!,
                sheetState = workSheetState,
                onDismiss = { selectedWorkContact = null },
                onCall = { simSlot ->
                    // Close sheet then call
                    scope.launch { workSheetState.hide() }.invokeOnCompletion {
                        // Keep a reference to number before nulling
                        val num = selectedWorkContact?.number ?: ""
                        selectedWorkContact = null
                        onCallClick(num, true, simSlot)
                    }
                },
                isWorkContact = true,
                isDualSim = isDualSim,
                department = department,
                onRequestSubmit = {
                    // Handle request Logic here
                    Toast.makeText(context, "Request Submitted: $it", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // ---------------- SHEET: DEVICE CONTACT ----------------
        if (selectedDeviceContact != null) {
            ModernDeviceBottomSheet(
                contact = selectedDeviceContact!!,
                sheetState = deviceSheetState,
                isDualSim = isDualSim,
                onDismiss = { selectedDeviceContact = null },
                onCall = { number, simSlot ->
                    scope.launch { deviceSheetState.hide() }.invokeOnCompletion {
                        selectedDeviceContact = null
                        onCallClick(number, false, simSlot)
                    }
                }
            )
        }

        // ---------------- SHEET: SIM SELECTION (MANUAL) ----------------
        if (showSimSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSimSheet = false },
                sheetState = simSheetState,
                containerColor = Color(0xFF1E293B),
                contentColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        "Select SIM to Call",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // SIM 1 Button
                        Button(
                            onClick = {
                                scope.launch { simSheetState.hide() }.invokeOnCompletion {
                                    showSimSheet = false
                                    onCallClick(phoneNumber, false, 0)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = Color(0xFF3B82F6), spotColor = Color(0xFF3B82F6)),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 4.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.Phone, contentDescription = null)
                                Text("  SIM 1", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // SIM 2 Button
                        Button(
                            onClick = {
                                scope.launch { simSheetState.hide() }.invokeOnCompletion {
                                    showSimSheet = false
                                    onCallClick(phoneNumber, false, 1)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = Color(0xFF10B981), spotColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 4.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.Phone, contentDescription = null)
                                Text("  SIM 2", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- DIALER BUTTON ----------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialerButton(
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .size(72.dp)
            .shadow(
                elevation = 2.dp,
                shape = CircleShape,
                ambientColor = Color.White.copy(alpha = 0.05f)
            )
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF242424), Color(0xFF1A1A1A))
                ),
                CircleShape
            )
            .combinedClickable(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongClick = onLongClick?.let {
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        it()
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 32.sp, color = Color.White, fontWeight = FontWeight.Medium)
    }
}


// Extension for absoluteValue if standard lib issues (kotlin.math.absoluteValue is standard though)
val Int.absoluteValue: Int get() = if (this < 0) -this else this