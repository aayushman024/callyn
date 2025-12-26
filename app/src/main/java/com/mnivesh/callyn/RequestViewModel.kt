package com.mnivesh.callyn.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mnivesh.callyn.api.PendingRequest
import com.mnivesh.callyn.data.ContactRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RequestsUiState(
    val isLoading: Boolean = false,
    val requests: List<PendingRequest> = emptyList(),
    val error: String? = null
)

class RequestsViewModel(private val repository: ContactRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(RequestsUiState())
    val uiState = _uiState.asStateFlow()

    fun loadRequests(token: String) {
        viewModelScope.launch {
            _uiState.value = RequestsUiState(isLoading = true)
            try {
                val list = repository.getPendingRequests(token)
                _uiState.value = RequestsUiState(requests = list)
            } catch (e: Exception) {
                _uiState.value = RequestsUiState(error = "Failed to load requests")
            }
        }
    }

    fun updateStatus(token: String, requestId: String, newStatus: String) {
        viewModelScope.launch {
            // Optimistic Update: Remove from list immediately
            val currentList = _uiState.value.requests
            _uiState.value = _uiState.value.copy(
                requests = currentList.filter { it._id != requestId }
            )

            // API Call
            val success = repository.updateRequestStatus(token, requestId, newStatus)

            if (!success) {
                // Revert if failed
                _uiState.value = _uiState.value.copy(requests = currentList, error = "Action failed")
            }
        }
    }
}

class RequestsViewModelFactory(private val repository: ContactRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RequestsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RequestsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}