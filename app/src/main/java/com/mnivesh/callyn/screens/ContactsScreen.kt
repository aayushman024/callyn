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
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
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
import com.mnivesh.callyn.R
import com.mnivesh.callyn.managers.AuthManager
import com.mnivesh.callyn.managers.SimManager
import com.mnivesh.callyn.viewmodels.SmsViewModel
import kotlinx.coroutines.delay
import com.mnivesh.callyn.components.*
import com.mnivesh.callyn.sheets.*
import com.mnivesh.callyn.tabs.*
import com.mnivesh.callyn.components.DeviceContact
import com.mnivesh.callyn.db.CrmContact
import com.mnivesh.callyn.viewmodels.CrmViewModel
import com.mnivesh.callyn.viewmodels.CrmViewModelFactory
import kotlinx.coroutines.joinAll
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
// [!code ++] Import CrmContactCard
import com.mnivesh.callyn.tabs.CrmContactCard

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

    val crmViewModel: CrmViewModel = viewModel(
        factory = CrmViewModelFactory(application, application.repository)
    )
    val crmUiState by crmViewModel.uiState.collectAsState()

    val recentCallsViewModel: RecentCallsViewModel = viewModel(
        factory = RecentCallsViewModelFactory(application, application.repository)
    )
    val callLogs by recentCallsViewModel.mergedCalls.collectAsState()

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

    val pagerState = rememberPagerState(initialPage = 0) { 3 }
    val selectedTabIndex = pagerState.currentPage
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    // --- PERMISSIONS ---
    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasWritePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isDualSim by remember { mutableStateOf(false) }

    fun checkSimStatus() {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val subManager =
                    context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                val activeSims = subManager.activeSubscriptionInfoCount
                isDualSim = activeSims > 1
            } catch (e: Exception) {
                isDualSim = false
            }
        } else {
            isDualSim = false
        }
    }

    LaunchedEffect(Unit) { checkSimStatus() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions[Manifest.permission.READ_CONTACTS] == true
        hasContactsPermission = isGranted
        hasWritePermission = permissions[Manifest.permission.WRITE_CONTACTS] == true
        if (permissions[Manifest.permission.READ_PHONE_STATE] == true) {
            checkSimStatus()
        }
        if (isGranted) viewModel.loadDeviceContacts()
    }

    val uiState by viewModel.uiState.collectAsState()
    val workContacts by viewModel.localContacts.collectAsState()
    val deviceContacts by viewModel.deviceContacts.collectAsState()
    val contentResolver = LocalContext.current.contentResolver
    val history by viewModel.contactHistory.collectAsState()
    val isHistoryLoading by viewModel.isHistoryLoading.collectAsState()

    // --- FILTER LOGIC ---
    val myContacts = remember(workContacts, userName, department, userEmail) {
        if (SimManager.workSimSlot == null) {
            emptyList()
        } else {
            workContacts.filter { contact ->
                val rshipManager = contact.rshipManager ?: ""

                // 1. Exclude "Employee" placeholder
                if (rshipManager.equals("Employee", ignoreCase = true)) {
                    return@filter false
                }

                // 2. Filter Visibility
                if (department == "Management" ||
                    department == "IT Desk" ||
                    department == "Operations Dept" ||
                    userEmail == "arbind@niveshonline.com") {
                    true
                } else {
                    rshipManager.equals(userName, ignoreCase = true)
                }
            }.sortedWith(
                compareBy(
                    // 3. Priority Sort: My Clients First
                    { contact ->
                        // Return 0 for my clients (top), 1 for others (bottom)
                        if (contact.rshipManager.equals(userName, ignoreCase = true)) 0 else 1
                    },
                    // 4. Secondary Sort: Alphabetical Name
                    { contact -> contact.name }
                )
            )
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
                    // Priority: Name > Family Head > PAN > Others (Code/Number)
                    when {
                        it.name.contains(searchQuery, true) -> 0
                        it.familyHead.contains(searchQuery, true) -> 1
                        it.pan.contains(searchQuery, true) -> 2
                        else -> 3
                    }
                }
            }

            // device logic remains untouched
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
    var selectedCrmContact by remember { mutableStateOf<CrmContact?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF0F172A))))
        )

        fun handleCallLogClick(item: RecentCallUiItem) {
            scope.launch {
                // Determine contact type for this number
                val numberStr = item.number.takeLast(10)

                val workContact = withContext(Dispatchers.IO) {
                    application.repository.findWorkContactByNumber(numberStr)
                }

                if (workContact != null) {
                    if (workContact.rshipManager.equals("Employee", ignoreCase = true)) {
                        selectedEmployeeContact = workContact
                    } else {
                        selectedWorkContact = workContact
                    }
                    sheetState.show()
                } else {
                    val crmContact = withContext(Dispatchers.IO) {
                        application.repository.findCrmContactByNumber(numberStr)
                    }

                    if (crmContact != null) {
                        selectedCrmContact = crmContact
                        sheetState.show()
                    } else {
                        selectedDeviceContact = DeviceContact(
                            id = item.id,
                            name = item.name,
                            numbers = listOf(DeviceNumber(item.number, isDefault = true))
                        )
                        sheetState.show()
                    }
                }
            }
        }

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Callyn",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.ssp()
                            )
                            if (department == "Management" || department == "IT Desk") {
                                Spacer(modifier = Modifier.width(8.sdp()))
                                Surface(
                                    color = Color(0xFF3B82F6).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(
                                        1.sdp(),
                                        Color(0xFF3B82F6).copy(alpha = 0.5f)
                                    )
                                ) {
                                    Text(
                                        text = " Admin ",
                                        color = Color(0xFF3B82F6),
                                        fontSize = 10.ssp(),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(
                                            horizontal = 8.sdp(),
                                            vertical = 3.sdp()
                                        ),
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
                            Box(modifier = Modifier.padding(end = 8.sdp())) {
                                IconButton(onClick = { showSmsScreen = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Notifications",
                                        tint = Color.White
                                    )
                                }
                                if (hasSmsNotification) {
                                    Box(
                                        modifier = Modifier.size(10.sdp()).clip(CircleShape)
                                            .background(Color.Red).align(Alignment.TopEnd)
                                            .padding(4.sdp())
                                    )
                                }
                            }
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

                // TAB LAYOUT MODIFICATIONS
                val configuration = LocalConfiguration.current
                val screenWidth = configuration.screenWidthDp.dp
                val tabWidth = screenWidth * 0.4f

                Spacer(modifier = Modifier.height(16.sdp()))

                // [!code ++] Create separate interaction sources to disable ripples
                val interactionSource1 = remember { MutableInteractionSource() }
                val interactionSource2 = remember { MutableInteractionSource() }
                val interactionSource3 = remember { MutableInteractionSource() }

                CompositionLocalProvider(LocalRippleConfiguration provides null) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        edgePadding = 0.sdp(),
                        indicator = { tabPositions ->
                            if (selectedTabIndex < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(
                                        tabPositions[selectedTabIndex]
                                    ), color = Color(0xFF3B82F6), height = 3.sdp()
                                )
                            }
                        },
                        divider = { HorizontalDivider(color = Color.White.copy(alpha = 0.1f)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                            text = {
                                CustomTabContent(
                                    "Personal",
                                    Icons.Default.Person,
                                    deviceContacts.size,
                                    selectedTabIndex == 0
                                )
                            },
                            modifier = Modifier.width(tabWidth),
                            interactionSource = interactionSource1
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                            text = {
                                CustomTabContent(
                                    "Work",
                                    Icons.Default.BusinessCenter,
                                    myContacts.size,
                                    selectedTabIndex == 1
                                )
                            },
                            modifier = Modifier.width(tabWidth),
                            interactionSource = interactionSource2
                        )
                        Tab(
                            selected = selectedTabIndex == 2,
                            onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                            text = {
                                CustomTabContent(
                                    "CRM Data",
                                    icon = painterResource(id = R.drawable.zoho_logo),
                                    null,
                                    selectedTabIndex == 2
                                )
                            },
                            modifier = Modifier.width(tabWidth),
                            interactionSource = interactionSource3
                        )
                    }
                }

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            if (token != null) {
                                val job1 = launch { viewModel.refreshAllAwait(token!!, userName) }
                                val job2 = launch { crmViewModel.onRefresh(token!!) }
                                joinAll(job1, job2)
                            }
                            isRefreshing = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.sdp(), 15.sdp(), 16.sdp(), 10.sdp())
                        ) {
                            Box {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { },
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(16.sdp())),
                                    placeholder = {
                                        Text(
                                            "Search contacts...",
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Search,
                                            "Search",
                                            tint = Color.White.copy(alpha = 0.6f)
                                        )
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = {
                                                searchQuery = ""
                                            }) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    "Clear",
                                                    tint = Color.White.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    enabled = false,
                                    colors = TextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedContainerColor = Color.White.copy(alpha = 0.1f),
                                        unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                                        disabledContainerColor = Color.White.copy(alpha = 0.08f),
                                        disabledTextColor = Color.White,
                                        disabledPlaceholderColor = Color.White.copy(alpha = 0.5f),
                                        disabledLeadingIconColor = Color.White.copy(alpha = 0.6f),
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        disabledIndicatorColor = Color.Transparent,
                                        cursorColor = Color.White
                                    )
                                )
                                Box(
                                    modifier = Modifier.matchParentSize()
                                        .clip(RoundedCornerShape(16.sdp()))
                                        .clickable { showFullSearch = true })
                            }
                        }

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.sdp()),
                            verticalAlignment = Alignment.Top
                        ) { page ->
                            when (page) {
                                0 -> PersonalTabContent(
                                    hasContactsPermission = hasContactsPermission,
                                    filteredDeviceContacts = filteredDeviceContacts,
                                    favoriteContacts = favoriteContacts,
                                    searchQuery = searchQuery,
                                    listState = personalListState,
                                    onGrantPermission = {
                                        permissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.READ_CONTACTS,
                                                Manifest.permission.WRITE_CONTACTS,
                                                Manifest.permission.READ_PHONE_STATE
                                            )
                                        )
                                    },
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

                                2 -> CrmTabContent(
                                    uiState = crmUiState,
                                    searchQuery = searchQuery,
                                    onContactSelected = { contact ->
                                        selectedCrmContact = contact
                                        scope.launch { sheetState.show() }
                                    },
                                    onRefresh = {
                                        scope.launch {
                                            if (token != null) {
                                                isRefreshing = true
                                                crmViewModel.onRefresh(token!!)
                                                isRefreshing = false
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ... Bottom Sheets (Same as before) ...
        if (selectedWorkContact != null) {
            ModernBottomSheet(
                contact = selectedWorkContact!!,
                sheetState = sheetState,
                department = department,
                onDismiss = {
                    scope.launch { sheetState.hide() }
                        .invokeOnCompletion { selectedWorkContact = null }
                    viewModel.clearCallHistory()
                },
                onCall = { slotIndex ->
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onContactClick(
                            selectedWorkContact!!.number,
                            true,
                            slotIndex
                        ); selectedWorkContact = null
                    }
                },
                isWorkContact = true,
                isDualSim = isDualSim,
                history = history,
                isLoading = isHistoryLoading,
                onShowHistory = {
                    viewModel.fetchCallHistory(selectedWorkContact!!.number, isWork = true)
                },
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
                isDualSim = isDualSim,
                onDismiss = {
                    scope.launch { sheetState.hide() }
                        .invokeOnCompletion { selectedDeviceContact = null }
                    viewModel.clearCallHistory()
                },
                history = history,
                isLoading = isHistoryLoading,
                onShowHistory = {
                    val number = selectedDeviceContact!!.numbers.firstOrNull()?.number ?: ""
                    viewModel.fetchCallHistory(number, isWork = false)
                },
                onCall = { number, slotIndex ->
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onContactClick(
                            number,
                            false,
                            slotIndex
                        ); selectedDeviceContact = null
                    }
                }
            )
        }
        if (selectedEmployeeContact != null) {
            EmployeeBottomSheet(
                contact = selectedEmployeeContact!!,
                sheetState = sheetState,
                isDualSim = isDualSim,
                onDismiss = {
                    scope.launch { sheetState.hide() }
                        .invokeOnCompletion { selectedEmployeeContact = null }
                    viewModel.clearCallHistory()
                },
                onShowHistory = {
                    val number = selectedDeviceContact!!.numbers.firstOrNull()?.number ?: ""
                    viewModel.fetchCallHistory(number, isWork = false)
                },
                history = history,
                isLoading = isHistoryLoading,
                onCall = { slotIndex ->
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onContactClick(
                            selectedEmployeeContact!!.number,
                            true,
                            slotIndex
                        ); selectedEmployeeContact = null
                    }
                }
            )
        }
        if (selectedCrmContact != null) {
            CrmBottomSheet(
                contact = selectedCrmContact!!,
                sheetState = sheetState,
                isDualSim = isDualSim,
                onDismiss = {
                    scope.launch { sheetState.hide() }
                        .invokeOnCompletion { selectedCrmContact = null }
                    viewModel.clearCallHistory()
                },
                onShowHistory = {
                    val number = selectedDeviceContact!!.numbers.firstOrNull()?.number ?: ""
                    viewModel.fetchCallHistory(number, isWork = false)
                },
                history = history,
                isLoading = isHistoryLoading,
                onCall = { slotIndex ->
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onContactClick(
                            selectedCrmContact!!.number,
                            true,
                            slotIndex
                        ); selectedCrmContact = null
                    }
                }
            )
        }

        if (showConflictSheet) {
            // ... (Keep existing conflict sheet)
            ModalBottomSheet(
                onDismissRequest = {},
                sheetState = rememberModalBottomSheetState(
                    skipPartiallyExpanded = true,
                    confirmValueChange = { newState -> newState != SheetValue.Hidden }),
                containerColor = Color(0xFF1E293B), contentColor = Color.White, dragHandle = null
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.sdp(), vertical = 8.sdp())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.sdp()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Duplicate Contacts Found",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.ssp(),
                            color = Color.White
                        )
                        Button(
                            onClick = {
                                if (hasWritePermission) {
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            conflictingContacts.forEach { contact ->
                                                val uri = Uri.withAppendedPath(
                                                    ContactsContract.Contacts.CONTENT_URI,
                                                    contact.id
                                                )
                                                contentResolver.delete(uri, null, null)
                                            }
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(
                                                    context,
                                                    "Cleaned up ${conflictingContacts.size} contacts",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                showConflictSheet = false
                                                viewModel.loadDeviceContacts()
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(
                                                    context,
                                                    "Error deleting contacts",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Write Permission Required",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.READ_CONTACTS,
                                            Manifest.permission.WRITE_CONTACTS,
                                            Manifest.permission.READ_PHONE_STATE
                                        )
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                            shape = RoundedCornerShape(8.sdp())
                        ) { Text("Continue to Delete", fontSize = 12.ssp()) }
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.sdp()),
                        contentPadding = PaddingValues(bottom = 30.sdp())
                    ) {
                        items(conflictingContacts) { contact ->
                            Row(
                                modifier = Modifier.fillMaxWidth().background(
                                    Color.White.copy(alpha = 0.05f),
                                    RoundedCornerShape(12.sdp())
                                ).padding(12.sdp()),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.ssp()
                                    )
                                }
                                Button(
                                    onClick = {
                                        val workMatch = workContacts.firstOrNull { work ->
                                            contact.numbers.any { numObj ->
                                                sanitizePhoneNumber(work.number) == sanitizePhoneNumber(
                                                    numObj.number
                                                )
                                            }
                                        }
                                        contactForRequest = workMatch?.name ?: contact.name
                                        showGlobalRequestDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White.copy(
                                            alpha = 0.1f
                                        )
                                    ),
                                    shape = RoundedCornerShape(8.sdp()),
                                    contentPadding = PaddingValues(
                                        horizontal = 12.sdp(),
                                        vertical = 8.sdp()
                                    )
                                ) {
                                    Text(
                                        "Mark Personal",
                                        fontSize = 12.ssp(),
                                        color = Color(0xFF60A5FA)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showGlobalRequestDialog) {
            // ... (Keep existing global request dialog)
            AlertDialog(
                onDismissRequest = { showGlobalRequestDialog = false },
                containerColor = Color(0xFF1E293B),
                title = {
                    Text(
                        "Request Change",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.ssp()
                    )
                },
                text = {
                    Column {
                        Text(
                            "Why do you want to mark ${contactForRequest ?: "this contact"} as Personal?",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.ssp(),
                            modifier = Modifier.padding(bottom = 16.sdp())
                        )
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
                            shape = RoundedCornerShape(12.sdp()),
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
                                    Toast.makeText(context, "Request Submitted", Toast.LENGTH_SHORT)
                                        .show()
                                }
                                showGlobalRequestDialog = false
                                globalRequestReason = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        shape = RoundedCornerShape(8.sdp())
                    ) { Text("Submit", fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showGlobalRequestDialog = false
                    }) { Text("Cancel", color = Color.White.copy(alpha = 0.6f)) }
                }
            )
        }

        // Search Overlay Logic
        SearchOverlay(
            visible = showFullSearch,
            onDismiss = { showFullSearch = false },
            deviceContacts = deviceContacts,
            workContacts = workContacts,
            myContacts = myContacts,
            callLogs = callLogs,
            crmUiState = crmUiState,
            department = department,
            userName = userName,
            onSelectDeviceContact = { contact ->
                selectedDeviceContact = contact
                scope.launch { delay(100); sheetState.show() }
            },
            onCallLogClick = { log -> handleCallLogClick(log) },
            onMakeCall = { number, isWork, simSlot ->
                onContactClick(number, isWork, simSlot)
            },
            onSelectWorkContact = { contact ->
                selectedWorkContact = contact
                scope.launch { delay(100); sheetState.show() }
            },
            onSelectEmployeeContact = { contact ->
                selectedEmployeeContact = contact
                scope.launch { delay(100); sheetState.show() }
            },
            onSelectCrmContact = { contact ->
                selectedCrmContact = contact
                scope.launch { delay(100); sheetState.show() }
            }
        )
    }
}
