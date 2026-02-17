package com.mnivesh.callyn.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "work_call_logs",
    indices = [
        Index(value = ["timestamp"]), // Speeds up "ORDER BY timestamp DESC"
        Index(value = ["number"]), // Speeds up filtering by number
        Index(value = ["name"])
    ]
    )
data class WorkCallLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val familyHead: String,
    val number: String,
    val duration: Long,
    val notes: String?,
    val timestamp: Long,
    val type: String,
    //val recordingPath: String? = null,
    val direction: String = "", // "incoming" or "outgoing"
    val simSlot: String? = null,
    val isSynced: Boolean = false       // true = uploaded
)