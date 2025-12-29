package com.mnivesh.callyn.data

import android.util.Log
import com.mnivesh.callyn.api.RetrofitInstance.api
import com.mnivesh.callyn.api.ApiService
import com.mnivesh.callyn.api.PendingRequest
import com.mnivesh.callyn.api.PersonalRequestData // <--- NEW IMPORT
import com.mnivesh.callyn.api.UpdateRequestStatusBody
import com.mnivesh.callyn.db.AppContact
import com.mnivesh.callyn.db.ContactDao
import com.mnivesh.callyn.db.WorkCallLog
import com.mnivesh.callyn.db.WorkCallLogDao
import kotlinx.coroutines.flow.Flow
import java.net.UnknownHostException

class ContactRepository(
    private val contactDao: ContactDao,
    private val workCallLogDao: WorkCallLogDao,
    val apiService: ApiService // Changed to 'val' so it's accessible if needed, or keep 'private' if only used internally
) {
    private val TAG = "ContactRepository"

    val allContacts: Flow<List<AppContact>> = contactDao.getAllContacts()
    val allWorkLogs: Flow<List<WorkCallLog>> = workCallLogDao.getAllWorkLogs()

    // [!code change] Return Boolean instead of Unit
    suspend fun refreshContacts(token: String, managerName: String): Boolean {
        try {
            val response = apiService.getContacts("Bearer $token", managerName)

            if (response.isSuccessful && response.body() != null) {
                // ... (your existing database logic) ...
                val networkContacts = response.body()!!
                val dbContacts = networkContacts.map {
                    AppContact(
                        name = it.name,
                        number = it.number,
                        type = it.type,
                        pan = it.pan,
                        familyHead = it.familyHead,
                        rshipManager = it.rshipManager
                    )
                }
                contactDao.deleteAll()
                contactDao.insertAll(dbContacts)
                Log.d(TAG, "Successfully refreshed local database.")

                return true // [!code ++] Return Success
            } else {
                Log.e(TAG, "API Error: ${response.message()}")
                return false // [!code ++] Return Failure
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh contacts", e)
            return false // [!code ++] Return Failure
        }
    }

    suspend fun findWorkContactByNumber(normalizedNumber: String): AppContact? {
        return contactDao.getContactByNumber(normalizedNumber)
    }

    suspend fun insertWorkLog(log: WorkCallLog) {
        workCallLogDao.insert(log)
    }

    suspend fun clearAllData() {
        contactDao.deleteAll()
        workCallLogDao.deleteAll()
    }

    suspend fun getUnsyncedLogs(): List<WorkCallLog> {
        return contactDao.getUnsyncedWorkLogs()
    }

    suspend fun markLogSynced(id: Int) {
        contactDao.markLogAsSynced(id)
    }

    // --- NEW FUNCTION: Request Personal Contact ---
    suspend fun submitPersonalRequest(token: String, contactName: String, userName: String, reason: String): Boolean {
        return try {
            val requestBody = PersonalRequestData(
                requestedContact = contactName,
                requestedBy = userName,
                reason = reason
            )
            // Call the API endpoint
            val response = apiService.requestAsPersonal("Bearer $token", requestBody)

            if (response.isSuccessful) {
                Log.d(TAG, "Personal request submitted successfully.")
                true
            } else {
                Log.e(TAG, "Personal request failed: ${response.code()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception submitting personal request", e)
            false
        }
    }
    suspend fun getPendingRequests(token: String): List<PendingRequest> {
        return try {
            val response = api.getPendingRequests("Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                response.body()!!
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun updateRequestStatus(token: String, requestId: String, status: String): Boolean {
        return try {
            val response = api.updateRequestStatus(
                "Bearer $token",
                UpdateRequestStatusBody(requestId, status)
            )
            response.isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}