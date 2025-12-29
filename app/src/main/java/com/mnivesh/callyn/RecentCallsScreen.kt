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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.core.content.ContextCompat
import com.mnivesh.callyn.db.AppContact
import com.mnivesh.callyn.db.WorkCallLog
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    var workLogs by remember { mutableStateOf<List<WorkCallLog>>(emptyList()) }
    var systemLogs by remember { mutableStateOf<List<RecentCallUiItem>>(emptyList()) }
    var allCalls by remember { mutableStateOf<List<RecentCallUiItem>>(emptyList()) }

    // Pagination States
    var isLoading by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(CallFilter.ALL) }

    // --- Bottom Sheet States ---
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedWorkContact by remember { mutableStateOf<AppContact?>(null) }
    var selectedDeviceContact by remember { mutableStateOf<DeviceContact?>(null) }
    var selectedContactHistory by remember { mutableStateOf<List<RecentCallUiItem>>(emptyList()) }

    // --- Data Fetching: Work Logs (Database) ---
    LaunchedEffect(Unit) {
        application.repository.allWorkLogs.collect { dbLogs ->
            workLogs = dbLogs
        }
    }

    // --- Data Fetching: System Logs (Pagination) ---
    val loadNextPage = {
        if (!isLoading && !endReached) {
            isLoading = true
            scope.launch(Dispatchers.Default) {
                val lastLogDate = systemLogs.lastOrNull()?.date
                val newLogs = fetchSystemCallLogs(context, limit = 50, olderThan = lastLogDate)

                withContext(Dispatchers.Main) {
                    if (newLogs.isEmpty()) {
                        endReached = true
                    } else {
                        systemLogs = systemLogs + newLogs
                    }
                    isLoading = false
                }
            }
        }
    }

    // Initial Load
    LaunchedEffect(Unit) {
        loadNextPage()
    }

    // --- Merging Logic (Background Thread) ---
    LaunchedEffect(workLogs, systemLogs) {
        withContext(Dispatchers.Default) {
            val workUiLogs = workLogs.map {
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
            val merged = (systemLogs + workUiLogs).sortedByDescending { it.date }

            withContext(Dispatchers.Main) {
                allCalls = merged
            }
        }
    }

    // --- Filtering Logic ---
    val authManager = remember { AuthManager(context) }
    val department = remember { authManager.getDepartment() }

    val filteredCalls = remember(allCalls, searchQuery, activeFilter, department) {
        allCalls.filter { call ->
            val isWorkCall = call.type == "Work"

            // Search Logic
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                val nameMatch = call.name.contains(searchQuery, ignoreCase = true)
                val numberMatch = if (isWorkCall && department != "Management") {
                    false
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
            selectedContactHistory = allCalls.filter { it.number == item.number }

            if (item.type == "Work") {
                val workContact = withContext(Dispatchers.IO) {
                    application.repository.findWorkContactByNumber(item.number.takeLast(10))
                }
                if (workContact != null) {
                    selectedWorkContact = workContact
                    sheetState.show()
                } else {
                    // [!code focus] FIX: Wrap number in DeviceNumber list
                    selectedDeviceContact = DeviceContact(
                        id = item.id,
                        name = item.name,
                        numbers = listOf(DeviceNumber(item.number, isDefault = true))
                    )
                    sheetState.show()
                }
            } else {
                // [!code focus] FIX: Wrap number in DeviceNumber list
                selectedDeviceContact = DeviceContact(
                    id = item.id,
                    name = item.name,
                    numbers = listOf(DeviceNumber(item.number, isDefault = true))
                )
                sheetState.show()
            }
        }
    }

    // --- Scroll Detection for Pagination ---
    val listState = rememberLazyListState()
    val reachedBottom by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 5
        }
    }

    LaunchedEffect(reachedBottom) {
        if (reachedBottom) {
            loadNextPage()
        }
    }

    // --- UI Structure ---
    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Fixed Background Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                    )
                )
        )

        // 2. Content Layer
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 80.sdp()),
            modifier = Modifier.fillMaxSize()
        ) {

            // --- SPACER FOR STATUS BAR ---
            item {
                Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            }

            // --- HEADER (Scrolls Away) ---
            item {
                Text(
                    text = "Recent Calls",
                    fontSize = 32.ssp(),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(top = 24.sdp(), bottom = 16.sdp(), start = 16.sdp(), end = 16.sdp())
                )
            }

            // --- SEARCH BAR (Scrolls Away) ---
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.sdp())
                        .padding(bottom = 12.sdp()),
                    placeholder = { Text("Search name or number...", color = Color.Gray, fontSize = 14.ssp()) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.sdp()),
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
            }

            // --- STICKY HEADER (Pins to Top) ---
            stickyHeader {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(vertical = 16.sdp(), horizontal = 16.sdp()),
                        horizontalArrangement = Arrangement.spacedBy(8.sdp())
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
                }
            }

            // --- EMPTY STATE ---
            if (filteredCalls.isEmpty() && !isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(400.sdp()), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.History, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.sdp()))
                            Spacer(modifier = Modifier.height(16.sdp()))
                            Text("No matching calls", color = Color.Gray, fontSize = 16.ssp())
                        }
                    }
                }
            }

            // --- LIST ITEMS ---
            items(filteredCalls, key = { it.id }) { log ->
                Box(modifier = Modifier.padding(horizontal = 16.sdp(), vertical = 6.sdp())) {
                    RecentCallItem(
                        log = log,
                        onBodyClick = { onItemClicked(log) },
                        onCallClick = { onCallClick(log.number) }
                    )
                }
            }

            // --- LOADER ITEM ---
            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.sdp()), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.sdp()), color = Color.White.copy(alpha = 0.5f))
                    }
                }
            }
        }

        // --- Bottom Sheets ---
        if (selectedWorkContact != null) {
            RecentWorkBottomSheet(
                contact = selectedWorkContact!!,
                history = selectedContactHistory,
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
                history = selectedContactHistory,
                sheetState = sheetState,
                onDismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        selectedDeviceContact = null
                    }
                },
                onCall = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        // [!code focus] FIX: Access number via object property
                        onCallClick(selectedDeviceContact!!.numbers.first().number)
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
            modifier = Modifier.padding(horizontal = 16.sdp(), vertical = 8.sdp()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else Color(0xFFEF4444),
                    modifier = Modifier.size(16.sdp())
                )
                Spacer(modifier = Modifier.width(6.sdp()))
            }
            Text(
                text = label,
                color = textColor,
                fontSize = 14.ssp(),
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
        shape = RoundedCornerShape(16.sdp()),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.sdp())
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.sdp())
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.sdp()))
            }

            Spacer(modifier = Modifier.width(16.sdp()))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = log.name.ifBlank { log.number },
                        color = if (log.isMissed) Color(0xFFEF4444) else Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.ssp(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (!log.simSlot.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.sdp()))
                        val simColor = when {
                            log.simSlot.contains("1") -> Color(0xFF10B981)
                            log.simSlot.contains("2") -> Color(0xFF60A5FA)
                            else -> Color.White.copy(alpha = 0.7f)
                        }
                        Text(
                            text = log.simSlot,
                            color = simColor,
                            fontSize = 12.ssp(),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.sdp())
                ) {
                    Text(
                        text = log.type,
                        color = tagColor,
                        fontSize = 12.ssp(),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.sdp()))
                    Text("•", color = Color.White.copy(alpha = 0.2f), fontSize = 10.ssp())
                    Spacer(modifier = Modifier.width(6.sdp()))
                    Text(
                        text = formatTime(log.date),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.ssp()
                    )
                    if (log.duration.isNotEmpty() && log.duration != "0s") {
                        Spacer(modifier = Modifier.width(6.sdp()))
                        Text("•", color = Color.White.copy(alpha = 0.2f), fontSize = 10.ssp())
                        Spacer(modifier = Modifier.width(6.sdp()))
                        Text(
                            text = log.duration,
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.ssp()
                        )
                    }
                }
            }

            IconButton(onClick = onCallClick) {
                Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(24.sdp()))
            }
        }
    }
}

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
            .padding(vertical = 8.sdp()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.sdp())
        )
        Spacer(modifier = Modifier.width(16.sdp()))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatTime(log.date),
                color = Color.White,
                fontSize = 14.ssp()
            )
            if (!log.simSlot.isNullOrBlank()) {
                Text(
                    text = log.simSlot,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.ssp()
                )
            }
        }
        Text(
            text = log.duration,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.ssp()
        )
    }
}


