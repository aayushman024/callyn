package com.mnivesh.callyn.utils

import android.util.LruCache
import com.mnivesh.callyn.db.AppContact
import com.mnivesh.callyn.db.CrmContact

data class ResolvedContactInfo(
    val name: String,
    val personalName: String? = null,
    val type: String = "unknown",
    val pan: String = "",
    val familyHead: String? = null,
    val rshipManager: String? = null,
    val aum: String? = null,
    val familyAum: String? = null,
    val dob: String? = null
)

object ContactCache {
    private const val CACHE_SIZE = 500
    private val cache = LruCache<String, ResolvedContactInfo>(CACHE_SIZE)

    private fun normalizeKey(number: String): String {
        return number.filter { it.isDigit() }
    }

    fun shouldCache(rshipManager: String?, userName: String?): Boolean {
        if (rshipManager.isNullOrBlank()) return false
        if (rshipManager.equals("Employee", ignoreCase = true)) return true
        return !userName.isNullOrEmpty() && rshipManager.equals(userName, ignoreCase = true)
    }

    fun get(number: String): ResolvedContactInfo? {
        val key = normalizeKey(number)
        if (key.length < 7) return null
        return synchronized(cache) { cache.get(key) }
    }

    fun put(number: String, info: ResolvedContactInfo, userName: String?) {
        val key = normalizeKey(number)
        if (key.length < 7) return
        if (shouldCache(info.rshipManager, userName)) {
            synchronized(cache) { cache.put(key, info) }
        }
    }

    fun preWarmAppContacts(contacts: List<AppContact>, userName: String?) {
        synchronized(cache) {
            contacts.forEach { contact ->
                if (shouldCache(contact.rshipManager, userName)) {
                    val key = normalizeKey(contact.number)
                    if (key.length >= 7) {
                        cache.put(
                            key,
                            ResolvedContactInfo(
                                name = contact.name,
                                type = "work",
                                pan = contact.pan,
                                dob = contact.dob,
                                familyHead = contact.familyHead,
                                rshipManager = contact.rshipManager,
                                aum = contact.aum,
                                familyAum = contact.familyAum
                            )
                        )
                    }
                }
            }
        }
    }

    fun preWarmCrmContacts(crmContacts: List<CrmContact>, userName: String?) {
        synchronized(cache) {
            crmContacts.forEach { crm ->
                if (shouldCache(crm.ownerName, userName)) {
                    val key = normalizeKey(crm.number)
                    if (key.length >= 7) {
                        cache.put(
                            key,
                            ResolvedContactInfo(
                                name = crm.name,
                                type = "work",
                                familyHead = crm.module,
                                rshipManager = crm.ownerName,
                                aum = crm.product
                            )
                        )
                    }
                }
            }
        }
    }

    fun clear() {
        synchronized(cache) { cache.evictAll() }
    }
}
