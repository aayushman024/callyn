package com.mnivesh.callyn

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentUris // [!code ++] Added
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.BlockedNumberContract // [!code ++] Added
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable // [!code ++] Added
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mnivesh.callyn.data.ContactRepository
import com.mnivesh.callyn.db.AppContact
import com.mnivesh.callyn.db.WorkCallLog
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// --- Filter Enum ---
enum class CallFilter {
    ALL, PERSONAL, WORK, MISSED
}

data class RecentCallUiItem(
    val id: String,
    val providerId: Long = 0, // [!code ++] Added to identify row for deletion
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

    // Main List State
    private val _systemLogs = MutableStateFlow<List<RecentCallUiItem>>(emptyList())
    val systemLogs = _systemLogs.asStateFlow()

    private val _workLogs = MutableStateFlow<List<WorkCallLog>>(emptyList())
    val workLogs = _workLogs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // History Sheet State
    private val _selectedContactHistory = MutableStateFlow<List<RecentCallUiItem>>(emptyList())
    val selectedContactHistory = _selectedContactHistory.asStateFlow()

    private val _isHistoryLoading = MutableStateFlow(false)
    val isHistoryLoading = _isHistoryLoading.asStateFlow()

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

        // Register Observer for System Call Log
        try {
            getApplication<Application>().contentResolver.registerContentObserver(
                CallLog.Calls.CONTENT_URI, true, callLogObserver
            )
        } catch (e: Exception) { e.printStackTrace() }

        // Initial Load
        loadNextPage()
    }

    /**
     * Silent Refresh: Called when DB changes (e.g. marked as read).
     */
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

    /**
     * Pull-to-Refresh: Explicitly resets the list to the top 50.
     */
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

            // 1. Fetch System Logs
            val sysLogs = fetchSystemCallLogs(getApplication(), numberFilter = number)

            // 2. Filter Work Logs (Fixed Logic)
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
                    simSlot = null
                )
            }

            // 3. Merge and Sort
            val combined = (sysLogs + localWorkLogs).sortedByDescending { it.date }

            withContext(Dispatchers.Main) {
                _selectedContactHistory.value = combined
                _isHistoryLoading.value = false
            }
        }
    }

    // [!code ++] Delete Log
    // [!code ++] Updated deleteLog function to fix crash
    fun deleteLog(providerId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Fix: Use selection clause instead of appending ID to URI
                val selection = "${CallLog.Calls._ID} = ?"
                val selectionArgs = arrayOf(providerId.toString())

                getApplication<Application>().contentResolver.delete(
                    CallLog.Calls.CONTENT_URI,
                    selection,
                    selectionArgs
                )
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    // Requires WRITE_CALL_LOG permission or Default Dialer status
                    Toast.makeText(getApplication(), "Could not delete. Check permissions.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // [!code ++] Block Number
    fun blockNumber(number: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val values = android.content.ContentValues().apply {
                    put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
                }
                getApplication<Application>().contentResolver.insert(
                    BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                    values
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Number blocked", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Failed to block. Ensure app is Default Dialer.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(callLogObserver)
        super.onCleared()
    }
}

class RecentCallsViewModelFactory(val app: Application, val repo: ContactRepository) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return RecentCallsViewModel(app, repo) as T
    }
}

