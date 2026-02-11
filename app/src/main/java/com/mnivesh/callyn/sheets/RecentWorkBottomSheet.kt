package com.mnivesh.callyn.screens.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mnivesh.callyn.components.getColorForName
import com.mnivesh.callyn.components.getInitials
import com.mnivesh.callyn.db.AppContact
import com.mnivesh.callyn.managers.SimManager
import com.mnivesh.callyn.screens.RecentCallUiItem
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentWorkBottomSheet(
    contact: AppContact,
    history: List<RecentCallUiItem>,
    isLoading: Boolean,
    sheetState: SheetState,
    isDualSim: Boolean,
    onDismiss: () -> Unit,
    onCall: (Int?) -> Unit
) {

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White
    ) {
        // Changed Column to LazyColumn to enable full-screen scrolling
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(start = 24.sdp(), end = 24.sdp(), top = 16.sdp(), bottom = 24.sdp())
        ) {
            // 1. Static Header Content (Avatar, Info, Buttons)
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.sdp())
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        getColorForName(contact.name),
                                        getColorForName(contact.name).copy(alpha = 0.7f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            getInitials(contact.name),
                            color = Color.White,
                            fontSize = 26.ssp(),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(18.sdp()))
                    Text(
                        contact.name,
                        fontSize = 21.ssp(),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.sdp())
                    ) {
                        Icon(
                            Icons.Default.BusinessCenter,
                            null,
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(13.sdp())
                        )
                        Spacer(modifier = Modifier.width(6.sdp()))
                        Text(
                            "Work Contact",
                            fontSize = 13.ssp(),
                            color = Color(0xFF60A5FA),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.sdp()))
                    ContactDetailRow(
                        icon = Icons.Default.CreditCard,
                        label = "PAN",
                        value = contact.pan,
                        labelColor = Color(0xFFFFB74D)
                    )
                    Spacer(modifier = Modifier.height(8.sdp()))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.sdp())
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            ContactDetailRow(
                                icon = Icons.Default.CurrencyRupee,
                                label = "AUM",
                                value = contact.aum ?: "0",
                                labelColor = Color(0xFFFFB74D)
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            ContactDetailRow(
                                icon = Icons.Default.Money,
                                label = "Family AUM",
                                value = contact.familyAum ?: "0",
                                labelColor = Color(0xFF60A5FA)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.sdp()))
                    ContactDetailRow(
                        Icons.Default.FamilyRestroom,
                        "Family Head",
                        contact.familyHead,
                        Color(0xFF81C784)
                    )
                    Spacer(modifier = Modifier.height(8.sdp()))
                    ContactDetailRow(
                        Icons.Default.AccountBox,
                        "Relationship Manager",
                        contact.rshipManager ?: "N/A",
                        Color(0xFFC084FC)
                    )

                    Spacer(modifier = Modifier.height(24.sdp()))

                    // Dual SIM Logic
                    val showDualButtons = isDualSim && SimManager.workSimSlot == null
                    if (showDualButtons) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.sdp())
                        ) {
                            // SIM 1 Button
                            Button(
                                onClick = { onCall(0) }, // Slot 0
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.sdp())
                                    .shadow(
                                        8.sdp(),
                                        RoundedCornerShape(20.sdp()),
                                        ambientColor = Color(0xFF3B82F6),
                                        spotColor = Color(0xFF3B82F6)
                                    ),
                                shape = RoundedCornerShape(20.sdp()),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                elevation = ButtonDefaults.buttonElevation(
                                    defaultElevation = 0.dp,
                                    pressedElevation = 4.dp
                                )
                            ) {
                                Row(horizontalArrangement = Arrangement.Center) {
                                    Icon(Icons.Default.Phone, contentDescription = null)
                                    Text("  SIM 1", fontSize = 16.ssp(), fontWeight = FontWeight.Bold)
                                }
                            }

                            // SIM 2 Button
                            Button(
                                onClick = { onCall(1) }, // Slot 1
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.sdp())
                                    .shadow(
                                        8.sdp(),
                                        RoundedCornerShape(20.sdp()),
                                        ambientColor = Color(0xFF10B981),
                                        spotColor = Color(0xFF10B981)
                                    ),
                                shape = RoundedCornerShape(20.sdp()),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                elevation = ButtonDefaults.buttonElevation(
                                    defaultElevation = 0.dp,
                                    pressedElevation = 4.dp
                                )
                            ) {
                                Row(horizontalArrangement = Arrangement.Center) {
                                    Icon(Icons.Default.Phone, contentDescription = null)
                                    Text("  SIM 2", fontSize = 16.ssp(), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        // Original Single Button
                        Button(
                            onClick = { onCall(null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.sdp()),
                            shape = RoundedCornerShape(16.sdp()),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Icon(Icons.Default.Call, null, modifier = Modifier.size(24.sdp()))
                            Spacer(modifier = Modifier.width(12.sdp()))
                            Text(if (SimManager.workSimSlot != null) "Call (Work SIM)" else "Call Now")
                        }
                    }

                    Spacer(modifier = Modifier.height(24.sdp()))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(16.sdp()))

                    // Header text aligned to start
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Previous Calls",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.ssp(),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.CenterStart)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.sdp()))
                }
            }

            // 2. Dynamic History List (Flattened onto the main scroll)
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.sdp()),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White.copy(alpha = 0.5f))
                    }
                }
            } else if (history.isNotEmpty()) {
                // This replaces the nested LazyColumn
                items(history) { log ->
                    CallHistoryRow(log)
                }
            } else {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No recent history",
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 12.ssp(),
                            modifier = Modifier.padding(20.sdp())
                        )
                    }
                }
            }
        }
    }
}