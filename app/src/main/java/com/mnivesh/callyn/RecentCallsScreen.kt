package com.mnivesh.callyn

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mnivesh.callyn.db.AppContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// --- Filter Enum ---
enum class CallFilter {
    ALL, PERSONAL, WORK, MISSED
}

data class RecentCallUiItem(
    val id: String,
    val name: String,
    val number: String,
    val type: String, // "Work" or "Personal"
    val date: Long,
    val duration: String,
    val isIncoming: Boolean,
    val isMissed: Boolean = false,
    val simSlot: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentCallsScreen(
    onCallClick: (String) -> Unit,
    onScreenEntry: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as CallynApplication
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        onScreenEntry()
    }

    // --- States ---
    var allCalls by remember { mutableStateOf<List<RecentCallUiItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(CallFilter.ALL) }

    // --- Bottom Sheet States ---
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedWorkContact by remember { mutableStateOf<AppContact?>(null) }
    var selectedDeviceContact by remember { mutableStateOf<DeviceContact?>(null) }

    // [!code ++] State to hold the history of the currently selected contact
    var selectedContactHistory by remember { mutableStateOf<List<RecentCallUiItem>>(emptyList()) }

    // --- Data Fetching ---
    val workLogsFlow = application.repository.allWorkLogs

    LaunchedEffect(Unit) {
        workLogsFlow.collect { dbLogs ->
            val systemLogs = fetchSystemCallLogs(context)

            val workUiLogs = dbLogs.map {
                val isIncomingCall = it.direction.equals("incoming", ignoreCase = true) ||
                        it.direction.equals("missed", ignoreCase = true)

                RecentCallUiItem(
                    id = "w_${it.id}",
                    name = it.name,
                    number = it.number,
                    type = "Work",
                    date = it.timestamp,
                    duration = formatDuration(it.duration),
                    isIncoming = isIncomingCall,
                    isMissed = it.direction.equals("missed", ignoreCase = true),
                    simSlot = null
                )
            }
            allCalls = (systemLogs + workUiLogs).sortedByDescending { it.date }
        }
    }

    // --- Filtering Logic ---
    // --- Auth & Data Fetching ---
    val authManager = remember { AuthManager(context) }
    // [!code ++] Get Department
    val department = remember { authManager.getDepartment() }

    // --- Filtering Logic ---
    val filteredCalls = remember(allCalls, searchQuery, activeFilter, department) {
        allCalls.filter { call ->
            val isWorkCall = call.type == "Work"

            // Search Logic
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                val nameMatch = call.name.contains(searchQuery, ignoreCase = true)
                // [!code ++] Conditional Number Search
                val numberMatch = if (isWorkCall && department != "Management") {
                    false // Hide work number from search for non-management
                } else {
                    call.number.contains(searchQuery)
                }
                nameMatch || numberMatch
            }

            val matchesFilter = when (activeFilter) {
                CallFilter.ALL -> true
                CallFilter.PERSONAL -> call.type == "Personal"
                CallFilter.WORK -> call.type == "Work"
                CallFilter.MISSED -> call.isMissed
            }
            matchesSearch && matchesFilter
        }
    }

    // --- Helper: Handle Item Click ---
    fun onItemClicked(item: RecentCallUiItem) {
        scope.launch {
            // [!code ++] Filter history for the selected number
            selectedContactHistory = allCalls.filter { it.number == item.number }

            if (item.type == "Work") {
                val workContact = withContext(Dispatchers.IO) {
                    application.repository.findWorkContactByNumber(item.number.takeLast(10))
                }
                if (workContact != null) {
                    selectedWorkContact = workContact
                    sheetState.show()
                } else {
                    selectedDeviceContact = DeviceContact(item.id, item.name, item.number)
                    sheetState.show()
                }
            } else {
                selectedDeviceContact = DeviceContact(item.id, item.name, item.number)
                sheetState.show()
            }
        }
    }

    // --- UI Structure ---
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                    )
                )
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Text(
                text = "Recent Calls",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)
            )

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                placeholder = { Text("Search name or number...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF3B82F6),
                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                ),
                singleLine = true
            )

            // Quick Filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChipItem("All", activeFilter == CallFilter.ALL) { activeFilter = CallFilter.ALL }
                FilterChipItem(
                    label = "Missed",
                    isSelected = activeFilter == CallFilter.MISSED,
                    icon = Icons.Default.CallMissed,
                    onClick = { activeFilter = CallFilter.MISSED }
                )
                FilterChipItem("Personal", activeFilter == CallFilter.PERSONAL) { activeFilter = CallFilter.PERSONAL }
                FilterChipItem("Work", activeFilter == CallFilter.WORK) { activeFilter = CallFilter.WORK }
            }

            // List
            if (filteredCalls.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.History, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No matching calls", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredCalls) { log ->
                        RecentCallItem(
                            log = log,
                            onBodyClick = { onItemClicked(log) },
                            onCallClick = { onCallClick(log.number) }
                        )
                    }
                }
            }
        }

        // --- Bottom Sheets ---
        if (selectedWorkContact != null) {
            RecentWorkBottomSheet(
                contact = selectedWorkContact!!,
                history = selectedContactHistory, // [!code ++] Pass history
                sheetState = sheetState,
                onDismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        selectedWorkContact = null
                    }
                },
                onCall = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onCallClick(selectedWorkContact!!.number)
                        selectedWorkContact = null
                    }
                }
            )
        }

        if (selectedDeviceContact != null) {
            RecentDeviceBottomSheet(
                contact = selectedDeviceContact!!,
                history = selectedContactHistory, // [!code ++] Pass history
                sheetState = sheetState,
                onDismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        selectedDeviceContact = null
                    }
                },
                onCall = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onCallClick(selectedDeviceContact!!.number)
                        selectedDeviceContact = null
                    }
                }
            )
        }
    }
}

