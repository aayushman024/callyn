package com.mnivesh.callyn.viewmodels

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mnivesh.callyn.api.CallLogResponse
import com.mnivesh.callyn.api.RetrofitInstance
import kotlinx.coroutines.launch

sealed class CallLogsUiState {
    object Idle : CallLogsUiState()
    object Loading : CallLogsUiState()
    data class Success(val logs: List<CallLogResponse>) : CallLogsUiState()
    data class Error(val message: String) : CallLogsUiState()
}

class CallLogsViewModel : ViewModel() {
    var uiState: CallLogsUiState by mutableStateOf(CallLogsUiState.Idle)
        private set

    // List of usernames for the dropdown
    var userNamesList: List<String> by mutableStateOf(emptyList())
        private set

    fun fetchUserNames(token: String) {
        viewModelScope.launch {
            try {
                // Reuse the user details API to populate the dropdown
                val response = RetrofitInstance.api.getAllUserDetails("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    userNamesList = response.body()!!.map { it.username }
                }
            } catch (e: Exception) {
                Log.e("CallLogsVM", "Failed to fetch users", e)
            }
        }
    }

    fun searchLogs(token: String, uploadedBy: String?, date: String?) {
        if (uploadedBy.isNullOrBlank() && date.isNullOrBlank()) {
            uiState = CallLogsUiState.Error("Please select a user or a date.")
            return
        }

        viewModelScope.launch {
            uiState = CallLogsUiState.Loading
            try {
                // Call API with optional params
                val response = RetrofitInstance.api.getCallLogs(
                    token = "Bearer $token",
                    uploadedBy = if (uploadedBy.isNullOrBlank()) null else uploadedBy,
                    date = if (date.isNullOrBlank()) null else date
                )

                if (response.isSuccessful && response.body() != null) {
                    val logs = response.body()!!.data
                    uiState = CallLogsUiState.Success(logs)
                } else {
                    uiState = CallLogsUiState.Error("Failed to fetch logs: ${response.code()}")
                }
            } catch (e: Exception) {
                uiState = CallLogsUiState.Error("Network Error: ${e.localizedMessage}")
            }
        }
    }
}