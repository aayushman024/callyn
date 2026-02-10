package com.mnivesh.callyn.screens.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mnivesh.callyn.components.getColorForName
import com.mnivesh.callyn.components.getInitials
import com.mnivesh.callyn.db.AppContact
import com.mnivesh.callyn.managers.SimManager
import com.mnivesh.callyn.screens.RecentCallUiItem
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentEmployeeBottomSheet(
    contact: AppContact,
    history: List<RecentCallUiItem>,
    isLoading: Boolean,
    sheetState: SheetState,
    isDualSim: Boolean,
    onDismiss: () -> Unit,
    onCall: (Int?) -> Unit
) {
    val context = LocalContext.current

    // UI Colors
    val backgroundColor = Color(0xFF0F172A)
    val textPrimary = Color.White
    val textSecondary = Color.White.copy(alpha = 0.6f)
    val secondaryColor = Color(0xFF60A5FA) // Blue for Employee

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = backgroundColor,
        contentColor = textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.sdp())
                .padding(bottom = 16.sdp()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- Avatar ---
            Box(
                modifier = Modifier
                    .size(110.sdp())
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                getColorForName(contact.name),
                                getColorForName(contact.name).copy(alpha = 0.6f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    getInitials(contact.name),
                    color = Color.White,
                    fontSize = 40.ssp(),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.sdp()))

            // --- Name ---
            Text(
                text = contact.name,
                fontSize = 26.ssp(),
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.sdp()))

            // --- Employee Pill ---
            Surface(
                color = secondaryColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(32.sdp())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.sdp())
                ) {
                    Icon(
                        Icons.Default.Badge,
                        null,
                        tint = secondaryColor,
                        modifier = Modifier.size(14.sdp())
                    )
                    Spacer(modifier = Modifier.width(6.sdp()))
                    Text(
                        text = "Employee",
                        fontSize = 13.ssp(),
                        color = secondaryColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.sdp()))

            // --- Contact Details (Mobile & Dept) ---
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "CONTACT DETAILS",
                    color = textSecondary,
                    fontSize = 12.ssp(),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.sdp(), bottom = 8.sdp())
                )

                // Mobile Row
                ContactDetailRow(
                    icon = Icons.Default.Phone,
                    label = "Mobile",
                    value = contact.number,
                    labelColor = Color(0xFF10B981)
                )

                Spacer(modifier = Modifier.height(8.sdp()))

                // Department Row (Using familyHead)
                ContactDetailRow(
                    icon = Icons.Default.Apartment,
                    label = "Department",
                    value = contact.familyHead.ifBlank { "N/A" },
                    labelColor = Color(0xFF8B5CF6)
                )
            }

            Spacer(modifier = Modifier.height(24.sdp()))

            // --- Call Buttons (Dual SIM Logic) ---
            val showDualButtons = isDualSim && SimManager.workSimSlot == null
            if (showDualButtons) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.sdp())
                ) {
                    // SIM 1
                    Button(
                        onClick = { onCall(0) },
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
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 4.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Phone, null)
                            Text("  SIM 1", fontSize = 16.ssp(), fontWeight = FontWeight.Bold)
                        }
                    }
                    // SIM 2
                    Button(
                        onClick = { onCall(1) },
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
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 4.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Phone, null)
                            Text("  SIM 2", fontSize = 16.ssp(), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Button(
                    onClick = { onCall(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.sdp()),
                    shape = RoundedCornerShape(16.sdp()),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Icon(Icons.Default.Phone, null, modifier = Modifier.size(24.sdp()))
                    Spacer(modifier = Modifier.width(12.sdp()))
                    Text(if(SimManager.workSimSlot != null) "Call (Work SIM)" else "Call Now")
                }
            }

            Spacer(modifier = Modifier.height(24.sdp()))

            // --- HISTORY SECTION ---
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.sdp()))

            Text(
                text = "Previous Calls",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.ssp(),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.sdp()))

            if (isLoading) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(100.sdp()), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White.copy(alpha = 0.5f))
                }
            } else if (history.isNotEmpty()) {
                LazyColumn(modifier = Modifier.heightIn(max = 250.sdp())) {
                    items(history) { log ->
                        CallHistoryRow(log)
                    }
                }
            } else {
                Text(
                    "No recent history",
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 12.ssp(),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(20.sdp())
                )
            }

            Spacer(modifier = Modifier.height(24.sdp()))
        }
    }
}