// [!code display_name:CallManager.kt]
// [!code file_path:aayushman024/callyn/callyn-b3dc429adcee543cca5d11b2c13564d35d3c1f65/app/src/main/java/com/mnivesh/callyn/managers/CallManager.kt]
package com.mnivesh.callyn.managers

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.annotation.RequiresApi
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mnivesh.callyn.services.MyInCallService
import com.mnivesh.callyn.data.ContactRepository
import com.mnivesh.callyn.db.WorkCallLog
import com.mnivesh.callyn.workers.PersonalUploadWorker
import com.mnivesh.callyn.workers.UploadCallLogWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// --- UPDATED CallState ---
data class CallState(
    val name: String,
    val number: String,
    val status: String,
    val type: String = "unknown",
    val isMuted: Boolean = false,
    val isHolding: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isBluetoothOn: Boolean = false,
    val isIncoming: Boolean = false,
    val availableRoutes: Int = 0,
    val isConference: Boolean = false,
    val canMerge: Boolean = false,
    val canSwap: Boolean = false,
    val participants: List<String> = emptyList(),
    val familyHead: String? = null,
    val rshipManager: String? = null,
    val aum: String? = null,
    val familyAum: String? = null,
    val connectTimeMillis: Long = 0,
    internal val call: Call? = null,

    // Call Waiting / Background Call
    val secondCallerName: String? = null,
    val secondCallerNumber: String? = null,
    internal val secondIncomingCall: Call? = null // The "Waiting" or "Background" call
)

object CallManager {

    private val _callState = MutableStateFlow<CallState?>(null)
    val callState = _callState.asStateFlow()

    // NEW: Track all calls to handle multi-call logic properly
    private val registeredCalls = mutableListOf<Call>()

    private var repository: ContactRepository? = null
    private var appContext: Context? = null

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun initialize(repository: ContactRepository, context: Context) {
        this.repository = repository
        this.appContext = context.applicationContext
    }

    private fun normalizeNumber(number: String): String {
        val digitsOnly = number.filter { it.isDigit() }
        return if (digitsOnly.length > 10) digitsOnly.takeLast(10) else digitsOnly
    }

