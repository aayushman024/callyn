package com.mnivesh.callyn.viewmodels

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
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mnivesh.callyn.components.DeviceContact
import com.mnivesh.callyn.components.DeviceNumber
import com.mnivesh.callyn.data.ContactRepository
import com.mnivesh.callyn.db.WorkCallLog
import com.mnivesh.callyn.managers.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
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
    val rawDuration: Long = 0,
    val duration: String,
    val isIncoming: Boolean,
    val isMissed: Boolean = false,
    val simSlot: String? = null
)

// --- Helper Functions ---
fun formatTime(millis: Long): String {
    val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
    return sdf.format(Date(millis))
}

fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

/**
 * Live lookup against device contacts. Resolves names for numbers
 * that were saved AFTER the call was made (stale CACHED_NAME fix).
 */
fun resolveContactName(context: Context, number: String): String? {
    try {
        val uri = android.net.Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            android.net.Uri.encode(number)
        )
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
    } catch (e: Exception) {
        // Ignore lookup failures
    }
    return null
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
                val idIdx = 0
                val numberIdx = 1
                val nameIdx = 2
                val typeIdx = 3
                val dateIdx = 4
                val durationIdx = 5
                val accountIdIdx = 6

                while (it.moveToNext()) {
                    val realId = it.getLong(idIdx)
                    val number = (it.getString(numberIdx) ?: "Unknown").removePrefix("+")
                    val cachedName = it.getString(nameIdx)
                    val type = it.getInt(typeIdx)
                    val date = it.getLong(dateIdx)
                    val durationSec = it.getLong(durationIdx)
                    val accountId = it.getString(accountIdIdx)
                    // PHONE_ACCOUNT_ID may be a bare sub-ID ("3") or a full component
                    // string ("com.android.phone:subId/3"). Extract trailing digits to normalise.
                    val simLabel = if (accountId != null) {
                        simMap[accountId]                          // exact match first
                            ?: accountId.filter { it.isDigit() }  // strip non-digits
                                .takeIf { it.isNotEmpty() }
                                ?.let { digits ->
                                    simMap[digits]                 // try numeric suffix
                                        ?: simMap[digits.takeLast(1)] // last digit only
                                }
                    } else null

                    val name = if (!cachedName.isNullOrBlank() && cachedName != "Unknown") {
                        cachedName
                    } else {
                        resolveContactName(context, number) ?: number
                    }

                    logs.add(
                        RecentCallUiItem(
                            id = "s_${date}_${number.takeLast(4)}",
                            providerId = realId,
                            name = name,
                            number = number,
                            type = "Personal",
                            date = date,
                            rawDuration = durationSec,
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

// --- ViewModel ---
class RecentCallsViewModel(
    application: Application,
    private val repository: ContactRepository
) : AndroidViewModel(application) {

    val authManager = AuthManager(application)
    val department = authManager.getDepartment()
    val userName = authManager.getUserName() ?: ""
    private val token = authManager.getToken()

    // Main List State
    private val _systemLogs = MutableStateFlow<List<RecentCallUiItem>>(emptyList())
    val systemLogs = _systemLogs.asStateFlow()

    private val _workLogs = MutableStateFlow<List<WorkCallLog>>(emptyList())
    val workLogs = _workLogs.asStateFlow()

    // Merged State: Combines System + Work logs automatically
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

    // Contacts & CRM Data (held here for SearchOverlay)
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

        // Observe CRM Contacts
        viewModelScope.launch {
            repository.crmContacts.collect { allContacts ->
                _crmUiState.value = _crmUiState.value.copy(
                    tickets = allContacts.filter { it.module == "Tickets" },
                    investmentLeads = allContacts.filter { it.module == "Investment_leads" },
                    insuranceLeads = allContacts.filter { it.module == "Insurance_Leads" }
                )
            }
        }

        // Fetch Device Contacts
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

    fun submitPersonalRequest(contactName: String, reason: String) {
        if (token.isNullOrBlank()) return
        viewModelScope.launch {
            repository.submitPersonalRequest(token, contactName, userName, reason)
        }
    }

    fun clearCallHistory() {
        _selectedContactHistory.value = emptyList()
        _isHistoryLoading.value = false
    }

    private fun mergeLogs(
        sysLogs: List<RecentCallUiItem>,
        dbLogs: List<WorkCallLog>
    ): List<RecentCallUiItem> {

        val TIME_BUFFER_MS = 5 * 1000L
        val DUR_BUFFER_SEC = 5

        fun normalize(num: String) =
            num.filter { it.isDigit() }.takeLast(10)

        fun isSameCall(primary: RecentCallUiItem, candidate: RecentCallUiItem): Boolean {
            if (normalize(primary.number) != normalize(candidate.number)) return false
            if (primary.isIncoming != candidate.isIncoming) return false

            val timeDiff = abs(primary.date - candidate.date)
            if (timeDiff > TIME_BUFFER_MS) return false

            if (!primary.isMissed && !candidate.isMissed) {
                val durDiff = abs(primary.rawDuration - candidate.rawDuration)
                if (durDiff > DUR_BUFFER_SEC) return false
            }

            return true
        }

        val workUiLogs = dbLogs.map {
            val isIncomingCall =
                it.direction.equals("incoming", true) ||
                        it.direction.equals("missed", true)

            RecentCallUiItem(
                id = "w_${it.id}",
                name = it.name,
                number = it.number,
                type = "Work",
                date = it.timestamp,
                rawDuration = it.duration,
                duration = formatDuration(it.duration),
                isIncoming = isIncomingCall,
                isMissed = it.direction.equals("missed", true),
                simSlot = it.simSlot
            )
        }.sortedByDescending { it.date }

        val sortedSysLogs = sysLogs.sortedByDescending { it.date }
        val workMap = workUiLogs.groupBy { normalize(it.number) }

        val usedWork = HashSet<RecentCallUiItem>()
        val usedSysIndices = HashSet<Int>()
        val result = ArrayList<RecentCallUiItem>()

        if (department == "Management") {
            val enriched = sortedSysLogs.map { sysItem ->
                val candidates = workMap[normalize(sysItem.number)] ?: emptyList()
                val match = candidates.firstOrNull {
                    !usedWork.contains(it) && isSameCall(sysItem, it)
                }

                if (match != null) {
                    usedWork.add(match)
                    val isUnknown = sysItem.name == sysItem.number || sysItem.name.isBlank()
                    if (isUnknown) sysItem.copy(name = match.name, type = "Work") else sysItem
                } else sysItem
            }
            result.addAll(enriched)
        } else {
            result.addAll(workUiLogs)
            workUiLogs.forEach { workItem ->
                val candidates = sortedSysLogs.withIndex()
                    .filter { normalize(it.value.number) == normalize(workItem.number) }
                for ((index, sysItem) in candidates) {
                    if (!usedSysIndices.contains(index) && isSameCall(workItem, sysItem)) {
                        usedSysIndices.add(index)
                        break
                    }
                }
            }
            sortedSysLogs.forEachIndexed { index, item ->
                if (!usedSysIndices.contains(index)) result.add(item)
            }
        }

        return result.sortedByDescending { it.date }
    }

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
