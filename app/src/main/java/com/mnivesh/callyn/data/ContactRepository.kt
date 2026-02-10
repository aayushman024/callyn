package com.mnivesh.callyn.data

import android.util.Log
import com.mnivesh.callyn.api.RetrofitInstance.api
import com.mnivesh.callyn.api.ApiService
import com.mnivesh.callyn.api.EmployeeDirectory
import com.mnivesh.callyn.api.PendingRequest
import com.mnivesh.callyn.api.PersonalRequestData // <--- NEW IMPORT
import com.mnivesh.callyn.api.UpdateRequestStatusBody
import com.mnivesh.callyn.db.AppContact
import com.mnivesh.callyn.db.ContactDao
import com.mnivesh.callyn.db.CrmContact
import com.mnivesh.callyn.db.WorkCallLog
import com.mnivesh.callyn.db.WorkCallLogDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.UnknownHostException
import java.time.Instant

class ContactRepository(
    private val contactDao: ContactDao,
    private val workCallLogDao: WorkCallLogDao,
    val apiService: ApiService // Changed to 'val' so it's accessible if needed, or keep 'private' if only used internally
) {
    private val TAG = "ContactRepository"

    val allContacts: Flow<List<AppContact>> = contactDao.getAllContacts()
    val allWorkLogs: Flow<List<WorkCallLog>> = workCallLogDao.getAllWorkLogs()
    val crmContacts: Flow<List<CrmContact>> = contactDao.getAllCrmContacts()

    private var cachedEmployees: List<EmployeeDirectory>? = null

    private val _isCrmLoading = MutableStateFlow(false)
    val isCrmLoading = _isCrmLoading.asStateFlow()

    // [!code change] Updated getEmployees function
    suspend fun getEmployees(token: String, forceRefresh: Boolean = false): Result<List<EmployeeDirectory>> {
        if (!forceRefresh && cachedEmployees != null) {
            return Result.success(cachedEmployees!!)
        }

        return try {
            val response = apiService.getEmployeePhoneDetails("Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                cachedEmployees = response.body()

                // 1. Map Employees to AppContact Entity
                val employeeContacts = cachedEmployees!!.map { employee ->
                    AppContact(
                        name = employee.name,
                        number = employee.phone,
                        type = "work",
                        pan = employee.email,
                        familyHead = employee.department,
                        rshipManager = "Employee",
                        aum = "0",
                        familyAum = "0",
                    )
                }

                // 2. Save to Database
                contactDao.deleteByRshipManager("Employee")
                // Note: insertAll uses OnConflictStrategy.REPLACE
                contactDao.insertAll(employeeContacts)
                Log.d(TAG, "Saved ${employeeContacts.size} employees to local database.")

                Result.success(cachedEmployees!!)
            } else {
                Result.failure(Exception("Failed to fetch employees: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // [!code change] Return Boolean instead of Unit
    suspend fun refreshContacts(token: String, managerName: String): Boolean {
        try {
            val response = apiService.getContacts("Bearer $token", managerName)

            if (response.isSuccessful && response.body() != null) {
                val networkContacts = response.body()!!
                val dbContacts = networkContacts.map {
                    AppContact(
                        name = it.name,
                        number = it.number,
                        type = it.type,
                        pan = it.pan,
                        familyHead = it.familyHead,
                        rshipManager = it.rshipManager,
                        aum = it.aum,
                        familyAum = it.familyAum
                    )
                }
                contactDao.deleteAll()
                contactDao.insertAll(dbContacts)
                Log.d(TAG, "Successfully refreshed local database.")

                //insert employees data
                getEmployees(token, forceRefresh = true)

                Log.d(TAG, "Successfully refreshed employee database.")

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

    suspend fun syncInitialData(token: String, managerName: String) {
        try {
            // [!code ++] Fire-and-Forget CRM Data Sync
            // This runs on a separate thread and does NOT block the lines below
            CoroutineScope(Dispatchers.IO).launch {
                refreshCrmData(token)
            }
            // 1. Refresh Contacts (Pre-load)
            refreshContacts(token, managerName)

            // 2. Fetch Call Logs from API
            val today = java.time.LocalDate.now().toString()

            val response = apiService.getCallLogs("Bearer $token", null, managerName, endDate = today)

            if (response.isSuccessful && response.body() != null) {
                val logs = response.body()!!.data

                // 3. Map and Insert Logs
                logs.forEach { log ->
                    // Find contact to get number
                    val contact = contactDao.getContactByName(log.callerName)
                    if (contact != null) {
                        // Parse Timestamp (API returns ISO string, DB needs Long)
                        val timestampLong = try {
                            Instant.parse(log.timestamp).toEpochMilli()
                        } catch (e: Exception) {
                            System.currentTimeMillis()
                        }

                        val workLog = WorkCallLog(
                            name = log.callerName,
                            familyHead = log.familyHead,
                            number = contact.number, // Mapped from DB
                            duration = log.duration,
                            notes = log.notes,
                            timestamp = timestampLong,
                            type = "work",
                            direction = log.type, // "incoming", "outgoing", "missed"
                            isSynced = true // Fetched from server, so it is synced
                        )
                        workCallLogDao.insert(workLog)
                    }
                }
            } else {
                Log.d(TAG, "Initial data sync completed successfully.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync initial data", e)
        }
    }

    suspend fun findWorkContactByNumber(normalizedNumber: String): AppContact? {
        return contactDao.getContactByNumber(normalizedNumber)
    }

    suspend fun findCrmContactByNumber(normalizedNumber: String): CrmContact? {
        return contactDao.getCrmContactByNumber(normalizedNumber)
    }

    suspend fun insertWorkLog(log: WorkCallLog) {
        workCallLogDao.insert(log)
    }

    suspend fun clearAllData() {
        contactDao.deleteAll()
        workCallLogDao.deleteAll()
        contactDao.deleteAllCrmContacts()
    }

    suspend fun getUnsyncedLogs(): List<WorkCallLog> {
        return contactDao.getUnsyncedWorkLogs()
    }

    suspend fun markLogSynced(id: Int) {
        contactDao.markLogAsSynced(id)
    }

    // --- NEW FUNCTION: Request Personal Contact ---
    // Inside com.mnivesh.callyn.data.ContactRepository

    suspend fun submitPersonalRequest(token: String, contactName: String, userName: String, reason: String): Boolean {
        return try {
            val requestBody = PersonalRequestData(
                requestedContact = contactName,
                requestedBy = userName,
                reason = reason
            )

            val response = apiService.requestAsPersonal("Bearer $token", requestBody)

            if (response.isSuccessful) {
                Log.d(TAG, "Personal request submitted successfully.")
                true
            } else {
                // --- UPDATED LOGGING HERE ---
                val errorMsg = response.errorBody()?.string()
                Log.e(TAG, "Personal request failed: ${response.code()} - Body: $errorMsg")
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

    /**
     * Fetches CRM data from API, wipes old CRM table, and inserts new data.
     */
    suspend fun refreshCrmData(token: String): Result<Boolean> {
        _isCrmLoading.value = true
        return try {
            val response = apiService.getCrmData("Bearer $token")

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!

                if (body.success) {
                    val syncData = body.data
                    val allCrmContacts = mutableListOf<CrmContact>()

                    // Helper to map API Record -> DB Entity
                    fun mapToEntity(records: List<com.mnivesh.callyn.api.CrmRecord>, module: String) {
                        records.forEach { record ->
                            allCrmContacts.add(
                                CrmContact(
                                    recordId = record.id ?: "N/A",
                                    name = record.clientName ?: "Unknown",
                                    number = record.clientMobileNumber ?: "",
                                    ownerName = record.ownerName ?: "N/A",
                                    module = module,
                                    product = record.product,
                                )
                            )
                        }
                    }

                    // Map all modules
                    syncData.tickets?.data?.let { mapToEntity(it, "Tickets") }
                    syncData.investmentLeads?.data?.let { mapToEntity(it, "Investment_leads") }
                    syncData.insuranceLeads?.data?.let { mapToEntity(it, "Insurance_Leads") }

                    // Database Transaction
                    contactDao.deleteAllCrmContacts()
                    contactDao.insertCrmContacts(allCrmContacts)

                    Result.success(true)
                } else {
                    Result.failure(Exception(body.message))
                }
            } else {
                Result.failure(Exception("Failed to fetch CRM data: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _isCrmLoading.value = false
        }
    }
}
