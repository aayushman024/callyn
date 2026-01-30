package com.mnivesh.callyn.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mnivesh.callyn.components.*
import com.mnivesh.callyn.db.AppContact
import com.mnivesh.callyn.ui.ContactsUiState

@Composable
fun WorkTabContent(
    uiState: ContactsUiState,
    workContacts: List<AppContact>,
    filteredWorkContacts: List<AppContact>,
    searchQuery: String,
    listState: LazyListState,
    shimmerOffset: Float,
    onContactSelected: (AppContact) -> Unit
) {
    if (uiState.isLoading && workContacts.isEmpty()) {
        LoadingCard(shimmerOffset)
    } else if (uiState.errorMessage != null) {
        ErrorCard(uiState.errorMessage!!)
    } else if (filteredWorkContacts.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            EmptyStateCard(
                if (searchQuery.isNotEmpty()) "No matches found" else "No assigned contacts",
                Icons.Default.BusinessCenter
            )
        }
    } else {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredWorkContacts, key = { it.id }) { contact ->
                ModernWorkContactCard(contact, onClick = { onContactSelected(contact) })
            }
        }
    }
}