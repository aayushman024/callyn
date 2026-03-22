package com.mnivesh.callyn.viewmodels

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

    var userNamesList: List<String> by mutableStateOf(emptyList())
        private set

    fun fetchUserDetails(token: String) {
        viewModelScope.launch {
            uiState = UserDetailsUiState.Loading
            try {
                val response = RetrofitInstance.api.getAllUserDetails("Bearer $token")

                when {
                    response.isSuccessful && response.body() != null -> {
                        val users = response.body()!!
                        userNamesList = users.map { it.username }
                        uiState = UserDetailsUiState.Success(users)
                    }
                    response.code() == 404 -> {
                        uiState = UserDetailsUiState.Error("No users found")
                    }
                    response.code() == 401 -> {
                        uiState = UserDetailsUiState.Error("Unauthorized: Please log in again")
                    }
                    response.code() in 500..599 -> {
                        uiState = UserDetailsUiState.Error("Server error, please try again later")
                    }
                    else -> {
                        uiState = UserDetailsUiState.Error("Unexpected error: ${response.code()}")
                    }
                }

            } catch (e: Exception) {
                Log.e("UserDetailsVM", "Error fetching user details", e)
                uiState = UserDetailsUiState.Error("Network Error: ${e.localizedMessage}")
            }
        }
    }
}