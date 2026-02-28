package com.mnivesh.callyn.managers

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.annotation.RequiresApi
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mnivesh.callyn.services.MyInCallService
import com.mnivesh.callyn.data.ContactRepository
import com.mnivesh.callyn.db.WorkCallLog
import com.mnivesh.callyn.workers.DeleteCallLogWorker
import com.mnivesh.callyn.workers.PersonalUploadWorker
import com.mnivesh.callyn.workers.UploadCallLogWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

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
    internal val secondIncomingCall: Call? = null, // The "Waiting" or "Background" call
    val isSecondCallHolding: Boolean = false
)

object CallManager {

    private val _callState = MutableStateFlow<CallState?>(null)
    val callState = _callState.asStateFlow()

    // Thread-safe list and map to prevent CME and memory leaks
    private val registeredCalls = CopyOnWriteArrayList<Call>()
    private val callCallbacks = ConcurrentHashMap<Call, Call.Callback>()

    private var repository: ContactRepository? = null
    private var appContext: Context? = null

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Left as-is per request
    private var currentNote: String? = null

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

        // Unregister callback to prevent memory leak
        callCallbacks.remove(call)?.let { call.unregisterCallback(it) }

        val noteToSave = currentNote

        // 1. Handle Logging (Passing the note)
        handleCallLogging(call, noteToSave)

        // Reset note for next call
        currentNote = null

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
        val handle = details.handle
        val displayNumber = handle?.schemeSpecificPart ?: ""

        val currentState = _callState.value
        var finalName = if (isConference) {
            "Conference (${children.size})"
        } else {
            displayNumber
        }

        // prevent UI flickering: reuse the resolved name if the number hasn't changed while DB queries run
        if (!isConference && displayNumber.isNotEmpty()) {
            if (currentState?.number == displayNumber && currentState.name != displayNumber) {
                finalName = currentState.name
            } else {
                resolveContactInfo(displayNumber)
            }
        }

        // --- BACKGROUND CALL LOGIC (Waiting & Hold) ---
        val waitingCall = registeredCalls.find { it != primary && it.state == Call.STATE_RINGING }
        val heldCall = registeredCalls.find { it != primary && it.state == Call.STATE_HOLDING }

        val targetSecondary = waitingCall ?: heldCall
        val isSecondRinging = targetSecondary?.state == Call.STATE_RINGING
        val isSecondHolding = targetSecondary?.state == Call.STATE_HOLDING

        // Separate raw number from resolved name to keep the number dialable
        val secNumber = targetSecondary?.details?.handle?.schemeSpecificPart ?: ""
        var secName = secNumber

        if (isSecondRinging && currentState?.secondCallerNumber == secNumber) {
            secName = currentState.secondCallerName ?: secNumber
        } else if (targetSecondary != null) {
            resolveSecondaryContactInfo(secNumber)
        }

        // --- EMIT STATE atomically to prevent lost updates ---
        _callState.update { current ->
            (current ?: CallState(name = finalName, number = displayNumber, status = "Connecting")).copy(
                name = finalName,
                number = displayNumber,
                status = primary.getStateString(),
                isIncoming = (primary.state == Call.STATE_RINGING),
                isConference = isConference,
                canMerge = (details.callCapabilities and Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0 ||
                        (registeredCalls.size > 1 && !isSecondRinging),
                canSwap = (details.callCapabilities and Call.Details.CAPABILITY_SWAP_CONFERENCE) != 0 ||
                        (registeredCalls.size > 1 && isSecondHolding),
                participants = children.map { it.details.handle?.schemeSpecificPart ?: "Unknown" },
                call = primary,
                isHolding = (primary.state == Call.STATE_HOLDING),
                connectTimeMillis = details.connectTimeMillis,
                secondIncomingCall = if (isSecondRinging) targetSecondary else null,
                secondCallerName = if (targetSecondary != null) secName else null,
                secondCallerNumber = if (targetSecondary != null) secNumber else null,
                isSecondCallHolding = isSecondHolding
            )
        }
    }

    // --- Actions ---

    fun setCallNote(note: String) {
        currentNote = if (note.isBlank()) null else note
    }

