package com.mnivesh.callyn.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.widget.Toast
import android.os.Environment
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.zIndex
import com.mnivesh.callyn.components.AppDrawer
import com.mnivesh.callyn.CallynApplication
import com.mnivesh.callyn.managers.AuthManager
import com.mnivesh.callyn.managers.SimManager
import com.mnivesh.callyn.managers.ViewLimitManager
import kotlin.math.abs
import kotlinx.coroutines.delay

// --- Data Classes ---

data class DeviceNumber(
    val number: String,
    val isDefault: Boolean
)

@Immutable
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
    return palette[abs(name.hashCode()) % palette.size]
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

// [!code ++] ADD AT BOTTOM OF FILE
@Composable
fun getHighlightedText(text: String, query: String): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)

    val startIndex = text.indexOf(query, ignoreCase = true)
    if (startIndex == -1) return AnnotatedString(text)

    val spanStyles = listOf(
        AnnotatedString.Range(
            SpanStyle(color = Color(0xFFCB9C00), fontWeight = FontWeight.ExtraBold),
            start = startIndex,
            end = startIndex + query.length
        )
    )
    return AnnotatedString(text, spanStyles = spanStyles)
}

// --- Main Contact Screen Composable ---
@SuppressLint("SuspiciousIndentation")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(
    onContactClick: (String, Boolean, Int?) -> Unit,
//    onShowRequests: () -> Unit,
//    onShowUserDetails: () -> Unit,
//    onShowCallLogs: () -> Unit,
//    onShowDirectory: () -> Unit,
    onOpenDrawer: () -> Unit,
//    onLogout: () -> Unit
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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

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

    // Sim Count State
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

    // Check SIM initially
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

    // --- FILTER LOGIC ---
    // [!code update] Added SimManager check to hide list if Work SIM is missing
    val myContacts = remember(workContacts, userName, department) {
        if (SimManager.workSimSlot == null) {
            // "Show none to the user" if no Work SIM
            emptyList()
        } else {
            // Otherwise, apply standard logic
            workContacts.filter { contact ->
                val rshipManager = contact.rshipManager ?: ""

                // 1. Global Rule: If rshipManager is "Employee", exclude them immediately
                if (rshipManager.equals("Employee", ignoreCase = true)) {
                    return@filter false
                }

                // 2. Department Logic
                if (department == "Management" || department == "IT Desk" || department == "Operations Dept" || userEmail == "arbind@niveshonline.com") {
                    true // Show all (except the ones excluded above)
                } else {
                    // Show only if assigned to this specific user
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
//        if (department != "Management" && department != "IT Desk"  && !hasCheckedConflicts && workContacts.isNotEmpty() && deviceContacts.isNotEmpty()) {
        if (department == "ConflictContactPaused" && !hasCheckedConflicts && workContacts.isNotEmpty() && deviceContacts.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                val conflicts = deviceContacts.filter { device ->
                    device.numbers.any { numObj ->
                        val deviceNum = sanitizePhoneNumber(numObj.number)
                        if (deviceNum.length < 5) false
                        else workContacts.any { work ->
                            // [!code change] Added check to ignore contacts where rshipManager is "Employee"
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

    // [!code replace]
// 1. Define mutable state to hold the results (initially empty)
    var filteredWorkContacts by remember { mutableStateOf(emptyList<AppContact>()) }
    var filteredDeviceContacts by remember { mutableStateOf(emptyList<DeviceContact>()) }

// 2. Favorites is lightweight, so we can keep it as a simple remember block
    val favoriteContacts = remember(deviceContacts, searchQuery) {
        if (searchQuery.isBlank()) deviceContacts.filter { it.isStarred } else emptyList()
    }

// 3. Perform heavy filtering (MD5 hashing) on a background thread to prevent UI lag
    LaunchedEffect(searchQuery, workContacts, myContacts, department, deviceContacts) {
        withContext(Dispatchers.Default) {
            // --- Filter Work Contacts ---
            val workResult = if (searchQuery.isBlank()) {
                myContacts
            } else {
                val isCodeSearch = searchQuery.length == 6
                workContacts.filter {
                    it.name.contains(searchQuery, true) ||
                            it.familyHead.contains(searchQuery, true) ||
                            it.pan.contains(searchQuery, true) ||
                            (department == "Management" && it.number.contains(searchQuery)) ||
                            // MD5 hash calculation now happens here (Background Thread) instead of UI Thread
                            (isCodeSearch && it.uniqueCode.equals(searchQuery, ignoreCase = true))
                }.sortedBy {
                    // Prioritize exact code match at the very top
                    if (isCodeSearch && it.uniqueCode.equals(
                            searchQuery,
                            ignoreCase = true
                        )
                    ) 0 else 1
                }
            }

            // --- Filter Device Contacts ---
            val deviceResult = if (searchQuery.isBlank()) {
                deviceContacts
            } else {
                deviceContacts.filter { contact ->
                    contact.name.contains(searchQuery, true) ||
                            contact.numbers.any { it.number.contains(searchQuery) }
                }.sortedBy { !it.name.startsWith(searchQuery, true) }
            }

            // 4. Update UI State on Main Thread
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
    var showShareCodeDialog by remember { mutableStateOf(false) }
    var globalRequestReason by remember { mutableStateOf("") }
    var contactForRequest by remember { mutableStateOf<String?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer"
    )

    // --- MAIN UI ---
//    ModalNavigationDrawer(
//        drawerState = drawerState,
//        drawerContent = {
//            AppDrawer(
//                userName = userName,
//                onSync = {
//                    token?.let { t ->
//                        scope.launch {
//                            val isSuccess = viewModel.refreshContactsAwait(t, userName)
//                            if (isSuccess) Toast.makeText(context, "Sync Successful!", Toast.LENGTH_SHORT).show()
//                            else Toast.makeText(context, "Sync Failed. Check Internet.", Toast.LENGTH_SHORT).show()
//                        }
//                    }
//                },
//                onLogout = { authManager.logout(); onLogout() },
//                onShowRequests = onShowRequests,
//                onShowUserDetails = onShowUserDetails,
//                onShowCallLogs = onShowCallLogs,
//                onShowDirectory = {
//                    scope.launch { drawerState.close() }
//                    onShowDirectory()
//                },
//                onClose = { scope.launch { drawerState.close() } }
//            )
//        }
//    ) {
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
                            Text(
                                "Callyn",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                            if (department == "Management" || department == "IT Desk") {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = Color(0xFF3B82F6).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(
                                        1.dp,
                                        Color(0xFF3B82F6).copy(alpha = 0.5f)
                                    )
                                ) {
                                    Text(
                                        text = " Admin ",
                                        color = Color(0xFF3B82F6),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(
                                            horizontal = 8.dp,
                                            vertical = 3.dp
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
                        text = {
                            CustomTabContent(
                                "Personal",
                                Icons.Default.Person,
                                deviceContacts.size,
                                selectedTabIndex == 0
                            )
                        }
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
                        }
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
                            // [!code ++] ADD THIS BOX WRAPPER
                            Box {
                                TextField(
                                    value = searchQuery, // Visual only
                                    onValueChange = { }, // Disabled
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp)),
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
                                    enabled = false, // IMPORTANT: Disable interaction here
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

                                // [!code ++] ADD THIS INVISIBLE OVERLAY
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { showFullSearch = true }
                                )
                            }
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
                                                arrayOf(
                                                    Manifest.permission.READ_CONTACTS,
                                                    Manifest.permission.WRITE_CONTACTS,
                                                    Manifest.permission.READ_PHONE_STATE
                                                )
                                            )
                                        }
                                    } else if (filteredDeviceContacts.isEmpty() && favoriteContacts.isEmpty()) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            EmptyStateCard(
                                                "No personal contacts found",
                                                Icons.Default.PersonOff
                                            )
                                        }
                                    } else {
                                        LazyColumn(
                                            state = personalListState,
                                            contentPadding = PaddingValues(
                                                top = 8.dp,
                                                bottom = 100.dp
                                            ),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            if (favoriteContacts.isNotEmpty()) {
                                                item {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(bottom = 12.dp)
                                                    ) {
                                                        Text(
                                                            text = "Favourites",
                                                            color = Color(0xFFF59E0B),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp,
                                                            modifier = Modifier.padding(
                                                                bottom = 12.dp,
                                                                start = 4.dp
                                                            )
                                                        )
                                                        LazyRow(
                                                            horizontalArrangement = Arrangement.spacedBy(
                                                                16.dp
                                                            ),
                                                            contentPadding = PaddingValues(
                                                                horizontal = 4.dp
                                                            )
                                                        ) {
                                                            items(favoriteContacts) { contact ->
                                                                FavoriteContactItem(
                                                                    contact = contact,
                                                                    onClick = {
                                                                        selectedDeviceContact =
                                                                            contact
                                                                        scope.launch { sheetState.show() }
                                                                    }
                                                                )
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.height(16.dp))
                                                        HorizontalDivider(
                                                            color = Color.White.copy(
                                                                alpha = 0.1f
                                                            )
                                                        )
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
                                                        modifier = Modifier.padding(
                                                            top = 8.dp,
                                                            start = 4.dp
                                                        )
                                                    )
                                                }
                                            }

                                            items(
                                                filteredDeviceContacts,
                                                key = { it.id }) { contact ->
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
                                            EmptyStateCard(
                                                if (searchQuery.isNotEmpty()) "No matches found" else "No assigned contacts",
                                                Icons.Default.BusinessCenter
                                            )
                                        }
                                    } else {
                                        LazyColumn(
                                            state = workListState,
                                            contentPadding = PaddingValues(
                                                top = 8.dp,
                                                bottom = 100.dp
                                            ),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(
                                                filteredWorkContacts,
                                                key = { it.id }) { contact ->
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
                onDismiss = {
                    scope.launch { sheetState.hide() }
                        .invokeOnCompletion { selectedWorkContact = null }
                },
                onCall = { slotIndex ->
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onContactClick(selectedWorkContact!!.number, true, slotIndex)
                        selectedWorkContact = null
                    }
                },
                isWorkContact = true,
                isDualSim = isDualSim,
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
                },
                onCall = { number, slotIndex ->
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onContactClick(number, false, slotIndex)
                        selectedDeviceContact = null
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
                },
                onCall = { slotIndex ->
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onContactClick(selectedEmployeeContact!!.number, true, slotIndex)
                        selectedEmployeeContact = null
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
                        Text(
                            "Duplicate Contacts Found",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
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
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Continue to Delete", fontSize = 12.sp)
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
                                    .background(
                                        Color.White.copy(alpha = 0.05f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp
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
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(
                                        horizontal = 12.dp,
                                        vertical = 8.dp
                                    )
                                ) {
                                    Text(
                                        "Mark Personal",
                                        fontSize = 12.sp,
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
            AlertDialog(
                onDismissRequest = { showGlobalRequestDialog = false },
                containerColor = Color(0xFF1E293B),
                title = {
                    Text(
                        "Request Change",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                text = {
                    Column {
                        Text(
                            "Why do you want to mark ${contactForRequest ?: "this contact"} as Personal?",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
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
                                    Toast.makeText(context, "Request Submitted", Toast.LENGTH_SHORT)
                                        .show()
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
        // ... (Your existing Scaffold closing brace is above this)

        // [!code ++] PASTE THIS NEW UPDATED BLOCK HERE (OUTSIDE SCAFFOLD)
        androidx.compose.animation.AnimatedVisibility(
            visible = showFullSearch,
            enter = expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = tween(300)
            ) + fadeIn(tween(300)),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(300)
            ) + fadeOut(tween(300)),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(999f) // Max Z-Index to cover everything
        ) {
            // Search & Filter State
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
//}

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
                .background(
                    Brush.linearGradient(
                        listOf(
                            avatarColor,
                            avatarColor.copy(alpha = 0.7f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                getInitials(contact.name),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            contact.name.split(" ").first(),
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun CustomTabContent(text: String, icon: ImageVector, count: Int, isSelected: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    if (isSelected) Color.White.copy(alpha = 0.2f) else Color.White.copy(
                        alpha = 0.1f
                    )
                )
                .padding(6.dp, 2.dp)
        ) {
            Text(
                count.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ModernWorkContactCard(
    contact: AppContact,
    onClick: () -> Unit,
    highlightQuery: String = ""
) {
    val avatarColor = getColorForName(contact.name)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                avatarColor,
                                avatarColor.copy(alpha = 0.7f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    getInitials(contact.name),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = getHighlightedText(contact.name, highlightQuery), // Use helper
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        Icons.Default.FamilyRestroom,
                        null,
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = getHighlightedText(contact.familyHead, highlightQuery), // Use helper
                        fontSize = 13.sp, color = Color(0xFF60A5FA), fontWeight = FontWeight.Medium
                    )
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
                Icon(
                    Icons.Default.Call,
                    "Call",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeBottomSheet(
    contact: AppContact, // Changed from EmployeeDirectory to AppContact to match your list
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
            // Header: Avatar
            Box(
                modifier = Modifier
                    .size(110.dp)
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

            Spacer(modifier = Modifier.height(16.dp))

            // Name
            Text(
                text = contact.name,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Pill
            Surface(
                color = secondaryColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(
                        Icons.Default.Badge,
                        null,
                        tint = secondaryColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Employee",
                        fontSize = 13.sp,
                        color = secondaryColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Details Card
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "CONTACT DETAILS",
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
                        // Phone Row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(primaryColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Phone,
                                    null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Mobile",
                                    fontSize = 11.sp,
                                    color = textSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    contact.number,
                                    fontSize = 16.sp,
                                    color = textPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            IconButton(onClick = {
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Phone", contact.number)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    null,
                                    tint = textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = textSecondary.copy(alpha = 0.1f)
                        )

                        // Department Row (Using familyHead as Department per schema)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF8B5CF6).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Apartment,
                                    null,
                                    tint = Color(0xFF8B5CF6),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    "Department",
                                    fontSize = 11.sp,
                                    color = textSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    if (contact.familyHead.isNotBlank()) contact.familyHead else "N/A",
                                    fontSize = 16.sp,
                                    color = textPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // LOGIC: Show Dual Buttons ONLY if it's Dual SIM AND we assume we don't know the Work Slot yet.
            // If SimManager.workSimSlot is set, we skip the selection and show one "Call Work" button.
            val showDualButtons = isDualSim && SimManager.workSimSlot == null

            if (showDualButtons) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // SIM 1
                    Button(
                        onClick = { onCall(0) },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .shadow(
                                8.dp,
                                RoundedCornerShape(20.dp),
                                ambientColor = Color(0xFF3B82F6),
                                spotColor = Color(0xFF3B82F6)
                            ),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 4.dp
                        )
                    ) {
                        Row(horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Phone, null)
                            Text("  SIM 1", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    // SIM 2
                    Button(
                        onClick = { onCall(1) },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .shadow(
                                8.dp,
                                RoundedCornerShape(20.dp),
                                ambientColor = Color(0xFF10B981),
                                spotColor = Color(0xFF10B981)
                            ),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 4.dp
                        )
                    ) {
                        Row(horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Phone, null)
                            Text("  SIM 2", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Button(
                    onClick = { onCall(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .shadow(
                            12.dp,
                            RoundedCornerShape(20.dp),
                            ambientColor = primaryColor,
                            spotColor = primaryColor
                        ),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 4.dp
                    )
                ) {
                    Icon(Icons.Default.Call, null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    // Update Text to reflect if we are using Work SIM
                    val buttonText = if (SimManager.workSimSlot != null) "Call (Work SIM)" else "Call"
                    Text(text = buttonText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ModernDeviceContactCard(
    contact: DeviceContact,
    onClick: () -> Unit,
    highlightQuery: String = ""
) {
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                avatarColor,
                                avatarColor.copy(alpha = 0.7f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    getInitials(contact.name),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = getHighlightedText(contact.name, highlightQuery), // Use helper
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(displayNumber, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                    if (count > 1) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "+$count",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
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
                Icon(
                    Icons.Default.Call,
                    "Call",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun PermissionRequiredCard(onGrantPermission: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ContactPage,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Contact Permission Required",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                "Allow access to view your personal contacts",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onGrantPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
            ) {
                Text("Grant Permission", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun EmptyStateCard(message: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            message,
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LoadingCard(shimmerOffset: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(1) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.4f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.15f))
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Error,
                null,
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                message,
                color = Color(0xFFEF4444),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ContactDetailRow(icon: ImageVector, label: String, value: String, labelColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(12.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = labelColor, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = labelColor.copy(alpha = 0.8f)
            )
            Text(
                value.ifBlank { "N/A" },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// [!code ++] ADD THIS FUNCTION AT THE BOTTOM OF THE FILE
@Composable
fun ModernEmployeeCard(contact: AppContact, onClick: () -> Unit, highlightQuery: String = "") {
    // Reuse your existing helper
    val avatarColor = getColorForName(contact.name)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                avatarColor,
                                avatarColor.copy(alpha = 0.7f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Reuse your existing helper
                Text(
                    getInitials(contact.name),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = getHighlightedText(contact.name, highlightQuery), // Use helper
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Business,
                        null,
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    // Mapping familyHead to Department as per common schema, or fallback
                    Text(
                        if (contact.familyHead.isNotBlank()) contact.familyHead else "Employee",
                        fontSize = 13.sp,
                        color = Color(0xFF60A5FA),
                        fontWeight = FontWeight.Medium
                    )
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
                Icon(
                    Icons.Default.Call,
                    "Call",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernBottomSheet(
    contact: AppContact,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onCall: (Int?) -> Unit,
    isWorkContact: Boolean,
    isDualSim: Boolean,
    department: String?,
    onRequestSubmit: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showShareCodeDialog by remember { mutableStateOf(false) }
    var showRequestDialog by remember { mutableStateOf(false) }
    var requestReason by remember { mutableStateOf("") }
    val context = LocalContext.current

    var isNumberVisible by remember { mutableStateOf(false) }
    var remainingViews by remember { mutableIntStateOf(ViewLimitManager.getRemainingViews()) }

    // Automatically show number for Management or IT Desk
    LaunchedEffect(department) {
        if (department == "Management") {
            isNumberVisible = true
        }
    }

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
                    Row {
                        IconButton(
                            onClick = { showShareCodeDialog = true },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = surfaceColor,
                                contentColor = textSecondary
                            ),
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                        ) {
                            Icon(Icons.Default.Share, "Share Code", modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))

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
                    }

                    // Custom Dropdown
                    MaterialTheme(
                        shapes = MaterialTheme.shapes.copy(
                            extraSmall = RoundedCornerShape(
                                12.dp
                            )
                        )
                    ) {
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(surfaceColor),
                            offset = DpOffset((-12).dp, 0.dp)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Raise request to mark as personal",
                                        color = textPrimary,
                                        fontSize = 14.sp
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showRequestDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Edit,
                                        null,
                                        tint = workColor,
                                        modifier = Modifier.size(18.dp)
                                    )
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
                            val context = LocalContext.current
                            val haptics = LocalHapticFeedback.current

                            if (isNumberVisible) {
                                // 1. VISIBLE STATE (Standard View)
                                Box(
                                    modifier = Modifier
                                        .combinedClickable(
                                            onClick = {},
                                            onLongClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                val clipboard =
                                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText(
                                                    "Phone Number",
                                                    contact.number.takeLast(10)
                                                )
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(
                                                    context,
                                                    "Number copied",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        )
                                ) {
                                    ModernDetailRow(
                                        Icons.Default.Phone,
                                        "Phone Number",
                                        contact.number.takeLast(10),
                                        Color(0xFF10B981)
                                    )
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = textSecondary.copy(alpha = 0.1f)
                                )
                            } else {
                                // 2. MASKED STATE (Hidden with Button)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Masked Info
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Phone,
                                                null,
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(
                                                "Phone Number",
                                                fontSize = 11.sp,
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontWeight = FontWeight.Medium
                                            )
                                            // Show only last 2 digits
                                            val masked =
                                                if (contact.number.length > 2) "******" + contact.number.takeLast(
                                                    2
                                                ) else "******"
                                            Text(
                                                masked,
                                                fontSize = 16.sp,
                                                color = Color.White,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    // View Button
                                    Button(
                                        onClick = {
                                            // Verify Storage Permission
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                                                Toast.makeText(
                                                    context,
                                                    "Permission required",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                try {
                                                    val intent =
                                                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                                    intent.data =
                                                        Uri.parse("package:${context.packageName}")
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                }
                                                return@Button
                                            }

                                            // Check Limit
                                            if (ViewLimitManager.canViewNumber()) {
                                                ViewLimitManager.incrementViewCount()
                                                remainingViews =
                                                    ViewLimitManager.getRemainingViews()
                                                isNumberVisible = true
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Daily limit exhausted",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(
                                                0xFF3B82F6
                                            )
                                        ),
                                        contentPadding = PaddingValues(
                                            horizontal = 16.dp,
                                            vertical = 0.dp
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text(
                                            "View ($remainingViews)",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = textSecondary.copy(alpha = 0.1f)
                                )
                            }
                            ModernDetailRow(
                                Icons.Default.CreditCard,
                                "PAN",
                                contact.pan,
                                warningColor
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = textSecondary.copy(alpha = 0.1f)
                            )
                            Row(){
                                Box(modifier = Modifier.weight(1f)) {
                                    ModernDetailRow(
                                        Icons.Default.CurrencyRupee,
                                        "AUM",
                                        "₹ " + contact.aum,
                                        Color(0xFF60A5FA)
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    ModernDetailRow(
                                        Icons.Default.Money,
                                        "Family AUM",
                                        "₹ " + contact.familyAum,
                                        Color(0xFF60A5FA)
                                    )
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = textSecondary.copy(alpha = 0.1f)
                            )
                            ModernDetailRow(
                                Icons.Default.FamilyRestroom,
                                "Family Head",
                                contact.familyHead,
                                Color(0xFF81C784)
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = textSecondary.copy(alpha = 0.1f)
                            )
                            ModernDetailRow(
                                Icons.Default.AccountBox,
                                "Relationship Manager",
                                contact.rshipManager ?: "N/A",
                                Color(0xFFC084FC)
                            )
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
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Phone Number", contact.number)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                "Copy",
                                tint = textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- Main Call Button Logic (Split for Dual Sim) ---
            val showDualButtons = isDualSim && SimManager.workSimSlot == null

            if (showDualButtons) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // SIM 1 Button
                    Button(
                        onClick = { onCall(0) }, // Slot 0
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .shadow(
                                8.dp,
                                RoundedCornerShape(20.dp),
                                ambientColor = Color(0xFF3B82F6),
                                spotColor = Color(0xFF3B82F6)
                            ),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)), // Blue
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 4.dp
                        )
                    ) {
                        Row(horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Phone, contentDescription = null)
                            Text("  SIM 1", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // SIM 2 Button
                    Button(
                        onClick = { onCall(1) }, // Slot 1
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .shadow(
                                8.dp,
                                RoundedCornerShape(20.dp),
                                ambientColor = Color(0xFF10B981),
                                spotColor = Color(0xFF10B981)
                            ),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)), // Green
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 4.dp
                        )
                    ) {
                        Row(horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Phone, contentDescription = null)
                            Text("  SIM 2", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Original Single Button
                Button(
                    onClick = { onCall(null) }, // Pass null to trigger smart dial logic
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
                        // Update text to show if we are using a specific SIM automatically
                        text = if(isWorkContact && SimManager.workSimSlot != null) "Call (Work SIM)" else "Call",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
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
                        modifier = Modifier
                            .padding(bottom = 20.dp)
                            .fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = requestReason,
                        onValueChange = { requestReason = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "Type your reason here...",
                                color = textSecondary.copy(alpha = 0.5f)
                            )
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Submit Request", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRequestDialog = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text("Cancel", color = textSecondary, fontWeight = FontWeight.Medium)
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    //hex code dialog
    // ... existing showRequestDialog logic ...

    // [!code ++] Add Share Code Dialog
    if (showShareCodeDialog) {
        AlertDialog(
            onDismissRequest = { showShareCodeDialog = false },
            containerColor = surfaceColor,
            icon = {
                Icon(
                    Icons.Default.QrCode,
                    null,
                    tint = primaryColor,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    "Contact Code",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = textPrimary,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Code Display with Copy
                    Surface(
                        onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Contact Code", contact.uniqueCode)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Code copied!", Toast.LENGTH_SHORT).show()
                        },
                        color = backgroundColor,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = contact.uniqueCode,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                color = primaryColor
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Icon(
                                Icons.Default.ContentCopy,
                                "Copy",
                                tint = textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Share this code with other Callyn users to get this contact.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Share Button
                    Button(
                        onClick = {
                            val shareText =
                                "Hey! Find this contact by entering ${contact.uniqueCode} in the Callyn App."
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Share Contact Code")
                            context.startActivity(shareIntent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Share",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Close Button
                    Button(
                        onClick = { showShareCodeDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            })
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
            Text(
                label,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.5f),
                fontWeight = FontWeight.Medium
            )
            Text(value,
                maxLines = 1, // [!code ++]
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Medium)
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
    onCall: (String, Int?) -> Unit
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
                                    data = Uri.withAppendedPath(
                                        ContactsContract.Contacts.CONTENT_URI,
                                        contact.id
                                    )
                                    putExtra("finishActivityOnSaveCompleted", true)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "Could not edit contact",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        colors = actionButtonColors,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    ) { Icon(Icons.Default.Edit, "Edit Contact", modifier = Modifier.size(20.dp)) }

                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.withAppendedPath(
                                        ContactsContract.Contacts.CONTENT_URI,
                                        contact.id
                                    )
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "Could not open contact",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        colors = actionButtonColors,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            Icons.Default.OpenInNew,
                            "View Contact",
                            modifier = Modifier.size(20.dp)
                        )
                    }
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
            val effectiveDefault =
                if (contact.numbers.size == 1) contact.numbers.first() else defaultNumberObj

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
                                        .clickable {
                                            onCall(
                                                numObj.number,
                                                null
                                            )
                                        } // Default to system choice/auto
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
                                                val clipboard =
                                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(
                                                    ClipData.newPlainText(
                                                        "Phone Number",
                                                        numObj.number
                                                    )
                                                )
                                                Toast.makeText(
                                                    context,
                                                    "Copied",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                null,
                                                tint = textSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))

                                        // Small Call Button
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(primaryColor)
                                                .clickable { onCall(numObj.number, null) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Call,
                                                null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
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
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Phone Number", number)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                "Copy",
                                tint = textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Main Call Button (Split for Dual Sim) ---
            if (effectiveDefault != null) {
                if (isDualSim) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // SIM 1
                        Button(
                            onClick = { onCall(effectiveDefault.number, 0) },
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .shadow(
                                    8.dp,
                                    RoundedCornerShape(20.dp),
                                    ambientColor = Color(0xFF3B82F6),
                                    spotColor = Color(0xFF3B82F6)
                                ),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 4.dp
                            )
                        ) {
                            Row(horizontalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.Phone, contentDescription = null)
                                Text("  SIM 1", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // SIM 2
                        Button(
                            onClick = { onCall(effectiveDefault.number, 1) },
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .shadow(
                                    8.dp,
                                    RoundedCornerShape(20.dp),
                                    ambientColor = Color(0xFF10B981),
                                    spotColor = Color(0xFF10B981)
                                ),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 4.dp
                            )
                        ) {
                            Row(horizontalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.Phone, contentDescription = null)
                                Text("  SIM 2", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Original Single Button
                    Button(
                        onClick = { onCall(effectiveDefault.number, null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .shadow(
                                12.dp,
                                RoundedCornerShape(20.dp),
                                ambientColor = primaryColor,
                                spotColor = primaryColor
                            ),
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
                            text = if (contact.numbers.size > 1) "Call Default" else "Call",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}