    @SuppressLint("MissingPermission")
    fun onCallAdded(call: Call) {
        if (!registeredCalls.contains(call)) {
            registeredCalls.add(call)
            registerCallCallback(call)
        }
        recalculateGlobalState()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun onCallRemoved(call: Call) {
        registeredCalls.remove(call)

        // 1. Handle Logging
        handleCallLogging(call)

        // 2. Update UI
        if (registeredCalls.isEmpty()) {
            _callState.value = null
        } else {
            recalculateGlobalState()
        }
    }

    // --- LOGIC: The Brain of Multi-Call Management ---
    private fun recalculateGlobalState() {
        if (registeredCalls.isEmpty()) return

        val conferenceCall = registeredCalls.find { it.details.hasProperty(Call.Details.PROPERTY_CONFERENCE) }

        var primaryCall: Call = conferenceCall
            ?: registeredCalls.find { it.state == Call.STATE_ACTIVE }
            ?: registeredCalls.find { it.state == Call.STATE_DIALING }
            ?: registeredCalls.find { it.state == Call.STATE_RINGING }
            ?: registeredCalls.first()

        val secondaryCall = registeredCalls.find { it != primaryCall }

        var waitingCall: Call? = null
        if (primaryCall.state == Call.STATE_ACTIVE || primaryCall.state == Call.STATE_HOLDING) {
            waitingCall = registeredCalls.find { it.state == Call.STATE_RINGING }
        }

        updateStateForPrimary(primaryCall, waitingCall ?: secondaryCall)
    }

    private fun updateStateForPrimary(primary: Call, secondary: Call?) {
        val details = primary.details
        val isConference = details.hasProperty(Call.Details.PROPERTY_CONFERENCE) ||
                (!primary.children.isNullOrEmpty())

        val children = primary.children ?: emptyList()

        var displayNumber = ""
        var finalName = ""

        if (isConference) {
            val count = children.size
            finalName = "Conference ($count)"
        } else {
            val handle = details.handle
            displayNumber = handle?.schemeSpecificPart ?: ""
            finalName = displayNumber
        }

        val currentState = _callState.value
        if (!isConference && displayNumber.isNotEmpty()) {
            if (currentState?.number == displayNumber && currentState.name != displayNumber) {
                finalName = currentState.name
            } else {
                resolveContactInfo(displayNumber)
            }
        }

        val status = primary.getStateString()
        val isIncoming = (primary.state == Call.STATE_RINGING)

        val canMerge = (details.callCapabilities and Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0 ||
                (registeredCalls.size > 1 && registeredCalls.none { it.state == Call.STATE_RINGING })

        val canSwap = (details.callCapabilities and Call.Details.CAPABILITY_SWAP_CONFERENCE) != 0 ||
                (registeredCalls.size > 1 && secondary?.state == Call.STATE_HOLDING)

        var secName: String? = null
        var secNumber: String? = null
        val isSecondRinging = secondary?.state == Call.STATE_RINGING

        if (secondary != null) {
            val rawSec = secondary.details.handle?.schemeSpecificPart ?: ""
            secNumber = rawSec
            secName = if (currentState?.secondCallerNumber == rawSec) currentState.secondCallerName else rawSec

            if (isSecondRinging && secName == rawSec) {
                resolveSecondaryContactInfo(rawSec)
            }
        }

        val newState = CallState(
            name = finalName,
            number = displayNumber,
            status = status,
            isIncoming = isIncoming,
            isConference = isConference,
            canMerge = canMerge,
            canSwap = canSwap,
            participants = children.map { it.details.handle?.schemeSpecificPart ?: "Unknown" },
            call = primary,
            type = currentState?.type ?: "unknown",
            isMuted = currentState?.isMuted ?: false,
            isHolding = (primary.state == Call.STATE_HOLDING),
            isSpeakerOn = currentState?.isSpeakerOn ?: false,
            isBluetoothOn = currentState?.isBluetoothOn ?: false,
            availableRoutes = currentState?.availableRoutes ?: 0,
            familyHead = currentState?.familyHead,
            rshipManager = currentState?.rshipManager,
            connectTimeMillis = details.connectTimeMillis,
            secondIncomingCall = if (isSecondRinging) secondary else null,
            secondCallerName = secName,
            secondCallerNumber = secNumber
        )

        _callState.value = newState
    }

    // --- Actions ---

    fun acceptCallWaiting() {
        val waiting = _callState.value?.secondIncomingCall ?: return
        waiting.answer(0)
    }

    fun rejectCallWaiting() {
        val waiting = _callState.value?.secondIncomingCall ?: return
        waiting.reject(false, "")
    }

    fun mergeCalls() {
        val primary = _callState.value?.call ?: return
        val secondary = registeredCalls.find { it != primary }

        if (primary.details.callCapabilities and Call.Details.CAPABILITY_MERGE_CONFERENCE != 0) {
            primary.mergeConference()
        } else if (secondary != null) {
            primary.conference(secondary)
        }
    }

    fun swapCalls() {
        val primary = _callState.value?.call ?: return
        val secondary = registeredCalls.find { it != primary } ?: return

        if (primary.details.callCapabilities and Call.Details.CAPABILITY_SWAP_CONFERENCE != 0) {
            primary.swapConference()
        } else {
            if (primary.state == Call.STATE_ACTIVE) {
                primary.hold()
                secondary.unhold()
            } else if (primary.state == Call.STATE_HOLDING) {
                secondary.hold()
                primary.unhold()
            }
        }
    }

    //send quick response text
    fun sendQuickResponse(context: Context, number: String, message: String) {
        try {
            // Use SupressLint if your target SDK marks this as deprecated,
            // or use context.getSystemService(SmsManager::class.java) for Android 12+
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(number, null, message, null, null)
            rejectCall()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Resolution Logic ---

    private fun resolveSecondaryContactInfo(number: String) {
        if (number.isBlank()) return
        val normalized = normalizeNumber(number)

        // Prevent short code lookup
        if (normalized.length < 7) return

        coroutineScope.launch {
            // 1. Device Contact
            var resolvedName: String? = findPersonalContactName(normalized)

            // 2. Work Contact
            if (resolvedName == null) {
                val workContact = repository?.findWorkContactByNumber(normalized)
                if (workContact != null) resolvedName = workContact.name
            }

            // 3. CNAP / Network Name
            if (resolvedName == null) {
                val primaryCall = _callState.value?.call
                // Find the actual secondary call object to get its specific details
                val secondaryCall = registeredCalls.find { it != primaryCall }

                val cnapName = secondaryCall?.details?.callerDisplayName
                if (!cnapName.isNullOrBlank() && normalizeNumber(cnapName) != normalized) {
                    resolvedName = cnapName
                }
            }

            // 4. Fallback to Number
            val finalName = resolvedName ?: number

            // Apply Update
            val current = _callState.value
            if (current != null && current.secondCallerNumber == number) {
                _callState.value = current.copy(secondCallerName = finalName)
            }
        }
    }

    private fun resolveContactInfo(number: String) {
        if (number.isBlank()) return
        val normalized = normalizeNumber(number)

        coroutineScope.launch {
            // 1. Device Contact
            var resolvedName: String? = findPersonalContactName(normalized)
            var type = "personal"
            var familyHead: String? = null
            var rshipManager: String? = null
            var aum: String? = null
            var familyAum: String? = null

            // 2. Work Contact (If not found in device)
            if (resolvedName == null && normalized.length > 9) {
                val workContact = repository?.findWorkContactByNumber(normalized)
                if (workContact != null) {
                    resolvedName = workContact.name
                    type = "work"
                    familyHead = workContact.familyHead
                    rshipManager = workContact.rshipManager
                    aum = workContact.aum
                    familyAum = workContact.familyAum
                }
            }

            // 3. CNAP / Network Name (If not found in work/device)
            if (resolvedName == null) {
                val cnapName = _callState.value?.call?.details?.callerDisplayName
                // Only use CNAP if it's not null and not just the number repeated
                if (!cnapName.isNullOrBlank() && normalizeNumber(cnapName) != normalized) {
                    resolvedName = cnapName
                    type = "unknown" // Keeps type safe
                }
            }

            // 4. Fallback to Number if everything fails
            val finalName = resolvedName ?: number

            // Apply Update using the same 'name' variable
            if (_callState.value?.number == number) {
                val updatedState = _callState.value?.copy(
                    name = finalName, // <--- Passing result to the existing variable
                    type = type,
                    familyHead = familyHead,
                    rshipManager = rshipManager,
                    aum = aum,
                    familyAum = familyAum

                )
                _callState.value = updatedState
            }
        }
    }

    //get sim card info for the call
    @SuppressLint("MissingPermission")
    private fun getSimSlot(context: Context, call: Call): String {
        try {
            val accountHandle = call.details.accountHandle ?: return "Unknown"
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

            // Iterate active subs to find matching ID
            val activeSubs = sm.activeSubscriptionInfoList ?: return "Unknown"
            val subInfo = activeSubs.find {
                it.subscriptionId == accountHandle.id.toIntOrNull() || it.iccId == accountHandle.id
            }

            return if (subInfo != null) {
                // Human readable format: "SIM 1" or "SIM 2"
                "SIM ${subInfo.simSlotIndex + 1}"
            } else {
                "Unknown"
            }
        } catch (e: Exception) {
            return "Unknown"
        }
    }

    // --- Logging & Deletion ---
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun handleCallLogging(call: Call) {
        val rawNumber = call.details.handle?.schemeSpecificPart ?: ""
        if (rawNumber.isBlank()) return

        coroutineScope.launch {
            // 0. Get User Department
            val dept = appContext?.let { AuthManager(it).getDepartment() } ?: "N/A"
            val isManagement = dept == "Management"

            // 1. Prepare Common Data
            val now = System.currentTimeMillis()
            val durationSeconds = if (call.details.connectTimeMillis > 0) {
                (now - call.details.connectTimeMillis) / 1000
            } else {
                0
            }

            var direction = "outgoing"
            val wasIncoming = (call.details.callDirection == Call.Details.DIRECTION_INCOMING)
            if (wasIncoming) {
                direction = if (call.details.connectTimeMillis > 0) "incoming" else "missed"
            }

            // Get SIM info safely
            val simSlot = appContext?.let { getSimSlot(it, call) } ?: "Unknown"
            val normalized = normalizeNumber(rawNumber)

            // 2. Identify Call Type (Duplication Logic Check)
            var isWork = false
            var workName = ""
            var familyHead = ""

            if (normalized.length > 9) {
                // Check Work DB
                val workContact = repository?.findWorkContactByNumber(normalized)
                val inWorkDb = workContact != null

                if (inWorkDb) {
                    // It is definitely Work
                    isWork = true
                    workName = workContact.name
                    familyHead = workContact.familyHead
                } else {
                    // Only check device contacts if NOT in work DB
                    val deviceName = findPersonalContactName(normalized)
                    if (deviceName != null) {
                        isWork = false // It's purely personal
                    }
                }
            }

            // 3. Branch Logic
            if (isWork) {
                // ================= WORK FLOW =================
                // A. Save to Local DB (Always for Work calls, even for Management)
                repository?.insertWorkLog(
                    WorkCallLog(
                        name = workName,
                        familyHead = familyHead,
                        number = rawNumber,
                        duration = durationSeconds,
                        timestamp = now,
                        type = "work",
                        direction = direction,
                        simSlot = simSlot,
                        isSynced = false
                    )
                )

                appContext?.let { ctx ->
                    if (!isManagement) {
                        // B. Delete from System Log (Only for Non-Management)
                        setupLogDeletionObserver(rawNumber)

                        // C. Trigger Upload Worker (Only for Non-Management)
                        val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadCallLogWorker>()
                            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                            .build()
                        WorkManager.getInstance(ctx).enqueue(uploadWorkRequest)
                    }
                }

            } else {
                // ================= PERSONAL FLOW =================
                // No local app DB for personal calls (they stay in system log).

                // Only upload if NOT Management
                if (!isManagement) {
                    val personalData = workDataOf(
                        "duration" to durationSeconds,
                        "timestamp" to now,
                        "direction" to direction,
                        "simSlot" to simSlot
                    )

                    val personalWorkRequest = OneTimeWorkRequestBuilder<PersonalUploadWorker>()
                        .setInputData(personalData)
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .build()

                    appContext?.let { ctx ->
                        WorkManager.getInstance(ctx).enqueue(personalWorkRequest)
                    }
                }
            }
        }
    }

    // --- Boilerplate (Unchanged) ---
    private fun registerCallCallback(call: Call) {
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) { recalculateGlobalState() }
            override fun onDetailsChanged(call: Call, details: Call.Details) { recalculateGlobalState() }
            override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: List<Call>) { recalculateGlobalState() }
            override fun onChildrenChanged(call: Call, children: List<Call>) { recalculateGlobalState() }
        })
    }

