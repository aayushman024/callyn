package com.mnivesh.callyn

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.mnivesh.callyn.api.RetrofitInstance
import com.mnivesh.callyn.data.ContactRepository
import com.mnivesh.callyn.db.ContactDatabase
import com.mnivesh.callyn.managers.AuthManager
import com.mnivesh.callyn.managers.CallManager
import com.mnivesh.callyn.managers.RemoteConfigManager
import com.mnivesh.callyn.managers.TokenRefreshManager
import com.mnivesh.callyn.workers.TokenRefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        RemoteConfigManager.initialize()

        // Enqueue periodic WorkManager background refresh task
        TokenRefreshWorker.enqueuePeriodicWork(this)

        // Register App-wide Process Lifecycle Observer for Foreground Refresh
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                super.onStart(owner)
                val authManager = AuthManager(this@CallynApplication)
                if (authManager.shouldRefreshToken()) {
                    Log.d("CallynApplication", "App entered foreground. Proactively refreshing auth token...")
                    CoroutineScope(Dispatchers.IO).launch {
                        TokenRefreshManager.performRefresh(this@CallynApplication)
                    }
                }
            }
        })
    }
}