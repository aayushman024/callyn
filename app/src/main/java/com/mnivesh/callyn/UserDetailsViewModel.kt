package com.mnivesh.callyn

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mnivesh.callyn.api.RetrofitInstance
import com.mnivesh.callyn.api.UserDetailsResponse
import kotlinx.coroutines.launch

sealed class UserDetailsUiState {
    object Loading : UserDetailsUiState()
    data class Success(val users: List<UserDetailsResponse>) : UserDetailsUiState()
    data class Error(val message: String) : UserDetailsUiState()
}

class UserDetailsViewModel : ViewModel() {
    var uiState: UserDetailsUiState by mutableStateOf(UserDetailsUiState.Loading)
        private set

    fun fetchUserDetails(token: String) {
        viewModelScope.launch {
            uiState = UserDetailsUiState.Loading
            try {
                val response = RetrofitInstance.api.getAllUserDetails("Bearer $token")

                // [!code ++] Handle 404 with Hardcoded Data
                if (response.code() == 404) {
                    val dummyList = listOf(
                        UserDetailsResponse(
                            _id = "1",
                            username = "Aayushman Ranjan",
                            phoneModel = "Samsung Galaxy S24",
                            osLevel = "Android 14 (SDK 34)",
                            appVersion = "1.0.5",
                            department = "IT Desk",
                            lastSeen = "2025-12-31T10:00:00.000Z"
                        ),
                        UserDetailsResponse(
                            _id = "2",
                            username = "Demo User 2",
                            phoneModel = "Pixel 8 Pro",
                            osLevel = "Android 15 (SDK 35)",
                            appVersion = "1.0.4",
                            department = "Management",
                            lastSeen = "2025-12-29T15:30:00.000Z"
                        ),
                        UserDetailsResponse(
                            _id = "3",
                            username = "Ishu Mavar",
                            phoneModel = "OnePlus 12",
                            osLevel = "Android 13 (SDK 33)",
                            appVersion = "1.0.5",
                            department = "Mutual Funds",
                            lastSeen = "2025-12-31T09:15:00.000Z"
                        )
                    )
                    uiState = UserDetailsUiState.Success(dummyList)
                }
                // Existing Success Logic
                else if (response.isSuccessful && response.body() != null) {
                    uiState = UserDetailsUiState.Success(response.body()!!)
                } else {
                    uiState = UserDetailsUiState.Error("Failed to fetch data: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("UserDetailsVM", "Error", e)
                uiState = UserDetailsUiState.Error("Network Error: ${e.localizedMessage}")
            }
        }
    }
}