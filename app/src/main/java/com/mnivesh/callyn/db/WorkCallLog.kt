package com.mnivesh.callyn.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "work_call_logs")
data class WorkCallLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val number: String,
    val duration: Long,
    val timestamp: Long,
    val type: String,          // "work"
    //val recordingPath: String? = null,

    // --- NEW FIELDS ---
    val direction: String = "", // "incoming" or "outgoing"
    val isSynced: Boolean = false       // true = uploaded
)