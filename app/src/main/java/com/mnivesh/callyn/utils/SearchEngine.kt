package com.mnivesh.callyn.utils

import com.mnivesh.callyn.components.DeviceContact
import com.mnivesh.callyn.db.AppContact
import com.mnivesh.callyn.db.CrmContact
import com.mnivesh.callyn.viewmodels.CrmUiState
import com.mnivesh.callyn.viewmodels.RecentCallUiItem

/**
 * Pre-parsed representation of a user search query to avoid repeated string allocations,
 * regex parsing, and case conversions on every contact/field.
 */
data class ParsedSearchQuery(
    val rawQuery: String,
    val normalizedQuery: String,
    val tokens: List<String>,
    val isNumeric: Boolean,
    val isSixCharCode: Boolean
) {
    val isBlank: Boolean = tokens.isEmpty()

    companion object {
        val EMPTY = ParsedSearchQuery(
            rawQuery = "",
            normalizedQuery = "",
            tokens = emptyList(),
            isNumeric = false,
            isSixCharCode = false
        )

        fun parse(query: String): ParsedSearchQuery {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return EMPTY

            val normalized = trimmed.lowercase()
            val tokens = normalized.split(" ").filter { it.isNotEmpty() }
            val isNumeric = trimmed.isNotEmpty() && trimmed.all { it.isDigit() }
            val isSixCharCode = trimmed.length == 6

            return ParsedSearchQuery(
                rawQuery = trimmed,
                normalizedQuery = normalized,
                tokens = tokens,
                isNumeric = isNumeric,
                isSixCharCode = isSixCharCode
            )
        }
    }

    /**
     * Checks if all search tokens are contained within the given target text.
     */
    fun matchesText(text: String?): Boolean {
        if (text.isNullOrEmpty() || tokens.isEmpty()) return false
        val target = text.lowercase()
        for (i in tokens.indices) {
            if (!target.contains(tokens[i])) return false
        }
        return true
    }

    /**
     * Checks if the query matches the target string (either full token match or raw substring).
     */
    fun matchesSubstring(text: String?): Boolean {
        if (text.isNullOrEmpty() || normalizedQuery.isEmpty()) return false
        return text.lowercase().contains(normalizedQuery)
    }
}

/**
 * Scored search result item allowing single-pass sorting without repeated string/comparator allocations.
 */
private data class ScoredContact(
    val item: Any,
    val primaryRank: Int,       // 0 for device if numeric query, 0 for RM match, etc.
    val codeRank: Int,          // 0 if exact 6-char code match
    val matchFieldRank: Int,    // 0 = Name match, 1 = Family head, 2 = PAN, 3 = other
    val displayName: String     // Lowercased name for alphabetical tie-break
)

/**
 * High-performance search matcher and ranker optimized for low-end devices.
 */
object SearchEngine {

    /**
     * Filters and deduplicates call logs by query, filter category, and call type.
     */
    fun filterCallLogs(
        callLogs: List<RecentCallUiItem>,
        query: ParsedSearchQuery,
        selectedFilter: String,
        callTypeFilter: String,
        maxResults: Int = 50
    ): List<RecentCallUiItem> {
        if (query.isBlank || callLogs.isEmpty()) return emptyList()

        val results = ArrayList<RecentCallUiItem>(minOf(callLogs.size, maxResults))
        val seenNumbers = HashSet<String>()

        // Take up to 500 initial logs to avoid excessive scanning
        val scanCount = minOf(callLogs.size, 500)
        for (i in 0 until scanCount) {
            val log = callLogs[i]

            // 1. Universal Filter (Personal/Work)
            val matchesUniversal = when (selectedFilter) {
                "Personal" -> log.type == "Personal"
                "Work", "Employee" -> log.type == "Work"
                else -> true
            }
            if (!matchesUniversal) continue

            // 2. Call Type Filter (Incoming/Outgoing/Missed)
            val matchesType = when (callTypeFilter) {
                "Incoming" -> log.isIncoming
                "Outgoing" -> !log.isIncoming
                "Missed" -> log.isMissed
                else -> true
            }
            if (!matchesType) continue

            // 3. Query match (name or number)
            val matchesQuery = query.matchesText(log.name) || log.number.contains(query.rawQuery)
            if (!matchesQuery) continue

            // 4. Distinct by number (keep latest)
            if (seenNumbers.add(log.number)) {
                results.add(log)
                if (results.size >= maxResults) break
            }
        }

        return results
    }

