package com.mnivesh.callyn.workers

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mnivesh.callyn.managers.AuthManager
import com.mnivesh.callyn.api.RetrofitInstance
import com.mnivesh.callyn.api.UserDetailsRequest
import com.mnivesh.callyn.api.version
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncUserDetailsWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val authManager = AuthManager(applicationContext)

        // 1. Get required auth data
        val token = authManager.getToken() ?: return Result.failure()
        val username = authManager.getUserName() ?: return Result.failure()
        val department = authManager.getDepartment() ?: "N/A"
        val email = authManager.getUserEmail() ?: "N/A"

        // 2. Collect device metrics off the main thread
        val metrics = withContext(Dispatchers.IO) {
            collectDeviceMetrics(applicationContext)
        }

        // 3. Prepare Request
        val request = UserDetailsRequest(
            username = username,
            email = email,
            phoneModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            osLevel = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            appVersion = version,
            department = department,
            lastSeen = System.currentTimeMillis(),

            // Device metrics
            ramAvailableMb = metrics.ramAvailableMb,
            storageAvailableGb = metrics.storageAvailableGb,
            appCacheMb = metrics.appCacheMb,
            batteryPercent = metrics.batteryPercent,
            thermalStatus = metrics.thermalStatus,
            networkStatus = metrics.networkStatus
        )

        // 4. Send to API
        return try {
            val response = RetrofitInstance.api.syncUserDetails("Bearer $token", request)
            if (response.isSuccessful) {
                Log.d("SyncWorker", "User details synced successfully.")
                Result.success()
            } else {
                Log.e("SyncWorker", "Sync failed: ${response.code()}")
                if (response.code() in 500..599) Result.retry() else Result.failure()
            }
        } catch (e: Exception) {
            Log.e("SyncWorker", "Network error, retrying...", e)
            Result.retry()
        }
    }

    // ─────────────────────────────────────────────
    // Metrics collector — runs on Dispatchers.IO
    // All reads are lightweight (no polling, no loops)
    // ─────────────────────────────────────────────
    private fun collectDeviceMetrics(context: Context): DeviceMetrics {

        // --- RAM ---
        // Single ActivityManager call covers both available and used
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val ramAvailableMb = memInfo.availMem / 1_048_576L

        // --- Storage ---
        // StatFs on data partition — single stat() syscall, near zero cost
        val stat = StatFs(Environment.getDataDirectory().path)
        val storageAvailableGb = "%.2f GB".format(
            stat.availableBlocksLong * stat.blockSizeLong / 1_000_000_000.0
        )

        // --- App Cache / Heap ---
        // Runtime.getRuntime() is a simple field read, no syscall
        val runtime = Runtime.getRuntime()
        val appCacheMb = (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576L

        // --- Battery ---
        // registerReceiver with null receiver is a sticky broadcast read — no listener registered,
        // no ongoing overhead. Unregistered immediately after read.
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val batteryPercent = batteryIntent
            ?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            ?.takeIf { it >= 0 }
            ?: -1

        // --- CPU Thermal Status (API 29+) ---
        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            when (pm.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE      -> "normal"
                PowerManager.THERMAL_STATUS_LIGHT     -> "light"
                PowerManager.THERMAL_STATUS_MODERATE  -> "moderate"
                PowerManager.THERMAL_STATUS_SEVERE    -> "severe"
                PowerManager.THERMAL_STATUS_CRITICAL  -> "critical"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
                PowerManager.THERMAL_STATUS_SHUTDOWN  -> "shutdown"
                else                                  -> "unknown"
            }
        } else {
            "unavailable"  // Below API 29
        }

        // --- Network ---
        // getNetworkCapabilities() is a single binder call, non-blocking
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val networkStatus = when {
            caps == null                                                    -> "offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)          -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)      -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)      -> "ethernet"
            else                                                            -> "unknown"
        }

        return DeviceMetrics(
            ramAvailableMb = ramAvailableMb,
            storageAvailableGb = storageAvailableGb,
            appCacheMb = appCacheMb,
            batteryPercent = batteryPercent,
            thermalStatus = thermalStatus,
            networkStatus = networkStatus
        )
    }

    // Clean internal data holder — never exposed to the API layer directly
    private data class DeviceMetrics(
        val ramAvailableMb: Long,
        val storageAvailableGb: String,
        val appCacheMb: Long,
        val batteryPercent: Int,
        val thermalStatus: String,
        val networkStatus: String
    )
}