    fun splitFromConference(childIndex: Int) {
        val children = _callState.value?.call?.children
        if (children != null && childIndex in children.indices) children[childIndex].splitFromConference()
    }
    fun updateAudioState(isMuted: Boolean, isSpeakerOn: Boolean, isBluetoothOn: Boolean, availableRoutes: Int) {
        _callState.value = _callState.value?.copy(isMuted = isMuted, isSpeakerOn = isSpeakerOn, isBluetoothOn = isBluetoothOn, availableRoutes = availableRoutes)
    }
    fun answerCall() { _callState.value?.call?.answer(0) }
    fun rejectCall() {
        _callState.value?.call?.let { if (it.state == Call.STATE_RINGING) it.reject(false, "") else it.disconnect() }
    }
    fun toggleSpeaker() {
        val current = _callState.value ?: return
        val route = if (current.isSpeakerOn) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER
        MyInCallService.Companion.instance?.setAudioRoute(route)
    }
    fun toggleMute() { MyInCallService.Companion.instance?.setMuted(!(_callState.value?.isMuted ?: false)) }
    fun toggleHold() {
        val current = _callState.value ?: return
        if (current.isHolding) current.call?.unhold() else current.call?.hold()
    }
    fun isBluetoothAvailable(): Boolean { return ((_callState.value?.availableRoutes ?: 0) and CallAudioState.ROUTE_BLUETOOTH) != 0 }
    fun toggleBluetooth() {
        val current = _callState.value ?: return
        if (!isBluetoothAvailable()) return
        val newRoute = if (current.isBluetoothOn) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_BLUETOOTH
        MyInCallService.Companion.instance?.setAudioRoute(newRoute)
    }
    fun playDtmfTone(digit: Char) { _callState.value?.call?.playDtmfTone(digit) }

