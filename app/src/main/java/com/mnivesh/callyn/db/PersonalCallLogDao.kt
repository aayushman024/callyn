package com.mnivesh.callyn.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PersonalCallLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: PersonalCallLog)

    // fetch all, since this table only holds unsynced data
    @Query("SELECT * FROM personal_call_logs")
    suspend fun getAllPending(): List<PersonalCallLog>

    // drop specific IDs after server ack so we don't nuke mid-flight inserts
    @Query("DELETE FROM personal_call_logs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Int>)
}