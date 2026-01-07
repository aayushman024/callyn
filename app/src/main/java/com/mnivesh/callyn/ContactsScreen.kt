package com.mnivesh.callyn

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

// --- Data Classes ---

data class DeviceNumber(
    val number: String,
    val isDefault: Boolean
)

data class DeviceContact(
    val id: String,
    val name: String,
    val numbers: List<DeviceNumber>,
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
        // Find the first actual letter in each word, ignoring symbols
        .mapNotNull { word -> word.firstOrNull { it.isLetter() }?.uppercaseChar() }
        .take(2)
        .joinToString("")
        .ifEmpty {
            // Fallback: Find first letter of the name or return empty
            name.firstOrNull { it.isLetter() }?.uppercase() ?: ""
        }
}

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
    onShowUserDetails: () -> Unit,
    onShowCallLogs: () -> Unit,
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
        factory = ContactsViewModelFactory(application, application.repository)
    )

    // --- AUTH & USER RETRIEVAL ---
    val authManager = remember { AuthManager(context) }
    val token by remember(authManager) { mutableStateOf(authManager.getToken()) }
    val userName by remember(authManager) { mutableStateOf(authManager.getUserName() ?: "") }
    val department by remember(authManager) { mutableStateOf(authManager.getDepartment()) }

    var searchQuery by remember { mutableStateOf("") }
    val workListState = rememberLazyListState()
    val personalListState = rememberLazyListState()

    var isRefreshing by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, token, userName) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                if (!token.isNullOrBlank() && userName.isNotBlank()) {
                    viewModel.onRefresh(token!!, userName)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(searchQuery) {
        workListState.scrollToItem(0)
        personalListState.scrollToItem(0)
    }

    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    val selectedTabIndex = pagerState.currentPage
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    // --- PERMISSIONS ---
    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasWritePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }

    // [!code ++] Sim Count State
    var isDualSim by remember { mutableStateOf(false) }

    fun checkSimStatus() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                val activeSims = subManager.activeSubscriptionInfoCount
                isDualSim = activeSims > 1
            } catch (e: Exception) {
                isDualSim = false
            }
        } else {
            isDualSim = false
        }
    }

    // Check SIM initially
    LaunchedEffect(Unit) { checkSimStatus() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions[Manifest.permission.READ_CONTACTS] == true
        hasContactsPermission = isGranted
        hasWritePermission = permissions[Manifest.permission.WRITE_CONTACTS] == true

        // [!code ++] Re-check sim status after permission
        if (permissions[Manifest.permission.READ_PHONE_STATE] == true) {
            checkSimStatus()
        }

        if (isGranted) viewModel.loadDeviceContacts()
    }

    val uiState by viewModel.uiState.collectAsState()
    val workContacts by viewModel.localContacts.collectAsState()
    val deviceContacts by viewModel.deviceContacts.collectAsState()

    val contentResolver = LocalContext.current.contentResolver

    // --- FILTER LOGIC ---
    val myContacts = remember(workContacts, userName, department) {
        if (department == "Management" || department == "IT Desk") {
            workContacts
        } else {
            workContacts.filter { (it.rshipManager ?: "").equals(userName, ignoreCase = true) }
        }
    }

    // --- CONFLICT LOGIC ---
    var conflictingContacts by remember { mutableStateOf<List<DeviceContact>>(emptyList()) }
    var showConflictSheet by remember { mutableStateOf(false) }
    var hasCheckedConflicts by remember { mutableStateOf(false) }

    LaunchedEffect(workContacts, deviceContacts) {
        if (department != "Management" && department != "IT Desk"  && !hasCheckedConflicts && workContacts.isNotEmpty() && deviceContacts.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                val conflicts = deviceContacts.filter { device ->
                    device.numbers.any { numObj ->
                        val deviceNum = sanitizePhoneNumber(numObj.number)
                        if (deviceNum.length < 5) false
                        else workContacts.any { work -> sanitizePhoneNumber(work.number) == deviceNum }
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

    val filteredWorkContacts = remember(searchQuery, workContacts, myContacts, department) {
        if (searchQuery.isBlank()) myContacts else {
            workContacts.filter {
                it.name.contains(searchQuery, true) ||
                        it.familyHead.contains(searchQuery, true) ||
                        it.pan.contains(searchQuery, true) ||
                        (department == "Management" && it.number.contains(searchQuery))
            }.sortedBy { !it.name.startsWith(searchQuery, true) }
        }
    }

    val favoriteContacts = remember(deviceContacts, searchQuery) {
        if (searchQuery.isBlank()) deviceContacts.filter { it.isStarred } else emptyList()
    }

    val filteredDeviceContacts = remember(searchQuery, deviceContacts) {
        if (searchQuery.isBlank()) deviceContacts else {
            deviceContacts.filter { contact ->
                contact.name.contains(searchQuery, true) ||
                        contact.numbers.any { it.number.contains(searchQuery) }
            }.sortedBy { !it.name.startsWith(searchQuery, true) }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedWorkContact by remember { mutableStateOf<AppContact?>(null) }
    var selectedDeviceContact by remember { mutableStateOf<DeviceContact?>(null) }

    var showGlobalRequestDialog by remember { mutableStateOf(false) }
    var globalRequestReason by remember { mutableStateOf("") }
    var contactForRequest by remember { mutableStateOf<String?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer"
    )

    // --- MAIN UI ---
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                userName = userName,
                onSync = {
                    token?.let { t ->
                        scope.launch {
                            val isSuccess = viewModel.refreshContactsAwait(t, userName)
                            if (isSuccess) Toast.makeText(context, "Sync Successful!", Toast.LENGTH_SHORT).show()
                            else Toast.makeText(context, "Sync Failed. Check Internet.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onLogout = { authManager.logout(); onLogout() },
                onShowRequests = onShowRequests,
                onShowUserDetails = onShowUserDetails,
                onShowCallLogs = onShowCallLogs,
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
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                containerColor = Color.Transparent,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Callyn", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                if (department == "Management" || department == "IT Desk") {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = Color(0xFF3B82F6).copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(50),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = " Admin ",
                                            color = Color(0xFF3B82F6),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, "Menu", tint = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White
                        ),
                        scrollBehavior = scrollBehavior
                    )
                }
            ) { innerPadding ->
                Column(modifier = Modifier.padding(innerPadding)) {
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

                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            scope.launch {
                                isRefreshing = true
                                if (token != null) {
                                    viewModel.refreshAllAwait(token!!, userName)
                                }
                                isRefreshing = false
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
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
                                                    // [!code ++] Added READ_PHONE_STATE for SIM detection
                                                    arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_PHONE_STATE)
                                                )
                                            }
                                        } else if (filteredDeviceContacts.isEmpty() && favoriteContacts.isEmpty()) {
                                            Box (
                                                modifier = Modifier.fillMaxWidth(),
                                                contentAlignment = Alignment.Center
                                            ){
                                                EmptyStateCard("No personal contacts found", Icons.Default.PersonOff)
                                            }
                                        } else {
                                            LazyColumn(state = personalListState, contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                if (favoriteContacts.isNotEmpty()) {
                                                    item {
                                                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
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
                                            LazyColumn(state = workListState, contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                }
            }

            if (selectedWorkContact != null) {
                ModernBottomSheet(
                    contact = selectedWorkContact!!,
                    sheetState = sheetState,
                    department = department,
                    onDismiss = { scope.launch { sheetState.hide() }.invokeOnCompletion { selectedWorkContact = null } },
                    onCall = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            onContactClick(selectedWorkContact!!.number, true)
                            selectedWorkContact = null
                        }
                    },
                    isWorkContact = true,
                    isDualSim = isDualSim, // [!code ++] Pass Dual Sim State
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
                    isDualSim = isDualSim, // [!code ++] Pass Dual Sim State
                    onDismiss = { scope.launch { sheetState.hide() }.invokeOnCompletion { selectedDeviceContact = null } },
                    onCall = { number ->
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            onContactClick(number, false)
                            selectedDeviceContact = null
                        }
                    }
                )
            }

            if (showConflictSheet) {
                ModalBottomSheet(
                    onDismissRequest = {},
                    sheetState = rememberModalBottomSheetState(
                        skipPartiallyExpanded = true,
                        confirmValueChange = { newState -> newState != SheetValue.Hidden }
                    ),
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color.White,
                    dragHandle = null
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Duplicate Contacts Found", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                            Button(
                                onClick = {
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
                                                    viewModel.loadDeviceContacts()
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Error deleting contacts", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "Write Permission Required", Toast.LENGTH_SHORT).show()
                                        permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_PHONE_STATE))
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Continue to Delete")
                            }
                        }

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
                                        Text(text = contact.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                    }

                                    Button(
                                        onClick = {
                                            val workMatch = workContacts.firstOrNull { work ->
                                                contact.numbers.any { numObj -> sanitizePhoneNumber(work.number) == sanitizePhoneNumber(numObj.number) }
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
    val displayNumber = contact.numbers.firstOrNull()?.number ?: "No Number"
    val count = contact.numbers.size

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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Text(displayNumber, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                    if (count > 1) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("+$count", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
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
        repeat(1) {
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
    isDualSim: Boolean,
    department: String?,
    onRequestSubmit: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRequestDialog by remember { mutableStateOf(false) }
    var requestReason by remember { mutableStateOf("") }
    val context = LocalContext.current

    // --- Modern Theme Palette ---
    val backgroundColor = Color(0xFF0F172A) // Deep Slate Background
    val surfaceColor = Color(0xFF1E293B)    // Lighter Surface
    val primaryColor = Color(0xFF10B981)    // Emerald Green
    val workColor = Color(0xFF60A5FA)       // Blue
    val warningColor = Color(0xFFFFB74D)    // Orange/Gold
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
                    .padding(vertical = 16.dp)
                    .width(48.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // --- Header Section: Avatar & Menu ---
            Box(modifier = Modifier.fillMaxWidth()) {
                // Top Right Menu Button
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    IconButton(
                        onClick = { showMenu = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = surfaceColor,
                            contentColor = textSecondary
                        ),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(Icons.Default.MoreVert, "Options", modifier = Modifier.size(20.dp))
                    }

                    // Custom Dropdown
                    MaterialTheme(shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(12.dp))) {
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(surfaceColor),
                            offset = DpOffset((-12).dp, 0.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Raise request to mark as personal", color = textPrimary, fontSize = 14.sp) },
                                onClick = {
                                    showMenu = false
                                    showRequestDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, null, tint = workColor, modifier = Modifier.size(18.dp))
                                }
                            )
                        }
                    }
                }

                // Centered Avatar
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .align(Alignment.Center)
                        .border(4.dp, backgroundColor, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    getColorForName(contact.name),
                                    getColorForName(contact.name).copy(alpha = 0.6f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        getInitials(contact.name),
                        color = Color.White,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Contact Name ---
            Text(
                text = contact.name,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // --- Type Pill (Work/Personal) ---
            val pillColor = if (isWorkContact) workColor else primaryColor
            Surface(
                color = pillColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = if (isWorkContact) Icons.Default.BusinessCenter else Icons.Default.Person,
                        contentDescription = null,
                        tint = pillColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isWorkContact) "Work Contact" else "Personal Contact",
                        fontSize = 13.sp,
                        color = pillColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // --- Management Specific View ---
            if (department == "Management") {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = surfaceColor,
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Phone Number", contact.number)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = contact.number,
                            fontSize = 15.sp,
                            color = textPrimary.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Info Cards Section ---
            if (isWorkContact) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "CLIENT DETAILS",
                        color = textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                    )

                    Surface(
                        color = surfaceColor,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            ModernDetailRow(Icons.Default.CreditCard, "PAN Number", contact.pan, warningColor)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = textSecondary.copy(alpha = 0.1f))
                            ModernDetailRow(Icons.Default.FamilyRestroom, "Family Head", contact.familyHead, Color(0xFF81C784))
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = textSecondary.copy(alpha = 0.1f))
                            ModernDetailRow(Icons.Default.AccountBox, "Relationship Manager", contact.rshipManager ?: "N/A", Color(0xFFC084FC))
                        }
                    }
                }
            } else if (department != "Management") {
                // For Personal contacts (non-management), show number in big card
                Surface(
                    color = surfaceColor,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            contact.number,
                            fontSize = 20.sp,
                            color = textPrimary,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Phone Number", contact.number)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, "Copy", tint = textSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- Main Call Button ---
            Button(
                onClick = onCall,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .shadow(12.dp, RoundedCornerShape(20.dp), ambientColor = primaryColor, spotColor = primaryColor),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 4.dp)
            ) {
                Icon(Icons.Default.Call, null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isDualSim) {
                        if (isWorkContact) "Call via SIM 2 (Work)" else "Call via SIM 1 (Personal)"
                    } else {
                        "Call"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // --- Request Dialog Logic ---
    if (showRequestDialog) {
        AlertDialog(
            onDismissRequest = { showRequestDialog = false },
            containerColor = surfaceColor,
            icon = {
                Icon(Icons.Default.Edit, null, tint = workColor, modifier = Modifier.size(28.dp))
            },
            title = {
                Text(
                    "Request Change",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = textPrimary,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Why do you want to mark this contact as Personal?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 20.dp).fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = requestReason,
                        onValueChange = { requestReason = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text("Type your reason here...", color = textSecondary.copy(alpha = 0.5f))
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = backgroundColor, // Inset effect
                            unfocusedContainerColor = backgroundColor,
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            cursorColor = primaryColor
                        ),
                        shape = RoundedCornerShape(16.dp),
                        minLines = 3,
                        maxLines = 5,
                        textStyle = MaterialTheme.typography.bodyLarge
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (requestReason.isNotBlank()) {
                            onRequestSubmit(requestReason)
                            showRequestDialog = false
                            requestReason = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Submit Request", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRequestDialog = false },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Text("Cancel", color = textSecondary, fontWeight = FontWeight.Medium)
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }
}

// --- Helper Composable for Details ---
@Composable
private fun ModernDetailRow(icon: ImageVector, label: String, value: String, iconColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Medium)
            Text(value, fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernDeviceBottomSheet(
    contact: DeviceContact,
    sheetState: SheetState,
    isDualSim: Boolean,
    onDismiss: () -> Unit,
    onCall: (String) -> Unit
) {
    val context = LocalContext.current

    // Modern Dark Theme Colors
    val backgroundColor = Color(0xFF0F172A) // Deep Slate
    val surfaceColor = Color(0xFF1E293B)    // Lighter Slate
    val primaryColor = Color(0xFF10B981)    // Emerald
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
                    .padding(vertical = 16.dp)
                    .width(48.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp), // Bottom padding for safety
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // --- Header Section: Avatar & Actions ---
            Box(modifier = Modifier.fillMaxWidth()) {
                // Top Right Actions (Edit/View) - Styled as Glassmorphic Buttons
                Row(
                    modifier = Modifier.align(Alignment.TopEnd),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val actionButtonColors = IconButtonDefaults.iconButtonColors(
                        containerColor = surfaceColor,
                        contentColor = textSecondary
                    )

                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_EDIT).apply {
                                    data = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contact.id)
                                    putExtra("finishActivityOnSaveCompleted", true)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not edit contact", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = actionButtonColors,
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                    ) { Icon(Icons.Default.Edit, "Edit Contact", modifier = Modifier.size(20.dp)) }

                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contact.id)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open contact", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = actionButtonColors,
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                    ) { Icon(Icons.Default.OpenInNew, "View Contact", modifier = Modifier.size(20.dp)) }
                }

                // Centered Avatar
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .align(Alignment.Center)
                        .border(4.dp, backgroundColor, CircleShape) // "Cutout" effect
                        .padding(4.dp) // Spacing between border and avatar
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    getColorForName(contact.name),
                                    getColorForName(contact.name).copy(alpha = 0.6f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        getInitials(contact.name),
                        color = Color.White,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Name & Type ---
            Text(
                text = contact.name,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // "Personal Contact" Pill
            Surface(
                color = primaryColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        null,
                        tint = primaryColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Personal Contact",
                        fontSize = 13.sp,
                        color = primaryColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- Numbers Logic ---
            val defaultNumberObj = contact.numbers.find { it.isDefault }
            val effectiveDefault = if (contact.numbers.size == 1) contact.numbers.first() else defaultNumberObj

            if (contact.numbers.size > 1) {
                // --- Multiple Numbers Card ---
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "PHONE NUMBERS",
                        color = textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                    )

                    Surface(
                        color = surfaceColor,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 250.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            itemsIndexed(contact.numbers) { index, numObj ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onCall(numObj.number) } // Make whole row clickable
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            numObj.number,
                                            fontSize = 17.sp,
                                            color = textPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (numObj.isDefault) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                "Default",
                                                fontSize = 11.sp,
                                                color = Color(0xFF60A5FA),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Phone Number", numObj.number))
                                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, null, tint = textSecondary, modifier = Modifier.size(18.dp))
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))

                                        // Small Call Button
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(primaryColor)
                                                .clickable { onCall(numObj.number) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }

                                // Divider between items (except last)
                                if (index < contact.numbers.lastIndex) {
                                    HorizontalDivider(
                                        color = textSecondary.copy(alpha = 0.1f),
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // --- Single Number Card ---
                val number = contact.numbers.firstOrNull()?.number ?: ""
                Surface(
                    color = surfaceColor,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            number,
                            fontSize = 20.sp,
                            color = textPrimary,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Phone Number", number)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, "Copy", tint = textSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Main Call Button ---
            if (effectiveDefault != null) {
                Button(
                    onClick = { onCall(effectiveDefault.number) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .shadow(12.dp, RoundedCornerShape(20.dp), ambientColor = primaryColor, spotColor = primaryColor),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 4.dp
                    )
                ) {
                    Icon(Icons.Default.Call, null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (contact.numbers.size > 1) {
                            "Call Default"
                        } else if (isDualSim) {
                            "Call via SIM 1 (Personal)"
                        } else {
                            "Call"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}