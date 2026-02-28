package com.mnivesh.callyn.screens

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.CallMissed
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mnivesh.callyn.viewmodels.CallLogsUiState
import com.mnivesh.callyn.viewmodels.CallLogsViewModel
import com.mnivesh.callyn.api.CallLogResponse
import com.mnivesh.callyn.managers.AuthManager
import java.text.SimpleDateFormat
import java.util.*

// --- Color Palette ---
val BackgroundColor = Color(0xFF0F172A)
val CardColor = Color(0xFF1E293B)
val PrimaryColor = Color(0xFF3B82F6)
val SuccessColor = Color(0xFF10B981)
val ErrorColor = Color(0xFF2196F3)
val SubtextColor = Color(0xFF94A3B8)
val NotesHighVisColor = Color(0xFFFFB74D)
val NotesPopupBg = Color(0xFF334155)

// RM Pill Colors
val RolePillBg = Color(0xFF6366F1).copy(alpha = 0.2f)
val RolePillText = Color(0xFFC7D2FE)

// Personal Card Colors (Subtle)
val PersonalSubtleBg = Color(0xFF132522)
val PersonalBorder = Color(0xFF10B981)

// 1. MAIN SCREEN (Stateful)
@Composable
fun ShowCallLogsScreen(
    onNavigateBack: () -> Unit,
    viewModel: CallLogsViewModel = viewModel()
) {
    val context = LocalContext.current
    val authManager = remember { AuthManager(context) }
    val token = remember { authManager.getToken() }

    // --- Role Logic ---
    val department = remember { authManager.getDepartment() ?: "" }
    val currentUsername = remember { authManager.getUserName() ?: "" }

    // Define "Power Users"
    val isPowerUser = remember(department) {
        department.equals("Management", ignoreCase = true) ||
                department.equals("IT Desk", ignoreCase = true)
    }

    // State
    var selectedUser by remember { mutableStateOf(if (isPowerUser) "" else currentUsername) }

    // [!code focus] Updated Date State to Range
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }

    // Notes: Power User default false. Standard User default true (hidden).
    var showNotesOnly by remember { mutableStateOf(!isPowerUser) }
    var showUserDropdown by remember { mutableStateOf(false) }

    // [!code focus] Date Picker Logic (Handles both Start and End)
    val calendar = Calendar.getInstance()
    var isPickingStartDate by remember { mutableStateOf(true) }

    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, day ->
            val cal = Calendar.getInstance()
            cal.set(year, month, day)
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val newDate = format.format(cal.time)

            if (isPickingStartDate) {
                // Validate: Start cannot be after End
                if (endDate.isNotEmpty() && newDate > endDate) {
                    Toast.makeText(context, "Start date cannot be after end date", Toast.LENGTH_SHORT).show()
                } else {
                    startDate = newDate
                }
            } else {
                // Validate: End cannot be before Start
                if (startDate.isNotEmpty() && newDate < startDate) {
                    Toast.makeText(context, "End date cannot be before start date", Toast.LENGTH_SHORT).show()
                } else {
                    endDate = newDate
                }
            }
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )
    datePickerDialog.datePicker.maxDate = System.currentTimeMillis()

    fun formatDurationCompact(seconds: Int): String {
        if (seconds == 0) return "0m"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    // Initial Data Load
    LaunchedEffect(Unit) {
        if (token != null) {
            if (isPowerUser) {
                viewModel.fetchUserNames(token)
            } else {
                // [!code focus] Updated search params
                viewModel.searchLogs(
                    token = token,
                    uploadedBy = currentUsername,
                    startDate = null,
                    endDate = null,
                    showNotes = true
                )
            }
        }
    }

    // Render Content
    CallLogsContent(
        uiState = viewModel.uiState,
        userList = viewModel.userNamesList,
        selectedUser = selectedUser,
        startDate = startDate,
        endDate = endDate,
        showNotesOnly = showNotesOnly,
        showDropdown = showUserDropdown,
        isPowerUser = isPowerUser,
        onNavigateBack = onNavigateBack,
        onToggleDropdown = { showUserDropdown = it },
        onUserSelected = { selectedUser = it },
        // [!code focus] Updated Date Click Handlers
        onStartDateClick = {
            isPickingStartDate = true
            datePickerDialog.show()
        },
        onEndDateClick = {
            isPickingStartDate = false
            datePickerDialog.show()
        },
        onClearDate = {
            startDate = ""
            endDate = ""
        },
        onToggleShowNotes = { showNotesOnly = it },
        onSearch = {
            if (token != null) {
                val searchUser = if (isPowerUser) selectedUser else currentUsername
                val searchNotes = if (isPowerUser) showNotesOnly else true

                // [!code focus] Pass new range params to ViewModel
                viewModel.searchLogs(
                    token = token,
                    uploadedBy = searchUser,
                    startDate = startDate.ifBlank { null },
                    endDate = endDate.ifBlank { null },
                    showNotes = searchNotes
                )
            }
        }
    )
}

