package com.mnivesh.callyn

import android.app.Application
import com.mnivesh.callyn.api.RetrofitInstance
import com.mnivesh.callyn.data.ContactRepository
import com.mnivesh.callyn.db.ContactDatabase

class CallynApplication : Application() {

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