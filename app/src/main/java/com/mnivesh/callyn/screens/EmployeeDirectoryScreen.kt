// [!code filename:app/src/main/java/com/mnivesh/callyn/ui/EmployeeDirectoryScreen.kt]
package com.mnivesh.callyn.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
import android.telephony.SubscriptionManager
import android.widget.Toast
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mnivesh.callyn.managers.AuthManager
import com.mnivesh.callyn.CallynApplication
import com.mnivesh.callyn.api.EmployeeDirectory
import com.mnivesh.callyn.managers.SimManager

// --- Helper Functions ---

private fun getEmpColor(name: String): Color {
    val palette = listOf(
        Color(0xFF6366F1), Color(0xFFEC4899), Color(0xFF8B5CF6),
        Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFFEF4444),
        Color(0xFF3B82F6), Color(0xFF14B8A6), Color(0xFFF97316)
    )
    return palette[kotlin.math.abs(name.hashCode()) % palette.size]
}

private fun getEmpInitials(name: String): String {
    return name.split(" ")
        .mapNotNull { word -> word.firstOrNull { it.isLetter() }?.uppercaseChar() }
        .take(2)
        .joinToString("")
        .ifEmpty { name.firstOrNull { it.isLetter() }?.uppercase() ?: "" }
}

private fun sanitizePhoneNumber(number: String): String {
    return number.filter { it.isDigit() || it == '+' }
}

// --- Main Screen Composable ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeDirectoryScreen(
    onNavigateBack: () -> Unit,
    onCallClick: (String, Int?) -> Unit // Callback to MainActivity
) {
    val context = LocalContext.current
    val app = context.applicationContext as CallynApplication
    val viewModel: EmployeeViewModel = viewModel(factory = EmployeeViewModelFactory(app.repository))
    val authManager = remember { AuthManager(context) }

    val uiState by viewModel.uiState.collectAsState()

    // --- State ---
    var searchQuery by remember { mutableStateOf("") }
    var selectedDepartment by remember { mutableStateOf("All") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedEmployee by remember { mutableStateOf<EmployeeDirectory?>(null) }
    var isDualSim by remember { mutableStateOf(false) }

    // --- Initial Load & Checks ---
    LaunchedEffect(Unit) {
        val token = authManager.getToken()
        // We still trigger the API call to keep data fresh,
        // but UI will show DB data immediately due to Flow observation.
        if (token != null) viewModel.loadEmployees(token)

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                isDualSim = subManager.activeSubscriptionInfoCount > 1
            } catch (e: Exception) { isDualSim = false }
        }
    }

    Scaffold(
        containerColor = Color(0xFF0F172A),
        topBar = {
            TopAppBar(
                title = { Text("Directory", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A)),
                actions = {
                    IconButton(onClick = {
                        val token = authManager.getToken()
                        if (token != null) viewModel.loadEmployees(token, forceRefresh = true)
                    }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                is EmployeeUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFF3B82F6)
                    )
                }
                is EmployeeUiState.Error -> {
                    // Show error only if DB is empty and API failed
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.CloudOff, null, tint = Color.Red, modifier = Modifier.size(48.sdp()))
                        Spacer(modifier = Modifier.height(16.sdp()))
                        Text(state.message, color = Color.White, fontSize = 16.ssp())
                        Button(onClick = {
                            val token = authManager.getToken()
                            if(token != null) viewModel.loadEmployees(token, true)
                        }, modifier = Modifier.padding(top=16.sdp())) { Text("Retry") }
                    }
                }
                is EmployeeUiState.Success -> {
                    // Logic: Department List & Filtering
                    val departments = remember(state.employees) {
                        listOf("All") + state.employees.map { it.department }.distinct().sorted()
                    }
                    val groupedEmployees = remember(state.employees, searchQuery, selectedDepartment) {
                        state.employees
                            .filter { employee ->
                                val matchesSearch = employee.name.contains(searchQuery, ignoreCase = true) ||
                                        employee.email.contains(searchQuery, ignoreCase = true)
                                val matchesDept = selectedDepartment == "All" || employee.department == selectedDepartment
                                matchesSearch && matchesDept
                            }
                            .groupBy { it.department }
                            .toSortedMap()
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // 1. Search Bar
                        Box(modifier = Modifier.padding(16.sdp(), 0.sdp(), 16.sdp(), 10.sdp())) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.sdp())),
                                placeholder = { Text("Search directory...", color = Color.White.copy(alpha = 0.5f)) },
                                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.6f)) },
                                trailingIcon = if (searchQuery.isNotEmpty()) { { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null, tint = Color.White.copy(alpha = 0.6f)) } } } else null,
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color.White.copy(alpha = 0.1f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = Color.White
                                )
                            )
                        }

                        // 2. Filter Chips
                        ScrollableTabRow(
                            selectedTabIndex = departments.indexOf(selectedDepartment),
                            containerColor = Color.Transparent,
                            edgePadding = 16.sdp(),
                            divider = {},
                            indicator = {}
                        ) {
                            departments.forEach { dept ->
                                val isSelected = selectedDepartment == dept
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedDepartment = dept },
                                    label = { Text(dept,
                                        textAlign = TextAlign.Center) },
                                    modifier = Modifier.padding(end = 8.sdp()),
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = Color(0xFF1E293B),
                                        labelColor = Color.White.copy(alpha = 0.7f),
                                        selectedContainerColor = Color(0xFF3B82F6),
                                        selectedLabelColor = Color.White
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true, selected = isSelected,
                                        borderColor = if (isSelected) Color.Transparent else Color(0xFF334155)
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.sdp()))

                        // 3. Employee List
                        LazyColumn(
                            contentPadding = PaddingValues(top = 8.sdp(), bottom = 100.sdp(), start = 16.sdp(), end = 16.sdp()),
                            verticalArrangement = Arrangement.spacedBy(12.sdp())
                        ) {
                            groupedEmployees.forEach { (dept, employees) ->
                                item {
                                    Text(
                                        text = dept,
                                        color = Color(0xFF60A5FA),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.ssp(),
                                        modifier = Modifier.padding(vertical = 8.sdp(), horizontal = 4.sdp())
                                    )
                                }
                                items(employees.sortedBy { it.name }) { employee ->
                                    ModernEmployeeCard(employee) {
                                        selectedEmployee = employee
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Bottom Sheet ---
        if (selectedEmployee != null) {
            EmployeeBottomSheet(
                employee = selectedEmployee!!,
                sheetState = sheetState,
                isDualSim = isDualSim,
                onDismiss = { selectedEmployee = null },
                onCall = { slotIndex ->
                    val emp = selectedEmployee
                    selectedEmployee = null // Dismiss sheet
                    if (emp != null) {
                        val cleanNumber = sanitizePhoneNumber(emp.phone)
                        // Trigger Callback to MainActivity
                        onCallClick(cleanNumber, slotIndex)
                    }
                }
            )
        }
    }
}

// --- List Item Composable ---

@Composable
fun ModernEmployeeCard(employee: EmployeeDirectory, onClick: () -> Unit) {
    val avatarColor = getEmpColor(employee.name)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.sdp()),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.sdp()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.sdp())
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(avatarColor, avatarColor.copy(alpha = 0.7f)))),
                contentAlignment = Alignment.Center
            ) {
                Text(getEmpInitials(employee.name), color = Color.White, fontSize = 20.ssp(), fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.sdp()))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    employee.name,
                    fontSize = 17.ssp(),
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.sdp())) {
                    Icon(Icons.Default.Business, null, tint = Color(0xFF60A5FA), modifier = Modifier.size(14.sdp()))
                    Spacer(modifier = Modifier.width(4.sdp()))
                    Text(employee.department, fontSize = 13.ssp(), color = Color(0xFF60A5FA), fontWeight = FontWeight.Medium)
                }
            }
            Box(
                modifier = Modifier
                    .size(48.sdp())
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF059669))))
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Call, "Call", tint = Color.White, modifier = Modifier.size(22.sdp()))
            }
        }
    }
}

