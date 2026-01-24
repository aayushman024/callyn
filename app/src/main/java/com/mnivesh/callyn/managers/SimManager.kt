// [!code file_path:app/src/main/java/com/mnivesh/callyn/managers/SimManager.kt]
package com.mnivesh.callyn.managers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.ActivityCompat

object SimManager {
    var workSimSlot: Int? = null
        private set
    var personalSimSlot: Int? = null
        private set

    var isWorkSimDetected: Boolean = false
        private set

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

        // [!code ++] Check for READ_PHONE_NUMBERS permission before proceeding
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

            val normalizedWorkNumber = normalizeNumber(workPhoneNumber)
            Log.d("SimManager", "Target Work Number (Normalized): '$normalizedWorkNumber'")

            activeSubs.forEach { sub ->
                var rawSimNumber: String? = null
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        rawSimNumber = subscriptionManager.getPhoneNumber(sub.subscriptionId)
                    } else {
                        @Suppress("DEPRECATION")
                        rawSimNumber = sub.number
                    }
                } catch (e: Exception) {
                    Log.e("SimManager", "Error fetching number for slot ${sub.simSlotIndex}: ${e.message}")
                }

                val normalizedSimNumber = if (!rawSimNumber.isNullOrBlank()) normalizeNumber(rawSimNumber) else "null/empty"

                Log.d("SimManager", "Inspecting SIM at Slot ${sub.simSlotIndex}:")
                Log.d("SimManager", "   > Display Name: ${sub.displayName}")
                Log.d("SimManager", "   > Raw Number: '$rawSimNumber'")
                Log.d("SimManager", "   > Normalized: '$normalizedSimNumber'")
            }

            val matchedWorkSub = activeSubs.find { sub ->
                var simNumber: String? = null
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        simNumber = subscriptionManager.getPhoneNumber(sub.subscriptionId)
                    } else {
                        @Suppress("DEPRECATION")
                        simNumber = sub.number
                    }
                } catch (e: Exception) {
                    simNumber = ""
                }
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
            }

        } catch (e: Exception) {
            Log.e("SimManager", "Error detecting SIM roles", e)
        } finally {
            Log.d("SimManager", "--- Detection Finished. isWorkSimDetected=$isWorkSimDetected ---")
        }
    }

    private fun normalizeNumber(number: String): String {
        val digits = number.filter { it.isDigit() }
        return if (digits.length > 10) digits.takeLast(10) else digits
    }
}