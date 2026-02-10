package com.mnivesh.callyn.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    /**
     * Inserts a list of contacts.
     * OnConflictStrategy.REPLACE means it will overwrite existing contacts
     * if they (based on PrimaryKey) already exist.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<AppContact>)

    /**
     */
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<AppContact>>

    /**
     */
    @Query("""
        SELECT * FROM contacts 
        WHERE number LIKE '%' || :normalizedNumber 
        ORDER BY CASE WHEN rshipManager = 'Employee' THEN 0 ELSE 1 END ASC, name ASC 
        LIMIT 1
    """)
    suspend fun getContactByNumber(normalizedNumber: String): AppContact?


    @Query("SELECT * FROM contacts WHERE name = :name LIMIT 1")
    suspend fun getContactByName(name: String): AppContact?

    /**
     * Deletes all contacts from the table.
     * This is used before a refresh.
     */
    @Query("DELETE FROM contacts")
    suspend fun deleteAll()

    // In ContactDao.kt
    @Query("DELETE FROM contacts WHERE rshipManager = :identifier")
    suspend fun deleteByRshipManager(identifier: String)

    @Query("SELECT * FROM work_call_logs WHERE isSynced = 0")
    suspend fun getUnsyncedWorkLogs(): List<WorkCallLog>

    @Query("UPDATE work_call_logs SET isSynced = 1 WHERE id = :logId")
    suspend fun markLogAsSynced(logId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrmContacts(contacts: List<CrmContact>)

    @Query("SELECT * FROM crm_contacts")
    fun getAllCrmContacts(): Flow<List<CrmContact>>

    @Query("SELECT * FROM crm_contacts WHERE number LIKE '%' || :normalizedNumber LIMIT 1")
    suspend fun getCrmContactByNumber(normalizedNumber: String): CrmContact?

    @Query("DELETE FROM crm_contacts")
    suspend fun deleteAllCrmContacts()

    // Optional: Get by module if you want to filter tabs from DB
    @Query("SELECT * FROM crm_contacts WHERE module = :moduleName")
    fun getCrmContactsByModule(moduleName: String): Flow<List<CrmContact>>

}