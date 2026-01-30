package com.mnivesh.callyn.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
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
import com.mnivesh.callyn.CallynApplication
import com.mnivesh.callyn.managers.AuthManager
import com.mnivesh.callyn.managers.SimManager
import com.mnivesh.callyn.viewmodels.SmsViewModel
import kotlinx.coroutines.delay
import com.mnivesh.callyn.components.*
import com.mnivesh.callyn.sheets.*
import com.mnivesh.callyn.tabs.*
import com.mnivesh.callyn.components.DeviceContact // [!code ++] Explicit Import

// --- Main Contact Screen Composable ---
@SuppressLint("SuspiciousIndentation")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(
    onContactClick: (String, Boolean, Int?) -> Unit,
    onOpenDrawer: () -> Unit,
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
    val userEmail by remember(authManager) { mutableStateOf(authManager.getUserEmail() ?: "") }
    val department by remember(authManager) { mutableStateOf(authManager.getDepartment()) }

    var searchQuery by remember { mutableStateOf("") }
    val workListState = rememberLazyListState()
    val personalListState = rememberLazyListState()

    val smsViewModel: SmsViewModel = viewModel()
    val smsLogs by smsViewModel.smsLogs.collectAsState()
    val hasSmsNotification by smsViewModel.hasNotifications.collectAsState()

    var showSmsScreen by remember { mutableStateOf(false) }

    if (showSmsScreen) {
        val isSmsLoading by smsViewModel.isLoading.collectAsState()
        SmsLogsScreen(
            logs = smsLogs,
            isRefreshing = isSmsLoading,
            onRefresh = { if (token != null) smsViewModel.fetchSmsLogs(token!!) },
            onBack = { showSmsScreen = false }
        )
        return
    }

    var isRefreshing by remember { mutableStateOf(false) }
    var showFullSearch by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

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
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    // --- PERMISSIONS ---
    var hasContactsPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    var hasWritePermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }

    var isDualSim by remember { mutableStateOf(false) }

    fun checkSimStatus() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                val activeSims = subManager.activeSubscriptionInfoCount
                isDualSim = activeSims > 1
            } catch (e: Exception) { isDualSim = false }
        } else { isDualSim = false }
    }

    LaunchedEffect(Unit) { checkSimStatus() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions[Manifest.permission.READ_CONTACTS] == true
        hasContactsPermission = isGranted
        hasWritePermission = permissions[Manifest.permission.WRITE_CONTACTS] == true
        if (permissions[Manifest.permission.READ_PHONE_STATE] == true) { checkSimStatus() }
        if (isGranted) viewModel.loadDeviceContacts()
    }

    val uiState by viewModel.uiState.collectAsState()
    val workContacts by viewModel.localContacts.collectAsState()
    val deviceContacts by viewModel.deviceContacts.collectAsState()
    val contentResolver = LocalContext.current.contentResolver

    // --- FILTER LOGIC ---
    val myContacts = remember(workContacts, userName, department) {
        if (SimManager.workSimSlot == null) {
            emptyList()
        } else {
            workContacts.filter { contact ->
                val rshipManager = contact.rshipManager ?: ""
                if (rshipManager.equals("Employee", ignoreCase = true)) {
                    return@filter false
                }
                if (department == "Management" || department == "IT Desk" || department == "Operations Dept" || userEmail == "arbind@niveshonline.com") {
                    true
                } else {
                    rshipManager.equals(userName, ignoreCase = true)
                }
            }
        }
    }

    // --- CONFLICT LOGIC ---
    var conflictingContacts by remember { mutableStateOf<List<DeviceContact>>(emptyList()) }
    var showConflictSheet by remember { mutableStateOf(false) }
    var hasCheckedConflicts by remember { mutableStateOf(false) }

    LaunchedEffect(workContacts, deviceContacts) {
        if (department == "ConflictContactPaused" && !hasCheckedConflicts && workContacts.isNotEmpty() && deviceContacts.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                val conflicts = deviceContacts.filter { device ->
                    device.numbers.any { numObj ->
                        val deviceNum = sanitizePhoneNumber(numObj.number)
                        if (deviceNum.length < 5) false
                        else workContacts.any { work ->
                            !work.rshipManager.equals("Employee", ignoreCase = true) &&
                                    sanitizePhoneNumber(work.number) == deviceNum
                        }
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

    var filteredWorkContacts by remember { mutableStateOf(emptyList<AppContact>()) }
    var filteredDeviceContacts by remember { mutableStateOf(emptyList<DeviceContact>()) }

    val favoriteContacts = remember(deviceContacts, searchQuery) {
        if (searchQuery.isBlank()) deviceContacts.filter { it.isStarred } else emptyList()
    }

    LaunchedEffect(searchQuery, workContacts, myContacts, department, deviceContacts) {
        withContext(Dispatchers.Default) {
            val workResult = if (searchQuery.isBlank()) {
                myContacts
            } else {
                val isCodeSearch = searchQuery.length == 6
                workContacts.filter {
                    it.name.contains(searchQuery, true) ||
                            it.familyHead.contains(searchQuery, true) ||
                            it.pan.contains(searchQuery, true) ||
                            (department == "Management" && it.number.contains(searchQuery)) ||
                            (isCodeSearch && it.uniqueCode.equals(searchQuery, ignoreCase = true))
                }.sortedBy {
                    if (isCodeSearch && it.uniqueCode.equals(searchQuery, ignoreCase = true)) 0 else 1
                }
            }
            val deviceResult = if (searchQuery.isBlank()) {
                deviceContacts
            } else {
                deviceContacts.filter { contact ->
                    contact.name.contains(searchQuery, true) ||
                            contact.numbers.any { it.number.contains(searchQuery) }
                }.sortedBy { !it.name.startsWith(searchQuery, true) }
            }
            withContext(Dispatchers.Main) {
                filteredWorkContacts = workResult
                filteredDeviceContacts = deviceResult
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedWorkContact by remember { mutableStateOf<AppContact?>(null) }
    var selectedDeviceContact by remember { mutableStateOf<DeviceContact?>(null) }
    var selectedEmployeeContact by remember { mutableStateOf<AppContact?>(null) }
    var showGlobalRequestDialog by remember { mutableStateOf(false) }
    var globalRequestReason by remember { mutableStateOf("") }
    var contactForRequest by remember { mutableStateOf<String?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E293B))))
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
                                    border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.5f))
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
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Default.Menu, "Menu", tint = Color.White)
                        }
                    },
                    actions = {
                        if (department == "Management" || userEmail == "aayushman@niveshonline.com" || userEmail == "himanshu@niveshonline.com" || userEmail == "pramajeet@niveshonline.com") {
                            Box(modifier = Modifier.padding(end = 8.dp)) {
                                IconButton(onClick = { showSmsScreen = true }) {
                                    Icon(imageVector = Icons.Default.Notifications, contentDescription = "Notifications", tint = Color.White)
                                }
                                if (hasSmsNotification) {
                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.Red).align(Alignment.TopEnd).padding(4.dp))
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent, titleContentColor = Color.White, navigationIconContentColor = Color.White
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
                            TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]), color = Color(0xFF3B82F6), height = 3.dp)
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
                            if (token != null) viewModel.refreshAllAwait(token!!, userName)
                            isRefreshing = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp, 15.dp, 16.dp, 10.dp)) {
                            Box {
                                TextField(
                                    value = searchQuery, onValueChange = { }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
                                    placeholder = { Text("Search contacts...", color = Color.White.copy(alpha = 0.5f)) },
                                    leadingIcon = { Icon(Icons.Default.Search, "Search", tint = Color.White.copy(alpha = 0.6f)) },
                                    trailingIcon = { if (searchQuery.isNotEmpty()) { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, "Clear", tint = Color.White.copy(alpha = 0.6f)) } } },
                                    singleLine = true, enabled = false,
                                    colors = TextFieldDefaults.colors(
                                        focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = Color.White.copy(alpha = 0.1f),
                                        unfocusedContainerColor = Color.White.copy(alpha = 0.08f), disabledContainerColor = Color.White.copy(alpha = 0.08f),
                                        disabledTextColor = Color.White, disabledPlaceholderColor = Color.White.copy(alpha = 0.5f), disabledLeadingIconColor = Color.White.copy(alpha = 0.6f),
                                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent, cursorColor = Color.White
                                    )
                                )
                                Box(modifier = Modifier.matchParentSize().clip(RoundedCornerShape(16.dp)).clickable { showFullSearch = true })
                            }
                        }

                        HorizontalPager(
                            state = pagerState, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.Top
                        ) { page ->
                            when (page) {
                                0 -> PersonalTabContent(
                                    hasContactsPermission = hasContactsPermission,
                                    filteredDeviceContacts = filteredDeviceContacts,
                                    favoriteContacts = favoriteContacts,
                                    searchQuery = searchQuery,
                                    listState = personalListState,
                                    onGrantPermission = { permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_PHONE_STATE)) },
                                    onContactSelected = { contact ->
                                        selectedDeviceContact = contact
                                        scope.launch { sheetState.show() }
                                    }
                                )
                                1 -> WorkTabContent(
                                    uiState = uiState,
                                    workContacts = workContacts,
                                    filteredWorkContacts = filteredWorkContacts,
                                    searchQuery = searchQuery,
                                    listState = workListState,
                                    shimmerOffset = shimmerOffset,
                                    onContactSelected = { contact ->
                                        selectedWorkContact = contact
                                        scope.launch { sheetState.show() }
                                    }
                                )
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
                onCall = { slotIndex -> scope.launch { sheetState.hide() }.invokeOnCompletion { onContactClick(selectedWorkContact!!.number, true, slotIndex); selectedWorkContact = null } },
                isWorkContact = true,
                isDualSim = isDualSim,
                onRequestSubmit = { reason ->
                    if (token != null) {
                        viewModel.submitPersonalRequest(token = token!!, contactName = selectedWorkContact!!.name, userName = userName, reason = reason)
                        Toast.makeText(context, "Request Submitted", Toast.LENGTH_SHORT).show()
                    } else { Toast.makeText(context, "Authentication Error", Toast.LENGTH_SHORT).show() }
                }
            )
        }

        if (selectedDeviceContact != null) {
            ModernDeviceBottomSheet(
                contact = selectedDeviceContact!!,
                sheetState = sheetState,
                isDualSim = isDualSim,
                onDismiss = { scope.launch { sheetState.hide() }.invokeOnCompletion { selectedDeviceContact = null } },
                onCall = { number, slotIndex -> scope.launch { sheetState.hide() }.invokeOnCompletion { onContactClick(number, false, slotIndex); selectedDeviceContact = null } }
            )
        }

        if (selectedEmployeeContact != null) {
            EmployeeBottomSheet(
                contact = selectedEmployeeContact!!,
                sheetState = sheetState,
                isDualSim = isDualSim,
                onDismiss = { scope.launch { sheetState.hide() }.invokeOnCompletion { selectedEmployeeContact = null } },
                onCall = { slotIndex -> scope.launch { sheetState.hide() }.invokeOnCompletion { onContactClick(selectedEmployeeContact!!.number, true, slotIndex); selectedEmployeeContact = null } }
            )
        }

        if (showConflictSheet) {
            ModalBottomSheet(
                onDismissRequest = {},
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { newState -> newState != SheetValue.Hidden }),
                containerColor = Color(0xFF1E293B), contentColor = Color.White, dragHandle = null
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Duplicate Contacts Found", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
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
                                        } catch (e: Exception) { withContext(Dispatchers.Main) { Toast.makeText(context, "Error deleting contacts", Toast.LENGTH_SHORT).show() } }
                                    }
                                } else {
                                    Toast.makeText(context, "Write Permission Required", Toast.LENGTH_SHORT).show()
                                    permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_PHONE_STATE))
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)), shape = RoundedCornerShape(8.dp)
                        ) { Text("Continue to Delete", fontSize = 12.sp) }
                    }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
                        items(conflictingContacts) { contact ->
                            Row(
                                modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp)).padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = contact.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                }
                                Button(
                                    onClick = {
                                        val workMatch = workContacts.firstOrNull { work -> contact.numbers.any { numObj -> sanitizePhoneNumber(work.number) == sanitizePhoneNumber(numObj.number) } }
                                        contactForRequest = workMatch?.name ?: contact.name
                                        showGlobalRequestDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                    shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) { Text("Mark Personal", fontSize = 12.sp, color = Color(0xFF60A5FA)) }
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
                            value = globalRequestReason, onValueChange = { globalRequestReason = it }, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Enter reason...", color = Color.Gray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF3B82F6), unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color(0xFF3B82F6),
                                focusedContainerColor = Color.White.copy(alpha = 0.05f), unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                            ),
                            shape = RoundedCornerShape(12.dp), minLines = 3, maxLines = 5
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (globalRequestReason.isNotBlank() && contactForRequest != null) {
                                if (token != null) {
                                    viewModel.submitPersonalRequest(token = token!!, contactName = contactForRequest!!, userName = userName, reason = globalRequestReason)
                                    Toast.makeText(context, "Request Submitted", Toast.LENGTH_SHORT).show()
                                }
                                showGlobalRequestDialog = false
                                globalRequestReason = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)), shape = RoundedCornerShape(8.dp)
                    ) { Text("Submit", fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = { TextButton(onClick = { showGlobalRequestDialog = false }) { Text("Cancel", color = Color.White.copy(alpha = 0.6f)) } }
            )
        }

        // Search Overlay Logic (This remains as is from your snippet or can be moved if needed, keeping it here for context as it overlays the screen)
        androidx.compose.animation.AnimatedVisibility(
            visible = showFullSearch,
            enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(300)) + fadeIn(tween(300)),
            exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(300)) + fadeOut(tween(300)),
            modifier = Modifier.fillMaxSize().zIndex(999f)
        ) {
            // [Paste the Search Overlay content from your original code here, it uses variables from this scope]
            // For brevity in this response, assume the search overlay code is preserved exactly here.
            // If you want to extract this too, it requires passing many state variables, so it's often better kept in the main screen or extracted carefully.
            var internalQuery by remember { mutableStateOf("") }
            var debouncedQuery by remember { mutableStateOf("") }
            var searchHistory by remember { mutableStateOf(SearchHistoryManager.getHistory(context)) }
            var selectedFilter by remember { mutableStateOf("All") } // Options: All, Personal, Work, Employee
            val focusManager = LocalFocusManager.current
            val keyboardController = LocalSoftwareKeyboardController.current

            // Debounce Logic
            LaunchedEffect(internalQuery) {
                if (internalQuery.isBlank()) {
                    debouncedQuery = ""
                } else {
                    kotlinx.coroutines.delay(300)
                    debouncedQuery = internalQuery
                }
            }

            // Auto-Focus
            LaunchedEffect(Unit) { searchFocusRequester.requestFocus() }

            // --- 1. Enhanced Filtering Logic ---
            val combinedResults = remember(
                debouncedQuery,
                selectedFilter,
                myContacts,
                deviceContacts,
                workContacts,
                department
            ) {
                if (debouncedQuery.isBlank()) emptyList<Any>() else {
                    val isCodeSearch = debouncedQuery.length == 6
                    val results = mutableListOf<Any>()

                    // Helper for text matching
                    fun AppContact.matches(): Boolean {
                        return name.contains(debouncedQuery, true) ||
                                familyHead.contains(debouncedQuery, true) ||
                                pan.contains(debouncedQuery, true) ||
                                (department == "Management" && number.contains(debouncedQuery)) ||
                                (isCodeSearch && uniqueCode.equals(
                                    debouncedQuery,
                                    ignoreCase = true
                                ))
                    }

                    // A. Personal Contacts (Source: deviceContacts)
                    if (selectedFilter == "All" || selectedFilter == "Personal") {
                        results.addAll(deviceContacts.filter { contact ->
                            contact.name.contains(debouncedQuery, true) ||
                                    contact.numbers.any { it.number.contains(debouncedQuery) }
                        })
                    }

                    // B. Work Contacts (Source: myContacts - already filters out Employees)
                    if (selectedFilter == "All" || selectedFilter == "Work") {
                        results.addAll(myContacts.filter { it.matches() })
                    }

                    // C. Employee Contacts (Source: workContacts - specifically where rship is Employee)
                    if (selectedFilter == "All" || selectedFilter == "Employee") {
                        results.addAll(workContacts.filter {
                            it.rshipManager.equals("Employee", ignoreCase = true) && it.matches()
                        })
                    }

                    // [!code change] UPDATED SORTING LOGIC
                    results.sortedWith(
                        compareBy<Any> { item ->
                            // LEVEL 0: If searching by Number, Personal Contacts go TOP
                            val isNumericSearch = debouncedQuery.all { it.isDigit() }
                            if (isNumericSearch) {
                                if (item is DeviceContact) 0 else 1
                            } else {
                                // Default behavior for text: Text matches rely on subsequent filters
                                0
                            }
                        }.thenBy { item ->
                            // LEVEL 1: "My Work Contacts" (Assigned to me) go to TOP
                            // Only prioritize if NOT a numeric search (or treat equally)
                            if (item is AppContact && item.rshipManager.equals(
                                    userName,
                                    ignoreCase = true
                                )
                            ) 0 else 1
                        }.thenBy { item ->
                            // LEVEL 2: Exact Code Match (if query is 6 chars)
                            if (isCodeSearch && item is AppContact && item.uniqueCode.equals(
                                    debouncedQuery,
                                    ignoreCase = true
                                )
                            ) 0 else 1
                        }.thenBy { item ->
                            // LEVEL 3: Alphabetical Name
                            when (item) {
                                is AppContact -> item.name.lowercase()
                                is DeviceContact -> item.name.lowercase()
                                else -> ""
                            }
                        }
                    )
                }
            }

            // --- 2. Full Screen UI ---
            androidx.activity.compose.BackHandler { showFullSearch = false }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F172A))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // Catch clicks to prevent them passing through to the screen behind
                    }
            ) {

                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B))
                            .statusBarsPadding()
                            .padding(bottom = 12.dp)
                    ) {
                        // Search Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, start = 8.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { showFullSearch = false }) {
                                Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                            }
                            TextField(
                                value = internalQuery,
                                onValueChange = { internalQuery = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(searchFocusRequester)
                                    .clip(RoundedCornerShape(16.dp)),
                                placeholder = {
                                    Text(
                                        "Search...",
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color.White.copy(alpha = 0.1f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = Color(0xFF3B82F6)
                                ),
                                trailingIcon = {
                                    if (internalQuery.isNotEmpty()) {
                                        IconButton(onClick = { internalQuery = "" }) {
                                            Icon(
                                                Icons.Default.Close,
                                                "Clear",
                                                tint = Color.White.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // [!code ++] Filter Chips Row
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(listOf("All", "Personal", "Work", "Employee")) { filter ->
                                val isSelected = selectedFilter == filter
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedFilter = filter },
                                    label = { Text(filter) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF3B82F6),
                                        selectedLabelColor = Color.White,
                                        containerColor = Color.White.copy(alpha = 0.05f),
                                        labelColor = Color.White.copy(alpha = 0.7f)
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = Color.Transparent,
                                        enabled = true,
                                        selected = isSelected
                                    )
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    // Results
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = 100.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // [!code ++] HISTORY SECTION (Show when query is blank)
                            if (internalQuery.isBlank()) {
                                if (searchHistory.isNotEmpty()) {
                                    item {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Recent Searches",
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "Clear All",
                                                color = Color(0xFF60A5FA),
                                                fontSize = 12.sp,
                                                modifier = Modifier.clickable {
                                                    SearchHistoryManager.clearHistory(context)
                                                    searchHistory = emptyList()
                                                }
                                            )
                                        }
                                    }
                                    items(searchHistory) { historyItem ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    internalQuery = historyItem
                                                } // Click to search
                                                .padding(vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.History,
                                                null,
                                                tint = Color.White.copy(alpha = 0.4f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Text(
                                                historyItem,
                                                color = Color.White.copy(alpha = 0.9f),
                                                fontSize = 16.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                Icons.Default.NorthWest,
                                                null,
                                                tint = Color.White.copy(alpha = 0.2f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                // [!code ++] RESULTS SECTION (Show when query exists)
                                if (combinedResults.isEmpty() && debouncedQuery.isNotEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 50.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "No matching contacts",
                                                color = Color.White.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }

                                items(combinedResults) { item ->
                                    // Helper to save history on click
                                    val onResultClick: () -> Unit = {
                                        // 1. Save History
                                        SearchHistoryManager.addSearch(context, debouncedQuery)
                                        searchHistory = SearchHistoryManager.getHistory(context)

                                        // 2. Clear Focus & Hide Keyboard Explicitly
                                        keyboardController?.hide()
                                        focusManager.clearFocus()

                                        // 3. Launch Sheet with a tiny delay
                                        // This prevents the keyboard closing animation from "eating" the sheet animation
                                        scope.launch {
                                            delay(100)
                                            sheetState.show()
                                        }
                                    }

                                    when (item) {
                                        is AppContact -> {
                                            if (item.rshipManager.equals(
                                                    "Employee",
                                                    ignoreCase = true
                                                )
                                            ) {
                                                ModernEmployeeCard(
                                                    contact = item,
                                                    highlightQuery = debouncedQuery, // [!code ++] Pass Query
                                                    onClick = {
                                                        selectedEmployeeContact = item
                                                        onResultClick()
                                                    }
                                                )
                                            } else {
                                                ModernWorkContactCard(
                                                    contact = item,
                                                    highlightQuery = debouncedQuery, // [!code ++] Pass Query
                                                    onClick = {
                                                        selectedWorkContact = item
                                                        onResultClick()
                                                    }
                                                )
                                            }
                                        }

                                        is DeviceContact -> {
                                            ModernDeviceContactCard(
                                                contact = item,
                                                highlightQuery = debouncedQuery, // [!code ++] Pass Query
                                                onClick = {
                                                    selectedDeviceContact = item
                                                    onResultClick()
                                                }
                                            )
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
}