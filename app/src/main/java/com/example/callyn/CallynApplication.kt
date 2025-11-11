package com.example.callyn

import android.app.Application
import com.example.callyn.data.ContactRepository
import com.example.callyn.db.ContactDatabase

/**
 * Custom Application class to initialize singletons (Database and Repository).
 */
class CallynApplication : Application() {

    // Lazy-initialize the database
    private val database by lazy { ContactDatabase.getDatabase(this) }

    // Lazy-initialize the repository, passing in the DAO and ApiService
    val repository by lazy {
        ContactRepository(database.contactDao(), RetrofitInstance.api)
    }
}