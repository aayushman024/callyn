// Create file: app/src/main/java/com/mnivesh/callyn/viewmodels/SmsViewModel.kt

package com.mnivesh.callyn.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mnivesh.callyn.api.RetrofitInstance
import com.mnivesh.callyn.api.SmsLogResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SmsViewModel(application: Application) : AndroidViewModel(application) {

    private val _smsLogs = MutableStateFlow<List<SmsLogResponse>>(emptyList())
    val smsLogs: StateFlow<List<SmsLogResponse>> = _smsLogs.asStateFlow()

    private val _hasNotifications = MutableStateFlow(false)
    val hasNotifications: StateFlow<Boolean> = _hasNotifications.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun fetchSmsLogs(token: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = RetrofitInstance.api.getSmsLogs("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    val logs = response.body()!!
                    _smsLogs.value = logs
                    // Set notification flag if we have content
                    _hasNotifications.value = logs.isNotEmpty()
                } else {
                    _smsLogs.value = emptyList()
                    _hasNotifications.value = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}