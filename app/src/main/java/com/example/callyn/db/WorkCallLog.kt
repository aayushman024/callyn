package com.example.callyn.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "work_call_logs")
data class WorkCallLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val number: String,
    val duration: Long,
    val timestamp: Long,
    val type: String = "work",
    val recordingPath: String? = null // Added field for the recording file path
)