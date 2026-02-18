package com.mnivesh.callyn.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.core.content.ContextCompat
import com.mnivesh.callyn.CallynApplication
import com.mnivesh.callyn.components.DeviceContact
import com.mnivesh.callyn.components.DeviceNumber
import com.mnivesh.callyn.db.AppContact
import com.mnivesh.callyn.db.CrmContact // [!code ++]
import com.mnivesh.callyn.managers.AuthManager
import com.mnivesh.callyn.managers.SimManager
import com.mnivesh.callyn.sheets.CrmBottomSheet // [!code ++] 8750756516
import com.mnivesh.callyn.sheets.ModernBottomSheet
import com.mnivesh.callyn.sheets.ModernDeviceBottomSheet
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalClipboardManager // [!code ++]


// --- SEARCH RESULT SEALED CLASS ---
sealed class DialerSearchResult {
    data class Work(val contact: AppContact) : DialerSearchResult()
    data class Crm(val contact: CrmContact) : DialerSearchResult() // [!code ++]
    data class Device(val contact: DeviceContact) : DialerSearchResult()
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DialerScreen(
    onCallClick: (String, Boolean, Int?) -> Unit,
    incomingNumber: String? = null,
    onConsumeIncomingNumber: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val application = context.applicationContext as CallynApplication
    val clipboardManager = LocalClipboardManager.current // [!code ++]
    // Auth & Work Contacts Setup
    val authManager = remember { AuthManager(context) }
    val department = remember { authManager.getDepartment() }
    val workContacts by application.repository.allContacts.collectAsState(initial = emptyList())
    // [!code ++] CRM Contacts
    val crmContacts by application.repository.crmContacts.collectAsState(initial = emptyList())

    // [!code ++] Prefs for CRM
    val sharedPrefs = remember { context.getSharedPreferences("callyn_prefs", Context.MODE_PRIVATE) }
    val isCrmSearchEnabled = remember { sharedPrefs.getBoolean("pref_crm_search_enabled", false) }

    var phoneNumber by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<DialerSearchResult>>(emptyList()) }

    // --- Bottom Sheet States ---
    val workSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedWorkContact by remember { mutableStateOf<AppContact?>(null) }

    val deviceSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedDeviceContact by remember { mutableStateOf<DeviceContact?>(null) }

    // [!code ++] CRM Sheet State
    val crmSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedCrmContact by remember { mutableStateOf<CrmContact?>(null) }

    // SIM sheet (Manual Dial)
    var showSimSheet by remember { mutableStateOf(false) }
    val simSheetState = rememberModalBottomSheetState()

    var isDualSim by remember { mutableStateOf(false) }

    // --- History State ---
    var history by remember { mutableStateOf<List<RecentCallUiItem>>(emptyList()) }
    var isHistoryLoading by remember { mutableStateOf(false) }

    LaunchedEffect(incomingNumber) {
        if (!incomingNumber.isNullOrEmpty()) {
            phoneNumber = incomingNumber
            onConsumeIncomingNumber() // Clear it so it doesn't trigger again
        }
    }

    fun formatDuration(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return if (m > 0) "${m}m ${s}s" else "${s}s"
    }