// 2. UI CONTENT (Stateless)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogsContent(
    uiState: CallLogsUiState,
    userList: List<String>,
    selectedUser: String,
    startDate: String,
    endDate: String,
    showNotesOnly: Boolean,
    showDropdown: Boolean,
    isPowerUser: Boolean,
    onNavigateBack: () -> Unit,
    onToggleDropdown: (Boolean) -> Unit,
    onUserSelected: (String) -> Unit,
    onStartDateClick: () -> Unit,
    onEndDateClick: () -> Unit,
    onClearDate: () -> Unit,
    onToggleShowNotes: (Boolean) -> Unit,
    onSearch: () -> Unit
) {
    val isUserListReady = userList.isNotEmpty()
    var selectedTypeFilter by remember { mutableStateOf("All") }

    // 1. Define Filtered Logs
    val filteredLogs = remember(uiState, selectedTypeFilter) {
        if (uiState is CallLogsUiState.Success) {
            when (selectedTypeFilter) {
                "All" -> uiState.logs
                "Work" -> uiState.logs.filter { it.isWork }
                "Personal" -> uiState.logs.filter { !it.isWork }
                else -> uiState.logs.filter { it.type.equals(selectedTypeFilter, ignoreCase = true) }
            }
        } else emptyList()
    }

    // 2. [FIX] Calculate Durations HERE (Top Level), not inside LazyColumn
    val (workDuration, personalDuration) = remember(filteredLogs) {
        val work = filteredLogs.filter { it.isWork != false }.sumOf { it.duration }
        val personal = filteredLogs.filter { it.isWork == false }.sumOf { it.duration }
        Pair(work, personal)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Call History", fontWeight = FontWeight.Bold, fontSize = 20.ssp()) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundColor,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = BackgroundColor
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.sdp())
        ) {
            // Section 1: Main Filters
            item {
                FilterSection(
                    selectedUser = selectedUser,
                    startDate = startDate,
                    endDate = endDate,
                    showNotesOnly = showNotesOnly,
                    isUserListReady = isUserListReady,
                    userList = userList,
                    showDropdown = showDropdown,
                    isPowerUser = isPowerUser,
                    onToggleDropdown = onToggleDropdown,
                    onUserSelected = onUserSelected,
                    onStartDateClick = onStartDateClick,
                    onEndDateClick = onEndDateClick,
                    onClearDate = onClearDate,
                    onToggleShowNotes = onToggleShowNotes,
                    onSearch = onSearch
                )
            }

            // Section 2: Quick Type Filters
            if (uiState is CallLogsUiState.Success) {
                item {
                    QuickFilterRow(
                        selectedFilter = selectedTypeFilter,
                        onFilterSelected = { selectedTypeFilter = it }
                    )
                }
            }

            // Section 3: Results & Stats
            when (uiState) {
                is CallLogsUiState.Idle -> item { EmptyStateMessage("Adjust filters to view logs.") }
                is CallLogsUiState.Loading -> item {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.sdp()), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PrimaryColor)
                    }
                }
                is CallLogsUiState.Error -> item { EmptyStateMessage("Error: ${uiState.message}", isError = true) }
                is CallLogsUiState.Success -> {
                    if (filteredLogs.isEmpty()) {
                        item { EmptyStateMessage("No logs found.") }
                    } else {
                        // Header Row with Stats
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.sdp(), vertical = 8.sdp()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Record Count
                                Text(
                                    text = "Found ${filteredLogs.size} records",
                                    color = SubtextColor,
                                    fontSize = 13.ssp()
                                )

                                // Duration Statistics
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.sdp())
                                ) {
                                    // Work Stats
                                    if (workDuration > 0) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Work,
                                                contentDescription = "Work Duration",
                                                tint = SubtextColor,
                                                modifier = Modifier.size(14.sdp())
                                            )
                                            Spacer(modifier = Modifier.width(4.sdp()))
                                            Text(
                                                text = formatDurationCompact(workDuration),
                                                color = SubtextColor,
                                                fontSize = 12.ssp(),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    // Personal Stats
                                    if (personalDuration > 0) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = "Personal Duration",
                                                tint = SubtextColor,
                                                modifier = Modifier.size(14.sdp())
                                            )
                                            Spacer(modifier = Modifier.width(4.sdp()))
                                            Text(
                                                text = formatDurationCompact(personalDuration),
                                                color = SubtextColor,
                                                fontSize = 12.ssp(),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // List Items
                        items(filteredLogs) { log ->
                            if (isPowerUser) {
                                ManagementCallLogCard(log)
                            } else {
                                StandardCallLogCard(log)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Helper for Stats formatting ---
// Example Output: "1h 20m" or "45m"
private fun formatDurationCompact(seconds: Long): String {
    if (seconds == 0L) return "0s"

    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60

    return when {
        h > 0 -> "${h}h ${m}m ${s}s"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

// --- FILTER SECTION ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSection(
    selectedUser: String,
    startDate: String,
    endDate: String,
    showNotesOnly: Boolean,
    isUserListReady: Boolean,
    userList: List<String>,
    showDropdown: Boolean,
    isPowerUser: Boolean,
    onToggleDropdown: (Boolean) -> Unit,
    onUserSelected: (String) -> Unit,
    onStartDateClick: () -> Unit,
    onEndDateClick: () -> Unit,
    onClearDate: () -> Unit,
    onToggleShowNotes: (Boolean) -> Unit,
    onSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.sdp())
            .clip(RoundedCornerShape(24.sdp()))
            .background(CardColor)
            .padding(16.sdp())
    ) {
        if (isPowerUser) {
            Text("Filter Logs", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.ssp())
            Spacer(modifier = Modifier.height(16.sdp()))

            // Username Dropdown
            ExposedDropdownMenuBox(
                expanded = showDropdown && isUserListReady,
                onExpandedChange = { if (isUserListReady) onToggleDropdown(!showDropdown) }
            ) {
                OutlinedTextField(
                    value = selectedUser,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(if (isUserListReady) "Team Member" else "Loading...") },
                    placeholder = { Text("Select User") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDropdown) },
                    colors = filterTextFieldColors(),
                    shape = RoundedCornerShape(12.sdp()),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = showDropdown,
                    onDismissRequest = { onToggleDropdown(false) },
                    modifier = Modifier.background(CardColor)
                ) {
                    DropdownMenuItem(
                        text = { Text("All Members", color = PrimaryColor) },
                        onClick = { onUserSelected(""); onToggleDropdown(false) }
                    )
                    userList.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(name, color = Color.White) },
                            onClick = { onUserSelected(name); onToggleDropdown(false) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.sdp()))
        } else {
            Text("My Log Book", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.ssp())
            Spacer(modifier = Modifier.height(16.sdp()))
        }

        // --- UPDATED DATE FILTER ROW (START / END) ---
        // [!code focus] Split into two columns
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.sdp()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Start Date Field
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = startDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("From") },
                    placeholder = { Text("Start") },
                    colors = filterTextFieldColors(),
                    shape = RoundedCornerShape(12.sdp()),
                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(12.sdp()))
                        .clickable { onStartDateClick() }
                )
            }

            // End Date Field
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = endDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("To") },
                    placeholder = { Text("End") },
                    colors = filterTextFieldColors(),
                    shape = RoundedCornerShape(12.sdp()),
                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(12.sdp()))
                        .clickable { onEndDateClick() }
                )
            }

            // Clear Button (Only shows if dates are selected)
            if (startDate.isNotEmpty() || endDate.isNotEmpty()) {
                IconButton(onClick = onClearDate) {
                    Icon(Icons.Default.Close, null, tint = ErrorColor)
                }
            } else {
                // Placeholder to keep alignment if needed, or just standard icon
                Icon(Icons.Default.DateRange, null, tint = SubtextColor, modifier = Modifier.padding(start = 4.sdp()))
            }
        }
        // -------------------------------

        Spacer(modifier = Modifier.height(6.sdp()))
        Text(
            text = "Leave dates blank to view today's logs",
            color = SubtextColor.copy(alpha = 0.9f),
            fontSize = 12.ssp(),
            modifier = Modifier.padding(start = 4.sdp())
        )

        Spacer(modifier = Modifier.height(20.sdp()))

        if (isPowerUser) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.sdp(), horizontal = 8.sdp()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Show calls with notes",
                    color = Color.White,
                    fontSize = 15.ssp(),
                    fontWeight = FontWeight.Medium
                )
                Switch(
                    checked = showNotesOnly,
                    onCheckedChange = onToggleShowNotes,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PrimaryColor,
                        uncheckedThumbColor = SubtextColor,
                        uncheckedTrackColor = BackgroundColor
                    )
                )
            }
            Spacer(modifier = Modifier.height(16.sdp()))
        }

        Button(
            onClick = onSearch,
            modifier = Modifier.fillMaxWidth().height(50.sdp()),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
            shape = RoundedCornerShape(12.sdp())
        ) {
            Icon(Icons.Default.Search, null, modifier = Modifier.size(20.sdp()))
            Spacer(modifier = Modifier.width(8.sdp()))
            Text("Show Results", fontSize = 16.ssp(), fontWeight = FontWeight.Bold)
        }
    }
}

