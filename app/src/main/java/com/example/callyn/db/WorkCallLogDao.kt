package com.example.callyn.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkCallLogDao {
    @Insert
    suspend fun insert(log: WorkCallLog)

    @Query("SELECT * FROM work_call_logs ORDER BY timestamp DESC")
    fun getAllWorkLogs(): Flow<List<WorkCallLog>>
}