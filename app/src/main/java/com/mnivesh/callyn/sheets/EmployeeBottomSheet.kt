package com.mnivesh.callyn.sheets

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.sp
import com.mnivesh.callyn.components.getColorForName
import com.mnivesh.callyn.components.getInitials
import com.mnivesh.callyn.db.AppContact
import com.mnivesh.callyn.managers.SimManager
import com.mnivesh.callyn.screens.RecentCallUiItem
import com.mnivesh.callyn.screens.sheets.CallHistoryRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeBottomSheet(
    contact: AppContact,
    history: List<RecentCallUiItem>,
    isLoading: Boolean,
    sheetState: SheetState,
    isDualSim: Boolean,
    initialHistoryExpanded: Boolean = false,
    onDismiss: () -> Unit,
    onShowHistory: () -> Unit,
    onCall: (Int?) -> Unit
) {
    val context = LocalContext.current
    var isHistoryExpanded by remember { mutableStateOf(initialHistoryExpanded) }

    // Theme Colors
    val backgroundColor = Color(0xFF0F172A)
    val surfaceColor = Color(0xFF1E293B)
    val primaryColor = Color(0xFF10B981)
    val secondaryColor = Color(0xFF60A5FA)
    val textPrimary = Color.White
    val textSecondary = Color.White.copy(alpha = 0.6f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = backgroundColor,
        contentColor = textPrimary,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 16.sdp())
                    .width(48.sdp())
                    .height(6.sdp())
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 24.sdp(), end = 24.sdp(), bottom = 24.sdp()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                // Header: Avatar
                Box(
                    modifier = Modifier
                        .size(110.sdp())
                        .border(4.sdp(), backgroundColor, CircleShape)
                        .padding(4.sdp())
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

                // Name
                Text(
                    text = contact.name,
                    fontSize = 26.ssp(),
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.sdp()))

                // Pill
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

                // Details Card
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "CONTACT DETAILS",
                        color = textSecondary,
                        fontSize = 12.ssp(),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.ssp(),
                        modifier = Modifier.padding(start = 8.sdp(), bottom = 8.sdp())
                    )

                    Surface(
                        color = surfaceColor,
                        shape = RoundedCornerShape(20.sdp()),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.sdp())) {
                            // Phone Row
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.sdp())
                                        .clip(RoundedCornerShape(10.sdp()))
                                        .background(primaryColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Phone,
                                        null,
                                        tint = primaryColor,
                                        modifier = Modifier.size(18.sdp())
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.sdp()))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Mobile",
                                        fontSize = 11.ssp(),
                                        color = textSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        contact.number,
                                        fontSize = 16.ssp(),
                                        color = textPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                IconButton(onClick = {
                                    val clipboard =
                                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Phone", contact.number)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        null,
                                        tint = textSecondary,
                                        modifier = Modifier.size(18.sdp())
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.sdp()),
                                color = textSecondary.copy(alpha = 0.1f)
                            )

                            // Department Row
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.sdp())
                                        .clip(RoundedCornerShape(10.sdp()))
                                        .background(Color(0xFF8B5CF6).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Apartment,
                                        null,
                                        tint = Color(0xFF8B5CF6),
                                        modifier = Modifier.size(18.sdp())
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.sdp()))
                                Column {
                                    Text(
                                        "Department",
                                        fontSize = 11.ssp(),
                                        color = textSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        if (contact.familyHead.isNotBlank()) contact.familyHead else "N/A",
                                        fontSize = 16.ssp(),
                                        color = textPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.sdp()))

                // Call Buttons
                val showDualButtons = isDualSim && SimManager.workSimSlot == null

                if (showDualButtons) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.sdp())
                    ) {
                        Button(
                            onClick = { onCall(0) },
                            modifier = Modifier
                                .weight(1f)
                                .height(64.sdp())
                                .shadow(8.sdp(), RoundedCornerShape(20.sdp()), ambientColor = Color(0xFF3B82F6), spotColor = Color(0xFF3B82F6)),
                            shape = RoundedCornerShape(20.sdp()),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                        ) {
                            Row(horizontalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.Phone, null)
                                Text("  SIM 1", fontSize = 16.ssp(), fontWeight = FontWeight.Bold)
                            }
                        }
                        Button(
                            onClick = { onCall(1) },
                            modifier = Modifier
                                .weight(1f)
                                .height(64.sdp())
                                .shadow(8.sdp(), RoundedCornerShape(20.sdp()), ambientColor = Color(0xFF10B981), spotColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(20.sdp()),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
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
                            .height(64.sdp())
                            .shadow(12.sdp(), RoundedCornerShape(20.sdp()), ambientColor = primaryColor, spotColor = primaryColor),
                        shape = RoundedCornerShape(20.sdp()),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                    ) {
                        Icon(Icons.Default.Call, null, modifier = Modifier.size(24.sdp()))
                        Spacer(modifier = Modifier.width(12.sdp()))
                        val buttonText = if (SimManager.workSimSlot != null) "Call (Work SIM)" else "Call"
                        Text(text = buttonText, fontSize = 18.ssp(), fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(24.sdp()))
            }

            // --- History Toggle Section ---
            item {
                HorizontalDivider(color = textSecondary.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(16.sdp()))

                OutlinedButton(
                    onClick = {
                        isHistoryExpanded = !isHistoryExpanded
                        if (isHistoryExpanded) {
                            onShowHistory()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(40.sdp()),
                    border = BorderStroke(1.sdp(), textSecondary.copy(alpha = 0.3f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = textSecondary)
                ) {
                    Text(
                        if (isHistoryExpanded) "Hide Call History" else "Show Call History",
                        fontSize = 14.ssp()
                    )
                }
                Spacer(modifier = Modifier.height(12.sdp()))
            }

            // --- History Content ---
            if (isHistoryExpanded) {
                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(80.sdp()),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = textSecondary)
                        }
                    }
                } else {
                    if (history.isNotEmpty()) {
                        items(history) { log ->
                            CallHistoryRow(log)
                        }
                    } else {
                        item {
                            Text(
                                "No recent history",
                                color = textSecondary.copy(alpha = 0.5f),
                                fontSize = 13.ssp(),
                                modifier = Modifier.padding(top = 16.sdp(), bottom = 32.sdp())
                            )
                        }
                    }
                }
            }
        }
    }
}