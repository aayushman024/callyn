package com.example.callyn.db

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
     * Gets all contacts from the table, ordered by name.
     * Returns a Flow, so the UI can observe changes automatically.
     */
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<AppContact>>

    /**
     */
    @Query("SELECT * FROM contacts WHERE number LIKE '%' || :normalizedNumber LIMIT 1")
    suspend fun getContactByNumber(normalizedNumber: String): AppContact?

    /**
     * Deletes all contacts from the table.
     * This is used before a refresh.
     */
    @Query("DELETE FROM contacts")
    suspend fun deleteAll()
}