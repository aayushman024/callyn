package com.mnivesh.callyn.managers

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.mnivesh.callyn.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Data class representing the visibility of drawer features.
 */
data class DrawerFeatureFlags(
    val isEmployeeDirectoryEnabled: Boolean = true,
    val isUserStatusEnabled: Boolean = true,
    val isHandsFreeEnabled: Boolean = false,
    val isCallLogsEnabled: Boolean = true,
    val isQuickRepliesEnabled: Boolean = true,
    val isThemeToggleEnabled: Boolean = true,
    val isWhatsNewEnabled: Boolean = true
)

/**
 * Manager handling Firebase Remote Config initialization, fetching, real-time listeners,
 * and feature flag state exposure.
 */
object RemoteConfigManager {
    private const val TAG = "RemoteConfigManager"

    // Remote Config Keys
    const val KEY_EMPLOYEE_DIRECTORY_ENABLED = "feature_employee_directory_enabled"
    const val KEY_USER_STATUS_ENABLED = "feature_user_status_enabled"
    const val KEY_HANDS_FREE_ENABLED = "feature_hands_free_enabled"
    const val KEY_CALL_LOGS_ENABLED = "feature_call_logs_enabled"
    const val KEY_QUICK_REPLIES_ENABLED = "feature_quick_replies_enabled"
    const val KEY_THEME_TOGGLE_ENABLED = "feature_theme_toggle_enabled"
    const val KEY_WHATS_NEW_ENABLED = "feature_whats_new_enabled"

    private val defaults: Map<String, Any> = mapOf(
        KEY_EMPLOYEE_DIRECTORY_ENABLED to true,
        KEY_USER_STATUS_ENABLED to true,
        KEY_HANDS_FREE_ENABLED to true,
        KEY_CALL_LOGS_ENABLED to true,
        KEY_QUICK_REPLIES_ENABLED to true,
        KEY_THEME_TOGGLE_ENABLED to true,
        KEY_WHATS_NEW_ENABLED to true
    )

    private val _featureFlags = MutableStateFlow(DrawerFeatureFlags())
    val featureFlags: StateFlow<DrawerFeatureFlags> = _featureFlags.asStateFlow()

    private var isInitialized = false

    /**
     * Initializes Firebase Remote Config settings, defaults, listeners, and fetches latest values.
     */
    fun initialize() {
        if (isInitialized) return
        isInitialized = true

        try {
            val remoteConfig = Firebase.remoteConfig

            val interval = if (BuildConfig.DEBUG) 0L else 3600L
            val configSettings = remoteConfigSettings {
                fetchTimeoutInSeconds = 30L
                minimumFetchIntervalInSeconds = interval
            }

            remoteConfig.setConfigSettingsAsync(configSettings)
            remoteConfig.setDefaultsAsync(defaults)

            // Update local state from cached/default values immediately
            updateFlagsFromRemoteConfig()

            // Fetch and activate latest values from server
            remoteConfig.fetchAndActivate()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Remote config fetch and activate succeeded (updated: ${task.result})")
                        updateFlagsFromRemoteConfig()
                    } else {
                        Log.w(TAG, "Remote config fetch failed: ${task.exception?.message}")
                    }
                }

            // Real-time config updates listener
            remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
                override fun onUpdate(configUpdate: ConfigUpdate) {
                    Log.d(TAG, "Updated keys: " + configUpdate.updatedKeys)
                    remoteConfig.activate().addOnCompleteListener { activateTask ->
                        if (activateTask.isSuccessful) {
                            Log.d(TAG, "Activated updated remote config successfully")
                            updateFlagsFromRemoteConfig()
                        }
                    }
                }

                override fun onError(error: FirebaseRemoteConfigException) {
                    Log.w(TAG, "Config update listener error with code: ${error.code}", error)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing RemoteConfigManager", e)
        }
    }

    private fun updateFlagsFromRemoteConfig() {
        try {
            val remoteConfig = Firebase.remoteConfig
            _featureFlags.value = DrawerFeatureFlags(
                isEmployeeDirectoryEnabled = remoteConfig.getBoolean(KEY_EMPLOYEE_DIRECTORY_ENABLED),
                isUserStatusEnabled = remoteConfig.getBoolean(KEY_USER_STATUS_ENABLED),
                isHandsFreeEnabled = remoteConfig.getBoolean(KEY_HANDS_FREE_ENABLED),
                isCallLogsEnabled = remoteConfig.getBoolean(KEY_CALL_LOGS_ENABLED),
                isQuickRepliesEnabled = remoteConfig.getBoolean(KEY_QUICK_REPLIES_ENABLED),
                isThemeToggleEnabled = remoteConfig.getBoolean(KEY_THEME_TOGGLE_ENABLED),
                isWhatsNewEnabled = remoteConfig.getBoolean(KEY_WHATS_NEW_ENABLED)
            )
            Log.d(TAG, "Current DrawerFeatureFlags: ${_featureFlags.value}")
        } catch (e: Exception) {
            Log.e(TAG, "Error reading flags from RemoteConfig", e)
        }
    }
}