    fun fetchHistory(number: String, isWork: Boolean) {
        scope.launch(Dispatchers.IO) {
            isHistoryLoading = true
            history = emptyList()

            val sanitizedQuery = number.takeLast(10).replace(Regex("[^0-9]"), "")
            val logs = mutableListOf<RecentCallUiItem>()

            if (isWork) {
                // --- WORK DB ---
                val allLogs = application.repository.allWorkLogs.first()
                val filtered = allLogs.filter {
                    it.number.replace(Regex("[^0-9]"), "").endsWith(sanitizedQuery)
                }.sortedByDescending { it.timestamp }

                filtered.forEach { workLog ->
                    val isIncoming = workLog.direction.equals("incoming", true) || workLog.direction.equals("missed", true)
                    logs.add(
                        RecentCallUiItem(
                            id = "w_hist_${workLog.id}",
                            name = workLog.name,
                            number = workLog.number,
                            type = "Work",
                            date = workLog.timestamp,
                            rawDuration = workLog.duration,
                            duration = formatDuration(workLog.duration),
                            isIncoming = isIncoming,
                            isMissed = workLog.direction.equals("missed", true),
                            simSlot = workLog.simSlot
                        )
                    )
                }
            } else {
                // --- SYSTEM LOGS ---
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        val cursor = context.contentResolver.query(
                            CallLog.Calls.CONTENT_URI,
                            arrayOf(
                                CallLog.Calls._ID,
                                CallLog.Calls.NUMBER,
                                CallLog.Calls.CACHED_NAME,
                                CallLog.Calls.TYPE,
                                CallLog.Calls.DATE,
                                CallLog.Calls.DURATION,
                                CallLog.Calls.PHONE_ACCOUNT_ID
                            ),
                            "${CallLog.Calls.NUMBER} LIKE ?",
                            arrayOf("%$sanitizedQuery%"),
                            "${CallLog.Calls.DATE} DESC"
                        )

                        cursor?.use {
                            val idIdx = it.getColumnIndex(CallLog.Calls._ID)
                            val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                            val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                            val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                            val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                            val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)
                            val accIdx = it.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)

                            while (it.moveToNext()) {
                                val type = it.getInt(typeIdx)
                                val durationSec = it.getLong(durIdx)
                                val accId = it.getString(accIdx)
                                val simLabel = if (accId?.contains("2") == true) "SIM 2" else "SIM 1"

                                logs.add(
                                    RecentCallUiItem(
                                        id = "s_${it.getLong(dateIdx)}_${it.getLong(idIdx)}",
                                        providerId = it.getLong(idIdx),
                                        name = it.getString(nameIdx) ?: "Unknown",
                                        number = it.getString(numIdx) ?: "",
                                        type = "Personal",
                                        date = it.getLong(dateIdx),
                                        rawDuration = durationSec,
                                        duration = formatDuration(durationSec),
                                        isIncoming = type == CallLog.Calls.INCOMING_TYPE || type == CallLog.Calls.MISSED_TYPE,
                                        isMissed = type == CallLog.Calls.MISSED_TYPE,
                                        simSlot = simLabel
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
            withContext(Dispatchers.Main) {
                history = logs
                isHistoryLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                isDualSim = subManager.activeSubscriptionInfoCount > 1
            } catch (e: Exception) { isDualSim = false }
        }
    }

    // ---------------- SEARCH LOGIC ----------------
    LaunchedEffect(phoneNumber, workContacts, crmContacts, isCrmSearchEnabled) { // [!code ++] Trigger on CRM changes
        if (phoneNumber.isEmpty()) {
            searchResults = emptyList()
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            val combinedResults = mutableListOf<DialerSearchResult>()
            val seenNumbers = mutableSetOf<String>()

            fun normalizeNumber(number: String): String {
                return number.filter { it.isDigit() }.takeLast(10)
            }

            // 1. Search Work Contacts
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

            // [!code ++] 2. Search CRM Contacts (If Enabled)
            if (isCrmSearchEnabled) {
                val crmMatches = crmContacts.filter {
                    it.name.contains(phoneNumber, ignoreCase = true) ||
                            it.number.contains(phoneNumber)
                }
                crmMatches.forEach { crmContact ->
                    val normalized = normalizeNumber(crmContact.number)
                    if (normalized !in seenNumbers) {
                        seenNumbers.add(normalized)
                        combinedResults.add(DialerSearchResult.Crm(crmContact))
                    }
                }
            }

            // 3. Search Device Contacts
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                try {
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

            searchResults = combinedResults.take(6)
        }
    }

    // ---------------- CALL LOGIC ----------------
    fun initiateCall() {
        if (phoneNumber.isBlank()) return

        val cleanInput = phoneNumber.filter { it.isDigit() }

        // Check Work & CRM for matches to decide SIM logic
        val isWorkMatch = workContacts.any {
            it.number.filter { c -> c.isDigit() }.endsWith(cleanInput.takeLast(10))
        } || (isCrmSearchEnabled && crmContacts.any {
            it.number.filter { c -> c.isDigit() }.endsWith(cleanInput.takeLast(10))
        })

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
                .padding(horizontal = 24.sdp())
                .padding(bottom = 94.sdp()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // -------- SEARCH RESULTS --------
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(10.sdp(), Alignment.Bottom),
                contentPadding = PaddingValues(bottom = 20.sdp())
            ) {
                items(searchResults) { result ->
                    // [!code ++] Handle CRM type
                    val (name, number, isWork, isCrm) = when(result) {
                        is DialerSearchResult.Work -> Triple(result.contact.name, result.contact.number, true).let { Quad(it.first, it.second, it.third, false) }
                        is DialerSearchResult.Crm -> Triple(result.contact.name, result.contact.number, true).let { Quad(it.first, it.second, it.third, true) }
                        is DialerSearchResult.Device -> Triple(result.contact.name, result.contact.numbers.first().number, false).let { Quad(it.first, it.second, it.third, false) }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.sdp()))
                            .clickable {
                                scope.launch(Dispatchers.IO) {
                                    SearchHistoryManager.addSearch(context, number)
                                }
                                when (result) {
                                    is DialerSearchResult.Work -> selectedWorkContact = result.contact
                                    is DialerSearchResult.Crm -> selectedCrmContact = result.contact // [!code ++]
                                    is DialerSearchResult.Device -> selectedDeviceContact = result.contact
                                }
                                phoneNumber = number
                            },
                        color = if (isWork) Color(0xFF1E293B) else Color(0xFF1A1A1A),
                        tonalElevation = if (isWork) 4.sdp() else 2.sdp(),
                        shadowElevation = if (isWork) 2.sdp() else 0.sdp()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.sdp()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Box(
                                modifier = Modifier
                                    .size(48.sdp())
                                    .background(
                                        if (isWork) {
                                            // [!code ++] Distinct gradient for CRM? Using same Blue for now, or maybe different shade.
                                            if (isCrm) Brush.linearGradient(listOf(Color(0xFF2C7BE5), Color(0xFF1E40AF)))
                                            else Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF2563EB)))
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
                                    fontSize = 18.ssp()
                                )
                            }

                            Spacer(Modifier.width(14.sdp()))

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.sdp())
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = name,
                                        fontSize = 16.ssp(),
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (isWork) {
                                        Spacer(modifier = Modifier.width(8.sdp()))
                                        Surface(
                                            color = Color(0xFF3B82F6).copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.sdp())
                                        ) {
                                            Text(
                                                text = if (isCrm) "CRM" else "Work", // [!code ++]
                                                fontSize = 10.ssp(),
                                                color = Color(0xFF60A5FA),
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.sdp(), vertical = 2.sdp())
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = number,
                                    fontSize = 14.ssp(),
                                    color = Color(0xFF9CA3AF)
                                )
                            }

                            Icon(
                                Icons.Filled.ChevronRight,
                                null,
                                tint = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.sdp())
                            )
                        }
                    }
                }
            }

            BasicTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 36.ssp(),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                ),
                readOnly = true,
                cursorBrush = SolidColor(Color(0xFF3B82F6)),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.sdp(), bottom = 14.sdp()),
                        contentAlignment = Alignment.Center
                    ) {
                        if (phoneNumber.isEmpty()) {
                            Text(
                                text = "Enter Number",
                                color = Color.Gray,
                                fontSize = 36.ssp(),
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                        innerTextField()
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .combinedClickable(
                                    onClick = {
                                        // Optional: Handle cursor positioning or focus here if needed
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val pastedText = clipboardManager.getText()?.text
                                        if (!pastedText.isNullOrBlank()) {
                                            // Sanitize: Keep only valid dialer characters
                                            val sanitized = pastedText.filter { it.isDigit() || "+*#".contains(it) }
                                            if (sanitized.isNotEmpty()) {
                                                phoneNumber += sanitized
                                                Toast.makeText(context, "Pasted", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                        )
                    }
                }
            )

            // -------- KEYPAD --------
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.sdp())
            ) {
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("*", "0", "#")
                )

                keys.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(24.sdp())) {
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

                Spacer(Modifier.height(16.sdp()))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Box(
                        modifier = Modifier
                            .size(64.sdp())
                            .shadow(
                                elevation = if (phoneNumber.isNotEmpty()) 4.sdp() else 0.sdp(),
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
                            modifier = Modifier.size(28.sdp()),
                            tint = if (phoneNumber.isNotEmpty()) Color(0xFF60A5FA) else Color(0xFF4A4A4A)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(80.sdp())
                            .shadow(
                                elevation = 8.sdp(),
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
                            modifier = Modifier.size(40.sdp())
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(64.sdp())
                            .shadow(
                                elevation = if (phoneNumber.isNotEmpty()) 4.sdp() else 0.sdp(),
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
                            modifier = Modifier.size(28.sdp()),
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
                onDismiss = { selectedWorkContact = null; history = emptyList() },
                onCall = { simSlot ->
                    scope.launch { workSheetState.hide() }.invokeOnCompletion {
                        val num = selectedWorkContact?.number ?: ""
                        selectedWorkContact = null
                        history = emptyList()
                        onCallClick(num, true, simSlot)
                    }
                },
                isWorkContact = true,
                isDualSim = isDualSim,
                department = department,
                onShowHistory = {
                    fetchHistory(selectedWorkContact!!.number, isWork = true)
                },
                history = history,
                isLoading = isHistoryLoading,
                onRequestSubmit = {
                    Toast.makeText(context, "Request Submitted: $it", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // ---------------- SHEET: CRM CONTACT [!code ++] ----------------
        if (selectedCrmContact != null) {
            CrmBottomSheet(
                contact = selectedCrmContact!!,
                sheetState = crmSheetState,
                onDismiss = { selectedCrmContact = null; history = emptyList() },
                onCall = { simSlot ->
                    scope.launch { crmSheetState.hide() }.invokeOnCompletion {
                        val num = selectedCrmContact?.number ?: ""
                        selectedCrmContact = null
                        history = emptyList()
                        onCallClick(num, true, simSlot)
                    }
                },
                isDualSim = isDualSim,
                onShowHistory = {
                    fetchHistory(selectedCrmContact!!.number, isWork = true)
                },
                history = history,
                isLoading = isHistoryLoading
            )
        }

        // ---------------- SHEET: DEVICE CONTACT ----------------
        if (selectedDeviceContact != null) {
            ModernDeviceBottomSheet(
                contact = selectedDeviceContact!!,
                sheetState = deviceSheetState,
                isDualSim = isDualSim,
                onDismiss = { selectedDeviceContact = null; history = emptyList()},
                onShowHistory = {
                    val number = selectedDeviceContact!!.numbers.firstOrNull()?.number ?: ""
                    fetchHistory(number, isWork = false)
                },
                history = history,
                isLoading = isHistoryLoading,
                onCall = { number, simSlot ->
                    scope.launch { deviceSheetState.hide() }.invokeOnCompletion {
                        selectedDeviceContact = null
                        history = emptyList()
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
                // ... (Keep existing SIM selection UI)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.sdp())
                        .padding(bottom = 32.sdp()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        "Select SIM to Call",
                        color = Color.White,
                        fontSize = 18.ssp(),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 16.sdp())
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.sdp())
                    ) {
                        Button(
                            onClick = {
                                scope.launch { simSheetState.hide() }.invokeOnCompletion {
                                    showSimSheet = false
                                    onCallClick(phoneNumber, false, 0)
                                }
                            },
                            modifier = Modifier.weight(1f).height(64.sdp()),
                            shape = RoundedCornerShape(20.sdp()),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                        ) {
                            Text("SIM 1", fontSize = 16.ssp(), fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                scope.launch { simSheetState.hide() }.invokeOnCompletion {
                                    showSimSheet = false
                                    onCallClick(phoneNumber, false, 1)
                                }
                            },
                            modifier = Modifier.weight(1f).height(64.sdp()),
                            shape = RoundedCornerShape(20.sdp()),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Text("SIM 2", fontSize = 16.ssp(), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// Helper Tuple
data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

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
            .size(72.sdp())
            .shadow(
                elevation = 2.sdp(),
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
        Text(label, fontSize = 32.ssp(), color = Color.White, fontWeight = FontWeight.Medium)
    }
}


// Extension for absoluteValue if standard lib issues (kotlin.math.absoluteValue is standard though)
val Int.absoluteValue: Int get() = if (this < 0) -this else this