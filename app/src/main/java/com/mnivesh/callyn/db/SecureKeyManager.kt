package com.mnivesh.callyn.db

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

class SecureKeyManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "secure_db_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getOrGenerateSecureKey(): ByteArray {
        val base64Key = sharedPrefs.getString("db_key", null)
        if (base64Key != null) {
            return Base64.decode(base64Key, Base64.DEFAULT)
        }

        // Generate a 32-byte random key
        val randomBytes = ByteArray(32)
        SecureRandom().nextBytes(randomBytes)
        
        // Save it securely
        val newBase64Key = Base64.encodeToString(randomBytes, Base64.DEFAULT)
        sharedPrefs.edit().putString("db_key", newBase64Key).apply()
        
        return randomBytes
    }
}
