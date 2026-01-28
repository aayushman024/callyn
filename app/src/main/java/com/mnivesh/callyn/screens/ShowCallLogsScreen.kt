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
    startDate: String, // [!code focus]
    endDate: String,   // [!code focus]
    showNotesOnly: Boolean,
    showDropdown: Boolean,
    isPowerUser: Boolean,
    onNavigateBack: () -> Unit,
    onToggleDropdown: (Boolean) -> Unit,
    onUserSelected: (String) -> Unit,
    onStartDateClick: () -> Unit, // [!code focus]
    onEndDateClick: () -> Unit,   // [!code focus]
    onClearDate: () -> Unit,
    onToggleShowNotes: (Boolean) -> Unit,
    onSearch: () -> Unit
) {
    val isUserListReady = userList.isNotEmpty()
    var selectedTypeFilter by remember { mutableStateOf("All") }

    val filteredLogs = remember(uiState, selectedTypeFilter) {
        if (uiState is CallLogsUiState.Success) {
            when (selectedTypeFilter) {
                "All" -> uiState.logs
                "Personal" -> uiState.logs.filter { it.isWork == false }
                else -> uiState.logs.filter { it.type.equals(selectedTypeFilter, ignoreCase = true) }
            }
        } else emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Call History", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
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
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Section 1: Filters
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

            // Section 3: Results
            when (uiState) {
                is CallLogsUiState.Idle -> item { EmptyStateMessage("Adjust filters to view logs.") }
                is CallLogsUiState.Loading -> item {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PrimaryColor)
                    }
                }
                is CallLogsUiState.Error -> item { EmptyStateMessage("Error: ${uiState.message}", isError = true) }
                is CallLogsUiState.Success -> {
                    if (filteredLogs.isEmpty()) {
                        item { EmptyStateMessage("No logs found.") }
                    } else {
                        item {
                            Text(
                                text = "Found ${filteredLogs.size} records",
                                color = SubtextColor,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
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
            .padding(16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(CardColor)
            .padding(16.dp)
    ) {
        if (isPowerUser) {
            Text("Filter Logs", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(16.dp))

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
                    shape = RoundedCornerShape(12.dp),
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
            Spacer(modifier = Modifier.height(12.dp))
        } else {
            Text("My Log Book", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- UPDATED DATE FILTER ROW (START / END) ---
        // [!code focus] Split into two columns
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(12.dp))
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
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(12.dp))
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
                Icon(Icons.Default.DateRange, null, tint = SubtextColor, modifier = Modifier.padding(start = 4.dp))
            }
        }
        // -------------------------------

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Leave dates blank to view today's logs",
            color = SubtextColor.copy(alpha = 0.9f),
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (isPowerUser) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Show calls with notes",
                    color = Color.White,
                    fontSize = 15.sp,
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
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = onSearch,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Show Results", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
    val cardBorder = if (isPersonal) BorderStroke(1.dp, PersonalBorder.copy(alpha = 0.3f)) else null

    val (typeIcon, typeColor, typeBg) = getCallTypeStyles(log.type)
    val dateDisplay = remember(log.timestamp) { formatPrettyDate(log.timestamp) }

    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(typeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(typeIcon, null, tint = typeColor, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    if (isPersonal) {
                        Text("Personal Call", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    } else {
                        Text(
                            text = log.callerName.ifBlank { "Unknown Caller" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Tags
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            renderTags(log)

                            // View Notes Button (Popup)
                            if (hasNotes) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Box {
                                    OutlinedButton(
                                        onClick = { showNotesPopup = true },
                                        border = BorderStroke(1.dp, NotesHighVisColor),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NotesHighVisColor),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(Icons.Default.ChatBubbleOutline, null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("View Notes", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    Text(dateDisplay, color = SubtextColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Row (Includes Uploaded By)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudUpload, null, tint = SubtextColor, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = log.uploadedBy ?: "System",
                        color = SubtextColor,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 100.dp)
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
    val cardBorder = if (isPersonal) BorderStroke(1.dp, PersonalBorder.copy(alpha = 0.3f)) else null
    val (typeIcon, typeColor, typeBg) = getCallTypeStyles(log.type)
    val dateDisplay = remember(log.timestamp) { formatPrettyDate(log.timestamp) }

    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Section
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(typeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(typeIcon, null, tint = typeColor, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    if (isPersonal) {
                        Text("Personal Call", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    } else {
                        Text(
                            text = log.callerName.ifBlank { "Unknown Caller" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            renderTags(log)
                        }
                    }
                }
                Text(dateDisplay, color = SubtextColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }

            // Inline Notes (Standard Feature)
            if (hasNotes) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF334155).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .border(1.dp, NotesHighVisColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ChatBubbleOutline, null, tint = NotesHighVisColor, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CALL NOTES", color = NotesHighVisColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = log.notes ?: "",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(12.dp))

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
        Icon(Icons.Default.SimCard, null, tint = SubtextColor, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(log.simslot ?: "SIM ?", color = SubtextColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)

        Spacer(modifier = Modifier.width(16.dp))

        Icon(Icons.Rounded.Schedule, null, tint = SubtextColor, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(formatDuration(log.duration), color = SubtextColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
                    .width(280.dp)
                    .heightIn(min = 100.dp, max = 300.dp)
                    .shadow(12.dp, RoundedCornerShape(12.dp))
                    .background(NotesPopupBg, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text("Call Notes:", color = NotesHighVisColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))
                Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(notes, color = Color.White.copy(alpha = 0.95f), fontSize = 13.sp, lineHeight = 20.sp)
                }
            }
            Canvas(modifier = Modifier.size(width = 20.dp, height = 10.dp)) {
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
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EmptyStateMessage(msg: String, isError: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            if(isError) Icons.Default.Warning else Icons.Default.Search,
            null,
            tint = if(isError) ErrorColor else SubtextColor,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        FilterChip(Icons.Default.AllInbox, selectedFilter == "All", PrimaryColor) { onFilterSelected("All") }
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
            .border(1.dp, borderColor, CircleShape)
            .clickable { onClick() }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = contentColor, modifier = Modifier.size(24.dp))
    }
}