// ... (Rest of UI components: ManagementCallLogCard, StandardCallLogCard, etc. remain unchanged) ...

@Composable
fun ManagementCallLogCard(log: CallLogResponse) {
    val isPersonal = (log.isWork == false)
    val hasNotes = !log.notes.isNullOrBlank()
    var showNotesPopup by remember { mutableStateOf(false) }

    val cardBackground = if (isPersonal) PersonalSubtleBg else CardColor
    val cardBorder = if (isPersonal) BorderStroke(1.sdp(), PersonalBorder.copy(alpha = 0.3f)) else null

    val (typeIcon, typeColor, typeBg) = getCallTypeStyles(log.type)
    val dateDisplay = remember(log.timestamp) { formatPrettyDate(log.timestamp) }

    Card(
        modifier = Modifier.padding(horizontal = 16.sdp(), vertical = 6.sdp()).fillMaxWidth(),
        shape = RoundedCornerShape(16.sdp()),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(2.sdp())
    ) {
        Column(modifier = Modifier.padding(16.sdp())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.sdp()).clip(CircleShape).background(typeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(typeIcon, null, tint = typeColor, modifier = Modifier.size(24.sdp()))
                }
                Spacer(modifier = Modifier.width(16.sdp()))

                Column(modifier = Modifier.weight(1f)) {
                    if (isPersonal) {
                        Text("Personal Call", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.ssp())
                    } else {
                        Text(
                            text = log.callerName.ifBlank { "Unknown Caller" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.ssp(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.sdp()))

                        // Tags
                        Column(verticalArrangement = Arrangement.spacedBy(6.sdp())) {
                            renderTags(log)

                            // View Notes Button (Popup)
                            if (hasNotes) {
                                Spacer(modifier = Modifier.height(8.sdp()))
                                Box {
                                    OutlinedButton(
                                        onClick = { showNotesPopup = true },
                                        border = BorderStroke(1.sdp(), NotesHighVisColor),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NotesHighVisColor),
                                        contentPadding = PaddingValues(horizontal = 12.sdp(), vertical = 0.sdp()),
                                        modifier = Modifier.height(32.sdp())
                                    ) {
                                        Icon(Icons.Default.ChatBubbleOutline, null, modifier = Modifier.size(14.sdp()))
                                        Spacer(modifier = Modifier.width(6.sdp()))
                                        Text("View Notes", fontSize = 12.ssp(), fontWeight = FontWeight.Bold)
                                    }
                                    if (showNotesPopup) {
                                        NotesPopup(log.notes ?: "", onDismiss = { showNotesPopup = false })
                                    }
                                }
                            }
                        }
                    }
                }
                // Date Top Right
                Column(horizontalAlignment = Alignment.End) {
                    Text(dateDisplay, color = SubtextColor, fontSize = 12.ssp(), fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(16.sdp()))
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(12.sdp()))

            // Bottom Row (Includes Uploaded By)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudUpload, null, tint = SubtextColor, modifier = Modifier.size(14.sdp()))
                    Spacer(modifier = Modifier.width(6.sdp()))
                    Text(
                        text = log.uploadedBy ?: "System",
                        color = SubtextColor,
                        fontSize = 12.ssp(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 100.sdp())
                    )
                }
                SimAndDurationRow(log)
            }
        }
    }
}

