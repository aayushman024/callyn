package com.mnivesh.callyn.utils

object VersionManager {
    /**
     * Returns TRUE if remoteVersion > localVersion
     */
    fun isUpdateNeeded(localVersion: String, remoteVersion: String): Boolean {
        // Split "1.0.2" -> [1, 0, 2]
        val localParts = localVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val remoteParts = remoteVersion.split(".").map { it.toIntOrNull() ?: 0 }

        val length = maxOf(localParts.size, remoteParts.size)

        for (i in 0 until length) {
            val local = localParts.getOrElse(i) { 0 }
            val remote = remoteParts.getOrElse(i) { 0 }

            if (remote > local) return true
            if (remote < local) return false
        }
        return false
    }
}