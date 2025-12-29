package com.mnivesh.callyn.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mnivesh.callyn.data.ContactRepository
import com.mnivesh.callyn.db.AppContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// UI State for the ContactsScreen
data class ContactsUiState(
    val isLoading: Boolean = false,
    val contacts: List<AppContact> = emptyList(),
    val errorMessage: String? = null
)

class ContactsViewModel(private val repository: ContactRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactsUiState(isLoading = true))
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    // Observes the database for real-time updates
    val localContacts: StateFlow<List<AppContact>> = repository.allContacts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun submitPersonalRequest(token: String, contactName: String, userName: String, reason: String) {
        viewModelScope.launch {
            val success = repository.submitPersonalRequest(token, contactName, userName, reason)
            if (success) {
                Log.d("ContactsViewModel", "Request successfully sent to server.")
            } else {
                Log.e("ContactsViewModel", "Failed to send request.")
            }
        }
    }

    fun onRefresh(token: String, managerName: String) {
        Log.d("ContactsViewModel", "Refresh triggered for manager: $managerName")
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

    // [!code ++] NEW FUNCTION: Returns true/false for the Toast
    suspend fun refreshContactsAwait(token: String, managerName: String): Boolean {
        _uiState.value = _uiState.value.copy(isLoading = true)
        // This calls the repository (which returns Boolean)
        val isSuccess = repository.refreshContacts(token, managerName)
        _uiState.value = _uiState.value.copy(isLoading = false)
        return isSuccess
    }
}

class ContactsViewModelFactory(private val repository: ContactRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ContactsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ContactsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}