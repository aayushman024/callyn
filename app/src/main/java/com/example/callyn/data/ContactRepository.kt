package com.example.callyn.data

import android.util.Log
import com.example.callyn.api.ApiService
import com.example.callyn.db.AppContact
import com.example.callyn.db.ContactDao
import com.example.callyn.db.WorkCallLog
import com.example.callyn.db.WorkCallLogDao
import kotlinx.coroutines.flow.Flow
import java.net.UnknownHostException

class ContactRepository(
    private val contactDao: ContactDao,
    private val workCallLogDao: WorkCallLogDao,
    private val apiService: ApiService
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
                        type = it.type
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
}