@Composable
fun StandardCallLogCard(log: CallLogResponse) {
    val isPersonal = (log.isWork == false)
    val hasNotes = !log.notes.isNullOrBlank()

    val cardBackground = if (isPersonal) PersonalSubtleBg else CardColor
    val cardBorder = if (isPersonal) BorderStroke(1.sdp(), PersonalBorder.copy(alpha = 0.3f)) else null
    val (typeIcon, typeColor, typeBg) = getCallTypeStyles(log.type)
    val dateDisplay = remember(log.timestamp) { formatPrettyDate(log.timestamp) }

    Card(
        modifier = Modifier.padding(horizontal = 16.sdp(), vertical = 6.sdp()).fillMaxWidth(),
        shape = RoundedCornerShape(16.sdp()),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(2.sdp())
    ) {
        Column(modifier = Modifier.padding(16.sdp())) {
            // Top Section
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier.size(48.sdp()).clip(CircleShape).background(typeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(typeIcon, null, tint = typeColor, modifier = Modifier.size(24.sdp()))
                }
                Spacer(modifier = Modifier.width(16.sdp()))

                Column(modifier = Modifier.weight(1f)) {
                    if (isPersonal) {
                        Text("Personal Call", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.ssp())
                    } else {
                        Text(
                            text = log.callerName.ifBlank { "Unknown Caller" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.ssp(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.sdp()))
                        Column(verticalArrangement = Arrangement.spacedBy(6.sdp())) {
                            renderTags(log)
                        }
                    }
                }
                Text(dateDisplay, color = SubtextColor, fontSize = 12.ssp(), fontWeight = FontWeight.Medium)
            }

            // Inline Notes (Standard Feature)
            if (hasNotes) {
                Spacer(modifier = Modifier.height(16.sdp()))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF334155).copy(alpha = 0.5f), RoundedCornerShape(8.sdp()))
                        .border(1.sdp(), NotesHighVisColor.copy(alpha = 0.3f), RoundedCornerShape(8.sdp()))
                        .padding(12.sdp())
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ChatBubbleOutline, null, tint = NotesHighVisColor, modifier = Modifier.size(12.sdp()))
                            Spacer(modifier = Modifier.width(6.sdp()))
                            Text("CALL NOTES", color = NotesHighVisColor, fontSize = 11.ssp(), fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.sdp()))
                        Text(
                            text = log.notes ?: "",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.ssp(),
                            lineHeight = 20.ssp()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.sdp()))
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(12.sdp()))

            // Bottom Row (No Uploaded By)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End // Align everything to end
            ) {
                SimAndDurationRow(log)
            }
        }
    }
}

