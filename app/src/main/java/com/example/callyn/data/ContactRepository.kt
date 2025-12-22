package com.example.callyn.data

import android.util.Log
import com.example.callyn.api.ApiService
import com.example.callyn.api.PersonalRequestData // <--- NEW IMPORT
import com.example.callyn.db.AppContact
import com.example.callyn.db.ContactDao
import com.example.callyn.db.WorkCallLog
import com.example.callyn.db.WorkCallLogDao
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

    suspend fun refreshContacts(token: String, managerName: String) {
        try {
            val response = apiService.getContacts("Bearer $token", managerName)

            if (response.isSuccessful && response.body() != null) {
                val networkContacts = response.body()!!
                Log.d(TAG, "Fetched ${networkContacts.size} contacts from API")

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

            } else {
                Log.e(TAG, "API Error: ${response.message()}")
            }
        } catch (e: UnknownHostException) {
            Log.e(TAG, "Network Error: Cannot connect to host.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh contacts", e)
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
}