// --- UI Components ---

@Composable
fun FilterChipItem(
    label: String,
    isSelected: Boolean,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.1f)
    val textColor = if (isSelected) Color.White else Color.Gray

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(50),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else Color(0xFFEF4444),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun RecentCallItem(
    log: RecentCallUiItem,
    onBodyClick: () -> Unit,
    onCallClick: () -> Unit
) {
    val isWork = log.type.equals("Work", ignoreCase = true)
    val tagColor = if (isWork) Color(0xFF60A5FA) else Color(0xFF10B981)

    val icon = when {
        log.isMissed -> Icons.Default.CallMissed
        log.isIncoming -> Icons.Default.CallReceived
        else -> Icons.Default.CallMade
    }

    val iconTint = if (log.isMissed) Color(0xFFEF4444) else tagColor

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onBodyClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Box
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint)
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Details Column
            Column(modifier = Modifier.weight(1f)) {

                // Row 1: Name + SIM Slot (Side by Side)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = log.name.ifBlank { log.number },
                        color = if (log.isMissed) Color(0xFFEF4444) else Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (!log.simSlot.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))

                        val simColor = when {
                            log.simSlot.contains("1") -> Color(0xFF10B981)
                            log.simSlot.contains("2") -> Color(0xFF60A5FA)
                            else -> Color.White.copy(alpha = 0.7f)
                        }

                        Text(
                            text = log.simSlot,
                            color = simColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Row 2: Type • Time • Duration
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = log.type,
                        color = tagColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.width(6.dp))
                    Text("•", color = Color.White.copy(alpha = 0.2f), fontSize = 10.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatTime(log.date),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )

                    if (log.duration.isNotEmpty() && log.duration != "0s") {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("•", color = Color.White.copy(alpha = 0.2f), fontSize = 10.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = log.duration,
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            IconButton(onClick = onCallClick) {
                Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White)
            }
        }
    }
}

// [!code ++] New Composable for History Rows
@Composable
fun CallHistoryRow(log: RecentCallUiItem) {
    val icon = when {
        log.isMissed -> Icons.Default.CallMissed
        log.isIncoming -> Icons.Default.CallReceived
        else -> Icons.Default.CallMade
    }
    val iconColor = if (log.isMissed) Color(0xFFEF4444) else if (log.isIncoming) Color(0xFF10B981) else Color(0xFF60A5FA)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatTime(log.date),
                color = Color.White,
                fontSize = 14.sp
            )
            if (!log.simSlot.isNullOrBlank()) {
                Text(
                    text = log.simSlot,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
        }
        Text(
            text = log.duration,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
    }
}


