package com.mnivesh.callyn.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.BlockedNumberContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mnivesh.callyn.CallynApplication
import com.mnivesh.callyn.components.DeviceContact
import com.mnivesh.callyn.components.DeviceNumber
import com.mnivesh.callyn.data.ContactRepository
import com.mnivesh.callyn.db.AppContact
import com.mnivesh.callyn.db.CrmContact
import com.mnivesh.callyn.db.WorkCallLog
import com.mnivesh.callyn.managers.AuthManager
import com.mnivesh.callyn.screens.sheets.RecentCrmBottomSheet
import com.mnivesh.callyn.screens.sheets.RecentDeviceBottomSheet
import com.mnivesh.callyn.screens.sheets.RecentEmployeeBottomSheet
import com.mnivesh.callyn.screens.sheets.RecentWorkBottomSheet
import com.mnivesh.callyn.sheets.CrmBottomSheet
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
import com.mnivesh.callyn.viewmodels.CrmUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

// --- Filter Enum ---
enum class CallFilter {
    ALL, PERSONAL, WORK, MISSED
}

data class RecentCallUiItem(
    val id: String,
    val providerId: Long = 0,
    val name: String,
    val number: String,
    val type: String, // "Work" or "Personal"
    val date: Long,
    val duration: String,
    val isIncoming: Boolean,
    val isMissed: Boolean = false,
    val simSlot: String? = null
)