    @SuppressLint("Range")
    private fun findPersonalContactName(normalizedNumber: String): String? {
        val context = appContext ?: return null
        var contactName: String? = null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(normalizedNumber))
        try {
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) contactName = it.getString(it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME))
            }
        } catch (e: Exception) { e.printStackTrace() }
        return contactName
    }

    private fun Call.getStateString(): String = when (this.state) {
        Call.STATE_RINGING -> "Ringing"
        Call.STATE_DIALING -> "Dialing"
        Call.STATE_ACTIVE -> "Active"
        Call.STATE_HOLDING -> "On Hold"
        Call.STATE_DISCONNECTED -> "Disconnected"
        Call.STATE_CONNECTING -> "Connecting"
        else -> "Unknown"
    }

    private fun setupLogDeletionObserver(number: String) {
        val context = appContext ?: return
        val handler = Handler(Looper.getMainLooper())
        var observer: ContentObserver? = null
        observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                coroutineScope.launch {
                    val deleted = maskAndDeleteLog(context, number)
                    if (deleted) try { observer?.let { context.contentResolver.unregisterContentObserver(it) } } catch (e: Exception) {}
                }
            }
        }
        try { context.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer) } catch (e: Exception) {}
        handler.postDelayed({ try { observer?.let { context.contentResolver.unregisterContentObserver(it) } } catch (e: Exception) {} }, 5000)
        coroutineScope.launch {
            val deleted = maskAndDeleteLog(context, number)
            if (deleted) try { observer?.let { context.contentResolver.unregisterContentObserver(it) } } catch (e: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun maskAndDeleteLog(context: Context, number: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val numberToQuery = normalizeNumber(number)
                val contentResolver = context.contentResolver
                val queryUri = CallLog.Calls.CONTENT_URI
                val projection = arrayOf(CallLog.Calls._ID, CallLog.Calls.TYPE)
                val selection = "${CallLog.Calls.NUMBER} LIKE ?"
                val selectionArgs = arrayOf("%$numberToQuery")
                val sortOrder = "${CallLog.Calls.DATE} DESC LIMIT 1"
                var entryId: String? = null
                var currentType: Int = -1
                contentResolver.query(queryUri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        entryId = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls._ID))
                        currentType = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                    }
                }
                if (entryId != null) {
                    if (currentType == CallLog.Calls.MISSED_TYPE) {
                        try {
                            val values = ContentValues().apply {
                                put(CallLog.Calls.TYPE, CallLog.Calls.INCOMING_TYPE)
                                put(CallLog.Calls.IS_READ, 1)
                                put(CallLog.Calls.NEW, 0)
                            }
                            contentResolver.update(queryUri, values, "${CallLog.Calls._ID} = ?", arrayOf(entryId))
                            delay(400)
                        } catch (e: Exception) {}
                    }
                    val rows = contentResolver.delete(queryUri, "${CallLog.Calls._ID} = ?", arrayOf(entryId))
                    if (rows > 0) return@withContext true
                }
            } catch (e: Exception) {}
            return@withContext false
        }
    }
}