// --- Bottom Sheet Composable ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeBottomSheet(
    employee: EmployeeDirectory,
    sheetState: SheetState,
    isDualSim: Boolean,
    onDismiss: () -> Unit,
    onCall: (Int?) -> Unit
) {
    val context = LocalContext.current

    // Theme Colors
    val backgroundColor = Color(0xFF0F172A)
    val surfaceColor = Color(0xFF1E293B)
    val primaryColor = Color(0xFF10B981)
    val secondaryColor = Color(0xFF60A5FA)
    val textPrimary = Color.White
    val textSecondary = Color.White.copy(alpha = 0.6f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = backgroundColor,
        contentColor = textPrimary,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 16.sdp())
                    .width(48.sdp())
                    .height(6.sdp())
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.sdp())
                .padding(bottom = 24.sdp()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Avatar
            Box(
                modifier = Modifier
                    .size(110.sdp())
                    .border(4.sdp(), backgroundColor, CircleShape)
                    .padding(4.sdp())
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                getEmpColor(employee.name),
                                getEmpColor(employee.name).copy(alpha = 0.6f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    getEmpInitials(employee.name),
                    color = Color.White,
                    fontSize = 40.ssp(),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.sdp()))

            // Name
            Text(
                text = employee.name,
                fontSize = 26.ssp(),
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.sdp()))

            // Pill
            Surface(
                color = secondaryColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(32.sdp())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.sdp())
                ) {
                    Icon(
                        Icons.Default.Badge,
                        null,
                        tint = secondaryColor,
                        modifier = Modifier.size(14.sdp())
                    )
                    Spacer(modifier = Modifier.width(6.sdp()))
                    Text(
                        text = "Employee",
                        fontSize = 13.ssp(),
                        color = secondaryColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.sdp()))

            // Details Card
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "CONTACT DETAILS",
                    color = textSecondary,
                    fontSize = 12.ssp(),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.ssp(),
                    modifier = Modifier.padding(start = 8.sdp(), bottom = 8.sdp())
                )

                Surface(
                    color = surfaceColor,
                    shape = RoundedCornerShape(20.sdp()),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.sdp())) {
                        // Phone Row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(36.sdp()).clip(RoundedCornerShape(10.sdp())).background(primaryColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Phone, null, tint = primaryColor, modifier = Modifier.size(18.sdp()))
                            }
                            Spacer(modifier = Modifier.width(16.sdp()))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Mobile", fontSize = 11.ssp(), color = textSecondary, fontWeight = FontWeight.Medium)
                                Text(employee.phone, fontSize = 16.ssp(), color = textPrimary, fontWeight = FontWeight.Medium)
                            }
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Phone", employee.phone))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, null, tint = textSecondary, modifier = Modifier.size(18.sdp()))
                            }
                        }

                        // [!code change] Only show Email row if data exists (offline mode support)
                        if (employee.email.isNotBlank()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.sdp()), color = textSecondary.copy(alpha = 0.1f))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(36.sdp()).clip(RoundedCornerShape(10.sdp())).background(secondaryColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Email, null, tint = secondaryColor, modifier = Modifier.size(18.sdp()))
                                }
                                Spacer(modifier = Modifier.width(16.sdp()))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Email", fontSize = 11.ssp(), color = textSecondary, fontWeight = FontWeight.Medium)
                                    Text(employee.email, fontSize = 16.ssp(), color = textPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = {
                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("mailto:${employee.email}")
                                    }
                                    try { context.startActivity(intent) } catch (e: Exception) { Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show() }
                                }) {
                                    Icon(Icons.Default.Send, null, tint = textSecondary, modifier = Modifier.size(18.sdp()))
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.sdp()), color = textSecondary.copy(alpha = 0.1f))

                        // Department Row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(36.sdp()).clip(RoundedCornerShape(10.sdp())).background(Color(0xFF8B5CF6).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Apartment, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(18.sdp()))
                            }
                            Spacer(modifier = Modifier.width(16.sdp()))
                            Column {
                                Text("Department", fontSize = 11.ssp(), color = textSecondary, fontWeight = FontWeight.Medium)
                                Text(employee.department, fontSize = 16.ssp(), color = textPrimary, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.sdp()))

            //smart calling
            val showDualButtons = isDualSim && SimManager.workSimSlot == null
            if (showDualButtons) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.sdp())
                ) {
                    // SIM 1
                    Button(
                        onClick = { onCall(0) },
                        modifier = Modifier.weight(1f).height(64.sdp()).shadow(8.sdp(), RoundedCornerShape(20.sdp()), ambientColor = Color(0xFF3B82F6), spotColor = Color(0xFF3B82F6)),
                        shape = RoundedCornerShape(20.sdp()),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.sdp(), pressedElevation = 4.sdp())
                    ) {
                        Row(horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Phone, null)
                            Text("  SIM 1", fontSize = 16.ssp(), fontWeight = FontWeight.Bold)
                        }
                    }
                    // SIM 2
                    Button(
                        onClick = { onCall(1) },
                        modifier = Modifier.weight(1f).height(64.sdp()).shadow(8.sdp(), RoundedCornerShape(20.sdp()), ambientColor = Color(0xFF10B981), spotColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(20.sdp()),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.sdp(), pressedElevation = 4.sdp())
                    ) {
                        Row(horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Phone, null)
                            Text("  SIM 2", fontSize = 16.ssp(), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Button(
                    onClick = { onCall(null) },
                    modifier = Modifier.fillMaxWidth().height(64.sdp()).shadow(12.sdp(), RoundedCornerShape(20.sdp()), ambientColor = primaryColor, spotColor = primaryColor),
                    shape = RoundedCornerShape(20.sdp()),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.sdp(), pressedElevation = 4.sdp())
                ) {
                    Icon(Icons.Default.Call, null, modifier = Modifier.size(24.sdp()))
                    Spacer(modifier = Modifier.width(12.sdp()))
                    // Text Update
                    val buttonText = if (SimManager.workSimSlot != null) "Call (Work SIM)" else "Call"
                    Text(text = buttonText, fontSize = 18.ssp(), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(12.sdp()))
        }
    }
}