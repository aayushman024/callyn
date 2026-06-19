package com.mnivesh.callyn.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mnivesh.callyn.api.RetrofitInstance
import com.mnivesh.callyn.api.VersionResponse
import com.mnivesh.callyn.api.version
import com.mnivesh.callyn.components.DeviceContact
import com.mnivesh.callyn.db.AppContact
import com.mnivesh.callyn.managers.AuthManager
import com.mnivesh.callyn.managers.VersionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State representing update dialog status.
 */
data class UpdateState(
    val isUpdateAvailable: Boolean = false,
    val isHardUpdate: Boolean = false,
    val versionInfo: VersionResponse? = null
)

/**
 * UI State for MainActivity.
 */
sealed class MainActivityUiState {
    object Loading : MainActivityUiState()
    object LoggedOut : MainActivityUiState()
    data class LoggedIn(val userName: String) : MainActivityUiState()
    data class Preparing(val userName: String) : MainActivityUiState()
    data class ResolvingConflicts(
        val userName: String,
        val conflicts: List<DeviceContact>,
        val workContacts: List<AppContact>
    ) : MainActivityUiState()
}

/**
 * Main ViewModel for MainActivity.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val authManager = AuthManager(application)

    private val _uiState = MutableStateFlow<MainActivityUiState>(MainActivityUiState.Loading)
    val uiState: StateFlow<MainActivityUiState> = _uiState.asStateFlow()

    private val _updateState = MutableStateFlow(UpdateState())
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    /**
     * Updates the main UI state.
     */
    fun setUiState(state: MainActivityUiState) {
        _uiState.value = state
    }

    /**
     * Shows the update dialog with specific state.
     */
    fun setUpdateState(state: UpdateState) {
        _updateState.value = state
        _showUpdateDialog.value = true
    }

    /**
     * Hides the update dialog.
     */
    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }

    /**
     * Checks for app updates.
     */
    fun checkForUpdates(isManualCheck: Boolean = false, onManualCheckResult: (String) -> Unit = {}) {
        if (isManualCheck) {
            onManualCheckResult("Checking for updates...")
        }

        viewModelScope.launch {
            try {
                val currentVersion = version
                val token = authManager.getToken()

                if (token.isNullOrBlank()) {
                    if (isManualCheck) {
                        onManualCheckResult("Failed to check for updates: User not logged in")
                    }
                    return@launch
                }

                val response = RetrofitInstance.api.getLatestVersion("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    val remote = response.body()!!

                    if (VersionManager.isUpdateNeeded(currentVersion, remote.latestVersion)) {
                        _updateState.value = UpdateState(
                            isUpdateAvailable = true,
                            isHardUpdate = remote.updateType == "hard",
                            versionInfo = remote
                        )
                        _showUpdateDialog.value = true
                    } else if (isManualCheck) {
                        onManualCheckResult("You are already on the latest version")
                    }
                } else if (isManualCheck) {
                    onManualCheckResult("Failed to check for updates")
                }
            } catch (e: Exception) {
                if (isManualCheck) {
                    onManualCheckResult("Error: ${e.localizedMessage}")
                }
            }
        }
    }
}