@SuppressLint("MissingPermission")
suspend fun fetchSystemCallLogs(context: Context): List<RecentCallUiItem> {
    return withContext(Dispatchers.IO) {
        val logs = mutableListOf<RecentCallUiItem>()
        val simMap = mutableMapOf<String, String>()
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                val subManager = context.getSystemService(SubscriptionManager::class.java)
                val activeSims = subManager.activeSubscriptionInfoList
                activeSims?.forEach { info ->
                    simMap[info.subscriptionId.toString()] = "SIM ${info.simSlotIndex + 1}"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                null, null, null,
                "${CallLog.Calls.DATE} DESC LIMIT 50"
            )
            cursor?.use {
                val numberIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val durationIdx = it.getColumnIndex(CallLog.Calls.DURATION)
                val accountIdIdx = it.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)

                while (it.moveToNext()) {
                    val number = it.getString(numberIdx) ?: "Unknown"
                    val name = it.getString(nameIdx) ?: "Unknown"
                    val type = it.getInt(typeIdx)
                    val date = it.getLong(dateIdx)
                    val durationSec = it.getLong(durationIdx)
                    val accountId = if (accountIdIdx != -1) it.getString(accountIdIdx) else null
                    val simLabel = if (accountId != null) simMap[accountId] else null

                    logs.add(
                        RecentCallUiItem(
                            id = "s_${date}",
                            name = if (name != "Unknown") name else number,
                            number = number,
                            type = "Personal",
                            date = date,
                            duration = formatDuration(durationSec),
                            isIncoming = type == CallLog.Calls.INCOMING_TYPE || type == CallLog.Calls.MISSED_TYPE,
                            isMissed = type == CallLog.Calls.MISSED_TYPE,
                            simSlot = simLabel
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        logs
    }
}

fun formatTime(millis: Long): String {
    val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
    return sdf.format(Date(millis))
}

fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

private fun getColorForName(name: String): Color {
    val palette = listOf(
        Color(0xFF6366F1), Color(0xFFEC4899), Color(0xFF8B5CF6),
        Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFFEF4444),
        Color(0xFF3B82F6), Color(0xFF14B8A6), Color(0xFFF97316)
    )
    return palette[kotlin.math.abs(name.hashCode()) % palette.size]
}

private fun getInitials(name: String): String {
    return name.split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { name.take(1).uppercase() }
        .ifBlank { "?" } // Returns "?" if the result is still empty or blank
}

@Composable
private fun ContactDetailRow(icon: ImageVector, label: String, value: String, labelColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.05f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = labelColor, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = labelColor.copy(alpha = 0.8f))
            Text(value.ifBlank { "N/A" }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentWorkBottomSheet(
    contact: AppContact,
    history: List<RecentCallUiItem>, // [!code ++]
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onCall: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color(0xFF1E293B), contentColor = Color.White) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ... (Existing Contact Info) ...
            Box(
                modifier = Modifier.size(100.dp).clip(CircleShape).background(Brush.linearGradient(listOf(getColorForName(contact.name), getColorForName(contact.name).copy(alpha = 0.7f)))).shadow(8.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(getInitials(contact.name), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(contact.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Default.BusinessCenter, null, tint = Color(0xFF60A5FA), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Work Contact", fontSize = 15.sp, color = Color(0xFF60A5FA), fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(16.dp))
            ContactDetailRow(Icons.Default.CreditCard, "PAN", contact.pan, Color(0xFFFFB74D))
            Spacer(modifier = Modifier.height(8.dp))
            ContactDetailRow(Icons.Default.FamilyRestroom, "Family Head", contact.familyHead, Color(0xFF81C784))
            Spacer(modifier = Modifier.height(8.dp))
            ContactDetailRow(Icons.Default.AccountBox, "Relationship Manager", contact.rshipManager ?: "N/A", Color(0xFFC084FC))

            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onCall, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))) {
                Icon(Icons.Default.Call, null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Call Now", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            // [!code ++] Call History Section
            if (history.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Previous Calls",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                    items(history) { log ->
                        CallHistoryRow(log)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentDeviceBottomSheet(
    contact: DeviceContact,
    history: List<RecentCallUiItem>,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onCall: () -> Unit
) {
    val context = LocalContext.current
    val contentResolver = context.contentResolver

    // [!code ++] State to hold the Real Contact URI (required for Edit/View)
    var contactUri by remember { mutableStateOf<Uri?>(null) }

    // [!code ++] Logic: If we find a URI, it's saved. If null, it's unknown.
    val isUnknown = contactUri == null

    // [!code ++] Effect: Look up the real Contact URI using the phone number
    LaunchedEffect(contact.number) {
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(contact.number)
                )
                val cursor = contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY),
                    null, null, null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val id = it.getLong(it.getColumnIndexOrThrow(ContactsContract.PhoneLookup._ID))
                        val lookupKey = it.getString(it.getColumnIndexOrThrow(ContactsContract.PhoneLookup.LOOKUP_KEY))
                        contactUri = ContactsContract.Contacts.getLookupUri(id, lookupKey)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E293B),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // --- Header Section ---
            Box(modifier = Modifier.fillMaxWidth()) {
                // 1. Centered Avatar
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    getColorForName(contact.name),
                                    getColorForName(contact.name).copy(alpha = 0.7f)
                                )
                            )
                        )
                        .shadow(8.dp, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = getInitials(contact.name),
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 2. Top Right Actions (Only if we found a valid Contact URI)
                if (!isUnknown && contactUri != null) {
                    Row(modifier = Modifier.align(Alignment.TopEnd)) {
                        // Edit Button
                        IconButton(onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_EDIT).apply {
                                    data = contactUri
                                    putExtra("finishActivityOnSaveCompleted", true)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot edit contact", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.Edit, "Edit", tint = Color.White.copy(alpha = 0.7f))
                        }

                        // View Contact Button
                        IconButton(onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = contactUri
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot open contact", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.OpenInNew, "View", tint = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- Name Display ---
            // If unknown, the "Name" is just the number, so we handle that below in the number section
            // to avoid displaying the number twice (once as title, once as subtitle).
            if (!isUnknown) {
                Text(
                    text = contact.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            } else {
                // If unknown, use the number as the main title
                Text(
                    text = contact.number,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // --- Label ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(
                    Icons.Default.Person,
                    null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Personal Contact",
                    fontSize = 15.sp,
                    color = Color(0xFF10B981),
                    fontWeight = FontWeight.Medium
                )
            }

            // --- Number & Copy Row ---
            // We always show this row unless it's unknown (where number is already the title).
            // BUT you asked for the copy button for ALL numbers.
            // So for Unknown, we add the copy button next to the Title?
            // Better approach: Always show the number row with copy button for consistency,
            // or just add copy button to the number text.

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                // If saved, we show number here. If unknown, we show "Unknown Name" or just repeat number for clarity?
                // Let's standardise: If saved, show number. If unknown, show "Add to contacts" helper text or nothing.

                if (!isUnknown) {
                    Text(
                        contact.number,
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }

                // Copy Button (Always visible)
                // If unknown, the number is in the Title, so we need a way to copy it.
                // Let's just put the copy button here regardless.

                if (isUnknown) {
                    // If unknown, the number is big above. We can just put a "Copy Number" text button or Icon
                    // Or just render the number again small with the copy icon
                    Text(
                        "Copy Number",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Icon(
                    Icons.Default.ContentCopy,
                    "Copy",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(18.dp)
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Phone Number", contact.number)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        }
                )
            }

            // --- Add to Contact Button (Unknown Only) ---
            if (isUnknown) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_INSERT).apply {
                                type = ContactsContract.Contacts.CONTENT_TYPE
                                putExtra(ContactsContract.Intents.Insert.PHONE, contact.number)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Unable to add contact", Toast.LENGTH_SHORT).show()
                        }
                    },
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF3B82F6)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add to Contacts")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- Call Button ---
            Button(
                onClick = onCall,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Icon(Icons.Default.Call, null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Call Now", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            // --- Call History Section ---
            if (history.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Previous Calls",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                    items(history) { log ->
                        CallHistoryRow(log)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}