// ... (Helpers: renderTags, SimAndDurationRow, NotesPopup, getCallTypeStyles, ContainerPill, EmptyStateMessage, formatDuration, formatPrettyDate, QuickFilterRow, FilterChip remain unchanged) ...

@Composable
fun renderTags(log: CallLogResponse) {
    val isEmployee = log.rshipManagerName?.equals("Employee", ignoreCase = true) == true
    if (isEmployee) {
        ContainerPill("Employee", RolePillText, RolePillBg)
        if (!log.familyHead.isNullOrBlank()) {
            ContainerPill("Dept: ${log.familyHead}", Color(0xFF60A5FA), Color(0xFF60A5FA).copy(alpha = 0.15f))
        }
    } else {
        ContainerPill("RM: ${log.rshipManagerName ?: "-"}", RolePillText, RolePillBg)
        if (!log.familyHead.isNullOrBlank()) {
            ContainerPill("FH: ${log.familyHead}", Color(0xFFFCD34D), Color(0xFFFCD34D).copy(alpha = 0.15f))
        }
    }
}

@Composable
fun SimAndDurationRow(log: CallLogResponse) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.SimCard, null, tint = SubtextColor, modifier = Modifier.size(14.sdp()))
        Spacer(modifier = Modifier.width(4.sdp()))
        Text(log.simslot ?: "SIM ?", color = SubtextColor, fontSize = 12.ssp(), fontWeight = FontWeight.Medium)

        Spacer(modifier = Modifier.width(16.sdp()))

        Icon(Icons.Rounded.Schedule, null, tint = SubtextColor, modifier = Modifier.size(14.sdp()))
        Spacer(modifier = Modifier.width(4.sdp()))
        Text(formatDuration(log.duration), color = SubtextColor, fontSize = 12.ssp(), fontWeight = FontWeight.Medium)
    }
}

