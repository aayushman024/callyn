package com.mnivesh.callyn.utils

import android.content.Context
import android.provider.ContactsContract
import com.mnivesh.callyn.components.DeviceContact
import com.mnivesh.callyn.components.DeviceNumber
import com.mnivesh.callyn.db.AppContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility functions for contact operations.
 */
object ContactUtils {

    /**
     * Loads device contacts and maps them to a DeviceContact model.
     */
    suspend fun loadDeviceContacts(context: Context): List<DeviceContact> =
        withContext(Dispatchers.IO) {
            val contactsMap = mutableMapOf<String, DeviceContact>()
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.STARRED,
                    ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY
                ),
                null, null, null
            )

            cursor?.use {
                val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val starredIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)
                val defaultIndex =
                    it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY)

                while (it.moveToNext()) {
                    val id = it.getString(idIndex)
                    val name = it.getString(nameIndex) ?: "Unknown"
                    val rawNumber = it.getString(numberIndex)?.replace("\\s".toRegex(), "") ?: ""
                    val isStarred = it.getInt(starredIndex) == 1
                    val isDefault = it.getInt(defaultIndex) > 0

                    if (rawNumber.isNotEmpty()) {
                        val numberObj = DeviceNumber(rawNumber, isDefault)
                        if (contactsMap.containsKey(id)) {
                            val existing = contactsMap[id]!!
                            if (existing.numbers.none { n -> n.number == rawNumber }) {
                                contactsMap[id] = existing.copy(numbers = existing.numbers + numberObj)
                            }
                        } else {
                            contactsMap[id] = DeviceContact(id, name, listOf(numberObj), isStarred)
                        }
                    }
                }
            }
            contactsMap.values.toList()
        }

    /**
     * Finds conflicts between device contacts and work contacts.
     */
    fun findConflicts(
        deviceContacts: List<DeviceContact>,
        workContacts: List<AppContact>
    ): List<DeviceContact> {
        return deviceContacts.filter { device ->
            device.numbers.any { numObj ->
                val deviceNum = sanitizePhoneNumber(numObj.number)
                if (deviceNum.length < 5) false
                else workContacts.any { work ->
                    !work.rshipManager.equals("Employee", ignoreCase = true) &&
                            sanitizePhoneNumber(work.number) == deviceNum
                }
            }
        }
    }

    /**
     * Sanitizes a phone number to only contain digits, optionally keeping only the last 10 digits.
     */
    fun sanitizePhoneNumber(number: String): String {
        val digits = number.filter { it.isDigit() }
        return if (digits.length > 10) digits.takeLast(10) else digits
    }
}