    fun getCallNote(): String? {
        return currentNote
    }

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

    fun sendQuickResponse(context: Context, number: String, message: String) {
        try {
            val call = _callState.value?.call
            if (call != null && call.state == Call.STATE_RINGING) {
                call.reject(true, message)
            } else {
                val smsManager = context.getSystemService(SmsManager::class.java)
                smsManager.sendTextMessage(number, null, message, null, null)
                rejectCall()
            }
        } catch (e: Exception) {
            rejectCall()
        }
    }

    // --- Resolution Logic ---

    private fun resolveSecondaryContactInfo(number: String) {
        if (number.isBlank()) return
        val normalized = normalizeNumber(number)

        if (normalized.length < 7) return

        coroutineScope.launch {
            var resolvedName: String? = null

            // 1. Work Contact
            val workContact = repository?.findWorkContactByNumber(normalized)
            if (workContact != null) {
                resolvedName = workContact.name
            } else {
                // Check CRM if not in work DB
                val crmContact = repository?.findCrmContactByNumber(normalized)
                if (crmContact != null) {
                    resolvedName = crmContact.name
                }
            }

            // 2. Device Contact
            if (resolvedName == null) {
                resolvedName = findPersonalContactName(normalized)
            }

            // 3. CNAP / Network Name
            if (resolvedName == null) {
                val primaryCall = _callState.value?.call
                val secondaryCall = registeredCalls.find { it != primaryCall }

                val cnapName = secondaryCall?.details?.callerDisplayName
                if (!cnapName.isNullOrBlank() && normalizeNumber(cnapName) != normalized) {
                    resolvedName = cnapName
                }
            }

            val finalName = resolvedName ?: number

            _callState.update { current ->
                if (current != null && current.secondCallerNumber == number) {
                    current.copy(secondCallerName = finalName)
                } else current
            }
        }
    }

