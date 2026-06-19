package com.mnivesh.callyn.managers

import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "DialerManager"

/**
 * Manages outgoing call logic including dialer roles, missed calls, and smart dialing.
 */
class DialerManager(
    private val context: Context,
    private val permissionManager: PermissionManager
) {

    private var defaultDialerLauncher: ActivityResultLauncher<Intent>? = null

    /**
     * Initializes the dialer launcher (should be called from Activity).
     */
    fun setLauncher(launcher: ActivityResultLauncher<Intent>) {
        this.defaultDialerLauncher = launcher
    }

    /**
     * Checks if the app is the default dialer.
     */
    fun isDefaultDialer(): Boolean {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        return telecomManager?.defaultDialerPackage == context.packageName
    }

    /**
     * Prompts the user to set the app as the default dialer or call screening app.
     */
    fun offerDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)

            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) && !isDefaultDialer()) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                defaultDialerLauncher?.launch(intent)
            }

            val roleScreening = RoleManager.ROLE_CALL_SCREENING
            if (roleManager.isRoleAvailable(roleScreening) && !roleManager.isRoleHeld(roleScreening)) {
                val intent = roleManager.createRequestRoleIntent(roleScreening)
                defaultDialerLauncher?.launch(intent)
            }
        } else {
            if (!isDefaultDialer()) {
                try {
                    val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                        .putExtra(
                            TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                            context.packageName
                        )
                    defaultDialerLauncher?.launch(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch default dialer intent", e)
                }
            }
        }
    }

    /**
     * Gets the count of unread missed calls.
     */
    @SuppressLint("MissingPermission")
    fun getUnreadMissedCallsCount(): Int {
        if (!permissionManager.checkAllPermissions()) return 0
        var count = 0
        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                null,
                "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.IS_READ} = ?",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString(), "0"),
                null
            )
            count = cursor?.count ?: 0
            cursor?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error counting missed calls", e)
        }
        return count
    }

    /**
     * Marks all unread missed calls as read.
     */
    @SuppressLint("MissingPermission")
    fun markMissedCallsAsRead() {
        if (!permissionManager.checkAllPermissions()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val values = ContentValues().apply {
                    put(CallLog.Calls.IS_READ, 1)
                }
                context.contentResolver.update(
                    CallLog.Calls.CONTENT_URI,
                    values,
                    "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.IS_READ} = ?",
                    arrayOf(CallLog.Calls.MISSED_TYPE.toString(), "0")
                )

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.cancel(12346) // Matches MISSED_CALL_NOTIF_ID from MyInCallService
            } catch (e: Exception) {
                Log.e(TAG, "Error marking calls as read", e)
            }
        }
    }

    /**
     * Smartly dials a number choosing the appropriate SIM based on work/personal context.
     */
    @SuppressLint("MissingPermission")
    fun dialSmart(number: String, isWorkCall: Boolean, specificSlot: Int? = null) {
        if (!isDefaultDialer()) { offerDefaultDialer(); return }
        if (!permissionManager.checkAllPermissions()) { permissionManager.requestPermissions(); return }

        // Strip everything except digits, plus signs, and USSD characters
        val cleanNumber = number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }

        // Mask phone number for security in Crashlytics
        val maskedNumber = if (cleanNumber.length > 4) "*".repeat(cleanNumber.length - 4) + cleanNumber.takeLast(4) else cleanNumber
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.log("dialSmart called with maskedNumber=$maskedNumber, isWorkCall=$isWorkCall, specificSlot=$specificSlot")

        // Handle Indian telecom routing (Toll-free, STD, USSD, and standard mobile)
        val stripped = cleanNumber.removePrefix("+")
        val numberToDial = when {
            cleanNumber.contains('*') || cleanNumber.contains('#') -> cleanNumber // USSD codes
            stripped.startsWith("1800") -> stripped  // Toll-free (strip + if present)
            stripped.startsWith("1860") -> stripped  // Toll-free (strip + if present)
            stripped.startsWith("0") -> stripped      // STD codes (strip + if present)
            cleanNumber.startsWith("+") -> cleanNumber // International (keep +)
            cleanNumber.length > 10 -> "+$cleanNumber"
            else -> cleanNumber
        }

        try {
            val telecomManager = context.getSystemService(TelecomManager::class.java)
            val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
            val activeSims = subscriptionManager.activeSubscriptionInfoList

            if (activeSims.isNullOrEmpty()) {
                crashlytics.log("dialSmart abort: No active SIMs found.")
                Toast.makeText(context, "No SIM card found", Toast.LENGTH_SHORT).show()
                return
            }

            // --- STRICT SLOT SELECTION ---
            val targetSlotIndex = specificSlot ?: if (isWorkCall) SimManager.workSimSlot else SimManager.personalSimSlot
            val selectedSim = if (targetSlotIndex != null) activeSims.find { it.simSlotIndex == targetSlotIndex } else null

            crashlytics.log("dialSmart routing: targetSlotIndex=$targetSlotIndex, selectedSimFound=${selectedSim != null}")

            val uri = Uri.fromParts("tel", numberToDial, null)
            val intent = Intent(Intent.ACTION_CALL, uri)

            if (selectedSim != null) {
                // Force call through the specific SIM
                val targetHandle = findHandleForSubId(telecomManager, selectedSim.subscriptionId)
                crashlytics.log("dialSmart handle resolution: subId=${selectedSim.subscriptionId}, handleFound=${targetHandle != null}")
                if (targetHandle != null) {
                    intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, targetHandle)
                }
                Toast.makeText(context, "Dialing via ${selectedSim.displayName} (${if(isWorkCall) "Work" else "Personal"})", Toast.LENGTH_SHORT).show()
            } else {
                Log.d(TAG, "Smart Dial: No specific SIM determined. Using system default.")
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Smart dial failed", e)
            crashlytics.recordException(e)
            Toast.makeText(context, "Call failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun findHandleForSubId(
        telecomManager: TelecomManager,
        subId: Int
    ): PhoneAccountHandle? {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val accounts = telecomManager.callCapablePhoneAccounts ?: return null

        // 1. Try standard API on API >= 30 (R)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && telephonyManager != null) {
            try {
                val matched = accounts.firstOrNull { handle ->
                    telephonyManager.getSubscriptionId(handle) == subId
                }
                if (matched != null) return matched
            } catch (e: Exception) {
                Log.e(TAG, "Error matching handle via telephonyManager.getSubscriptionId", e)
            }
        }

        // 2. Fallback to contains subId in ID (standard on API < 30)
        val subIdStr = subId.toString()
        val matchedById = accounts.firstOrNull { handle ->
            handle.id?.contains(subIdStr) == true
        }
        if (matchedById != null) return matchedById

        // 3. Fallback: match by slot index pattern mapping
        try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val subInfo = subscriptionManager?.activeSubscriptionInfoList?.find { it.subscriptionId == subId }
            if (subInfo != null) {
                val slotIndex = subInfo.simSlotIndex
                val matchedBySlot = accounts.firstOrNull { handle ->
                    val id = handle.id?.lowercase() ?: ""
                    id.contains("slot$slotIndex") || id.contains("slot_$slotIndex") || (id == slotIndex.toString())
                }
                if (matchedBySlot != null) return matchedBySlot
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in slot matching fallback", e)
        }

        return null
    }
}
