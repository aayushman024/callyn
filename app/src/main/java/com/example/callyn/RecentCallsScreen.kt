package com.example.callyn

import android.annotation.SuppressLint
import android.content.Context
import android.provider.CallLog
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
import com.example.callyn.db.AppContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// --- Filter Enum ---
enum class CallFilter {
    ALL, PERSONAL, WORK
}

// Ensure DeviceContact is available (Duplicate if needed or import)

data class RecentCallUiItem(
    val id: String,
    val name: String,
    val number: String,
    val type: String, // "Work" or "Personal"
    val date: Long,
    val duration: String,
    val isIncoming: Boolean,
    val isMissed: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentCallsScreen(
    onCallClick: (String) -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as CallynApplication
    val scope = rememberCoroutineScope()

    // --- States ---
    var allCalls by remember { mutableStateOf<List<RecentCallUiItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(CallFilter.ALL) }

    // --- Bottom Sheet States ---
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedWorkContact by remember { mutableStateOf<AppContact?>(null) }
    var selectedDeviceContact by remember { mutableStateOf<DeviceContact?>(null) }

    // --- Data Fetching ---
    val workLogsFlow = application.repository.allWorkLogs

    LaunchedEffect(Unit) {
        workLogsFlow.collect { dbLogs ->
            val systemLogs = fetchSystemCallLogs(context)

            val workUiLogs = dbLogs.map {
                RecentCallUiItem(
                    id = "w_${it.id}",
                    name = it.name,
                    number = it.number,
                    type = "Work",
                    date = it.timestamp,
                    duration = formatDuration(it.duration),
                    isIncoming = false
                )
            }
            allCalls = (systemLogs + workUiLogs).sortedByDescending { it.date }
        }
    }

    // --- Filtering Logic ---
    val filteredCalls = remember(allCalls, searchQuery, activeFilter) {
        allCalls.filter { call ->
            val matchesSearch = searchQuery.isBlank() ||
                    call.name.contains(searchQuery, ignoreCase = true) ||
                    call.number.contains(searchQuery)

            val matchesFilter = when (activeFilter) {
                CallFilter.ALL -> true
                CallFilter.PERSONAL -> call.type == "Personal"
                CallFilter.WORK -> call.type == "Work"
            }
            matchesSearch && matchesFilter
        }
    }

    // --- Helper: Handle Item Click ---
    fun onItemClicked(item: RecentCallUiItem) {
        scope.launch {
            if (item.type == "Work") {
                // Fetch full work contact details from DB
                val workContact = withContext(Dispatchers.IO) {
                    // Try to find by number (normalized)
                    application.repository.findWorkContactByNumber(item.number.takeLast(10))
                }
                if (workContact != null) {
                    selectedWorkContact = workContact
                    sheetState.show()
                } else {
                    // Fallback if not found in DB (shouldn't happen for logged work calls)
                    onCallClick(item.number)
                }
            } else {
                // Personal Contact
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
                            onBodyClick = { onItemClicked(log) }, // Opens Sheet
                            onCallClick = { onCallClick(log.number) } // Calls Directly
                        )
                    }
                }
            }
        }

        // --- Bottom Sheets ---
        if (selectedWorkContact != null) {
            RecentWorkBottomSheet(
                contact = selectedWorkContact!!,
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
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.1f)
    val textColor = if (isSelected) Color.White else Color.Gray

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(50),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun RecentCallItem(
    log: RecentCallUiItem,
    onBodyClick: () -> Unit,
    onCallClick: () -> Unit
) {
    val isWork = log.type == "Work"
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
            .clickable { onBodyClick() }, // Body click opens sheet
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.name.ifBlank { log.number },
                    color = if (log.isMissed) Color(0xFFEF4444) else Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = log.type,
                        color = tagColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatTime(log.date),
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    if (log.duration.isNotEmpty() && log.duration != "0s") {
                        Text(
                            text = " • ${log.duration}",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Call Button (Calls directly)
            IconButton(onClick = onCallClick) {
                Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White)
            }
        }
    }
}

// --- Helpers ---

@SuppressLint("MissingPermission")
suspend fun fetchSystemCallLogs(context: Context): List<RecentCallUiItem> {
    return withContext(Dispatchers.IO) {
        val logs = mutableListOf<RecentCallUiItem>()
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

                while (it.moveToNext()) {
                    val number = it.getString(numberIdx) ?: "Unknown"
                    val name = it.getString(nameIdx) ?: "Unknown"
                    val type = it.getInt(typeIdx)
                    val date = it.getLong(dateIdx)
                    val durationSec = it.getLong(durationIdx)

                    logs.add(
                        RecentCallUiItem(
                            id = "s_${date}",
                            name = if (name != "Unknown") name else number,
                            number = number,
                            type = "Personal",
                            date = date,
                            duration = formatDuration(durationSec),
                            isIncoming = type == CallLog.Calls.INCOMING_TYPE || type == CallLog.Calls.MISSED_TYPE,
                            isMissed = type == CallLog.Calls.MISSED_TYPE
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

// --- DUPLICATED BOTTOM SHEET HELPERS (To ensure they work here) ---

private fun getColorForName(name: String): Color {
    val palette = listOf(
        Color(0xFF6366F1), Color(0xFFEC4899), Color(0xFF8B5CF6),
        Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFFEF4444),
        Color(0xFF3B82F6), Color(0xFF14B8A6), Color(0xFFF97316)
    )
    return palette[kotlin.math.abs(name.hashCode()) % palette.size]
}

private fun getInitials(name: String): String {
    return name.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").ifEmpty { name.take(1).uppercase() }
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
private fun RecentWorkBottomSheet(contact: AppContact, sheetState: SheetState, onDismiss: () -> Unit, onCall: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color(0xFF1E293B), contentColor = Color.White) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onCall, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))) {
                Icon(Icons.Default.Call, null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Call Now", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentDeviceBottomSheet(contact: DeviceContact, sheetState: SheetState, onDismiss: () -> Unit, onCall: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color(0xFF1E293B), contentColor = Color.White) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(100.dp).clip(CircleShape).background(Brush.linearGradient(listOf(getColorForName(contact.name), getColorForName(contact.name).copy(alpha = 0.7f)))).shadow(8.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(getInitials(contact.name), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(contact.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Default.Person, null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Personal Contact", fontSize = 15.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Medium)
            }
            Text(contact.number, fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp))
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onCall, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))) {
                Icon(Icons.Default.Call, null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Call Now", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}