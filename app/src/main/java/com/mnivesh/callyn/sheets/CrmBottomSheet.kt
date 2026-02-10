package com.mnivesh.callyn.sheets

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.sp
import com.mnivesh.callyn.components.ModernDetailRow
import com.mnivesh.callyn.components.getColorForName
import com.mnivesh.callyn.components.getInitials
import com.mnivesh.callyn.db.CrmContact
import com.mnivesh.callyn.managers.SimManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrmBottomSheet(
    contact: CrmContact,
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
    // Changed Violet to Zoho-like Blue
    val crmColor = Color(0xFF2C7BE5)
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
                    .padding(vertical = 16.dp)
                    .width(48.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Avatar + Share Button
            Box(modifier = Modifier.fillMaxWidth()) {
                // Share Code Button (Top Right)
                IconButton(
                    onClick = { showShareCodeDialog = true },
                    modifier = Modifier.align(Alignment.TopEnd)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(surfaceColor)
                ) {
                    Icon(Icons.Default.QrCode, "Share CRM ID", tint = crmColor, modifier = Modifier.size(20.dp))
                }

                // Avatar (Center)
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .align(Alignment.Center)
                        .border(4.dp, backgroundColor, CircleShape)
                        .padding(4.dp)
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
                        fontSize = 32.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Name
            Text(
                text = contact.name,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Module Pill
            Surface(
                color = crmColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(
                        Icons.Default.Dns,
                        null,
                        tint = crmColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = contact.module.replace("_", " "),
                        fontSize = 13.sp,
                        color = crmColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Call Buttons (Smart Dialing Logic)
            val showDualButtons = isDualSim && SimManager.workSimSlot == null

            if (showDualButtons) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { onCall(0) },
                        modifier = Modifier.weight(1f).height(64.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Phone, null)
                            Text("SIM 1", fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = { onCall(1) },
                        modifier = Modifier.weight(1f).height(64.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Phone, null)
                            Text("SIM 2", fontSize = 12.sp)
                        }
                    }
                }
            } else {
                Button(
                    onClick = { onCall(null) }, // Null = Auto/Work Slot
                    modifier = Modifier.fillMaxWidth().height(64.dp).shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = crmColor, spotColor = crmColor),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = crmColor)
                ) {
                    Icon(Icons.Default.Call, null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (SimManager.workSimSlot != null) "Call (Work SIM)" else "Call",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Info Card
            Surface(
                color = surfaceColor,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Mobile
                    ModernDetailRow(Icons.Default.Phone, "Mobile Number", contact.number, Color(0xFF10B981))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = textSecondary.copy(alpha = 0.1f))

                    // CRM ID
                    ModernDetailRow(Icons.Default.ConfirmationNumber, "CRM ID", contact.recordId, Color(0xFFFFB74D))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = textSecondary.copy(alpha = 0.1f))

                    // Custom Multi-line Row for Product/Subject
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF60A5FA).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Inventory, null, tint = Color(0xFF60A5FA), modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "Product / Subject",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = contact.product ?: "N/A",
                                fontSize = 16.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                                // Removed maxLines here to show full text
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = textSecondary.copy(alpha = 0.1f))

                    // Owner
                    ModernDetailRow(Icons.Default.PersonOutline, "Owner", contact.ownerName, Color(0xFFF472B6))
                }
            }
        }
    }

    // --- Share Code Dialog ---
    if (showShareCodeDialog) {
        AlertDialog(
            onDismissRequest = { showShareCodeDialog = false },
            containerColor = surfaceColor,
            icon = { Icon(Icons.Default.QrCode, null, tint = crmColor) },
            title = { Text("CRM ID", color = textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        color = backgroundColor,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, crmColor.copy(alpha = 0.3f)),
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("CRM ID", contact.recordId)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(contact.recordId, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = crmColor)
                            Spacer(modifier = Modifier.width(12.dp))
                            Icon(Icons.Default.ContentCopy, null, tint = textSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Share this ID with other users.", color = textSecondary, fontSize = 14.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "CRM Record ID: ${contact.recordId}\nName: ${contact.name}")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share CRM ID"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = crmColor)
                ) { Text("Share") }
            },
            dismissButton = {
                TextButton(onClick = { showShareCodeDialog = false }) { Text("Close", color = textSecondary) }
            }
        )
    }
}