// --- ViewModel ---
class RecentCallsViewModel(
    application: Application,
    private val repository: ContactRepository
) : AndroidViewModel(application) {

    // [!code ++] Auth details for filtering logic
    val authManager = AuthManager(application)
    val department = authManager.getDepartment()
    val userName = authManager.getUserName() ?: ""

    // Main List State
    private val _systemLogs = MutableStateFlow<List<RecentCallUiItem>>(emptyList())
    val systemLogs = _systemLogs.asStateFlow()

    private val _workLogs = MutableStateFlow<List<WorkCallLog>>(emptyList())
    val workLogs = _workLogs.asStateFlow()

    // [!code ++] Merged State: Combines System + Work logs automatically
    val mergedCalls = combine(_systemLogs, _workLogs) { system, work ->
        mergeLogs(system, work)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // History Sheet State
    private val _selectedContactHistory = MutableStateFlow<List<RecentCallUiItem>>(emptyList())
    val selectedContactHistory = _selectedContactHistory.asStateFlow()

    private val _isHistoryLoading = MutableStateFlow(false)
    val isHistoryLoading = _isHistoryLoading.asStateFlow()

    // [!code ++] Contacts & CRM Data (Held here for SearchOverlay)
    private val _deviceContacts = MutableStateFlow<List<DeviceContact>>(emptyList())
    val deviceContacts = _deviceContacts.asStateFlow()

    val workContacts = repository.allContacts.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private val _crmUiState = MutableStateFlow(CrmUiState())
    val crmUiState = _crmUiState.asStateFlow()

    // Pagination Flags
    private var endReached = false
    private var loadJob: Job? = null

    // Content Observer for auto-updates
    private val callLogObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            silentRefresh()
        }
    }

    init {
        // Observe Work Logs from DB
        viewModelScope.launch {
            repository.allWorkLogs.collect { _workLogs.value = it }
        }

        // [!code ++] Observe CRM Contacts
        viewModelScope.launch {
            repository.crmContacts.collect { allContacts ->
                _crmUiState.value = _crmUiState.value.copy(
                    tickets = allContacts.filter { it.module == "Tickets" },
                    investmentLeads = allContacts.filter { it.module == "Investment_leads" },
                    insuranceLeads = allContacts.filter { it.module == "Insurance_Leads" }
                )
            }
        }

        // [!code ++] Fetch Device Contacts
        viewModelScope.launch { fetchDeviceContactsInternal() }

        // Register Observer for System Call Log
        try {
            getApplication<Application>().contentResolver.registerContentObserver(
                CallLog.Calls.CONTENT_URI, true, callLogObserver
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Initial Load
        loadNextPage()
    }

    // [!code ++] Merging Logic moved from UI to ViewModel
    private fun mergeLogs(sysLogs: List<RecentCallUiItem>, dbLogs: List<WorkCallLog>): List<RecentCallUiItem> {
        // 1. Convert Work Logs to UI items
        val workUiLogs = dbLogs.map {
            val isIncomingCall = it.direction.equals("incoming", ignoreCase = true) ||
                    it.direction.equals("missed", ignoreCase = true)
            RecentCallUiItem(
                id = "w_${it.id}",
                name = it.name,
                number = it.number,
                type = "Work",
                date = it.timestamp,
                duration = formatDuration(it.duration),
                isIncoming = isIncomingCall,
                isMissed = it.direction.equals("missed", ignoreCase = true),
                simSlot = it.simSlot
            )
        }

        // 2. Duplicate Check
        fun areCallsDuplicate(item1: RecentCallUiItem, item2: RecentCallUiItem): Boolean {
            // A. Normalize Numbers (Last 10 digits to ignore +91, 0, etc.)
            val n1 = item1.number.filter { it.isDigit() }.takeLast(10)
            val n2 = item2.number.filter { it.isDigit() }.takeLast(10)

            // Basic mismatch checks
            if (n1.isEmpty() || n2.isEmpty()) return false
            if (n1 != n2) return false
            if (item1.isIncoming != item2.isIncoming) return false
            if (item1.isMissed != item2.isMissed) return false

            // B. REMOVED strict duration check (item1.duration != item2.duration)
            // Reasons: "1m 0s" vs "60s", or 1-second variance breaks the old logic.

            // C. Time Check
            val timeDiff = abs(item1.date - item2.date)
            // Increased buffer to 10 seconds (10000ms) to handle slight DB write delays
            return timeDiff < 10000
        }

        val merged = if (department == "Management") {
            // Priority: System Logs.
            // We check if a System Log has a matching Work Log. If yes, we can optionally mark it or just keep the system one.
            // Here, we filter out Work logs that already exist in System logs to avoid duplicates.
            val uniqueWorkLogs = workUiLogs.filter { workItem ->
                sysLogs.none { sysItem -> areCallsDuplicate(sysItem, workItem) }
            }
            sysLogs + uniqueWorkLogs
        } else {
            // Priority: Work Logs.
            // We filter out System logs that already exist in Work logs.
            val uniqueSystemLogs = sysLogs.filter { sysItem ->
                workUiLogs.none { workItem -> areCallsDuplicate(sysItem, workItem) }
            }
            workUiLogs + uniqueSystemLogs
        }

        return merged.sortedByDescending { it.date }
    }

    // [!code ++] Fetch Device Contacts Internal
    private suspend fun fetchDeviceContactsInternal() {
        withContext(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return@withContext

                val contactsMap = mutableMapOf<String, DeviceContact>()
                val cursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                )

                cursor?.use {
                    val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    while (it.moveToNext()) {
                        val id = it.getString(idIdx)
                        val name = it.getString(nameIdx) ?: "Unknown"
                        val rawNum = it.getString(numIdx)?.replace("\\s".toRegex(), "") ?: ""
                        if (rawNum.isNotEmpty()) {
                            val numObj = DeviceNumber(rawNum, isDefault = true)
                            if (contactsMap.containsKey(id)) {
                                val existing = contactsMap[id]!!
                                if (existing.numbers.none { n -> n.number == rawNum }) {
                                    contactsMap[id] = existing.copy(numbers = existing.numbers + numObj)
                                }
                            } else {
                                contactsMap[id] = DeviceContact(id, name, listOf(numObj))
                            }
                        }
                    }
                }
                _deviceContacts.value = contactsMap.values.sortedBy { it.name }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // --- Existing Functions (SilentRefresh, RefreshAll, LoadNext, etc.) ---
    fun silentRefresh() {
        if (_isLoading.value) return
        viewModelScope.launch(Dispatchers.IO) {
            val currentSize = _systemLogs.value.size
            val limitToFetch = if (currentSize < 50) 50 else currentSize
            val updatedLogs = fetchSystemCallLogs(getApplication(), limit = limitToFetch)
            withContext(Dispatchers.Main) {
                if (_systemLogs.value != updatedLogs) {
                    _systemLogs.value = updatedLogs
                }
            }
        }
    }

    fun refreshAllSuspend() {
        endReached = false
        viewModelScope.launch(Dispatchers.IO) {
            val newLogs = fetchSystemCallLogs(getApplication(), limit = 50)
            withContext(Dispatchers.Main) {
                _systemLogs.value = newLogs
            }
        }
    }

    fun loadNextPage() {
        if (_isLoading.value || endReached) return
        _isLoading.value = true
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            val lastLogDate = _systemLogs.value.lastOrNull()?.date
            val newLogs = fetchSystemCallLogs(getApplication(), limit = 50, olderThan = lastLogDate)
            withContext(Dispatchers.Main) {
                if (newLogs.isEmpty()) {
                    endReached = true
                } else {
                    val combined = (_systemLogs.value + newLogs).distinctBy { it.id }
                    _systemLogs.value = combined
                }
                _isLoading.value = false
            }
        }
    }

    fun fetchHistoryForNumber(number: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isHistoryLoading.value = true
            _selectedContactHistory.value = emptyList()
            val sysLogs = fetchSystemCallLogs(getApplication(), numberFilter = number)
            val normalizedQuery = number.filter { it.isDigit() }.takeLast(10)
            val localWorkLogs = _workLogs.value.filter {
                it.number.filter { c -> c.isDigit() }.takeLast(10) == normalizedQuery
            }.map { workLog ->
                val isIncoming = workLog.direction.equals("incoming", true) || workLog.direction.equals("missed", true)
                RecentCallUiItem(
                    id = "w_hist_${workLog.id}",
                    name = workLog.name,
                    number = workLog.number,
                    type = "Work",
                    date = workLog.timestamp,
                    duration = formatDuration(workLog.duration),
                    isIncoming = isIncoming,
                    isMissed = workLog.direction.equals("missed", true),
                    simSlot = workLog.simSlot
                )
            }
            val combined = (sysLogs + localWorkLogs).sortedByDescending { it.date }
            withContext(Dispatchers.Main) {
                _selectedContactHistory.value = combined
                _isHistoryLoading.value = false
            }
        }
    }

    fun deleteLog(providerId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val selection = "${CallLog.Calls._ID} = ?"
                val selectionArgs = arrayOf(providerId.toString())
                getApplication<Application>().contentResolver.delete(CallLog.Calls.CONTENT_URI, selection, selectionArgs)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun blockNumber(number: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val values = ContentValues().apply { put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) }
                getApplication<Application>().contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values)
                withContext(Dispatchers.Main) { Toast.makeText(getApplication(), "Number blocked", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(callLogObserver)
        super.onCleared()
    }
}

class RecentCallsViewModelFactory(val app: Application, val repo: ContactRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return RecentCallsViewModel(app, repo) as T
    }
}

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
    // [!code ++] Use mergedCalls directly
    val allCalls by viewModel.mergedCalls.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // [!code ++] Overlay State
    var showSearchOverlay by remember { mutableStateOf(false) }

    var activeFilter by remember { mutableStateOf(CallFilter.ALL) }
    var isRefreshing by remember { mutableStateOf(false) }

    // [!code ++] Fixed: Collecting missing states here
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

    // [!code ++] Sim Count State
    var isDualSim by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                isDualSim = subManager.activeSubscriptionInfoCount > 1
            } catch (e: Exception) { isDualSim = false }
        }
    }

    // [!code ++] Click Handler reused for List and Search
    fun handleItemClick(item: RecentCallUiItem) {
        scope.launch {
            viewModel.fetchHistoryForNumber(item.number)
            val numberStr = item.number.takeLast(10)
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
                .background(Color(0xFF0F172A))
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
                        fontSize = 24.ssp(),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 24.sdp(), bottom = 16.sdp(), start = 16.sdp(), end = 16.sdp())
                    )
                }

                // [!code ++] Fake Search Bar (Triggers Overlay)
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.sdp())
                            .padding(bottom = 4.sdp())
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.sdp()))
                            .clickable { showSearchOverlay = true },
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(12.sdp())
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Search name or number...", color = Color.Gray, fontSize = 14.ssp())
                        }
                    }
                }

                stickyHeader {
                    Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF0F172A))) {
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

                // [!code ++] Simple Filtering (Merging is done in VM)
                val displayLogs = allCalls.filter { call ->
                    when (activeFilter) {
                        CallFilter.ALL -> true
                        CallFilter.PERSONAL -> call.type == "Personal"
                        CallFilter.WORK -> call.type == "Work"
                        CallFilter.MISSED -> call.isMissed
                    }
                }

                if (displayLogs.isEmpty() && !isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(400.sdp()), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.History, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.sdp()))
                                Spacer(modifier = Modifier.height(16.sdp()))
                                Text("No calls found", color = Color.Gray, fontSize = 16.ssp())
                            }
                        }
                    }
                }

                items(displayLogs, key = { it.id }) { log ->
                    Box(modifier = Modifier.padding(horizontal = 16.sdp(), vertical = 6.sdp())) {
                        RecentCallItem(
                            log = log,
                            onBodyClick = { handleItemClick(log) },
                            onCallClick = { onCallClick(log.number, log.type.equals("Work", ignoreCase = true), null) },
                            onDelete = { viewModel.deleteLog(log.providerId) },
                            onBlock = { viewModel.blockNumber(log.number) }
                        )
                    }
                }

                if (isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.sdp()), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.sdp()), color = Color.White.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }

        // [!code ++] Search Overlay
        SearchOverlay(
            visible = showSearchOverlay,
            onDismiss = { showSearchOverlay = false },
            // [!code ++] Pass lists explicitly
            deviceContacts = deviceContacts,
            workContacts = workContacts,
            myContacts = workContacts,
            crmUiState = crmUiState,
            callLogs = allCalls, // [!code ++] Pass merged calls here

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
            onCallLogClick = { log ->
                handleItemClick(log)
            },
            onMakeCall = { number, isWork, simSlot ->
                onCallClick(number, isWork, simSlot)
                showSearchOverlay = false
            }
        )

        // --- Bottom Sheets ---
        if (selectedEmployeeContact != null) {
            RecentEmployeeBottomSheet(
                contact = selectedEmployeeContact!!,
                history = selectedContactHistory,
                isLoading = isHistoryLoading,
                sheetState = sheetState,
                isDualSim = isDualSim,
                onDismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { selectedEmployeeContact = null }
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
            RecentWorkBottomSheet(
                contact = selectedWorkContact!!,
                history = selectedContactHistory,
                isLoading = isHistoryLoading,
                sheetState = sheetState,
                isDualSim = isDualSim,
                onDismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { selectedWorkContact = null }
                },
                onCall = { slotIndex ->
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onCallClick(selectedWorkContact!!.number, true, slotIndex)
                        selectedWorkContact = null
                    }
                }
            )
        }

        //CRM Bottom Sheet
        if (selectedCrmContact != null) {
            RecentCrmBottomSheet(
                contact = selectedCrmContact!!,
                history = selectedContactHistory,
                isLoading = isHistoryLoading,
                sheetState = sheetState,
                isDualSim = isDualSim,
                onDismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { selectedCrmContact = null }
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
            RecentDeviceBottomSheet(
                contact = selectedDeviceContact!!,
                history = selectedContactHistory,
                isLoading = isHistoryLoading,
                sheetState = sheetState,
                isDualSim = isDualSim,
                onDismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { selectedDeviceContact = null }
                },
                onCall = { slotIndex ->
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onCallClick(selectedDeviceContact!!.numbers.first().number, false, slotIndex)
                        selectedDeviceContact = null
                    }
                }
            )
        }
    }
}

