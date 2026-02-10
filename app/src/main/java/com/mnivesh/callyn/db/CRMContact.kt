package com.mnivesh.callyn.db

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "crm_contacts")
data class CrmContact(
    @PrimaryKey(autoGenerate = true)
    val localId: Int = 0,
    val recordId: String,          // The CRM ID (e.g., TicketID, Lead_UCC)
    val name: String,              // ClientName
    val number: String,            // ClientMobileNumber
    val ownerName: String,         // OwnerName
    val module: String,            // "Tickets", "Investment_leads", etc.
    val product: String?,          // Product or Subject
)