@Composable
fun NotesPopup(notes: String, onDismiss: () -> Unit) {
    Popup(
        popupPositionProvider = remember {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize
                ): IntOffset {
                    val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
                    val y = anchorBounds.top - popupContentSize.height - 10
                    return IntOffset(x, y)
                }
            }
        },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                modifier = Modifier
                    .width(280.sdp())
                    .heightIn(min = 100.sdp(), max = 300.sdp())
                    .shadow(12.sdp(), RoundedCornerShape(12.sdp()))
                    .background(NotesPopupBg, RoundedCornerShape(12.sdp()))
                    .padding(16.sdp())
            ) {
                Text("Call Notes:", color = NotesHighVisColor, fontSize = 14.ssp(), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.sdp()))
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 1.sdp())
                Spacer(modifier = Modifier.height(10.sdp()))
                Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(notes, color = Color.White.copy(alpha = 0.95f), fontSize = 13.ssp(), lineHeight = 20.ssp())
                }
            }
            Canvas(modifier = Modifier.size(width = 20.sdp(), height = 10.sdp())) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2, size.height)
                    close()
                }
                drawPath(path, color = NotesPopupBg)
            }
        }
    }
}

fun getCallTypeStyles(type: String): Triple<ImageVector, Color, Color> {
    return when (type.lowercase()) {
        "incoming" -> Triple(Icons.Rounded.CallReceived, PrimaryColor, PrimaryColor.copy(alpha = 0.15f))
        "outgoing" -> Triple(Icons.Rounded.CallMade, SuccessColor, SuccessColor.copy(alpha = 0.15f))
        "missed" -> Triple(Icons.Rounded.CallMissed, ErrorColor, ErrorColor.copy(alpha = 0.15f))
        else -> Triple(Icons.Default.Phone, SubtextColor, SubtextColor.copy(alpha = 0.1f))
    }
}

