package com.mnivesh.callyn.ui

import android.app.Application
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mnivesh.callyn.components.DeviceContact
import com.mnivesh.callyn.components.DeviceNumber
import com.mnivesh.callyn.data.ContactRepository
import com.mnivesh.callyn.db.AppContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// UI State for the ContactsScreen
data class ContactsUiState(
    val isLoading: Boolean = false,
    val contacts: List<AppContact> = emptyList(),
    val errorMessage: String? = null
)

class ContactsViewModel(
    private val application: Application,
    private val repository: ContactRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ContactsUiState(isLoading = true))
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    // Observes the database for real-time updates (Work Contacts)
    val localContacts: StateFlow<List<AppContact>> = repository.allContacts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Personal Contacts State
    private val _deviceContacts = MutableStateFlow<List<DeviceContact>>(emptyList())
    val deviceContacts: StateFlow<List<DeviceContact>> = _deviceContacts.asStateFlow()

    // Content Observer for Automatic Updates
    private val contactsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            loadDeviceContacts() // Auto-reload when system contacts change
        }
    }

    init {
        // Register observer
        try {
            application.contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                true,
                contactsObserver
            )
            loadDeviceContacts() // Initial load
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadDeviceContacts() {
        if (ContextCompat.checkSelfPermission(application, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            _deviceContacts.value = emptyList()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val contactsMap = mutableMapOf<String, DeviceContact>()
            val cursor = application.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.STARRED,
                    ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY
                ),
                null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val starredIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)
                val defaultIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY)

                while (it.moveToNext()) {
                    val id = it.getString(idIndex)
                    val name = it.getString(nameIndex) ?: "Unknown"
                    val rawNumber = it.getString(numberIndex)?.replace("\\s".toRegex(), "") ?: ""
                    val isStarred = it.getInt(starredIndex) == 1
                    val isDefault = it.getInt(defaultIndex) > 0

                    if (rawNumber.isNotEmpty()) {
                        val numberObj = DeviceNumber(rawNumber, isDefault)
                        if (contactsMap.containsKey(id)) {
                            val existing = contactsMap[id]!!
                            if (existing.numbers.none { n -> n.number == rawNumber }) {
                                contactsMap[id] = existing.copy(numbers = existing.numbers + numberObj)
                            }
                        } else {
                            contactsMap[id] = DeviceContact(id, name, listOf(numberObj), isStarred)
                        }
                    }
                }
            }
            withContext(Dispatchers.Main) {
                _deviceContacts.value = contactsMap.values.map { contact ->
                    contact.copy(numbers = contact.numbers.sortedByDescending { it.isDefault })
                }.sortedBy { it.name }
            }
        }
    }

    fun submitPersonalRequest(token: String, contactName: String, userName: String, reason: String) {
        viewModelScope.launch {
            repository.submitPersonalRequest(token, contactName, userName, reason)
        }
    }

    fun onRefresh(token: String, managerName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                repository.refreshContacts(token, managerName)
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message)
            }
        }
    }

    suspend fun refreshContactsAwait(token: String, managerName: String): Boolean {
        _uiState.value = _uiState.value.copy(isLoading = true)
        val isSuccess = repository.refreshContacts(token, managerName)
        _uiState.value = _uiState.value.copy(isLoading = false)
        return isSuccess
    }

    // [!code ++] Suspend function for PullToRefresh
    suspend fun refreshAllAwait(token: String, managerName: String) {
        val job = viewModelScope.async(Dispatchers.IO) { loadDeviceContacts() }
        refreshContactsAwait(token, managerName)
        job.await()
    }

    override fun onCleared() {
        application.contentResolver.unregisterContentObserver(contactsObserver)
        super.onCleared()
    }
}

class ContactsViewModelFactory(
    private val application: Application,
    private val repository: ContactRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ContactsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ContactsViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}