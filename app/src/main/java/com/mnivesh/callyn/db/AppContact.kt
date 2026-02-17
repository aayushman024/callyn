package com.mnivesh.callyn.db

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "contacts",
    indices = [
        Index(value = ["number"]), // Speeds up lookup by phone number
        Index(value = ["name"])    // Speeds up sorting and searching by name
    ])
data class AppContact(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val number: String,
    val type: String = "work",
    val pan: String,
    val rshipManager: String,
    val familyHead: String,
    val aum: String?,
    val familyAum: String?,
) {
    /**
     * HIGH-SPEED OPTIMIZATION:
     * 1. Computed inside the class as a 'val'. This runs ONLY ONCE when the object is created.
     * 2. Uses 'hashCode()' instead of MD5. This is ~100x faster and requires zero byte-array allocations.
     * 3. @Ignore ensures Room does not try to save this as a database column.
     */
    @get:Ignore
    val uniqueCode: String by lazy(LazyThreadSafetyMode.NONE) {
        // 1. Combine fields (String concatenation is fast enough for this)
        val rawInput = name.trim().lowercase() + number.trim() + pan.trim().lowercase()

        // 2. Compute Hash (Native Java/Kotlin Int Hash is extremely fast)
        val hash = rawInput.hashCode()

        // 3. Convert to 6-char Hex
        // We mask with 0xFFFFFF to ensure we get a clean 24-bit integer (max 6 hex chars)
        // and handle negative hash codes gracefully.
        val hex = Integer.toHexString(hash and 0xFFFFFF).uppercase()

        // 4. Pad with zeros if the hash happens to be small (e.g., "A1" -> "0000A1")
        hex.padStart(6, '0')
    }
}