@Composable
fun ContainerPill(text: String, color: Color, bgColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.sdp()))
            .background(bgColor)
            .padding(horizontal = 8.sdp(), vertical = 3.sdp())
    ) {
        Text(text = text, color = color, fontSize = 11.ssp(), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EmptyStateMessage(msg: String, isError: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 60.sdp()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            if(isError) Icons.Default.Warning else Icons.Default.Search,
            null,
            tint = if(isError) ErrorColor else SubtextColor,
            modifier = Modifier.size(48.sdp())
        )
        Spacer(modifier = Modifier.height(16.sdp()))
        Text(msg, color = SubtextColor)
    }
}

@Composable
fun filterTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = PrimaryColor,
    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
    focusedLabelColor = PrimaryColor,
    unfocusedLabelColor = SubtextColor
)

private fun formatDuration(seconds: Int): String {
    if (seconds == 0) return "0s"
    if (seconds < 60) return "${seconds}s"
    val m = seconds / 60
    val s = seconds % 60
    return if (s > 0) "${m}m ${s}s" else "${m}m"
}

private fun formatPrettyDate(timestamp: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val date = sdf.parse(timestamp) ?: return ""

        val now = Calendar.getInstance()
        val logTime = Calendar.getInstance().apply { time = date }
        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)

        when {
            now.get(Calendar.DAY_OF_YEAR) == logTime.get(Calendar.DAY_OF_YEAR) &&
                    now.get(Calendar.YEAR) == logTime.get(Calendar.YEAR) -> "Today, $timeStr"
            now.get(Calendar.DAY_OF_YEAR) - logTime.get(Calendar.DAY_OF_YEAR) == 1 &&
                    now.get(Calendar.YEAR) == logTime.get(Calendar.YEAR) -> "Yesterday, $timeStr"
            else -> SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(date)
        }
    } catch (e: Exception) { timestamp }
}

@Composable
fun QuickFilterRow(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.sdp(), vertical = 8.sdp()),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        FilterChip(Icons.Default.AllInbox, selectedFilter == "All", PrimaryColor) { onFilterSelected("All") }
        FilterChip(Icons.Default.Work, selectedFilter == "Work", PrimaryColor) { onFilterSelected("Work") }
        FilterChip(Icons.Default.Person, selectedFilter == "Personal", PersonalBorder) { onFilterSelected("Personal") }
        FilterChip(Icons.Rounded.CallReceived, selectedFilter == "Incoming", PrimaryColor) { onFilterSelected("Incoming") }
        FilterChip(Icons.Rounded.CallMade, selectedFilter == "Outgoing", SuccessColor) { onFilterSelected("Outgoing") }
        FilterChip(Icons.Rounded.CallMissed, selectedFilter == "Missed", ErrorColor) { onFilterSelected("Missed") }
    }
}

@Composable
fun FilterChip(icon: ImageVector, isSelected: Boolean, selectedColor: Color, onClick: () -> Unit) {
    val backgroundColor = if (isSelected) selectedColor.copy(alpha = 0.2f) else CardColor
    val borderColor = if (isSelected) selectedColor else Color.Transparent
    val contentColor = if (isSelected) selectedColor else SubtextColor

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.sdp(), borderColor, CircleShape)
            .clickable { onClick() }
            .padding(12.sdp()),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = contentColor, modifier = Modifier.size(24.sdp()))
    }
}