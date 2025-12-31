package com.mnivesh.callyn

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mnivesh.callyn.api.CallLogResponse
import java.text.SimpleDateFormat
import java.util.*

// --- Color Palette ---
val BackgroundColor = Color(0xFF0F172A)
val CardColor = Color(0xFF1E293B)
val PrimaryColor = Color(0xFF3B82F6)
val SuccessColor = Color(0xFF10B981)
val ErrorColor = Color(0xFFEF4444)
val SubtextColor = Color(0xFF94A3B8)
// New colors for RM Pill
val RolePillBg = Color(0xFF6366F1).copy(alpha = 0.2f) // Indigo with opacity
val RolePillText = Color(0xFFC7D2FE) // Light Indigo

// 1. MAIN SCREEN (Stateful)
@Composable
fun ShowCallLogsScreen(
    onNavigateBack: () -> Unit,
    viewModel: CallLogsViewModel = viewModel()
) {
    val context = LocalContext.current
    val authManager = remember { AuthManager(context) }
    val token = remember { authManager.getToken() }

    // State
    var selectedUser by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf("") }
    var showUserDropdown by remember { mutableStateOf(false) }

    // Date Picker Logic
    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, day ->
            val cal = Calendar.getInstance()
            cal.set(year, month, day)
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            selectedDate = format.format(cal.time)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )
    datePickerDialog.datePicker.maxDate = System.currentTimeMillis()

    // Load Data
    LaunchedEffect(Unit) {
        if (token != null) viewModel.fetchUserNames(token)
    }

    // Render Content
    CallLogsContent(
        uiState = viewModel.uiState,
        userList = viewModel.userNamesList,
        selectedUser = selectedUser,
        selectedDate = selectedDate,
        showDropdown = showUserDropdown,
        onNavigateBack = onNavigateBack,
        onToggleDropdown = { showUserDropdown = it },
        onUserSelected = { selectedUser = it },
        onDateClick = { datePickerDialog.show() },
        onClearDate = { selectedDate = "" },
        onSearch = { if (token != null) viewModel.searchLogs(token, selectedUser, selectedDate) }
    )
}

// 2. UI CONTENT (Stateless)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogsContent(
    uiState: CallLogsUiState,
    userList: List<String>,
    selectedUser: String,
    selectedDate: String,
    showDropdown: Boolean,
    onNavigateBack: () -> Unit,
    onToggleDropdown: (Boolean) -> Unit,
    onUserSelected: (String) -> Unit,
    onDateClick: () -> Unit,
    onClearDate: () -> Unit,
    onSearch: () -> Unit
) {
    val isUserListReady = userList.isNotEmpty()

    // 1. New Local State for Quick Filters
    var selectedTypeFilter by remember { mutableStateOf("All") }

    // 2. Filter Logic
    val filteredLogs = remember(uiState, selectedTypeFilter) {
        if (uiState is CallLogsUiState.Success) {
            if (selectedTypeFilter == "All") uiState.logs
            else uiState.logs.filter { it.type.equals(selectedTypeFilter, ignoreCase = true) }
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
            contentPadding = PaddingValues(bottom = 100.dp) // Nav bar padding
        ) {
            // Section 1: Main Search Filters
            item {
                FilterSection(
                    selectedUser = selectedUser,
                    selectedDate = selectedDate,
                    isUserListReady = isUserListReady,
                    userList = userList,
                    showDropdown = showDropdown,
                    onToggleDropdown = onToggleDropdown,
                    onUserSelected = onUserSelected,
                    onDateClick = onDateClick,
                    onClearDate = onClearDate,
                    onSearch = onSearch
                )
            }

            // Section 2: Quick Type Filters (Visible only when we have results or success state)
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
                is CallLogsUiState.Idle -> item { EmptyStateMessage("Adjust filters above to view logs.") }
                is CallLogsUiState.Loading -> item {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PrimaryColor)
                    }
                }
                is CallLogsUiState.Error -> item { EmptyStateMessage("Error: ${uiState.message}", isError = true) }
                is CallLogsUiState.Success -> {
                    if (filteredLogs.isEmpty()) {
                        item { EmptyStateMessage("No ${selectedTypeFilter.lowercase()} logs found.") }
                    } else {
                        item {
                            Text(
                                text = "Found ${filteredLogs.size} records",
                                color = SubtextColor,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                        // 3. Use filteredLogs here instead of uiState.logs
                        items(filteredLogs) { log ->
                            CallLogCard(log)
                        }
                    }
                }
            }
        }
    }
}

