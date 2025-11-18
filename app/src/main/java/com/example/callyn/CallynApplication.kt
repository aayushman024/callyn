package com.example.callyn

import android.app.Application
import com.example.callyn.data.ContactRepository
import com.example.callyn.db.ContactDatabase

class CallynApplication : Application() {

    private val database by lazy { ContactDatabase.getDatabase(this) }

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