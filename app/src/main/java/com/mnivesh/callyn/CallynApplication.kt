package com.mnivesh.callyn

import android.app.Application
import com.mnivesh.callyn.api.RetrofitInstance
import com.mnivesh.callyn.data.ContactRepository
import com.mnivesh.callyn.db.ContactDatabase
import com.mnivesh.callyn.managers.CallManager

class CallynApplication : Application() {

    private val database by lazy {
        // Load native libraries required by SQLCipher
        System.loadLibrary("sqlcipher")
        
        val secureKeyManager = com.mnivesh.callyn.db.SecureKeyManager(this)
        val securePassphrase = secureKeyManager.getOrGenerateSecureKey()
        
        ContactDatabase.getDatabase(this, securePassphrase)
    }

    val repository by lazy {
        ContactRepository(
            database.contactDao(),
            database.workCallLogDao(),
            database.personalCallLogDao(),
            RetrofitInstance.api
        )
    }

    override fun onCreate() {
        super.onCreate()
        RetrofitInstance.init(this)
        CallManager.initialize(repository, this)
    }
}