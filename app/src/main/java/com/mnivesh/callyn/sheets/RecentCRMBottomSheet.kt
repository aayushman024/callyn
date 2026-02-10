package com.mnivesh.callyn.screens.sheets

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import com.mnivesh.callyn.components.ModernDetailRow
import com.mnivesh.callyn.components.getColorForName
import com.mnivesh.callyn.components.getInitials
import com.mnivesh.callyn.db.CrmContact
import com.mnivesh.callyn.managers.SimManager
import com.mnivesh.callyn.screens.RecentCallUiItem
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentCrmBottomSheet(
    contact: CrmContact,
    history: List<RecentCallUiItem>,
    isLoading: Boolean,
    sheetState: SheetState,
    isDualSim: Boolean,
    onDismiss: () -> Unit,
    onCall: (Int?) -> Unit
) {
    val context = LocalContext.current
    var showShareCodeDialog by remember { mutableStateOf(false) }

    // Theme Colors
    val backgroundColor = Color(0xFF0F172A)
    val surfaceColor = Color(0xFF1E293B)
    val crmColor = Color(0xFF2C7BE5) // Zoho Blue
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.sdp())
                .padding(bottom = 24.sdp()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- Header: Avatar + Share ---
            Box(modifier = Modifier.fillMaxWidth()) {
                // Share Button (Top Right)
                IconButton(
                    onClick = { showShareCodeDialog = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(36.sdp())
                        .clip(CircleShape)
                        .background(surfaceColor)
                ) {
                    Icon(
                        Icons.Default.QrCode,
                        "Share CRM ID",
                        tint = crmColor,
                        modifier = Modifier.size(18.sdp())
                    )
                }

                // Avatar (Center)
                Box(
                    modifier = Modifier
                        .size(90.sdp())
                        .align(Alignment.Center)
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
                        fontSize = 32.ssp(),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.sdp()))

            // --- Name ---
            Text(
                text = contact.name,
                fontSize = 24.ssp(),
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.sdp()))

            // --- Module Pill ---
            Surface(
                color = crmColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(32.sdp())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.sdp())
                ) {
                    Icon(
                        Icons.Default.Dns,
                        null,
                        tint = crmColor,
                        modifier = Modifier.size(14.sdp())
                    )
                    Spacer(modifier = Modifier.width(6.sdp()))
                    Text(
                        text = contact.module.replace("_", " "),
                        fontSize = 13.ssp(),
                        color = crmColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.sdp()))

            // --- Contact Details ---
            Surface(
                color = surfaceColor,
                shape = RoundedCornerShape(16.sdp()),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.sdp())) {
                    // Mobile
                    ModernDetailRow(
                        Icons.Default.Phone,
                        "Mobile Number",
                        contact.number,
                        Color(0xFF10B981)
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.sdp()),
                        color = textSecondary.copy(alpha = 0.1f)
                    )

                    // CRM ID
                    ModernDetailRow(
                        Icons.Default.ConfirmationNumber,
                        "CRM ID",
                        contact.recordId,
                        Color(0xFFFFB74D)
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.sdp()),
                        color = textSecondary.copy(alpha = 0.1f)
                    )

                    // Product / Subject
                    ModernDetailRow(
                        Icons.Default.Inventory,
                        "Product / Subject",
                        contact.product ?: "N/A",
                        Color(0xFF60A5FA)
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.sdp()),
                        color = textSecondary.copy(alpha = 0.1f)
                    )

                    // Owner
                    ModernDetailRow(
                        Icons.Default.PersonOutline,
                        "Owner",
                        contact.ownerName,
                        Color(0xFFF472B6)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.sdp()))

            // --- Call Buttons ---
            val showDualButtons = isDualSim && SimManager.workSimSlot == null

            if (showDualButtons) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.sdp())
                ) {
                    Button(
                        onClick = { onCall(0) },
                        modifier = Modifier.weight(1f).height(48.sdp()),
                        shape = RoundedCornerShape(20.sdp()),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, null, modifier = Modifier.size(16.sdp()))
                            Spacer(modifier = Modifier.width(4.sdp()))
                            Text("SIM 1", fontSize = 14.ssp(), fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = { onCall(1) },
                        modifier = Modifier.weight(1f).height(48.sdp()),
                        shape = RoundedCornerShape(20.sdp()),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, null, modifier = Modifier.size(16.sdp()))
                            Spacer(modifier = Modifier.width(4.sdp()))
                            Text("SIM 2", fontSize = 14.ssp(), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Button(
                    onClick = { onCall(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.sdp())
                        .shadow(8.sdp(), RoundedCornerShape(20.sdp()), ambientColor = crmColor, spotColor = crmColor),
                    shape = RoundedCornerShape(20.sdp()),
                    colors = ButtonDefaults.buttonColors(containerColor = crmColor)
                ) {
                    Icon(Icons.Default.Call, null, modifier = Modifier.size(24.sdp()))
                    Spacer(modifier = Modifier.width(12.sdp()))
                    Text(
                        text = if (SimManager.workSimSlot != null) "Call (Work SIM)" else "Call",
                        fontSize = 18.ssp(),
                        fontWeight = FontWeight.Bold
                    )
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.sdp()),
                    contentAlignment = Alignment.Center
                ) {
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

    // --- Share Code Dialog ---
    if (showShareCodeDialog) {
        AlertDialog(
            onDismissRequest = { showShareCodeDialog = false },
            containerColor = surfaceColor,
            icon = {
                Icon(Icons.Default.QrCode, null, tint = crmColor, modifier = Modifier.size(24.sdp()))
            },
            title = {
                Text(
                    "CRM ID",
                    color = textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.ssp()
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        color = backgroundColor,
                        shape = RoundedCornerShape(12.sdp()),
                        border = BorderStroke(1.sdp(), crmColor.copy(alpha = 0.3f)),
                        onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("CRM ID", contact.recordId)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.sdp()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                contact.recordId,
                                fontSize = 20.ssp(),
                                fontWeight = FontWeight.Bold,
                                color = crmColor
                            )
                            Spacer(modifier = Modifier.width(12.sdp()))
                            Icon(
                                Icons.Default.ContentCopy,
                                null,
                                tint = textSecondary,
                                modifier = Modifier.size(16.sdp())
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.sdp()))
                    Text(
                        "Share this ID with other users.",
                        color = textSecondary,
                        fontSize = 14.ssp()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "CRM Record ID: ${contact.recordId}\nName: ${contact.name}"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share CRM ID"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = crmColor)
                ) { Text("Share", fontSize = 14.ssp()) }
            },
            dismissButton = {
                TextButton(onClick = { showShareCodeDialog = false }) {
                    Text(
                        "Close",
                        color = textSecondary,
                        fontSize = 14.ssp()
                    )
                }
            }
        )
    }
}