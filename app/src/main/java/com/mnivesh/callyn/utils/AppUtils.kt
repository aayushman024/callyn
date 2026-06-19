package com.mnivesh.callyn.utils

import android.os.Build

/**
 * Utility functions for general app operations.
 */
object AppUtils {

    /**
     * Checks if the current device is a Xiaomi, Redmi, or POCO device.
     */
    fun isXiaomiDevice(): Boolean {
        return Build.MANUFACTURER.equals("xiaomi", ignoreCase = true) ||
                Build.MANUFACTURER.equals("redmi", ignoreCase = true) ||
                Build.BRAND.equals("poco", ignoreCase = true)
    }
}
