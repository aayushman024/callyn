package com.mnivesh.callyn.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Defines the schema for the local contacts table.
 * This matches your requested schema.
 */
@Entity(tableName = "contacts")
data class AppContact(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val number: String,
    val type: String = "work",
    val pan: String,         // ADDED FIELD
    val rshipManager: String, // ADDED FIELD
    val familyHead: String   // ADDED FIELD
)