// --- Optimized Fetch Function (Background Thread) ---
@SuppressLint("MissingPermission")
suspend fun fetchSystemCallLogs(
    context: Context,
    limit: Int? = null,
    olderThan: Long? = null
): List<RecentCallUiItem> {
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

        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.PHONE_ACCOUNT_ID
        )

        val selection = if (olderThan != null) "${CallLog.Calls.DATE} < ?" else null
        val selectionArgs = if (olderThan != null) arrayOf(olderThan.toString()) else null

        val sortOrder = if (limit != null) {
            "${CallLog.Calls.DATE} DESC LIMIT $limit"
        } else {
            "${CallLog.Calls.DATE} DESC"
        }

        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )
            cursor?.use {
                val numberIdx = 0
                val nameIdx = 1
                val typeIdx = 2
                val dateIdx = 3
                val durationIdx = 4
                val accountIdIdx = 5

                while (it.moveToNext()) {
                    val number = it.getString(numberIdx) ?: "Unknown"
                    val name = it.getString(nameIdx) ?: "Unknown"
                    val type = it.getInt(typeIdx)
                    val date = it.getLong(dateIdx)
                    val durationSec = it.getLong(durationIdx)
                    val accountId = it.getString(accountIdIdx)
                    val simLabel = if (accountId != null) simMap[accountId] else null

                    logs.add(
                        RecentCallUiItem(
                            id = "s_${date}_${number.takeLast(4)}",
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
        .ifBlank { "?" }
}

@Composable
private fun ContactDetailRow(icon: ImageVector, label: String, value: String, labelColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.sdp())).background(Color.White.copy(alpha = 0.05f)).padding(12.sdp()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = labelColor, modifier = Modifier.size(24.sdp()))
        Spacer(modifier = Modifier.width(12.sdp()))
        Column {
            Text(label, fontSize = 12.ssp(), fontWeight = FontWeight.Medium, color = labelColor.copy(alpha = 0.8f))
            Text(value.ifBlank { "N/A" }, fontSize = 16.ssp(), fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentWorkBottomSheet(
    contact: AppContact,
    history: List<RecentCallUiItem>,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onCall: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color(0xFF1E293B), contentColor = Color.White) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.sdp(), vertical = 16.sdp()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(100.sdp()).clip(CircleShape).background(Brush.linearGradient(listOf(getColorForName(contact.name), getColorForName(contact.name).copy(alpha = 0.7f)))).shadow(8.sdp(), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(getInitials(contact.name), color = Color.White, fontSize = 36.ssp(), fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(20.sdp()))
            Text(contact.name, fontSize = 24.ssp(), fontWeight = FontWeight.Bold, color = Color.White)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.sdp())) {
                Icon(Icons.Default.BusinessCenter, null, tint = Color(0xFF60A5FA), modifier = Modifier.size(16.sdp()))
                Spacer(modifier = Modifier.width(6.sdp()))
                Text("Work Contact", fontSize = 15.ssp(), color = Color(0xFF60A5FA), fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(16.sdp()))
            ContactDetailRow(Icons.Default.CreditCard, "PAN", contact.pan, Color(0xFFFFB74D))
            Spacer(modifier = Modifier.height(8.sdp()))
            ContactDetailRow(Icons.Default.FamilyRestroom, "Family Head", contact.familyHead, Color(0xFF81C784))
            Spacer(modifier = Modifier.height(8.sdp()))
            ContactDetailRow(Icons.Default.AccountBox, "Relationship Manager", contact.rshipManager ?: "N/A", Color(0xFFC084FC))

            Spacer(modifier = Modifier.height(24.sdp()))
            Button(onClick = onCall, modifier = Modifier.fillMaxWidth().height(52.sdp()), shape = RoundedCornerShape(16.sdp()), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))) {
                Icon(Icons.Default.Call, null, modifier = Modifier.size(24.sdp()))
                Spacer(modifier = Modifier.width(12.sdp()))
                Text("Call Now", fontSize = 18.ssp(), fontWeight = FontWeight.Bold)
            }

            if (history.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.sdp()))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(16.sdp()))
                Text(
                    text = "Previous Calls",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.ssp(),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.sdp()))
                LazyColumn(modifier = Modifier.heightIn(max = 250.sdp())) {
                    items(history) { log ->
                        CallHistoryRow(log)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.sdp()))
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
    // [!code focus] Access the number property of the first element safely
    val number = contact.numbers.firstOrNull()?.number ?: ""
    var contactUri by remember { mutableStateOf<Uri?>(null) }
    val isUnknown = contactUri == null

    LaunchedEffect(number) {
        if (number.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.withAppendedPath(
                        ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                        Uri.encode(number)
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
                .padding(horizontal = 24.sdp(), vertical = 16.sdp()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(100.sdp())
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
                        .shadow(8.sdp(), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = getInitials(contact.name),
                        color = Color.White,
                        fontSize = 36.ssp(),
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!isUnknown && contactUri != null) {
                    Row(modifier = Modifier.align(Alignment.TopEnd)) {
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

            Spacer(modifier = Modifier.height(20.sdp()))

            if (!isUnknown) {
                Text(
                    text = contact.name,
                    fontSize = 24.ssp(),
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            } else {
                Text(
                    text = number,
                    fontSize = 24.ssp(),
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.sdp())
            ) {
                Icon(
                    Icons.Default.Person,
                    null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(16.sdp())
                )
                Spacer(modifier = Modifier.width(6.sdp()))
                Text(
                    "Personal Contact",
                    fontSize = 15.ssp(),
                    color = Color(0xFF10B981),
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.sdp())
            ) {
                if (!isUnknown) {
                    Text(
                        number,
                        fontSize = 16.ssp(),
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(10.sdp()))
                }

                if (isUnknown) {
                    Text(
                        "Copy Number",
                        fontSize = 14.ssp(),
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(8.sdp()))
                }

                Icon(
                    Icons.Default.ContentCopy,
                    "Copy",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(18.sdp())
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Phone Number", number)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        }
                )
            }

            if (isUnknown) {
                Spacer(modifier = Modifier.height(16.sdp()))
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_INSERT).apply {
                                type = ContactsContract.Contacts.CONTENT_TYPE
                                putExtra(ContactsContract.Intents.Insert.PHONE, number)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Unable to add contact", Toast.LENGTH_SHORT).show()
                        }
                    },
                    border = androidx.compose.foundation.BorderStroke(1.sdp(), Color(0xFF3B82F6)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF3B82F6)
                    ),
                    shape = RoundedCornerShape(12.sdp())
                ) {
                    Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.sdp()))
                    Spacer(modifier = Modifier.width(8.sdp()))
                    Text("Add to Contacts", fontSize = 14.ssp())
                }
            }

            Spacer(modifier = Modifier.height(32.sdp()))

            Button(
                onClick = onCall,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.sdp()),
                shape = RoundedCornerShape(16.sdp()),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Icon(Icons.Default.Call, null, modifier = Modifier.size(24.sdp()))
                Spacer(modifier = Modifier.width(12.sdp()))
                Text("Call Now", fontSize = 18.ssp(), fontWeight = FontWeight.Bold)
            }

            if (history.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.sdp()))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(16.sdp()))
                Text(
                    text = "Previous Calls",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.ssp(),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.sdp()))
                LazyColumn(modifier = Modifier.heightIn(max = 250.sdp())) {
                    items(history) { log ->
                        CallHistoryRow(log)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.sdp()))
        }
    }
}