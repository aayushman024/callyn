package com.mnivesh.callyn.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mnivesh.callyn.components.*
import com.mnivesh.callyn.db.AppContact
import com.mnivesh.callyn.ui.ContactsUiState
import com.mnivesh.callyn.ui.theme.AppTheme
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp

@Composable
fun WorkTabContent(
    uiState: ContactsUiState,
    workContacts: List<AppContact>,
    filteredWorkContacts: List<AppContact>,
    favoriteContacts: List<AppContact> = emptyList(),
    searchQuery: String,
    listState: LazyListState,
    shimmerOffset: Float,
    onContactSelected: (AppContact) -> Unit
) {
    val colors = AppTheme.colors

    if (uiState.isLoading && workContacts.isEmpty()) {
        LoadingCard(shimmerOffset)
    } else if (uiState.errorMessage != null) {
        ErrorCard(uiState.errorMessage!!)
    } else if (filteredWorkContacts.isEmpty() && favoriteContacts.isEmpty()) {
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
            contentPadding = PaddingValues(top = 8.sdp(), bottom = 100.sdp()),
            verticalArrangement = Arrangement.spacedBy(12.sdp())
        ) {
            if (favoriteContacts.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.sdp())
                    ) {
                        Text(
                            text = "Favourites",
                            color = Color(0xFFF59E0B),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.ssp(),
                            modifier = Modifier.padding(bottom = 12.sdp(), start = 4.sdp())
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.sdp()),
                            contentPadding = PaddingValues(horizontal = 4.sdp())
                        ) {
                            items(favoriteContacts, key = { it.id }) { contact ->
                                FavoriteContactItem(
                                    contact = contact,
                                    onClick = { onContactSelected(contact) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.sdp()))
                        HorizontalDivider(color = colors.border)
                    }
                }
            }

            if (favoriteContacts.isNotEmpty() && searchQuery.isBlank()) {
                item {
                    Text(
                        text = "All Contacts",
                        color = colors.textSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.ssp(),
                        modifier = Modifier.padding(bottom = 4.sdp(), start = 4.sdp())
                    )
                }
            }

            items(filteredWorkContacts, key = { it.id }) { contact ->
                ModernWorkContactCard(contact, onClick = { onContactSelected(contact) })
            }
        }
    }
}