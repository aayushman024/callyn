package com.mnivesh.callyn.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mnivesh.callyn.data.ContactRepository
import com.mnivesh.callyn.db.CrmContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// UI State now uses the DB entity 'CrmContact'
data class CrmUiState(
    val isLoading: Boolean = false,
    val tickets: List<CrmContact> = emptyList(),
    val investmentLeads: List<CrmContact> = emptyList(),
    val insuranceLeads: List<CrmContact> = emptyList(),
    val errorMessage: String? = null
)

class CrmViewModel(
    application: Application,
    private val repository: ContactRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CrmUiState())
    val uiState: StateFlow<CrmUiState> = _uiState.asStateFlow()

    init {
        // Automatically observe the DB when ViewModel is created
        observeDatabase()
    }

    private fun observeDatabase() {
        viewModelScope.launch {
            repository.crmContacts.collect { allContacts ->
                // Filter the single DB list into specific UI categories
                _uiState.value = _uiState.value.copy(
                    tickets = allContacts.filter { it.module == "Tickets" },
                    investmentLeads = allContacts.filter { it.module == "Investment_leads" },
                    insuranceLeads = allContacts.filter { it.module == "Insurance_Leads" }
                )
            }
        }
    }

    /**
     * Trigger a network refresh.
     * The UI updates automatically via observeDatabase() when the DB changes.
     */
    fun onRefresh(token: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val result = repository.refreshCrmData(token)

            result.onFailure { exception ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = exception.message ?: "Unknown error"
                )
            }
            // onSuccess: We don't need to manually update the list here because
            // the DB flow in observeDatabase() will fire with the new data.

            // Just turn off loading if successful
            result.onSuccess {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}

class CrmViewModelFactory(
    private val application: Application,
    private val repository: ContactRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CrmViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CrmViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}