    private fun resolveContactInfo(number: String) {
        if (number.isBlank()) return
        val normalized = normalizeNumber(number)

        coroutineScope.launch {
            var resolvedName: String? = null
            var type = "unknown"
            var familyHead: String? = null
            var rshipManager: String? = null
            var aum: String? = null
            var familyAum: String? = null

            // Properly gate CRM contact check
            if (normalized.length > 9) {
                val workContact = repository?.findWorkContactByNumber(normalized)
                if (workContact != null) {
                    resolvedName = workContact.name
                    type = "work"
                    familyHead = workContact.familyHead
                    rshipManager = workContact.rshipManager
                    aum = workContact.aum
                    familyAum = workContact.familyAum
                } else {
                    val crmContact = repository?.findCrmContactByNumber(normalized)
                    if (crmContact != null) {
                        resolvedName = crmContact.name
                        type = "work"
                        familyHead = crmContact.module
                        rshipManager = crmContact.ownerName
                        aum = crmContact.product
                    }
                }
            }

            if (resolvedName == null) {
                val personalName = findPersonalContactName(normalized)
                if (personalName != null) {
                    resolvedName = personalName
                    type = "personal"
                }
            }

            if (resolvedName == null) {
                val cnapName = _callState.value?.call?.details?.callerDisplayName
                if (!cnapName.isNullOrBlank() && normalizeNumber(cnapName) != normalized) {
                    resolvedName = cnapName
                }
            }

            val finalName = resolvedName ?: number

            _callState.update { current ->
                if (current?.number == number) {
                    current.copy(
                        name = finalName,
                        type = type,
                        familyHead = familyHead,
                        rshipManager = rshipManager,
                        aum = aum,
                        familyAum = familyAum
                    )
                } else current
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getSimSlot(context: Context, call: Call): String {
        try {
            val accountHandle = call.details.accountHandle ?: return "Unknown"
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

            val activeSubs = sm.activeSubscriptionInfoList ?: return "Unknown"
            val subInfo = activeSubs.find {
                it.subscriptionId == accountHandle.id.toIntOrNull() || it.iccId == accountHandle.id
            }

            return if (subInfo != null) "SIM ${subInfo.simSlotIndex + 1}" else "Unknown"
        } catch (e: Exception) {
            return "Unknown"
        }
    }

    // Added API guard explicitly
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun handleCallLogging(call: Call, callNote: String?) {
        val rawNumber = call.details.handle?.schemeSpecificPart ?: ""
        if (rawNumber.isBlank()) return

        coroutineScope.launch {
            val dept = appContext?.let { AuthManager(it).getDepartment() } ?: "N/A"
            val isManagement = dept == "Management"

            val now = System.currentTimeMillis()
            val durationSeconds = if (call.details.connectTimeMillis > 0) {
                (now - call.details.connectTimeMillis) / 1000
            } else {
                0
            }
            val startTimestamp = if (call.details.connectTimeMillis > 0) call.details.connectTimeMillis else call.details.creationTimeMillis

            var direction = "outgoing"
            val wasIncoming = (call.details.callDirection == Call.Details.DIRECTION_INCOMING)
            if (wasIncoming) {
                direction = if (call.details.connectTimeMillis > 0) "incoming" else "missed"
            }

            val simSlot = appContext?.let { getSimSlot(it, call) } ?: "Unknown"
            val normalized = normalizeNumber(rawNumber)

            var isWork = false
            var workName = ""
            var familyHead = ""

            // CRM logic shifted inside the length > 9 block
            if (normalized.length > 9) {
                val workContact = repository?.findWorkContactByNumber(normalized)
                if (workContact != null) {
                    isWork = true
                    workName = workContact.name
                    familyHead = workContact.familyHead
                } else {
                    val crmContact = repository?.findCrmContactByNumber(normalized)
                    if (crmContact != null) {
                        isWork = true
                        workName = crmContact.name
                        familyHead = crmContact.product ?: "N/A"
                    } else {
                        val deviceName = findPersonalContactName(normalized)
                        if (deviceName != null) {
                            isWork = false
                        }
                    }
                }
            }

            if (isWork) {
                repository?.insertWorkLog(
                    WorkCallLog(
                        name = workName,
                        familyHead = familyHead,
                        number = rawNumber,
                        duration = durationSeconds,
                        timestamp = startTimestamp,
                        type = "work",
                        direction = direction,
                        simSlot = simSlot,
                        isSynced = false,
                        notes = callNote
                    )
                )

                appContext?.let { ctx ->
                    if (!isManagement) {
                        // Replaced in-memory observer with WorkManager implementation
                        val deleteData = workDataOf("number" to rawNumber)
                        val deleteWorkRequest = OneTimeWorkRequestBuilder<DeleteCallLogWorker>()
                            .setInputData(deleteData)
                            .build()

                        WorkManager.getInstance(ctx).enqueueUniqueWork(
                            "delete_log_$rawNumber",
                            ExistingWorkPolicy.REPLACE,
                            deleteWorkRequest
                        )

                        val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadCallLogWorker>()
                            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                            .build()
                        WorkManager.getInstance(ctx).enqueue(uploadWorkRequest)
                    }
                }

            } else {
                if (!isManagement) {
                    val personalData = workDataOf(
                        "duration" to durationSeconds,
                        "timestamp" to startTimestamp,
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

    private fun registerCallCallback(call: Call) {
        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) { recalculateGlobalState() }
            override fun onDetailsChanged(call: Call, details: Call.Details) { recalculateGlobalState() }
            override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: List<Call>) { recalculateGlobalState() }
            override fun onChildrenChanged(call: Call, children: List<Call>) { recalculateGlobalState() }
        }
        callCallbacks[call] = callback
        call.registerCallback(callback)
    }

    fun splitFromConference(childIndex: Int) {
        val children = _callState.value?.call?.children
        if (children != null && childIndex in children.indices) children[childIndex].splitFromConference()
    }
    fun updateAudioState(isMuted: Boolean, isSpeakerOn: Boolean, isBluetoothOn: Boolean, availableRoutes: Int) {
        _callState.update { current -> current?.copy(isMuted = isMuted, isSpeakerOn = isSpeakerOn, isBluetoothOn = isBluetoothOn, availableRoutes = availableRoutes) }
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
}