package com.mnivesh.callyn.managers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

/**
 * Manages permission checking and requesting logic.
 */
class PermissionManager(private val context: Context) {

    private val permissionsToRequest = mutableListOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.WRITE_CALL_LOG,
    ).apply {
        add(Manifest.permission.READ_PHONE_NUMBERS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private var multiplePermissionLauncher: ActivityResultLauncher<Array<String>>? = null

    /**
     * Initializes the permission launcher (should be called from Activity).
     */
    fun setLauncher(launcher: ActivityResultLauncher<Array<String>>) {
        this.multiplePermissionLauncher = launcher
    }

    /**
     * Checks if all required permissions are granted.
     */
    fun checkAllPermissions(): Boolean {
        return permissionsToRequest.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Returns a list of missing permission names.
     */
    fun getMissingPermissions(): List<String> {
        return permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.map { it.substringAfterLast(".") }
    }

    /**
     * Requests missing permissions using the registered launcher.
     */
    fun requestPermissions() {
        val permissionsToAsk = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissionsToAsk.isNotEmpty()) {
            multiplePermissionLauncher?.launch(permissionsToAsk.toTypedArray())
        }
    }
}
