package com.example.callyn

import android.app.Application
import com.example.callyn.data.ContactRepository
import com.example.callyn.db.ContactDatabase

class CallynApplication : Application() {

    // Easiest Approach: Hardcoded secure key
    private val passphrase = "8KqF*Z9!b@E#H&MbQeThWmadag4eadc!zC&F)J@NcRfUjXn2r5u8x/A?D*G-KaPdSs".toByteArray()

    private val database by lazy {
        // Load native libraries required by SQLCipher
        System.loadLibrary("sqlcipher")
        ContactDatabase.getDatabase(this, passphrase)
    }

    val repository by lazy {
        ContactRepository(
            database.contactDao(),
            database.workCallLogDao(),
            RetrofitInstance.api
        )
    }

    override fun onCreate() {
        super.onCreate()
        CallManager.initialize(repository, this)
    }
}