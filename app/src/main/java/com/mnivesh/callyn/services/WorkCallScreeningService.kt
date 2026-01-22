//package com.mnivesh.callyn
//
//import android.telecom.Call
//import android.telecom.CallScreeningService
//import android.util.Log
//import com.mnivesh.callyn.data.ContactRepository
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//
//class WorkCallScreeningService : CallScreeningService() {
//
//    private val scope = CoroutineScope(Dispatchers.IO)
//    private lateinit var repository: ContactRepository
//
//    override fun onCreate() {
//        super.onCreate()
//        try {
//            val app = applicationContext as CallynApplication
//            repository = app.repository
//        } catch (e: Exception) {
//            Log.e("ScreeningService", "Could not get repository", e)
//        }
//    }
//
//    override fun onScreenCall(callDetails: Call.Details) {
//        val handle = callDetails.handle
//        val rawNumber = handle?.schemeSpecificPart ?: ""
//
//        if (rawNumber.isEmpty()) {
//            respondToCall(callDetails, CallResponse.Builder().build())
//            return
//        }
//
//        scope.launch {
//            // Check if this number belongs to a Work Contact
//            val normalized = rawNumber.filter { it.isDigit() }.takeLast(10)
//            var isWork = false
//
//            if (normalized.length >= 10) {
//                val workContact = repository.findWorkContactByNumber(normalized)
//                if (workContact != null) {
//                    isWork = true
//                }
//            }
//
//            val response = CallResponse.Builder()
//
//            if (isWork) {
//                Log.d("ScreeningService", "Work call detected ($rawNumber). Skipping System Log & Notification.")
//                // ✅ CRITICAL: This prevents the system missed call notification
//                response.setSkipCallLog(true)
//                response.setSkipNotification(true)
//
//                // Allow the call to ring normally
//                response.setDisallowCall(false)
//            } else {
//                // Personal call: Standard behavior
//                response.setDisallowCall(false)
//                response.setSkipCallLog(false)
//                response.setSkipNotification(false)
//            }
//
//            respondToCall(callDetails, response.build())
//        }
//    }
//}