package com.mnivesh.callyn.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.security.MessageDigest

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

/**
 * Runtime Extension to generate a deterministic 6-digit Hex Code.
 * - Combines Name + Number + PAN (stable fields).
 * - Hashes them using MD5.
 * - Returns the first 6 characters as an Uppercase Hex string.
 * - This is NOT stored in the DB, it is calculated on the fly.
 */
val AppContact.uniqueCode: String
    get() {
        // 1. Combine stable fields.
        // Using trim() and lowercase() ensures "Ramesh" and "RAMESH" produce the SAME code.
        val rawInput = "${name.trim().lowercase()}${number.trim()}${pan.trim().lowercase()}"

        // 2. Compute MD5 Hash
        val bytes = MessageDigest.getInstance("MD5").digest(rawInput.toByteArray())

        // 3. Convert to Hex and take first 6 characters
        return bytes.joinToString("") { "%02x".format(it) }.take(6).uppercase()
    }