// --- SUB-COMPONENTS ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSection(
    selectedUser: String,
    selectedDate: String,
    isUserListReady: Boolean,
    userList: List<String>,
    showDropdown: Boolean,
    onToggleDropdown: (Boolean) -> Unit,
    onUserSelected: (String) -> Unit,
    onDateClick: () -> Unit,
    onClearDate: () -> Unit,
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
                    onClick = {
                        onUserSelected("")
                        onToggleDropdown(false)
                    }
                )
                userList.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name, color = Color.White) },
                        onClick = {
                            onUserSelected(name)
                            onToggleDropdown(false)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Date Picker
        OutlinedTextField(
            value = selectedDate,
            onValueChange = {},
            readOnly = true,
            label = { Text("Date") },
            placeholder = { Text("All Time") },
            trailingIcon = {
                if (selectedDate.isNotEmpty()) {
                    IconButton(onClick = onClearDate) {
                        Icon(Icons.Default.Close, null, tint = SubtextColor)
                    }
                } else {
                    Icon(Icons.Default.DateRange, null, tint = PrimaryColor)
                }
            },
            colors = filterTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().clickable { onDateClick() },
            enabled = false
        )
        Box(modifier = Modifier.clickable { onDateClick() })

        Spacer(modifier = Modifier.height(20.dp))

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

@Composable
fun CallLogCard(log: CallLogResponse) {
    // Determine Type Styling
    val (typeIcon, typeColor, typeBg) = when (log.type.lowercase()) {
        "incoming" -> Triple(Icons.Rounded.CallReceived, PrimaryColor, PrimaryColor.copy(alpha = 0.15f))
        "outgoing" -> Triple(Icons.Rounded.CallMade, SuccessColor, SuccessColor.copy(alpha = 0.15f))
        "missed" -> Triple(Icons.Rounded.CallMissed, ErrorColor, ErrorColor.copy(alpha = 0.15f))
        else -> Triple(Icons.Default.Phone, SubtextColor, SubtextColor.copy(alpha = 0.1f))
    }

    val dateDisplay = remember(log.timestamp) { formatPrettyDate(log.timestamp) }

    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 1. Icon Avatar (Visual direction)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(typeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(typeIcon, null, tint = typeColor, modifier = Modifier.size(24.dp))
                }

                Spacer(modifier = Modifier.width(16.dp))

                // 2. Names & Details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = log.callerName.ifBlank { "Unknown Caller" },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Metadata Row - Colored RM Pill
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ContainerPill(
                            text = "RM: ${log.rshipManagerName ?: "-"}",
                            color = RolePillText, // Changed to Light Indigo
                            bgColor = RolePillBg  // Changed to Indigo bg
                        )
                    }
                }

                // 3. Date (Top Right)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = dateDisplay,
                        color = SubtextColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(12.dp))

            // 4. Bottom Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Uploaded By
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudUpload, null, tint = SubtextColor, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = log.uploadedBy ?: "System",
                        color = SubtextColor,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 140.dp)
                    )
                }

                // Right: Duration (Always shown, no pill, plain text)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Schedule, null, tint = SubtextColor, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatDuration(log.duration),
                        color = SubtextColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// --- HELPERS ---

@Composable
fun ContainerPill(text: String, color: Color, bgColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp) // Slightly more padding
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FilterChip(
            label = "All",
            isSelected = selectedFilter == "All",
            selectedColor = PrimaryColor,
            onClick = { onFilterSelected("All") }
        )
        FilterChip(
            label = "Incoming",
            isSelected = selectedFilter == "Incoming",
            selectedColor = PrimaryColor,
            onClick = { onFilterSelected("Incoming") }
        )
        FilterChip(
            label = "Outgoing",
            isSelected = selectedFilter == "Outgoing",
            selectedColor = SuccessColor,
            onClick = { onFilterSelected("Outgoing") }
        )
        FilterChip(
            label = "Missed",
            isSelected = selectedFilter == "Missed",
            selectedColor = ErrorColor,
            onClick = { onFilterSelected("Missed") }
        )
    }
}

@Composable
fun FilterChip(
    label: String,
    isSelected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) selectedColor.copy(alpha = 0.2f) else CardColor
    val borderColor = if (isSelected) selectedColor else Color.Transparent
    val textColor = if (isSelected) selectedColor else SubtextColor

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}