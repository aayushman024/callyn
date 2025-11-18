package com.example.callyn.data

import android.util.Log
import com.example.callyn.api.ApiService
import com.example.callyn.api.ContactResponse
import com.example.callyn.db.AppContact
import com.example.callyn.db.ContactDao
import kotlinx.coroutines.flow.Flow
import java.net.UnknownHostException

/**
 * Repository to manage fetching data from the API and
 * storing/retrieving it from the local Room database.
 */
class ContactRepository(
    private val contactDao: ContactDao,
    private val apiService: ApiService
) {
    private val TAG = "ContactRepository"

    // The "Single Source of Truth". The UI will only observe this.
    val allContacts: Flow<List<AppContact>> = contactDao.getAllContacts()

    /**
     * Fetches fresh contacts from the server and refreshes the local database.
     */
    suspend fun refreshContacts(token: String, managerName: String) {
        try {
            // 1. Fetch from network
            val response = apiService.getContacts("Bearer $token", managerName)

            if (response.isSuccessful && response.body() != null) {
                val networkContacts = response.body()!!
                Log.d(TAG, "Fetched ${networkContacts.size} contacts from API")

                // 2. Transform API response to database entity
                val dbContacts = networkContacts.map {
                    AppContact(
                        name = it.name,
                        number = it.number,
                        type = it.type
                    )
                }

                // 3. Clear the old database
                contactDao.deleteAll()

                // 4. Insert the new data
                contactDao.insertAll(dbContacts)
                Log.d(TAG, "Successfully refreshed local database.")

            } else {
                Log.e(TAG, "API Error: ${response.message()}")
            }
        } catch (e: UnknownHostException) {
            Log.e(TAG, "Network Error: Cannot connect to host. Check IP address.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh contacts", e)
        }
    }

    /**
     * FIX: Function now accepts a normalized number for lookup.
     */
    suspend fun findWorkContactByNumber(normalizedNumber: String): AppContact? {
        return contactDao.getContactByNumber(normalizedNumber)
    }
}