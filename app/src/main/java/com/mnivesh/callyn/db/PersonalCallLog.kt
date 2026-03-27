package com.mnivesh.callyn.db

import androidx.room.Entity
import androidx.room.PrimaryKey

// keeping only unsynced logs here, will drop them once uploaded
@Entity(tableName = "personal_call_logs")
data class PersonalCallLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val duration: Long,
    val timestamp: Long,
    val direction: String,
    val simSlot: String
)