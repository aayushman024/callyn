// [!code file_path:app/src/main/java/com/mnivesh/callyn/managers/SimManager.kt]
package com.mnivesh.callyn.managers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import com.google.firebase.crashlytics.FirebaseCrashlytics

object SimManager {
    data class SimInfo(val slotIndex: Int, val displayName: String, val number: String?)

    private const val PREFS_NAME = "callyn_sim_prefs"
    private const val KEY_MANUAL_WORK_SIM = "manual_work_sim_slot"

    var workSimSlot: Int? = null
        private set
    var personalSimSlot: Int? = null
        private set

    var isWorkSimDetected: Boolean = false
        private set

    var showSimSelectionDialog by mutableStateOf(false)
        internal set

    var availableSimsForSelection by mutableStateOf<List<SimInfo>>(emptyList())
        internal set

    fun getSavedWorkSimSlot(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_MANUAL_WORK_SIM, -1)
    }

    fun saveManualWorkSim(context: Context, slotIndex: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_MANUAL_WORK_SIM, slotIndex).apply()
        
        try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            @SuppressLint("MissingPermission")
            val activeSubs = subscriptionManager.activeSubscriptionInfoList
            if (!activeSubs.isNullOrEmpty()) {
                val matchedWorkSub = activeSubs.find { it.simSlotIndex == slotIndex }
                if (matchedWorkSub != null) {
                    workSimSlot = matchedWorkSub.simSlotIndex
                    val personalSub = activeSubs.find { it.simSlotIndex != workSimSlot }
                    personalSimSlot = personalSub?.simSlotIndex
                    isWorkSimDetected = true
                    Log.d("SimManager", "saveManualWorkSim: Work SIM Slot manually set to $workSimSlot, Personal SIM Slot to $personalSimSlot.")
                }
            }
        } catch (e: Exception) {
            Log.e("SimManager", "Error updating SIM slots in saveManualWorkSim", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun detectSimRoles(context: Context, workPhoneNumber: String?) {
        workSimSlot = null
        personalSimSlot = null
        isWorkSimDetected = false

        Log.d("SimManager", "--- Starting SIM Role Detection ---")

        if (workPhoneNumber.isNullOrBlank()) {
            Log.d("SimManager", "Abort: No work phone number found in AuthManager.")
            return
        }

        // Check for READ_PHONE_NUMBERS permission before proceeding
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED) {
                Log.d("SimManager", "Abort: Missing READ_PHONE_NUMBERS permission.")
                return
            }
        }

        try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val activeSubs = subscriptionManager.activeSubscriptionInfoList

            if (activeSubs.isNullOrEmpty()) {
                Log.d("SimManager", "Abort: No active SIM cards found on device.")
                return
            }

            Log.d("SimManager", "Found ${activeSubs.size} active SIM(s).")

            if (activeSubs.size == 1) {
                val slot = activeSubs[0].simSlotIndex
                workSimSlot = slot
                personalSimSlot = slot
                isWorkSimDetected = true
                Log.d("SimManager", "Result: Single SIM detected (Slot $slot). Defaulting both Work and Personal to this slot.")
                return
            }

            // Check if there's a saved manual selection
            val savedSlot = getSavedWorkSimSlot(context)
            if (savedSlot != -1) {
                val matchedSub = activeSubs.find { it.simSlotIndex == savedSlot }
                if (matchedSub != null) {
                    workSimSlot = matchedSub.simSlotIndex
                    val personalSub = activeSubs.find { it.simSlotIndex != workSimSlot }
                    personalSimSlot = personalSub?.simSlotIndex
                    isWorkSimDetected = true
                    Log.d("SimManager", "Result: SUCCESS (Manual preference). Work SIM is Slot $workSimSlot. Personal SIM is Slot $personalSimSlot.")
                    return
                }
            }

            val normalizedWorkNumber = normalizeNumber(workPhoneNumber)
            Log.d("SimManager", "Target Work Number (Normalized): '$normalizedWorkNumber'")

            activeSubs.forEach { sub ->
                val rawSimNumber = getSimNumber(context, subscriptionManager, sub)
                val normalizedSimNumber = if (!rawSimNumber.isNullOrBlank()) normalizeNumber(rawSimNumber) else "null/empty"

                Log.d("SimManager", "Inspecting SIM at Slot ${sub.simSlotIndex}:")
                Log.d("SimManager", "   > Display Name: ${sub.displayName}")
                Log.d("SimManager", "   > Raw Number: '${if (rawSimNumber != null) maskNumber(rawSimNumber) else "null/empty"}'")
                Log.d("SimManager", "   > Normalized: '${if (normalizedSimNumber != "null/empty") maskNumber(normalizedSimNumber) else "null/empty"}'")
            }

            val matchedWorkSub = activeSubs.find { sub ->
                val simNumber = getSimNumber(context, subscriptionManager, sub)
                !simNumber.isNullOrBlank() && normalizeNumber(simNumber) == normalizedWorkNumber
            }

            if (matchedWorkSub != null) {
                workSimSlot = matchedWorkSub.simSlotIndex
                val personalSub = activeSubs.find { it.simSlotIndex != workSimSlot }
                personalSimSlot = personalSub?.simSlotIndex
                isWorkSimDetected = true
                Log.d("SimManager", "Result: SUCCESS. Work SIM is Slot $workSimSlot. Personal SIM is Slot $personalSimSlot.")
            } else {
                Log.d("SimManager", "Result: FAILED. Dual SIMs present, but neither matched the target work number.")
                isWorkSimDetected = false
                
                // Show dialog for manual selection if active count > 1
                if (activeSubs.size > 1) {
                    availableSimsForSelection = activeSubs.map { sub ->
                        val rawNumber = getSimNumber(context, subscriptionManager, sub)
                        SimInfo(
                            slotIndex = sub.simSlotIndex,
                            displayName = sub.displayName?.toString() ?: "SIM ${sub.simSlotIndex + 1}",
                            number = rawNumber
                        )
                    }
                    showSimSelectionDialog = true
                }
                
                // Log failed match details to Crashlytics
                try {
                    val crashlytics = FirebaseCrashlytics.getInstance()
                    crashlytics.setCustomKey("sim_detection_failed", true)
                    crashlytics.setCustomKey("active_sim_count", activeSubs.size)
                    crashlytics.setCustomKey("sdk_version", Build.VERSION.SDK_INT)
                    crashlytics.log("SIM matching failed: Target work number normalized to '${maskNumber(normalizedWorkNumber)}' but no active SIM numbers matched.")
                    activeSubs.forEach { sub ->
                        val rawNum = getSimNumber(context, subscriptionManager, sub)
                        val masked = if (rawNum != null) maskNumber(normalizeNumber(rawNum)) else "null/empty"
                        crashlytics.log("SIM Slot ${sub.simSlotIndex} details - DisplayName: ${sub.displayName}, Number: $masked")
                    }
                } catch (e: Exception) {
                    Log.e("SimManager", "Failed to log SIM match error to Crashlytics: ${e.message}")
                }
            }

        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            Log.e("SimManager", "Error detecting SIM roles", e)
        } finally {
            Log.d("SimManager", "--- Detection Finished. isWorkSimDetected=$isWorkSimDetected ---")
        }
    }

    fun getDeviceFirstNumber(context: Context): String? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED) {
                    return null
                }
            }
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            @SuppressLint("MissingPermission")
            val activeSubs = subscriptionManager.activeSubscriptionInfoList
            if (activeSubs.isNullOrEmpty()) return null

            for (sub in activeSubs) {
                val number = getSimNumber(context, subscriptionManager, sub)
                if (!number.isNullOrBlank()) {
                    return number
                }
            }
        } catch (e: Exception) {
            Log.e("SimManager", "Error in getDeviceFirstNumber", e)
        }
        return null
    }

    private fun getSimNumber(context: Context, subscriptionManager: SubscriptionManager, sub: android.telephony.SubscriptionInfo): String? {
        var number: String? = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                number = subscriptionManager.getPhoneNumber(sub.subscriptionId)
            } else {
                @Suppress("DEPRECATION")
                number = sub.number
            }
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
            Log.e("SimManager", "Error fetching number via primary API for slot ${sub.simSlotIndex}: ${e.message}")
        }

        if (number.isNullOrBlank()) {
            try {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
                if (telephonyManager != null) {
                    val subTelephonyManager = telephonyManager.createForSubscriptionId(sub.subscriptionId)
                    number = subTelephonyManager.line1Number
                }
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
                Log.e("SimManager", "Error fetching number via TelephonyManager fallback for slot ${sub.simSlotIndex}: ${e.message}")
            }
        }
        return number
    }

    private fun maskNumber(number: String): String {
        return if (number.length > 4) {
            "*".repeat(number.length - 4) + number.takeLast(4)
        } else {
            number
        }
    }

    private fun normalizeNumber(number: String): String {
        val digits = number.filter { it.isDigit() }
        return if (digits.length > 10) digits.takeLast(10) else digits
    }
}