// ... (Rest of Helper functions: formatTime, formatDuration, fetchSystemCallLogs, UI Components)
fun formatTime(millis: Long): String {
    val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
    return sdf.format(Date(millis))
}

fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

@SuppressLint("MissingPermission")
suspend fun fetchSystemCallLogs(
    context: Context,
    limit: Int? = null,
    olderThan: Long? = null,
    numberFilter: String? = null
): List<RecentCallUiItem> {
    return withContext(Dispatchers.IO) {
        val logs = mutableListOf<RecentCallUiItem>()
        val simMap = mutableMapOf<String, String>()
        try {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val subManager = context.getSystemService(SubscriptionManager::class.java)
                val activeSims = subManager.activeSubscriptionInfoList
                activeSims?.forEach { info ->
                    simMap[info.subscriptionId.toString()] = "SIM ${info.simSlotIndex + 1}"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.PHONE_ACCOUNT_ID
        )

        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        if (olderThan != null) {
            selectionParts.add("${CallLog.Calls.DATE} < ?")
            selectionArgs.add(olderThan.toString())
        }
        if (numberFilter != null) {
            selectionParts.add("${CallLog.Calls.NUMBER} LIKE ?")
            selectionArgs.add("%${numberFilter.takeLast(10)}%")
        }

        val selection =
            if (selectionParts.isNotEmpty()) selectionParts.joinToString(" AND ") else null
        val sortOrder =
            if (limit != null) "${CallLog.Calls.DATE} DESC LIMIT $limit" else "${CallLog.Calls.DATE} DESC"

        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs.toTypedArray(),
                sortOrder
            )
            cursor?.use {
                // Update indices
                val idIdx = 0
                val numberIdx = 1
                val nameIdx = 2
                val typeIdx = 3
                val dateIdx = 4
                val durationIdx = 5
                val accountIdIdx = 6

                while (it.moveToNext()) {
                    val realId = it.getLong(idIdx)
                    val number = it.getString(numberIdx) ?: "Unknown"
                    val name = it.getString(nameIdx) ?: "Unknown"
                    val type = it.getInt(typeIdx)
                    val date = it.getLong(dateIdx)
                    val durationSec = it.getLong(durationIdx)
                    val accountId = it.getString(accountIdIdx)
                    val simLabel = if (accountId != null) simMap[accountId] else null

                    logs.add(
                        RecentCallUiItem(
                            id = "s_${date}_${number.takeLast(4)}",
                            providerId = realId,
                            name = if (name != "Unknown") name else number,
                            number = number,
                            type = "Personal",
                            date = date,
                            duration = formatDuration(durationSec),
                            isIncoming = type == CallLog.Calls.INCOMING_TYPE || type == CallLog.Calls.MISSED_TYPE,
                            isMissed = type == CallLog.Calls.MISSED_TYPE,
                            simSlot = simLabel
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        logs
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
    val backgroundColor = if (isSelected) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.1f)
    val textColor = if (isSelected) Color.White else Color.Gray

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
                fontSize = 13.ssp(),
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

    // Menu State
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
                onLongClick = {
                    if (!isWork) {
                        showMenu = true
                    }
                }
            ),
        shape = RoundedCornerShape(16.sdp()),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
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
                            color = if (log.isMissed) Color(0xFFEF4444) else Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.ssp(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (!log.simSlot.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(8.sdp()))
                            val simColor = when {
                                log.simSlot.contains("1") -> Color(0xFF10B981)
                                log.simSlot.contains("2") -> Color(0xFF60A5FA)
                                else -> Color.White.copy(alpha = 0.7f)
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
                            imageVector = if (log.type.equals(
                                    "Work",
                                    ignoreCase = true
                                )
                            ) Icons.Default.BusinessCenter else Icons.Default.Person,
                            contentDescription = null,
                            tint = tagColor,
                            modifier = Modifier.size(14.sdp())
                        )
                        Text(" • ", color = Color.White.copy(alpha = 0.3f), fontSize = 11.ssp())
                        Spacer(modifier = Modifier.width(6.sdp()))
                        Text(
                            text = formatTime(log.date),
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.ssp()
                        )
                        if (log.duration.isNotEmpty() && log.duration != "0s") {
                            Spacer(modifier = Modifier.width(6.sdp()))
                            Text("•", color = Color.White.copy(alpha = 0.2f), fontSize = 10.ssp())
                            Spacer(modifier = Modifier.width(6.sdp()))
                            Text(
                                text = log.duration,
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 12.ssp()
                            )
                        }
                    }
                }

                IconButton(onClick = onCallClick) {
                    Icon(
                        Icons.Default.Call,
                        contentDescription = "Call",
                        tint = Color.White,
                        modifier = Modifier.size(24.sdp())
                    )
                }
            }

            // Dropdown Menu
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(Color(0xFF1E293B))
            ) {
                DropdownMenuItem(
                    text = { Text("Delete Log", color = Color.White) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444)) }
                )
                DropdownMenuItem(
                    text = { Text("Block Number", color = Color.White) },
                    onClick = {
                        showMenu = false
                        onBlock()
                    },
                    leadingIcon = { Icon(Icons.Default.Block, null, tint = Color.White) }
                )
            }
        }
    }
}