package com.mnivesh.callyn.tabs

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnivesh.callyn.components.*

@Composable
fun PersonalTabContent(
    hasContactsPermission: Boolean,
    filteredDeviceContacts: List<DeviceContact>,
    favoriteContacts: List<DeviceContact>,
    searchQuery: String,
    listState: LazyListState,
    onGrantPermission: () -> Unit,
    onContactSelected: (DeviceContact) -> Unit
) {
    if (!hasContactsPermission) {
        PermissionRequiredCard(onGrantPermission = onGrantPermission)
    } else if (filteredDeviceContacts.isEmpty() && favoriteContacts.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            EmptyStateCard(
                "No personal contacts found",
                Icons.Default.PersonOff
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
                            items(favoriteContacts) { contact ->
                                FavoriteContactItem(
                                    contact = contact,
                                    onClick = { onContactSelected(contact) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.sdp()))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    }
                }
            }

            if (favoriteContacts.isNotEmpty() && searchQuery.isBlank()) {
                item {
                    Text(
                        text = "All Contacts",
                        color = Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.ssp(),
                        modifier = Modifier.padding(top = 8.sdp(), start = 4.sdp())
                    )
                }
            }

            items(filteredDeviceContacts, key = { it.id }) { contact ->
                ModernDeviceContactCard(contact, onClick = { onContactSelected(contact) })
            }
        }
    }
}