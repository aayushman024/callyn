package com.mnivesh.callyn

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mnivesh.callyn.db.AppContact
import com.mnivesh.callyn.ui.ContactsViewModel
import com.mnivesh.callyn.ui.ContactsViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// --- Data class for device contacts ---
data class DeviceContact(
    val id: String,
    val name: String,
    val number: String,
    val isStarred: Boolean = false
)

// --- Helper functions ---
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
}

// Helper to sanitize phone numbers for comparison (last 10 digits)
private fun sanitizePhoneNumber(number: String): String {
    val digits = number.filter { it.isDigit() }
    return if (digits.length > 10) digits.takeLast(10) else digits
}

// --- Main Contact Screen Composable ---
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(
    onContactClick: (String, Boolean) -> Unit,
    onShowRequests: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as? CallynApplication

    if (application == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Application context not available.")
        }
        return
    }

    val viewModel: ContactsViewModel = viewModel(
        factory = ContactsViewModelFactory(application.repository)
    )

    // --- AUTH & USER RETRIEVAL ---
    val authManager = remember { AuthManager(context) }
    val token by remember(authManager) { mutableStateOf(authManager.getToken()) }
    val userName by remember(authManager) { mutableStateOf(authManager.getUserName() ?: "") }

    // [!code ++] Retrieve department
    val department by remember(authManager) { mutableStateOf(authManager.getDepartment()) }

    var searchQuery by remember { mutableStateOf("") }
    val workListState = rememberLazyListState()
    val personalListState = rememberLazyListState()

    LaunchedEffect(searchQuery) {
        workListState.scrollToItem(0)
        personalListState.scrollToItem(0)
    }

    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    val selectedTabIndex = pagerState.currentPage
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // --- PERMISSIONS (UPDATED to include WRITE_CONTACTS) ---
    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }

    // We need WRITE_CONTACTS to delete duplicates
    var hasWritePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasContactsPermission = permissions[Manifest.permission.READ_CONTACTS] == true
        hasWritePermission = permissions[Manifest.permission.WRITE_CONTACTS] == true
    }

    val uiState by viewModel.uiState.collectAsState()
    val workContacts by viewModel.localContacts.collectAsState()

    var deviceContacts by remember { mutableStateOf<List<DeviceContact>>(emptyList()) }
    val contentResolver = LocalContext.current.contentResolver

    // [!code ++] Reusable refresh function
    val refreshDeviceContacts = {
        if (hasContactsPermission) {
            scope.launch(Dispatchers.IO) {
                val contactsList = mutableListOf<DeviceContact>()
                val cursor = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.STARRED
                    ),
                    null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                )

                cursor?.use {
                    val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val starredIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)

                    while (it.moveToNext()) {
                        val id = it.getString(idIndex)
                        val name = it.getString(nameIndex) ?: "Unknown"
                        val number = it.getString(numberIndex)?.replace("\\s".toRegex(), "") ?: ""
                        val isStarred = it.getInt(starredIndex) == 1

                        if (number.isNotEmpty()) contactsList.add(DeviceContact(id, name, number, isStarred))
                    }
                }
                withContext(Dispatchers.Main) {
                    deviceContacts = contactsList.distinctBy { it.number }
                }
            }
        } else {
            deviceContacts = emptyList()
        }
    }

    // Fetch Device Contacts
    LaunchedEffect(hasContactsPermission) {
        refreshDeviceContacts()
    }

    LaunchedEffect(key1 = userName, key2 = token) {
        token?.let { viewModel.onRefresh(it, userName) }
    }

    // --- FILTER LOGIC ---
    val myContacts = remember(workContacts, userName) {
        workContacts.filter { (it.rshipManager ?: "").equals(userName, ignoreCase = true) }
    }

    // --- CONFLICT DETECTION LOGIC (UPDATED) ---
    var conflictingContacts by remember { mutableStateOf<List<DeviceContact>>(emptyList()) }
    var showConflictSheet by remember { mutableStateOf(false) }
    var hasCheckedConflicts by remember { mutableStateOf(false) }

    LaunchedEffect(workContacts, deviceContacts) {
        // [!code ++] Check department condition
        if (department != "Management" && !hasCheckedConflicts && workContacts.isNotEmpty() && deviceContacts.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                // Brute force comparison using sanitized numbers (last 10 digits)
                val conflicts = deviceContacts.filter { device ->
                    val deviceNum = sanitizePhoneNumber(device.number)
                    if (deviceNum.length < 5) return@filter false // skip short numbers

                    workContacts.any { work ->
                        val workNum = sanitizePhoneNumber(work.number)
                        workNum == deviceNum
                    }
                }

                if (conflicts.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        conflictingContacts = conflicts
                        showConflictSheet = true
                    }
                }
            }
            hasCheckedConflicts = true
        }
    }

    // [!code ++] Updated Filter Logic for Management Search
    val filteredWorkContacts = remember(searchQuery, workContacts, myContacts, department) {
        if (searchQuery.isBlank()) myContacts else {
            workContacts.filter {
                it.name.contains(searchQuery, true) ||
                        it.familyHead.contains(searchQuery, true) ||
                        it.pan.contains(searchQuery, true) ||
                        // [!code ++] Allow searching by number for Management
                        (department == "Management" && it.number.contains(searchQuery))
            }.sortedBy { !it.name.startsWith(searchQuery, true) }
        }
    }

    // Filter Favorites
    val favoriteContacts = remember(deviceContacts, searchQuery) {
        if (searchQuery.isBlank()) deviceContacts.filter { it.isStarred } else emptyList()
    }

    val filteredDeviceContacts = remember(searchQuery, deviceContacts) {
        if (searchQuery.isBlank()) deviceContacts else {
            deviceContacts.filter {
                it.name.contains(searchQuery, true) || it.number.contains(searchQuery)
            }.sortedBy { !it.name.startsWith(searchQuery, true) }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedWorkContact by remember { mutableStateOf<AppContact?>(null) }
    var selectedDeviceContact by remember { mutableStateOf<DeviceContact?>(null) }

    // --- GLOBAL DIALOG STATE (So it can be called from Conflict Sheet too) ---
    var showGlobalRequestDialog by remember { mutableStateOf(false) }
    var globalRequestReason by remember { mutableStateOf("") }
    var contactForRequest by remember { mutableStateOf<String?>(null) } // Name of contact to request

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer"
    )

    // --- MAIN UI STRUCTURE ---
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                userName = userName,
                onSync = { token?.let { viewModel.onRefresh(it, userName) } },
                onLogout = { authManager.logout(); onLogout() },
                onShowRequests = onShowRequests,
                onClose = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E293B)))
                    )
            )

            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }, modifier = Modifier.align(Alignment.CenterStart)) {
                                Icon(Icons.Default.Menu, "Menu", tint = Color.White)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Callyn", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)

                                if (department == "Management") {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(

                                        color = Color(0xFF3B82F6).copy(alpha = 0.15f), // Light Blue Background
                                        shape = RoundedCornerShape(50),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.5f)) // Blue Border
                                    ) {
                                        Text(
                                            text = " Admin ",
                                            color = Color(0xFF3B82F6), // Blue Text
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            }
                        }

                        TabRow(
                            selectedTabIndex = selectedTabIndex,
                            containerColor = Color.Transparent,
                            contentColor = Color.White,
                            indicator = { tabPositions ->
                                if (selectedTabIndex < tabPositions.size) {
                                    TabRowDefaults.SecondaryIndicator(
                                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                        color = Color(0xFF3B82F6), height = 3.dp
                                    )
                                }
                            },
                            divider = { HorizontalDivider(color = Color.White.copy(alpha = 0.1f)) }
                        ) {
                            Tab(
                                selected = selectedTabIndex == 0,
                                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                                text = { CustomTabContent("Personal", Icons.Default.Person, deviceContacts.size, selectedTabIndex == 0) }
                            )
                            Tab(
                                selected = selectedTabIndex == 1,
                                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                                text = { CustomTabContent("Work", Icons.Default.BusinessCenter, myContacts.size, selectedTabIndex == 1) }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Search Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp, 15.dp, 16.dp, 10.dp)
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp)),
                            placeholder = { Text("Search contacts...", color = Color.White.copy(alpha = 0.5f)) },
                            leadingIcon = { Icon(Icons.Default.Search, "Search", tint = Color.White.copy(alpha = 0.6f)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, "Clear", tint = Color.White.copy(alpha = 0.6f)) }
                                }
                            },
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

                    HorizontalPager(
                        state = pagerState, modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp), verticalAlignment = Alignment.Top
                    ) { page ->
                        when (page) {
                            0 -> { // Personal Tab
                                if (!hasContactsPermission) {
                                    PermissionRequiredCard {
                                        permissionLauncher.launch(
                                            arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
                                        )
                                    }
                                } else if (filteredDeviceContacts.isEmpty() && favoriteContacts.isEmpty()) {
                                    EmptyStateCard("No personal contacts found", Icons.Default.PersonOff)
                                } else {
                                    LazyColumn(state = personalListState, contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                                        // --- FAVORITES CAROUSEL ---
                                        if (favoriteContacts.isNotEmpty()) {
                                            item {
                                                Column(modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 12.dp)) {
                                                    Text(
                                                        text = "Favourites",
                                                        color = Color(0xFFF59E0B),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                                                    )
                                                    LazyRow(
                                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                                    ) {
                                                        items(favoriteContacts) { contact ->
                                                            FavoriteContactItem(
                                                                contact = contact,
                                                                onClick = {
                                                                    selectedDeviceContact = contact
                                                                    scope.launch { sheetState.show() }
                                                                }
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                                                }
                                            }
                                        }

                                        // --- ALL CONTACTS LIST ---
                                        if (favoriteContacts.isNotEmpty() && searchQuery.isBlank()) {
                                            item {
                                                Text(
                                                    text = "All Contacts",
                                                    color = Color.White.copy(alpha = 0.5f),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                                                )
                                            }
                                        }

                                        items(filteredDeviceContacts, key = { it.id }) { contact ->
                                            ModernDeviceContactCard(contact, onClick = {
                                                selectedDeviceContact = contact
                                                scope.launch { sheetState.show() }
                                            })
                                        }
                                    }
                                }
                            }

                            1 -> { // Work Tab
                                if (uiState.isLoading && workContacts.isEmpty()) {
                                    LoadingCard(shimmerOffset)
                                } else if (uiState.errorMessage != null) {
                                    ErrorCard(uiState.errorMessage!!)
                                } else if (filteredWorkContacts.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        EmptyStateCard(if (searchQuery.isNotEmpty()) "No matches found" else "No assigned contacts", Icons.Default.BusinessCenter)
                                    }
                                } else {
                                    LazyColumn(state = workListState, contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        items(filteredWorkContacts, key = { it.id }) { contact ->
                                            ModernWorkContactCard(contact, onClick = {
                                                selectedWorkContact = contact
                                                scope.launch { sheetState.show() }
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- BOTTOM SHEETS ---

            if (selectedWorkContact != null) {
                ModernBottomSheet(
                    contact = selectedWorkContact!!,
                    sheetState = sheetState,
                    department = department, // [!code ++] Pass department here
                    onDismiss = { scope.launch { sheetState.hide() }.invokeOnCompletion { selectedWorkContact = null } },
                    onCall = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            onContactClick(selectedWorkContact!!.number, true)
                            selectedWorkContact = null
                        }
                    },
                    isWorkContact = true,
                    // --- INTEGRATING API CALL HERE ---
                    onRequestSubmit = { reason ->
                        if (token != null) {
                            viewModel.submitPersonalRequest(
                                token = token!!,
                                contactName = selectedWorkContact!!.name,
                                userName = userName,
                                reason = reason
                            )
                            Toast.makeText(context, "Request Submitted", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Authentication Error", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            if (selectedDeviceContact != null) {
                ModernDeviceBottomSheet(
                    contact = selectedDeviceContact!!,
                    sheetState = sheetState,
                    onDismiss = { scope.launch { sheetState.hide() }.invokeOnCompletion { selectedDeviceContact = null } },
                    onCall = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            onContactClick(selectedDeviceContact!!.number, false)
                            selectedDeviceContact = null
                        }
                    }
                )
            }

            // --- CONFLICT BOTTOM SHEET (NEW) ---
            if (showConflictSheet) {
                ModalBottomSheet(
                    onDismissRequest = {
                    },
                    sheetState = rememberModalBottomSheetState(
                        skipPartiallyExpanded = true,
                        // [!code ++] This is the key part:
                        confirmValueChange = { newState ->
                            newState != SheetValue.Hidden
                        }
                    ),
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color.White,
                    // [!code ++] Optional: Remove the drag handle to visually indicate it can't be swiped
                    dragHandle = null
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // Header with Continue Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Duplicate Contacts Found",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                            Button(
                                onClick = {
                                    // Deletion Logic
                                    if (hasWritePermission) {
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                conflictingContacts.forEach { contact ->
                                                    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contact.id)
                                                    contentResolver.delete(uri, null, null)
                                                }
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Cleaned up ${conflictingContacts.size} contacts", Toast.LENGTH_SHORT).show()
                                                    showConflictSheet = false
                                                    // Refresh device contacts list
                                                    refreshDeviceContacts()
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Error deleting contacts", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "Write Permission Required", Toast.LENGTH_SHORT).show()
                                        permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS))
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Continue")
                            }
                        }

                        // List of Conflicts
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 30.dp)
                        ) {
                            items(conflictingContacts) { contact ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = contact.name, // Personal Contact Name
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp
                                        )
//                                        Text(
//                                            text = contact.number,
//                                            color = Color.White.copy(alpha = 0.6f),
//                                            fontSize = 14.sp
//                                        )
                                    }

                                    Button(
                                        onClick = {
                                            // Find corresponding work contact for the request logic
                                            val workMatch = workContacts.firstOrNull {
                                                sanitizePhoneNumber(it.number) == sanitizePhoneNumber(contact.number)
                                            }
                                            contactForRequest = workMatch?.name ?: contact.name
                                            showGlobalRequestDialog = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text("Mark Personal", fontSize = 12.sp, color = Color(0xFF60A5FA))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- GLOBAL REQUEST DIALOG ---
            if (showGlobalRequestDialog) {
                AlertDialog(
                    onDismissRequest = { showGlobalRequestDialog = false },
                    containerColor = Color(0xFF1E293B),
                    title = { Text("Request Change", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                    text = {
                        Column {
                            Text("Why do you want to mark ${contactForRequest ?: "this contact"} as Personal?", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp))
                            OutlinedTextField(
                                value = globalRequestReason,
                                onValueChange = { globalRequestReason = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Enter reason...", color = Color.Gray) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF3B82F6),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = Color(0xFF3B82F6),
                                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                minLines = 3,
                                maxLines = 5
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (globalRequestReason.isNotBlank() && contactForRequest != null) {
                                    if (token != null) {
                                        viewModel.submitPersonalRequest(
                                            token = token!!,
                                            contactName = contactForRequest!!,
                                            userName = userName,
                                            reason = globalRequestReason
                                        )
                                        Toast.makeText(context, "Request Submitted", Toast.LENGTH_SHORT).show()
                                    }
                                    showGlobalRequestDialog = false
                                    globalRequestReason = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Submit", fontWeight = FontWeight.SemiBold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showGlobalRequestDialog = false }) {
                            Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                )
            }
        }
    }
}

// ---------------------------------------------
// HELPER COMPOSABLES
// ---------------------------------------------

@Composable
fun FavoriteContactItem(contact: DeviceContact, onClick: () -> Unit) {
    val avatarColor = getColorForName(contact.name)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(avatarColor, avatarColor.copy(alpha = 0.7f)))),
            contentAlignment = Alignment.Center
        ) {
            Text(getInitials(contact.name), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(contact.name.split(" ").first(), color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
fun CustomTabContent(text: String, icon: ImageVector, count: Int, isSelected: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 15.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier
            .clip(CircleShape)
            .background(if (isSelected) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f))
            .padding(6.dp, 2.dp)) {
            Text(count.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun ModernWorkContactCard(contact: AppContact, onClick: () -> Unit) {
    val avatarColor = getColorForName(contact.name)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
    ) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(avatarColor, avatarColor.copy(alpha = 0.7f)))),
                contentAlignment = Alignment.Center
            ) {
                Text(getInitials(contact.name), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.name.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Default.FamilyRestroom, null, tint = Color(0xFF60A5FA), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(contact.familyHead.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }, fontSize = 13.sp, color = Color(0xFF60A5FA), fontWeight = FontWeight.Medium)
                }
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF059669))))
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Call, "Call", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun ModernDeviceContactCard(contact: DeviceContact, onClick: () -> Unit) {
    val avatarColor = getColorForName(contact.name)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
    ) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(avatarColor, avatarColor.copy(alpha = 0.7f)))),
                contentAlignment = Alignment.Center
            ) {
                Text(getInitials(contact.name), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(contact.number, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(top = 2.dp))
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF059669))))
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Call, "Call", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun PermissionRequiredCard(onGrantPermission: () -> Unit) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))) {
        Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6)))), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ContactPage, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("Contact Permission Required", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
            Text("Allow access to view your personal contacts", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onGrantPermission, modifier = Modifier
                .fillMaxWidth()
                .height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))) {
                Text("Grant Permission", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun EmptyStateCard(message: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        Box(modifier = Modifier
            .size(100.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(message, fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LoadingCard(shimmerOffset: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(5) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))) {
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f)))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.1f)))
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.08f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.15f))) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Error, null, tint = Color(0xFFEF4444), modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(message, color = Color(0xFFEF4444), fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ContactDetailRow(icon: ImageVector, label: String, value: String, labelColor: Color) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(Color.White.copy(alpha = 0.05f))
        .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
private fun ModernBottomSheet(
    contact: AppContact,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onCall: () -> Unit,
    isWorkContact: Boolean,
    department: String?, // [!code ++] New Parameter
    onRequestSubmit: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRequestDialog by remember { mutableStateOf(false) }
    var requestReason by remember { mutableStateOf("") }
    val context = LocalContext.current // [!code ++]

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E293B),
        contentColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier
                .padding(start = 16.dp)
                .align(Alignment.TopStart)
                .zIndex(1f)) {
                IconButton(onClick = { showMenu = true }) {
                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(Color(0xFF2C2C2E), RoundedCornerShape(12.dp)),
                    offset = DpOffset(0.dp, 8.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("Raise request to mark this contact as personal", color = Color.White, fontSize = 14.sp) },
                        onClick = { showMenu = false; showRequestDialog = true },
                        leadingIcon = { Icon(Icons.Default.Edit, null, tint = Color(0xFF60A5FA), modifier = Modifier.size(20.dp)) }
                    )
                }
            }

            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(getColorForName(contact.name), getColorForName(contact.name).copy(alpha = 0.7f)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(getInitials(contact.name), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(contact.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)

                // [!code ++] NEW: Show Number & Copy for Management in Bottom Sheet
                if (department == "Management") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = contact.number,
                            fontSize = 15.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Phone Number", contact.number)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                                }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(if (isWorkContact) Icons.Default.BusinessCenter else Icons.Default.Person, null, tint = if (isWorkContact) Color(0xFF60A5FA) else Color(0xFF10B981), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isWorkContact) "Work Contact" else "Personal Contact", fontSize = 15.sp, color = if (isWorkContact) Color(0xFF60A5FA) else Color(0xFF10B981), fontWeight = FontWeight.Medium)
                }

                if (isWorkContact) {
                    Spacer(modifier = Modifier.height(16.dp))
                    ContactDetailRow(Icons.Default.CreditCard, "PAN", contact.pan, Color(0xFFFFB74D))
                    Spacer(modifier = Modifier.height(8.dp))
                    ContactDetailRow(Icons.Default.FamilyRestroom, "Family Head", contact.familyHead, Color(0xFF81C784))
                    Spacer(modifier = Modifier.height(8.dp))
                    ContactDetailRow(Icons.Default.AccountBox, "Relationship Manager", contact.rshipManager ?: "N/A", Color(0xFFC084FC))
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    Text(contact.number, fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp))
                }

                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onCall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    elevation = ButtonDefaults.buttonElevation(4.dp, 8.dp)
                ) {
                    Icon(Icons.Default.Call, null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(if (isWorkContact) "Call via SIM 2 (Work)" else "Call via SIM 1 (Personal)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showRequestDialog) {
        AlertDialog(
            onDismissRequest = { showRequestDialog = false },
            containerColor = Color(0xFF1E293B),
            title = { Text("Request Change", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
            text = {
                Column {
                    Text("Why do you want to mark this contact as Personal?", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp))
                    OutlinedTextField(
                        value = requestReason,
                        onValueChange = { requestReason = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter reason...", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF3B82F6),
                            focusedContainerColor = Color.White.copy(alpha = 0.05f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 3,
                        maxLines = 5
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (requestReason.isNotBlank()) {
                            onRequestSubmit(requestReason) // <--- USE CALLBACK
                            showRequestDialog = false
                            requestReason = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Submit", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRequestDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernDeviceBottomSheet(contact: DeviceContact, sheetState: SheetState, onDismiss: () -> Unit, onCall: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color(0xFF1E293B), contentColor = Color.White, dragHandle = {
        Box(modifier = Modifier
            .padding(vertical = 12.dp)
            .width(40.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.3f)))
    }) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(getColorForName(contact.name), getColorForName(contact.name).copy(alpha = 0.7f)))), contentAlignment = Alignment.Center) {
                    Text(getInitials(contact.name), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_EDIT).apply {
                                data = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contact.id)
                                putExtra("finishActivityOnSaveCompleted", true)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) { Toast.makeText(context, "Could not edit contact", Toast.LENGTH_SHORT).show() }
                    }) { Icon(Icons.Default.Edit, "Edit Contact", tint = Color.White.copy(alpha = 0.7f)) }
                    IconButton(onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contact.id) }
                            context.startActivity(intent)
                        } catch (e: Exception) { Toast.makeText(context, "Could not open contact", Toast.LENGTH_SHORT).show() }
                    }) { Icon(Icons.Default.OpenInNew, "View Contact", tint = Color.White.copy(alpha = 0.7f)) }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(contact.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Default.Person, null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Personal Contact", fontSize = 15.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Medium)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(contact.number, fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Phone Number", contact.number))
                    Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ContentCopy, "Copy", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onCall, modifier = Modifier
                .fillMaxWidth()
                .height(58.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)), elevation = ButtonDefaults.buttonElevation(4.dp, 8.dp)) {
                Icon(Icons.Default.Call, null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Call via SIM 1 (Personal)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}