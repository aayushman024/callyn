// [!code filename:app/src/main/java/com/mnivesh/callyn/ui/EmployeeViewModel.kt]
package com.mnivesh.callyn.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mnivesh.callyn.api.EmployeeDirectory
import com.mnivesh.callyn.data.ContactRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class EmployeeUiState {
    object Loading : EmployeeUiState()
    data class Success(val employees: List<EmployeeDirectory>) : EmployeeUiState()
    data class Error(val message: String) : EmployeeUiState()
}

class EmployeeViewModel(private val repository: ContactRepository) : ViewModel() {

    // Observe the DB flow, filter for Employees, and map to UI model
    val uiState: StateFlow<EmployeeUiState> = repository.allContacts
        .map { contacts ->
            // 1. Filter only contacts marked as Employees
            val employees = contacts.filter { it.rshipManager == "Employee" }
                .map { dbContact ->
                    // 2. Map DB entity back to UI model
                    EmployeeDirectory(
                        name = dbContact.name,
                        phone = dbContact.number,
                        // We saved Department into 'familyHead' in Repository
                        department = dbContact.familyHead,
                        // Email wasn't in AppContact schema, so we pass empty string for offline
                        email = dbContact.pan
                    )
                }

            if (employees.isEmpty()) {
                EmployeeUiState.Loading // Or stay loading until API hits if DB is empty
            } else {
                EmployeeUiState.Success(employees)
            }
        }
        .catch { e ->
            emit(EmployeeUiState.Error(e.localizedMessage ?: "Database Error"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = EmployeeUiState.Loading
        )

    // This now just triggers a background sync to update the DB
    fun loadEmployees(token: String, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            repository.getEmployees(token, forceRefresh)
        }
    }
}

class EmployeeViewModelFactory(private val repository: ContactRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EmployeeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EmployeeViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}