// --- Main Composable ---
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecentCallsScreen(
    onCallClick: (String, Int?) -> Unit,
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
    val workLogs by viewModel.workLogs.collectAsState()
    val systemLogs by viewModel.systemLogs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val selectedContactHistory by viewModel.selectedContactHistory.collectAsState()
    val isHistoryLoading by viewModel.isHistoryLoading.collectAsState()

    var allCalls by remember { mutableStateOf<List<RecentCallUiItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(CallFilter.ALL) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Sim Count State
    var isDualSim by remember { mutableStateOf(false) }

    // Check SIM Status
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                val activeSims = subManager.activeSubscriptionInfoCount
                isDualSim = activeSims > 1
            } catch (e: Exception) {
                isDualSim = false
            }
        }
    }

    // Merging Logic (Background)
    LaunchedEffect(workLogs, systemLogs) {
        withContext(Dispatchers.Default) {
            val workUiLogs = workLogs.map {
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
                    simSlot = null
                )
            }
            val merged = (systemLogs + workUiLogs).sortedByDescending { it.date }
            withContext(Dispatchers.Main) { allCalls = merged }
        }
    }

    // Bottom Sheet UI
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedWorkContact by remember { mutableStateOf<AppContact?>(null) }
    var selectedDeviceContact by remember { mutableStateOf<DeviceContact?>(null) }

    // Filtering
    val authManager = remember { AuthManager(context) }
    val department = remember { authManager.getDepartment() }

    val filteredCalls = remember(allCalls, searchQuery, activeFilter, department) {
        allCalls.filter { call ->
            val isWorkCall = call.type == "Work"
            val matchesSearch = if (searchQuery.isBlank()) true else {
                val nameMatch = call.name.contains(searchQuery, ignoreCase = true)
                val numberMatch = if (isWorkCall && department != "Management") false else call.number.contains(searchQuery)
                nameMatch || numberMatch
            }
            val matchesFilter = when (activeFilter) {
                CallFilter.ALL -> true
                CallFilter.PERSONAL -> call.type == "Personal"
                CallFilter.WORK -> call.type == "Work"
                CallFilter.MISSED -> call.isMissed
            }
            matchesSearch && matchesFilter
        }
    }

    fun onItemClicked(item: RecentCallUiItem) {
        scope.launch {
            viewModel.fetchHistoryForNumber(item.number)

            if (item.type == "Work") {
                val workContact = withContext(Dispatchers.IO) {
                    application.repository.findWorkContactByNumber(item.number.takeLast(10))
                }
                if (workContact != null) {
                    selectedWorkContact = workContact
                    sheetState.show()
                } else {
                    selectedDeviceContact = DeviceContact(
                        id = item.id, name = item.name, numbers = listOf(DeviceNumber(item.number, isDefault = true))
                    )
                    sheetState.show()
                }
            } else {
                selectedDeviceContact = DeviceContact(
                    id = item.id, name = item.name, numbers = listOf(DeviceNumber(item.number, isDefault = true))
                )
                sheetState.show()
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
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A)))

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

                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.sdp()).padding(bottom = 4.sdp()),
                        placeholder = { Text("Search name or number...", color = Color.Gray, fontSize = 14.ssp()) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray) }
                            }
                        },
                        shape = RoundedCornerShape(12.sdp()),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6), unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color(0xFF3B82F6),
                            focusedContainerColor = Color.White.copy(alpha = 0.05f), unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        singleLine = true
                    )
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

                if (filteredCalls.isEmpty() && !isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(400.sdp()), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.History, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.sdp()))
                                Spacer(modifier = Modifier.height(16.sdp()))
                                Text("No matching calls", color = Color.Gray, fontSize = 16.ssp())
                            }
                        }
                    }
                }

                items(filteredCalls, key = { it.id }) { log ->
                    Box(modifier = Modifier.padding(horizontal = 16.sdp(), vertical = 6.sdp())) {
                        RecentCallItem(
                            log = log,
                            onBodyClick = { onItemClicked(log) },
                            onCallClick = { onCallClick(log.number, null) },
                            onDelete = { viewModel.deleteLog(log.providerId) }, // [!code ++]
                            onBlock = { viewModel.blockNumber(log.number) }     // [!code ++]
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

        // --- Bottom Sheets ---
        if (selectedWorkContact != null) {
            RecentWorkBottomSheet(
                contact = selectedWorkContact!!,
                history = selectedContactHistory,
                isLoading = isHistoryLoading,
                sheetState = sheetState,
                isDualSim = isDualSim,
                onDismiss = { scope.launch { sheetState.hide() }.invokeOnCompletion { selectedWorkContact = null } },
                onCall = { slotIndex ->
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onCallClick(selectedWorkContact!!.number, slotIndex)
                        selectedWorkContact = null
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
                onDismiss = { scope.launch { sheetState.hide() }.invokeOnCompletion { selectedDeviceContact = null } },
                onCall = { slotIndex ->
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onCallClick(selectedDeviceContact!!.numbers.first().number, slotIndex)
                        selectedDeviceContact = null
                    }
                }
            )
        }
    }
}

// ... (Rest of Helper functions and UI components)

fun formatTime(millis: Long): String {
    val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
    return sdf.format(Date(millis))
}

fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

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
        .mapNotNull { word -> word.firstOrNull { it.isLetter() }?.uppercaseChar() }
        .take(2)
        .joinToString("")
        .ifEmpty {
            name.firstOrNull { it.isLetter() }?.uppercase() ?: ""
        }
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
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
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
            CallLog.Calls._ID, // [!code ++] Add ID
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

        val selection = if (selectionParts.isNotEmpty()) selectionParts.joinToString(" AND ") else null
        val sortOrder = if (limit != null) "${CallLog.Calls.DATE} DESC LIMIT $limit" else "${CallLog.Calls.DATE} DESC"

        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs.toTypedArray(),
                sortOrder
            )
            cursor?.use {
                // [!code ++] Update indices
                val idIdx = 0
                val numberIdx = 1
                val nameIdx = 2
                val typeIdx = 3
                val dateIdx = 4
                val durationIdx = 5
                val accountIdIdx = 6

                while (it.moveToNext()) {
                    val realId = it.getLong(idIdx) // [!code ++]
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
                            providerId = realId, // [!code ++]
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

@OptIn(ExperimentalFoundationApi::class) // [!code ++]
@Composable
fun RecentCallItem(
    log: RecentCallUiItem,
    onBodyClick: () -> Unit,
    onCallClick: () -> Unit,
    onDelete: () -> Unit, // [!code ++]
    onBlock: () -> Unit   // [!code ++]
) {
    val isWork = log.type.equals("Work", ignoreCase = true)
    val tagColor = if (isWork) Color(0xFF60A5FA) else Color(0xFF10B981)

    // [!code ++] Menu State
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
            // [!code ++] Use combinedClickable for long press
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
        Box { // [!code ++] Wrap for anchoring menu
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
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.sdp()))
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
                            imageVector = if (log.type.equals("Work", ignoreCase = true)) Icons.Default.BusinessCenter else Icons.Default.Person,
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
                    Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(24.sdp()))
                }
            }

            // [!code ++] Dropdown Menu
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

