//package com.mnivesh.callyn
//
//import android.app.Application
//import android.content.ContentValues
//import android.database.ContentObserver
//import android.provider.CallLog
//import android.provider.ContactsContract
//import android.util.Log
//import androidx.lifecycle.AndroidViewModel
//import androidx.lifecycle.ViewModel
//import androidx.lifecycle.ViewModelProvider
//import androidx.lifecycle.viewModelScope
//import com.mnivesh.callyn.api.RetrofitInstance
//import com.mnivesh.callyn.api.VersionResponse
//import com.mnivesh.callyn.api.version
//import com.mnivesh.callyn.data.ContactRepository
//import com.mnivesh.callyn.utils.VersionManager
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.Job
//import kotlinx.coroutines.async
//import kotlinx.coroutines.awaitAll
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.SharingStarted
//import kotlinx.coroutines.flow.asStateFlow
//import kotlinx.coroutines.flow.first
//import kotlinx.coroutines.flow.stateIn
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//import java.util.Locale
//
//// --- SHARED DATA CLASSES ---
//data class DeviceNumber(
//    val number: String,
//    val isDefault: Boolean
//)
//
//data class DeviceContact(
//    val id: String,
//    val name: String,
//    val numbers: List<DeviceNumber>,
//    val isStarred: Boolean = false
//)
//
//// Helper class for UI Logs (Ensure this matches your RecentsScreen expectation)
//data class RecentCallUiItem(
//    val id: String,
//    val name: String?,
//    val number: String,
//    val type: String, // "Work" or "Personal"
//    val date: Long,
//    val duration: String,
//    val isIncoming: Boolean,
//    val isMissed: Boolean,
//    val simSlot: Int?
//)
//
//// --- STATES ---
//sealed class MainActivityUiState {
//    object Loading : MainActivityUiState()
//    object LoggedOut : MainActivityUiState()
//    data class LoggedIn(val userName: String) : MainActivityUiState()
//}
//
//data class UpdateState(
//    val isUpdateAvailable: Boolean = false,
//    val isHardUpdate: Boolean = false,
//    val versionInfo: VersionResponse? = null
//)
//
//// --- VIEW MODEL ---
//class MainViewModel(
//    application: Application,
//    private val repository: ContactRepository,
//    private val authManager: AuthManager
//) : AndroidViewModel(application) {
//
//    // 1. UI State
//    private val _uiState = MutableStateFlow<MainActivityUiState>(MainActivityUiState.Loading)
//    val uiState = _uiState.asStateFlow()
//
//    private val _updateState = MutableStateFlow(UpdateState())
//    val updateState = _updateState.asStateFlow()
//
//    // 2. Data State
//    private val _recentLogs = MutableStateFlow<List<RecentCallUiItem>>(emptyList())
//    val recentLogs = _recentLogs.asStateFlow()
//
//    private val _missedCallCount = MutableStateFlow(0)
//    val missedCallCount = _missedCallCount.asStateFlow()
//
//    // Fixed: Explicit type declaration to prevent inference errors
//    private val _deviceContacts = MutableStateFlow<List<DeviceContact>>(emptyList())
//    val deviceContacts = _deviceContacts.asStateFlow()
//
//    val workContacts = repository.allContacts.stateIn(
//        viewModelScope,
//        SharingStarted.WhileSubscribed(5000),
//        emptyList()
//    )
//
//    // 3. Internal Flags
//    private val PAGE_SIZE = 50
//    private var lastLoadedTimestamp: Long? = null
//    private var isLoadingMore = false
//    private var isDeviceContactsLoaded = false
//    private var startupJob: Job? = null
//
//    // 4. System Observer (Auto-refresh logs/badges)
//    private val callLogObserver = object : ContentObserver(null) {
//        override fun onChange(selfChange: Boolean) {
//            refreshLogs()
//            refreshMissedCallCount()
//        }
//    }
//
//    init {
//        try {
//            getApplication<Application>().contentResolver.registerContentObserver(
//                CallLog.Calls.CONTENT_URI, true, callLogObserver
//            )
//        } catch (e: Exception) {
//            Log.e("MainViewModel", "Observer error", e)
//        }
//    }
//
//    override fun onCleared() {
//        try {
//            getApplication<Application>().contentResolver.unregisterContentObserver(callLogObserver)
//        } catch (e: Exception) { /* Ignore */ }
//        super.onCleared()
//    }
//
//    // ===========================
//    // AUTH & STARTUP
//    // ===========================
//
//    fun checkLoginState() {
//        val token = authManager.getToken()
//        val savedName = authManager.getUserName()
//
//        if (token != null) {
//            if (savedName != null) {
//                _uiState.value = MainActivityUiState.LoggedIn(savedName)
//                // Verify token silently
//                viewModelScope.launch {
//                    try {
//                        val header = if (token.startsWith("Bearer ")) token else "Bearer $token"
//                        val response = RetrofitInstance.api.getMe(header)
//                        if (!response.isSuccessful) performLogout()
//                    } catch (e: Exception) { Log.d("VM", "Offline mode") }
//                }
//            } else {
//                fetchUserProfile(token)
//            }
//        } else {
//            _uiState.value = MainActivityUiState.LoggedOut
//        }
//    }
//
//    fun handleDeepLinkLogin(token: String, department: String?) {
//        viewModelScope.launch {
//            authManager.saveToken(token)
//            if (department != null) authManager.saveDepartment(department)
//            fetchUserProfile(token)
//        }
//    }
//
//    private fun fetchUserProfile(token: String) {
//        viewModelScope.launch {
//            _uiState.value = MainActivityUiState.Loading
//            try {
//                val headerToken = if (token.startsWith("Bearer ")) token else "Bearer $token"
//                val response = RetrofitInstance.api.getMe(headerToken)
//
//                if (response.isSuccessful && response.body() != null) {
//                    val name = response.body()!!.name
//                    authManager.saveUserName(name)
//                    _uiState.value = MainActivityUiState.LoggedIn(name)
//                    appStartup()
//                } else {
//                    performLogout()
//                }
//            } catch (e: Exception) {
//                performLogout()
//            }
//        }
//    }
//
//    fun performLogout() {
//        viewModelScope.launch {
//            _uiState.value = MainActivityUiState.Loading
//            repository.clearAllData()
//            authManager.logout()
//            _uiState.value = MainActivityUiState.LoggedOut
//        }
//    }
//
//    // ===========================
//    // DATA LOADING (LOGS & CONTACTS)
//    // ===========================
//
//    fun appStartup() {
//        if (startupJob?.isActive == true) return
//        startupJob = viewModelScope.launch(Dispatchers.IO) {
//            val token = authManager.getToken()
//            val name = authManager.getUserName() ?: ""
//
//            // Parallel execution for speed
//            val versionJob = async { checkAppVersion(token) }
//            val workSyncJob = async {
//                if (!token.isNullOrEmpty()) try { repository.refreshContacts(token, name) } catch (_: Exception) {}
//            }
//            val deviceJob = async { fetchDeviceContactsInternal() }
//            val logsJob = async { fetchMergedLogs(limit = PAGE_SIZE, olderThan = null) }
//            val badgeJob = async { refreshMissedCallCount() }
//
//            awaitAll(versionJob, workSyncJob, deviceJob, logsJob, badgeJob)
//
//            val initialLogs = logsJob.await()
//            withContext(Dispatchers.Main) {
//                _recentLogs.value = initialLogs
//                lastLoadedTimestamp = initialLogs.lastOrNull()?.date
//            }
//        }
//    }
//
//    fun refreshLogs() {
//        viewModelScope.launch(Dispatchers.IO) {
//            val fullLogs = fetchMergedLogs(limit = PAGE_SIZE, olderThan = null)
//            withContext(Dispatchers.Main) {
//                _recentLogs.value = fullLogs
//                lastLoadedTimestamp = fullLogs.lastOrNull()?.date
//            }
//        }
//    }
//
//    fun loadMoreLogs() {
//        if (isLoadingMore) return
//        isLoadingMore = true
//        viewModelScope.launch(Dispatchers.IO) {
//            val nextBatch = fetchMergedLogs(limit = PAGE_SIZE, olderThan = lastLoadedTimestamp)
//            if (nextBatch.isNotEmpty()) {
//                withContext(Dispatchers.Main) {
//                    _recentLogs.value += nextBatch
//                    lastLoadedTimestamp = nextBatch.last().date
//                }
//            }
//            isLoadingMore = false
//        }
//    }
//
//    fun refreshMissedCallCount() {
//        viewModelScope.launch(Dispatchers.IO) {
//            try {
//                val cursor = getApplication<Application>().contentResolver.query(
//                    CallLog.Calls.CONTENT_URI, null,
//                    "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.IS_READ} = ?",
//                    arrayOf(CallLog.Calls.MISSED_TYPE.toString(), "0"), null
//                )
//                val count = cursor?.count ?: 0
//                cursor?.close()
//                _missedCallCount.value = count
//            } catch (e: SecurityException) {
//                _missedCallCount.value = 0
//            } catch (e: Exception) {
//                Log.e("VM", "Count failed", e)
//            }
//        }
//    }
//
//    fun markMissedCallsAsRead() {
//        viewModelScope.launch(Dispatchers.IO) {
//            try {
//                val values = ContentValues().apply { put(CallLog.Calls.IS_READ, 1) }
//                getApplication<Application>().contentResolver.update(
//                    CallLog.Calls.CONTENT_URI, values,
//                    "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.IS_READ} = ?",
//                    arrayOf(CallLog.Calls.MISSED_TYPE.toString(), "0")
//                )
//                _missedCallCount.value = 0
//            } catch (e: Exception) {
//                Log.e("VM", "Mark read failed", e)
//            }
//        }
//    }
//
//    fun loadDeviceContactsIfMissing() {
//        if (!isDeviceContactsLoaded) {
//            viewModelScope.launch(Dispatchers.IO) { fetchDeviceContactsInternal() }
//        }
//    }
//
//    // ===========================
//    // INTERNAL HELPERS
//    // ===========================
//
//    private suspend fun fetchDeviceContactsInternal() {
//        try {
//            val context = getApplication<Application>()
//            // Basic permission check (Safety)
//            if (androidx.core.content.ContextCompat.checkSelfPermission(
//                    context, android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
//                return
//            }
//
//            val contactsMap = mutableMapOf<String, DeviceContact>()
//            val cursor = context.contentResolver.query(
//                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
//                arrayOf(
//                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
//                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
//                    ContactsContract.CommonDataKinds.Phone.NUMBER,
//                    ContactsContract.CommonDataKinds.Phone.STARRED,
//                    ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY
//                ),
//                null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
//            )
//
//            cursor?.use {
//                val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
//                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
//                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
//                val starIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)
//                val defIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY)
//
//                while (it.moveToNext()) {
//                    val id = it.getString(idIdx)
//                    val name = it.getString(nameIdx) ?: "Unknown"
//                    val rawNum = it.getString(numIdx)?.replace("\\s".toRegex(), "") ?: ""
//                    val isStarred = it.getInt(starIdx) == 1
//                    val isDefault = it.getInt(defIdx) > 0
//
//                    if (rawNum.isNotEmpty()) {
//                        val numObj = DeviceNumber(rawNum, isDefault)
//                        if (contactsMap.containsKey(id)) {
//                            val existing = contactsMap[id]!!
//                            if (existing.numbers.none { n -> n.number == rawNum }) {
//                                contactsMap[id] = existing.copy(numbers = existing.numbers + numObj)
//                            }
//                        } else {
//                            contactsMap[id] = DeviceContact(id, name, listOf(numObj), isStarred)
//                        }
//                    }
//                }
//            }
//
//            val sorted = contactsMap.values
//                .map { it.copy(numbers = it.numbers.sortedByDescending { n -> n.isDefault }) }
//                .sortedBy { it.name }
//
//            withContext(Dispatchers.Main) {
//                _deviceContacts.value = sorted
//                isDeviceContactsLoaded = true
//            }
//        } catch (e: Exception) {
//            Log.e("VM", "Contacts fetch failed", e)
//        }
//    }
//
//    private suspend fun fetchMergedLogs(limit: Int, olderThan: Long?): List<RecentCallUiItem> {
//        val context = getApplication<Application>()
//        val systemLogs = mutableListOf<RecentCallUiItem>()
//
//        // 1. Fetch System Logs
//        try {
//            if (androidx.core.content.ContextCompat.checkSelfPermission(
//                    context, android.Manifest.permission.READ_CALL_LOG) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
//
//                var selection = "${CallLog.Calls.DATE} IS NOT NULL"
//                val args = mutableListOf<String>()
//
//                if (olderThan != null) {
//                    selection += " AND ${CallLog.Calls.DATE} < ?"
//                    args.add(olderThan.toString())
//                }
//
//                val cursor = context.contentResolver.query(
//                    CallLog.Calls.CONTENT_URI,
//                    null,
//                    selection,
//                    if(args.isNotEmpty()) args.toTypedArray() else null,
//                    "${CallLog.Calls.DATE} DESC LIMIT $limit"
//                )
//
//                cursor?.use {
//                    val numberIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
//                    val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
//                    val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
//                    val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)
//                    val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
//                    val idIdx = it.getColumnIndex(CallLog.Calls._ID)
//
//                    while (it.moveToNext()) {
//                        val number = it.getString(numberIdx) ?: "Unknown"
//                        val name = it.getString(nameIdx)
//                        val date = it.getLong(dateIdx)
//                        val duration = it.getLong(durIdx)
//                        val type = it.getInt(typeIdx)
//                        val id = it.getString(idIdx)
//
//                        val isIncoming = type == CallLog.Calls.INCOMING_TYPE || type == CallLog.Calls.MISSED_TYPE
//                        val isMissed = type == CallLog.Calls.MISSED_TYPE
//
//                        systemLogs.add(RecentCallUiItem(
//                            id = id,
//                            name = name,
//                            number = number,
//                            type = "Personal",
//                            date = date,
//                            duration = formatDuration(duration),
//                            isIncoming = isIncoming,
//                            isMissed = isMissed,
//                            simSlot = null
//                        ))
//                    }
//                }
//            }
//        } catch (e: Exception) { Log.e("VM", "Log fetch failed", e) }
//
//        // 2. Fetch Work Logs
//        val workLogsDb = repository.allWorkLogs.first()
//        var workUiLogs = workLogsDb.map {
//            val isInc = it.direction.equals("incoming", true) || it.direction.equals("missed", true)
//            RecentCallUiItem(
//                id = "w_${it.id}",
//                name = it.name,
//                number = it.number,
//                type = "Work",
//                date = it.timestamp,
//                duration = formatDuration(it.duration),
//                isIncoming = isInc,
//                isMissed = it.direction.equals("missed", true),
//                simSlot = null
//            )
//        }
//
//        if (olderThan != null) {
//            workUiLogs = workUiLogs.filter { it.date < olderThan }
//        }
//
//        return (systemLogs + workUiLogs)
//            .sortedByDescending { it.date }
//            .take(limit)
//    }
//
//    private suspend fun checkAppVersion(token: String?) {
//        if (token == null) return
//        try {
//            val response = RetrofitInstance.api.getLatestVersion("Bearer $token")
//            if (response.isSuccessful && response.body() != null) {
//                val remote = response.body()!!
//                if (VersionManager.isUpdateNeeded(version, remote.latestVersion)) {
//                    _updateState.value = UpdateState(true, remote.updateType == "hard", remote)
//                }
//            }
//        } catch (e: Exception) { Log.e("VM", "Update check error", e) }
//    }
//
//    private fun formatDuration(seconds: Long): String {
//        return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
//    }
//}
//
//// Factory for ViewModel
//class MainViewModelFactory(
//    private val application: Application,
//    private val repository: ContactRepository,
//    private val authManager: AuthManager
//) : ViewModelProvider.Factory {
//    override fun <T : ViewModel> create(modelClass: Class<T>): T {
//        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
//            @Suppress("UNCHECKED_CAST")
//            return MainViewModel(application, repository, authManager) as T
//        }
//        throw IllegalArgumentException("Unknown ViewModel class")
//    }
//}