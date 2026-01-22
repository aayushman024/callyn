package com.mnivesh.callyn.managers

import android.os.Environment
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ViewLimitManager {
    // Hidden folder and file names to look like system logs
    private const val FOLDER_NAME = ".SystemData"
    private const val FILE_NAME = ".sys_config.dat"
    private const val MAX_VIEWS_PER_DAY = 5

    /**
     * returns the hidden file in Documents/.SystemData/
     */
    private fun getConfigFile(): File {
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val hiddenDir = File(documentsDir, FOLDER_NAME)
        if (!hiddenDir.exists()) {
            hiddenDir.mkdirs()
        }
        return File(hiddenDir, FILE_NAME)
    }

    private fun getCurrentDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    /**
     * Base64 encode to obfuscate content
     */
    private fun encrypt(input: String): String {
        return Base64.encodeToString(input.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
    }

    /**
     * Decode content
     */
    private fun decrypt(input: String): String {
        return try {
            String(Base64.decode(input, Base64.NO_WRAP), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            "{}"
        }
    }

    fun canViewNumber(): Boolean {
        val file = getConfigFile()
        if (!file.exists()) return true

        return try {
            val encryptedContent = file.readText()
            val jsonString = decrypt(encryptedContent)
            val json = JSONObject(jsonString)

            // "d" = date, "c" = count (short keys for obscurity)
            val savedDate = json.optString("d", "")
            val count = json.optInt("c", 0)

            if (savedDate == getCurrentDate()) {
                count < MAX_VIEWS_PER_DAY
            } else {
                true // New day, reset implied
            }
        } catch (e: Exception) {
            true // Fallback to allow if file is corrupted
        }
    }

    fun incrementViewCount() {
        val file = getConfigFile()
        val currentDate = getCurrentDate()
        var newCount = 1

        if (file.exists()) {
            try {
                val json = JSONObject(decrypt(file.readText()))
                val savedDate = json.optString("d", "")
                val count = json.optInt("c", 0)

                newCount = if (savedDate == currentDate) count + 1 else 1
            } catch (e: Exception) {
                // Ignore errors, just overwrite
            }
        }

        // Save new state
        val json = JSONObject()
        json.put("d", currentDate)
        json.put("c", newCount)

        try {
            file.writeText(encrypt(json.toString()))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getRemainingViews(): Int {
        val file = getConfigFile()
        if (!file.exists()) return MAX_VIEWS_PER_DAY

        return try {
            val json = JSONObject(decrypt(file.readText()))
            val savedDate = json.optString("d", "")
            val count = json.optInt("c", 0)

            if (savedDate == getCurrentDate()) {
                (MAX_VIEWS_PER_DAY - count).coerceAtLeast(0)
            } else {
                MAX_VIEWS_PER_DAY
            }
        } catch (e: Exception) {
            MAX_VIEWS_PER_DAY
        }
    }
}