@Composable
fun CallHistoryRow(log: RecentCallUiItem) {
    val icon = when {
        log.isMissed -> Icons.Default.CallMissed
        log.isIncoming -> Icons.Default.CallReceived
        else -> Icons.Default.CallMade
    }
    val iconColor = if (log.isMissed) Color(0xFFEF4444) else if (log.isIncoming) Color(0xFF10B981) else Color(0xFF60A5FA)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.sdp()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.sdp())
        )
        Spacer(modifier = Modifier.width(16.sdp()))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatTime(log.date),
                color = Color.White,
                fontSize = 14.ssp()
            )
            if (!log.simSlot.isNullOrBlank()) {
                Text(
                    text = log.simSlot,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.ssp()
                )
            }
        }
        Text(
            text = log.duration,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.ssp()
        )
    }
}

@Composable
private fun ContactDetailRow(icon: ImageVector, label: String, value: String, labelColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.sdp())).background(Color.White.copy(alpha = 0.05f)).padding(12.sdp()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = labelColor, modifier = Modifier.size(24.sdp()))
        Spacer(modifier = Modifier.width(12.sdp()))
        Column {
            Text(label, fontSize = 12.ssp(), fontWeight = FontWeight.Medium, color = labelColor.copy(alpha = 0.8f))
            Text(value.ifBlank { "N/A" }, fontSize = 16.ssp(), fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentWorkBottomSheet(
    contact: AppContact,
    history: List<RecentCallUiItem>,
    isLoading: Boolean,
    sheetState: SheetState,
    isDualSim: Boolean,
    onDismiss: () -> Unit,
    onCall: (Int?) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color(0xFF1E293B), contentColor = Color.White) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.sdp(), vertical = 16.sdp()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(100.sdp()).clip(CircleShape).background(Brush.linearGradient(listOf(getColorForName(contact.name), getColorForName(contact.name).copy(alpha = 0.7f)))),
                contentAlignment = Alignment.Center
            ) {
                Text(getInitials(contact.name), color = Color.White, fontSize = 36.ssp(), fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(20.sdp()))
            Text(contact.name, fontSize = 24.ssp(), fontWeight = FontWeight.Bold, color = Color.White)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.sdp())) {
                Icon(Icons.Default.BusinessCenter, null, tint = Color(0xFF60A5FA), modifier = Modifier.size(16.sdp()))
                Spacer(modifier = Modifier.width(6.sdp()))
                Text("Work Contact", fontSize = 15.ssp(), color = Color(0xFF60A5FA), fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(16.sdp()))
            ContactDetailRow(Icons.Default.CreditCard, "PAN", contact.pan, Color(0xFFFFB74D))
            Spacer(modifier = Modifier.height(8.sdp()))
            ContactDetailRow(Icons.Default.FamilyRestroom, "Family Head", contact.familyHead, Color(0xFF81C784))
            Spacer(modifier = Modifier.height(8.sdp()))
            ContactDetailRow(Icons.Default.AccountBox, "Relationship Manager", contact.rshipManager ?: "N/A", Color(0xFFC084FC))

            Spacer(modifier = Modifier.height(24.sdp()))

            // Dual SIM Logic
            if (isDualSim) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.sdp())
                ) {
                    // SIM 1 Button
                    Button(
                        onClick = { onCall(0) }, // Slot 0
                        modifier = Modifier
                            .weight(1f)
                            .height(48.sdp())
                            .shadow(8.sdp(), RoundedCornerShape(20.sdp()), ambientColor = Color(0xFF3B82F6), spotColor = Color(0xFF3B82F6)),
                        shape = RoundedCornerShape(20.sdp()),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 4.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Phone, contentDescription = null)
                            Text("  SIM 1", fontSize = 16.ssp(), fontWeight = FontWeight.Bold)
                        }
                    }

                    // SIM 2 Button
                    Button(
                        onClick = { onCall(1) }, // Slot 1
                        modifier = Modifier
                            .weight(1f)
                            .height(48.sdp())
                            .shadow(8.sdp(), RoundedCornerShape(20.sdp()), ambientColor = Color(0xFF10B981), spotColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(20.sdp()),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 4.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Phone, contentDescription = null)
                            Text("  SIM 2", fontSize = 16.ssp(), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Original Single Button
                Button(onClick = { onCall(null) }, modifier = Modifier.fillMaxWidth().height(52.sdp()), shape = RoundedCornerShape(16.sdp()), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))) {
                    Icon(Icons.Default.Call, null, modifier = Modifier.size(24.sdp()))
                    Spacer(modifier = Modifier.width(12.sdp()))
                    Text("Call Now", fontSize = 18.ssp(), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.sdp()))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.sdp()))
            Text(
                text = "Previous Calls",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.ssp(),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.sdp()))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(100.sdp()), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White.copy(alpha = 0.5f))
                }
            } else if (history.isNotEmpty()) {
                LazyColumn(modifier = Modifier.heightIn(max = 250.sdp())) {
                    items(history) { log ->
                        CallHistoryRow(log)
                    }
                }
            } else {
                Text("No recent history", color = Color.White.copy(alpha = 0.3f), fontSize = 12.ssp(), modifier = Modifier.align(Alignment.CenterHorizontally).padding(20.sdp()))
            }

            Spacer(modifier = Modifier.height(24.sdp()))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentDeviceBottomSheet(
    contact: DeviceContact,
    history: List<RecentCallUiItem>,
    isLoading: Boolean,
    sheetState: SheetState,
    isDualSim: Boolean,
    onDismiss: () -> Unit,
    onCall: (Int?) -> Unit
) {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    val number = contact.numbers.firstOrNull()?.number ?: ""
    var contactUri by remember { mutableStateOf<Uri?>(null) }
    val isUnknown = contactUri == null

    LaunchedEffect(number) {
        if (number.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.withAppendedPath(
                        ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                        Uri.encode(number)
                    )
                    val cursor = contentResolver.query(
                        uri,
                        arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY),
                        null, null, null
                    )
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val id = it.getLong(it.getColumnIndexOrThrow(ContactsContract.PhoneLookup._ID))
                            val lookupKey = it.getString(it.getColumnIndexOrThrow(ContactsContract.PhoneLookup.LOOKUP_KEY))
                            contactUri = ContactsContract.Contacts.getLookupUri(id, lookupKey)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E293B),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.sdp(), vertical = 16.sdp()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(100.sdp())
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    getColorForName(contact.name),
                                    getColorForName(contact.name).copy(alpha = 0.7f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = getInitials(contact.name),
                        color = Color.White,
                        fontSize = 36.ssp(),
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!isUnknown && contactUri != null) {
                    Row(modifier = Modifier.align(Alignment.TopEnd)) {
                        IconButton(onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_EDIT).apply {
                                    data = contactUri
                                    putExtra("finishActivityOnSaveCompleted", true)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot edit contact", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.Edit, "Edit", tint = Color.White.copy(alpha = 0.7f))
                        }

                        IconButton(onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = contactUri
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot open contact", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.OpenInNew, "View", tint = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.sdp()))

            if (!isUnknown) {
                Text(
                    text = contact.name,
                    fontSize = 24.ssp(),
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            } else {
                Text(
                    text = number,
                    fontSize = 24.ssp(),
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.sdp())
            ) {
                Icon(
                    Icons.Default.Person,
                    null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(16.sdp())
                )
                Spacer(modifier = Modifier.width(6.sdp()))
                Text(
                    "Personal Contact",
                    fontSize = 15.ssp(),
                    color = Color(0xFF10B981),
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.sdp())
            ) {
                if (!isUnknown) {
                    Text(
                        number,
                        fontSize = 16.ssp(),
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(10.sdp()))
                }

                if (isUnknown) {
                    Text(
                        "Copy Number",
                        fontSize = 14.ssp(),
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(8.sdp()))
                }

                Icon(
                    Icons.Default.ContentCopy,
                    "Copy",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(18.sdp())
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Phone Number", number)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        }
                )
            }

            if (isUnknown) {
                Spacer(modifier = Modifier.height(16.sdp()))
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_INSERT).apply {
                                type = ContactsContract.Contacts.CONTENT_TYPE
                                putExtra(ContactsContract.Intents.Insert.PHONE, number)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Unable to add contact", Toast.LENGTH_SHORT).show()
                        }
                    },
                    border = androidx.compose.foundation.BorderStroke(1.sdp(), Color(0xFF3B82F6)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF3B82F6)
                    ),
                    shape = RoundedCornerShape(12.sdp())
                ) {
                    Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.sdp()))
                    Spacer(modifier = Modifier.width(8.sdp()))
                    Text("Add to Contacts", fontSize = 14.ssp())
                }
            }

            Spacer(modifier = Modifier.height(32.sdp()))

            // Dual SIM Logic
            if (isDualSim) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.sdp())
                ) {
                    // SIM 1 Button
                    Button(
                        onClick = { onCall(0) }, // Slot 0
                        modifier = Modifier
                            .weight(1f)
                            .height(48.sdp())
                            .shadow(8.sdp(), RoundedCornerShape(20.sdp()), ambientColor = Color(0xFF3B82F6), spotColor = Color(0xFF3B82F6)),
                        shape = RoundedCornerShape(20.sdp()),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 4.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Phone, contentDescription = null)
                            Text("  SIM 1", fontSize = 16.ssp(), fontWeight = FontWeight.Bold)

                        }
                    }

                    // SIM 2 Button
                    Button(
                        onClick = { onCall(1) }, // Slot 1
                        modifier = Modifier
                            .weight(1f)
                            .height(48.sdp())
                            .shadow(8.sdp(), RoundedCornerShape(20.sdp()), ambientColor = Color(0xFF10B981), spotColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(20.sdp()),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 4.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Phone, contentDescription = null)
                            Text("  SIM 2", fontSize = 16.ssp(), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Original Single Button
                Button(
                    onClick = { onCall(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.sdp()),
                    shape = RoundedCornerShape(16.sdp()),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Icon(Icons.Default.Call, null, modifier = Modifier.size(24.sdp()))
                    Spacer(modifier = Modifier.width(12.sdp()))
                    Text("Call Now", fontSize = 18.ssp(), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.sdp()))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.sdp()))
            Text(
                text = "Previous Calls",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.ssp(),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.sdp()))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(100.sdp()), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White.copy(alpha = 0.5f))
                }
            } else if (history.isNotEmpty()) {
                LazyColumn(modifier = Modifier.heightIn(max = 250.sdp())) {
                    items(history) { log ->
                        CallHistoryRow(log)
                    }
                }
            } else {
                Text("No recent history", color = Color.White.copy(alpha = 0.3f), fontSize = 12.ssp(), modifier = Modifier.align(Alignment.CenterHorizontally).padding(20.sdp()))
            }

            Spacer(modifier = Modifier.height(24.sdp()))
        }
    }
}