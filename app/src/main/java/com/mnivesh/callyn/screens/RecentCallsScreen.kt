package com.mnivesh.callyn.screens

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import android.telephony.SubscriptionManager
import com.mnivesh.callyn.CallynApplication
import com.mnivesh.callyn.components.DeviceContact
import com.mnivesh.callyn.components.DeviceNumber
import com.mnivesh.callyn.db.AppContact
import com.mnivesh.callyn.db.CrmContact
import com.mnivesh.callyn.sheets.CrmBottomSheet
import com.mnivesh.callyn.sheets.EmployeeBottomSheet
import com.mnivesh.callyn.sheets.ModernBottomSheet
import com.mnivesh.callyn.sheets.ModernDeviceBottomSheet
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
import com.mnivesh.callyn.ui.theme.AppTheme
import com.mnivesh.callyn.viewmodels.CallFilter
import com.mnivesh.callyn.viewmodels.RecentCallUiItem
import com.mnivesh.callyn.viewmodels.RecentCallsViewModel
import com.mnivesh.callyn.viewmodels.RecentCallsViewModelFactory
import com.mnivesh.callyn.viewmodels.formatTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// --- Main Composable ---
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecentCallsScreen(
    onCallClick: (String, Boolean, Int?) -> Unit,
    onScreenEntry: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as CallynApplication
    val activity = LocalContext.current as ComponentActivity
    val viewModel: RecentCallsViewModel = viewModel(
        viewModelStoreOwner = activity,
        factory = RecentCallsViewModelFactory(application, application.repository)
    )

    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { onScreenEntry() }

    // --- State ---
    val allCalls by viewModel.mergedCalls.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Overlay State
    var showSearchOverlay by remember { mutableStateOf(false) }

    var activeFilter by remember { mutableStateOf(CallFilter.ALL) }
    var isRefreshing by remember { mutableStateOf(false) }

    val deviceContacts by viewModel.deviceContacts.collectAsState()
    val workContacts by viewModel.workContacts.collectAsState()
    val crmUiState by viewModel.crmUiState.collectAsState()

    // Bottom Sheet UI
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedWorkContact by remember { mutableStateOf<AppContact?>(null) }
    var selectedDeviceContact by remember { mutableStateOf<DeviceContact?>(null) }
    var selectedEmployeeContact by remember { mutableStateOf<AppContact?>(null) }
    var selectedCrmContact by remember { mutableStateOf<CrmContact?>(null) }

    val selectedContactHistory by viewModel.selectedContactHistory.collectAsState()
    val isHistoryLoading by viewModel.isHistoryLoading.collectAsState()

    // Sim Count State
    var isDualSim by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                isDualSim = subManager.activeSubscriptionInfoCount > 1
            } catch (e: Exception) { isDualSim = false }
        }
    }

    val displayLogs = remember(allCalls, activeFilter) {
        allCalls.filter { call ->
            when (activeFilter) {
                CallFilter.ALL -> true
                CallFilter.PERSONAL -> call.type == "Personal"
                CallFilter.WORK -> call.type == "Work"
                CallFilter.MISSED -> call.isMissed
            }
        }
    }

    // Click Handler reused for List and Search
    fun handleItemClick(item: RecentCallUiItem) {
        scope.launch {
            viewModel.fetchHistoryForNumber(item.number)
            val numberStr = item.number.filter { it.isDigit() }.takeLast(10)
            val workContact = withContext(Dispatchers.IO) { application.repository.findWorkContactByNumber(numberStr) }

            if (workContact != null) {
                if (workContact.rshipManager.equals("Employee", ignoreCase = true)) selectedEmployeeContact = workContact
                else selectedWorkContact = workContact
                sheetState.show()
            } else {
                val crmContact = withContext(Dispatchers.IO) { application.repository.findCrmContactByNumber(numberStr) }
                if (crmContact != null) {
                    selectedCrmContact = crmContact
                    sheetState.show()
                } else {
                    selectedDeviceContact = DeviceContact(id = item.id, name = item.name, numbers = listOf(DeviceNumber(item.number, isDefault = true)))
                    sheetState.show()
                }
            }
        }
    }

    val listState = rememberLazyListState()
    val reachedBottom by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 5
        }
    }

    LaunchedEffect(reachedBottom) {
        if (reachedBottom) viewModel.loadNextPage()
    }

    // --- UI Structure ---
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.background)
        )

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    viewModel.refreshAllSuspend()
                    withContext(Dispatchers.IO) { Thread.sleep(500) }
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize().statusBarsPadding()
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 80.sdp()),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Text(
                        text = "Recent Calls",
                        fontSize = 25.ssp(),
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary,
                        modifier = Modifier.padding(top = 24.sdp(), bottom = 16.sdp(), start = 16.sdp(), end = 16.sdp())
                    )
                }

                // Fake Search Bar (Triggers Overlay)
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.sdp())
                            .padding(bottom = 4.sdp())
                            .height(56.sdp())
                            .clip(RoundedCornerShape(12.sdp()))
                            .clickable { showSearchOverlay = true },
                        color = AppTheme.colors.surfaceVariant,
                        shape = RoundedCornerShape(12.sdp())
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.sdp())) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = AppTheme.colors.textSecondary)
                            Spacer(modifier = Modifier.width(12.sdp()))
                            Text("Search name or number...", color = AppTheme.colors.textSecondary, fontSize = 16.ssp())
                        }
                    }
                }

                stickyHeader {
                    Box(modifier = Modifier.fillMaxWidth().background(AppTheme.colors.background)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.sdp(), horizontal = 16.sdp()),
                            horizontalArrangement = Arrangement.spacedBy(8.sdp())
                        ) {
                            FilterChipItem("All", activeFilter == CallFilter.ALL) { activeFilter = CallFilter.ALL }
                            FilterChipItem(label = "Missed", isSelected = activeFilter == CallFilter.MISSED, icon = Icons.Default.CallMissed, onClick = { activeFilter = CallFilter.MISSED })
                            FilterChipItem("Personal", activeFilter == CallFilter.PERSONAL) { activeFilter = CallFilter.PERSONAL }
                            FilterChipItem("Work", activeFilter == CallFilter.WORK) { activeFilter = CallFilter.WORK }
                        }
                    }
                }

                if (displayLogs.isEmpty() && !isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(400.sdp()), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.History, contentDescription = null, tint = AppTheme.colors.textSecondary, modifier = Modifier.size(48.sdp()))
                                Spacer(modifier = Modifier.height(16.sdp()))
                                Text("No calls found", color = AppTheme.colors.textSecondary, fontSize = 16.ssp())
                            }
                        }
                    }
                }

                items(displayLogs, key = { it.id }) { log ->
                    Box(modifier = Modifier.padding(horizontal = 16.sdp(), vertical = 6.sdp())) {
                        RecentCallItem(
                            log = log,
                            onBodyClick = { handleItemClick(log) },
                            onCallClick = {
                                val slotIndex = log.simSlot?.filter { it.isDigit() }?.toIntOrNull()?.let { it - 1 }
                                onCallClick(log.number, log.type.equals("Work", ignoreCase = true), slotIndex)
                            },
                            onDelete = { viewModel.deleteLog(log.providerId) },
                            onBlock = { viewModel.blockNumber(log.number) }
                        )
                    }
                }

                if (isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.sdp()), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.sdp()), color = AppTheme.colors.textSecondary)
                        }
                    }
                }
            }
        }

        // Search Overlay
        SearchOverlay(
            visible = showSearchOverlay,
            onDismiss = { showSearchOverlay = false },
            deviceContacts = deviceContacts,
            workContacts = workContacts,
            myContacts = workContacts,
            crmUiState = crmUiState,
            callLogs = allCalls,
            department = viewModel.department,
            userName = viewModel.userName,
            onSelectDeviceContact = { contact ->
                selectedDeviceContact = contact
                scope.launch { sheetState.show() }
            },
            onSelectWorkContact = { contact ->
                selectedWorkContact = contact
                scope.launch { sheetState.show() }
            },
            onSelectEmployeeContact = { contact ->
                selectedEmployeeContact = contact
                scope.launch { sheetState.show() }
            },
            onSelectCrmContact = { contact ->
                selectedCrmContact = contact
                scope.launch { sheetState.show() }
            },
            onCallLogClick = { log -> handleItemClick(log) },
            onMakeCall = { number, isWork, simSlot ->
                onCallClick(number, isWork, simSlot)
                showSearchOverlay = false
            }
        )

        // --- Bottom Sheets ---
        if (selectedEmployeeContact != null) {
            EmployeeBottomSheet(
                contact = selectedEmployeeContact!!,
                history = selectedContactHistory,
                isLoading = isHistoryLoading,
                sheetState = sheetState,
                initialHistoryExpanded = true,
                isDualSim = isDualSim,
                onShowHistory = { viewModel.fetchHistoryForNumber(selectedEmployeeContact!!.number) },
                onDismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { selectedEmployeeContact = null }
                    viewModel.clearCallHistory()
                },
                onCall = { slotIndex ->
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onCallClick(selectedEmployeeContact!!.number, true, slotIndex)
                        selectedEmployeeContact = null
                    }
                }
            )
        }

        if (selectedWorkContact != null) {
            ModernBottomSheet(
                contact = selectedWorkContact!!,
                history = selectedContactHistory,
                isLoading = isHistoryLoading,
                sheetState = sheetState,
                isWorkContact = true,
                initialHistoryExpanded = true,
                department = viewModel.department,
                onRequestSubmit = { reason ->
                    viewModel.submitPersonalRequest(selectedWorkContact!!.name, reason)
                    Toast.makeText(context, "Request Submitted", Toast.LENGTH_SHORT).show()
                },
                isDualSim = isDualSim,
                onDismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { selectedWorkContact = null }
                    viewModel.clearCallHistory()
                },
                onShowHistory = { viewModel.fetchHistoryForNumber(selectedWorkContact!!.number) },
                onCall = { slotIndex ->
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onCallClick(selectedWorkContact!!.number, true, slotIndex)
                        selectedWorkContact = null
                    }
                }
            )
        }

        if (selectedCrmContact != null) {
            CrmBottomSheet(
                contact = selectedCrmContact!!,
                history = selectedContactHistory,
                isLoading = isHistoryLoading,
                sheetState = sheetState,
                isDualSim = isDualSim,
                initialHistoryExpanded = true,
                onShowHistory = { viewModel.fetchHistoryForNumber(selectedCrmContact!!.number) },
                onDismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { selectedCrmContact = null }
                    viewModel.clearCallHistory()
                },
                onCall = { slotIndex ->
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onCallClick(selectedCrmContact!!.number, true, slotIndex)
                        selectedCrmContact = null
                    }
                }
            )
        }

        if (selectedDeviceContact != null) {
            ModernDeviceBottomSheet(
                contact = selectedDeviceContact!!,
                history = selectedContactHistory,
                isLoading = isHistoryLoading,
                sheetState = sheetState,
                isDualSim = isDualSim,
                initialHistoryExpanded = true,
                onShowHistory = {
                    val number = selectedDeviceContact!!.numbers.firstOrNull()?.number ?: ""
                    viewModel.fetchHistoryForNumber(number)
                },
                onDismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        selectedDeviceContact = null
                        viewModel.clearCallHistory()
                    }
                },
                onCall = { number, slotIndex ->
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onCallClick(number, false, slotIndex)
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
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) Color(0xFF3B82F6) else AppTheme.colors.surfaceVariant
    val textColor = if (isSelected) Color.White else AppTheme.colors.textSecondary

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(50),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.sdp(), vertical = 8.sdp()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else Color(0xFFEF4444),
                    modifier = Modifier.size(16.sdp())
                )
                Spacer(modifier = Modifier.width(6.sdp()))
            }
            Text(
                text = label,
                color = textColor,
                fontSize = 14.ssp(),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentCallItem(
    log: RecentCallUiItem,
    onBodyClick: () -> Unit,
    onCallClick: () -> Unit,
    onDelete: () -> Unit,
    onBlock: () -> Unit
) {
    val isWork = log.type.equals("Work", ignoreCase = true)
    val tagColor = if (isWork) Color(0xFF60A5FA) else Color(0xFF10B981)

    var showMenu by remember { mutableStateOf(false) }

    val icon = when {
        log.isMissed -> Icons.Default.CallMissed
        log.isIncoming -> Icons.Default.CallReceived
        else -> Icons.Default.CallMade
    }

    val iconTint = if (log.isMissed) Color(0xFFEF4444) else tagColor

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onBodyClick,
                onLongClick = { if (!isWork) showMenu = true }
            ),
        shape = RoundedCornerShape(16.sdp()),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
        border = if (AppTheme.colors.isDark) null else BorderStroke(1.sdp(), AppTheme.colors.border)
    ) {
        Box {
            Row(
                modifier = Modifier
                    .padding(16.sdp())
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.sdp())
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.sdp())
                    )
                }

                Spacer(modifier = Modifier.width(16.sdp()))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = log.name.ifBlank { log.number },
                            color = if (log.isMissed) Color(0xFFEF4444) else AppTheme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.ssp(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (!log.simSlot.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(8.sdp()))
                            val simColor = when {
                                log.simSlot.contains("1") -> Color(0xFF10B981)
                                log.simSlot.contains("2") -> Color(0xFF60A5FA)
                                else -> AppTheme.colors.textSecondary
                            }
                            Text(
                                text = log.simSlot,
                                color = simColor,
                                fontSize = 12.ssp(),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.sdp())
                    ) {
                        Icon(
                            imageVector = if (log.type.equals("Work", ignoreCase = true)) Icons.Default.BusinessCenter else Icons.Default.Person,
                            contentDescription = null,
                            tint = tagColor,
                            modifier = Modifier.size(14.sdp())
                        )
                        Text(" • ", color = AppTheme.colors.textSecondary.copy(alpha = 0.5f), fontSize = 11.ssp())
                        Spacer(modifier = Modifier.width(6.sdp()))
                        Text(
                            text = formatTime(log.date),
                            color = AppTheme.colors.textSecondary,
                            fontSize = 12.ssp()
                        )
                        if (log.duration.isNotEmpty() && log.duration != "0s") {
                            Spacer(modifier = Modifier.width(6.sdp()))
                            Text("•", color = AppTheme.colors.textSecondary.copy(alpha = 0.5f), fontSize = 10.ssp())
                            Spacer(modifier = Modifier.width(6.sdp()))
                            Text(
                                text = log.duration,
                                color = AppTheme.colors.textSecondary,
                                fontSize = 12.ssp()
                            )
                        }
                    }
                }

                IconButton(onClick = onCallClick) {
                    Icon(
                        Icons.Default.Call,
                        contentDescription = "Call",
                        tint = AppTheme.colors.textPrimary,
                        modifier = Modifier.size(24.sdp())
                    )
                }
            }

            // Dropdown Menu
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(AppTheme.colors.surface)
            ) {
                DropdownMenuItem(
                    text = { Text("Delete Log", color = AppTheme.colors.textPrimary) },
                    onClick = { showMenu = false; onDelete() },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444)) }
                )
                DropdownMenuItem(
                    text = { Text("Block Number", color = AppTheme.colors.textPrimary) },
                    onClick = { showMenu = false; onBlock() },
                    leadingIcon = { Icon(Icons.Default.Block, null, tint = AppTheme.colors.textPrimary) }
                )
            }
        }
    }
}