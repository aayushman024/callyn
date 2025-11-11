package com.example.callyn.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.callyn.data.ContactRepository
import com.example.callyn.db.AppContact
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

    // This flow observes the database.
    // When the database changes, this flow will automatically update
    // and trigger a UI recomposition.
    val localContacts: StateFlow<List<AppContact>> = repository.allContacts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Called by the UI to trigger a manual refresh from the API.
     */
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
}

/**
 * Factory class to allow us to pass the Repository into the ViewModel's constructor.
 */
class ContactsViewModelFactory(private val repository: ContactRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ContactsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ContactsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}