    /**
     * Filters and ranks contacts across device, work, and CRM sources.
     */
    fun filterContacts(
        query: ParsedSearchQuery,
        selectedFilter: String,
        myContacts: List<AppContact>,
        deviceContacts: List<DeviceContact>,
        workContacts: List<AppContact>,
        crmUiState: CrmUiState,
        searchCrmData: Boolean,
        department: String?,
        userName: String,
        maxResults: Int = 100
    ): List<Any> {
        if (query.isBlank) return emptyList()

        val scoredResults = ArrayList<ScoredContact>()

        // Helper for AppContact matching
        fun checkAppContact(contact: AppContact): ScoredContact? {
            val nameMatch = query.matchesText(contact.name)
            val familyHeadMatch = if (!nameMatch) query.matchesText(contact.familyHead) else false
            val panMatch = if (!nameMatch && !familyHeadMatch) contact.pan.contains(query.rawQuery, ignoreCase = true) else false
            val numberMatch = if (!nameMatch && !familyHeadMatch && !panMatch && department == "Management") {
                contact.number.contains(query.rawQuery)
            } else false
            val codeMatch = if (query.isSixCharCode) contact.uniqueCode.equals(query.rawQuery, ignoreCase = true) else false

            if (nameMatch || familyHeadMatch || panMatch || numberMatch || codeMatch) {
                val isRmUser = contact.rshipManager.equals(userName, ignoreCase = true)
                val primaryRank = if (query.isNumeric) 1 else (if (isRmUser) 0 else 1)
                val codeRank = if (codeMatch) 0 else 1
                val matchFieldRank = when {
                    nameMatch -> 0
                    familyHeadMatch -> 1
                    panMatch -> 2
                    else -> 3
                }
                return ScoredContact(
                    item = contact,
                    primaryRank = primaryRank,
                    codeRank = codeRank,
                    matchFieldRank = matchFieldRank,
                    displayName = contact.name.lowercase()
                )
            }
            return null
        }

        // 1. Device Contacts
        if (selectedFilter == "All" || selectedFilter == "Personal") {
            for (i in deviceContacts.indices) {
                val contact = deviceContacts[i]
                val nameMatch = query.matchesText(contact.name)
                val numberMatch = if (!nameMatch) {
                    var found = false
                    for (n in contact.numbers.indices) {
                        if (contact.numbers[n].number.contains(query.rawQuery)) {
                            found = true
                            break
                        }
                    }
                    found
                } else false

                if (nameMatch || numberMatch) {
                    val primaryRank = if (query.isNumeric) 0 else 1
                    scoredResults.add(
                        ScoredContact(
                            item = contact,
                            primaryRank = primaryRank,
                            codeRank = 1,
                            matchFieldRank = if (nameMatch) 0 else 3,
                            displayName = contact.name.lowercase()
                        )
                    )
                }
            }
        }

        // 2. Work Contacts (My Contacts)
        if (selectedFilter == "All" || selectedFilter == "Work") {
            for (i in myContacts.indices) {
                val scored = checkAppContact(myContacts[i])
                if (scored != null) {
                    scoredResults.add(scored)
                }
            }
        }

        // 3. Employee Contacts
        if (selectedFilter == "All" || selectedFilter == "Employee") {
            for (i in workContacts.indices) {
                val contact = workContacts[i]
                if (contact.rshipManager.equals("Employee", ignoreCase = true)) {
                    val scored = checkAppContact(contact)
                    if (scored != null) {
                        scoredResults.add(scored)
                    }
                }
            }
        }

        // 4. CRM Contacts
        if (searchCrmData) {
            val crmLists = when (selectedFilter) {
                "All" -> listOf(crmUiState.tickets, crmUiState.investmentLeads, crmUiState.insuranceLeads)
                "Tickets" -> listOf(crmUiState.tickets)
                "Investment Leads" -> listOf(crmUiState.investmentLeads)
                "Insurance Leads" -> listOf(crmUiState.insuranceLeads)
                else -> emptyList()
            }

            for (list in crmLists) {
                for (i in list.indices) {
                    val contact = list[i]
                    val matches = query.matchesText(contact.name) ||
                            contact.number.contains(query.rawQuery) ||
                            contact.recordId.contains(query.rawQuery, ignoreCase = true) ||
                            (contact.product?.contains(query.rawQuery, ignoreCase = true) == true) ||
                            query.matchesText(contact.ownerName)

                    if (matches) {
                        scoredResults.add(
                            ScoredContact(
                                item = contact,
                                primaryRank = if (query.isNumeric) 1 else 1,
                                codeRank = 1,
                                matchFieldRank = 0,
                                displayName = contact.name.lowercase()
                            )
                        )
                    }
                }
            }
        }

        // Sort using precomputed ranks (avoids multiple string lowercasing & dynamic comparator evaluations)
        scoredResults.sortWith(
            compareBy<ScoredContact> { it.primaryRank }
                .thenBy { it.codeRank }
                .thenBy { it.matchFieldRank }
                .thenBy { it.displayName }
        )

        return if (scoredResults.size > maxResults) {
            scoredResults.subList(0, maxResults).map { it.item }
        } else {
            scoredResults.map { it.item }
        }
    }
}
