package com.mnivesh.callyn.sheets

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnivesh.callyn.R
import com.mnivesh.callyn.components.ModernDetailRow
import com.mnivesh.callyn.components.getColorForName
import com.mnivesh.callyn.components.getInitials
import com.mnivesh.callyn.db.CrmContact
import com.mnivesh.callyn.managers.SimManager
import com.mnivesh.callyn.managers.ViewLimitManager

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CrmBottomSheet(
    contact: CrmContact,
    sheetState: SheetState,
    isDualSim: Boolean,
    onDismiss: () -> Unit,
    onCall: (Int?) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var showShareCodeDialog by remember { mutableStateOf(false) }

    // Number Hiding State
    var isNumberVisible by remember { mutableStateOf(false) }
    var remainingViews by remember { mutableIntStateOf(ViewLimitManager.getRemainingViews()) }

    // Theme Colors
    val backgroundColor = Color(0xFF0F172A)
    val surfaceColor = Color(0xFF1E293B)
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
        // Changed Column to LazyColumn for full sheet scrolling
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header: WhatsApp + Avatar + Share Button
                    Box(modifier = Modifier.fillMaxWidth()) {

                        // WhatsApp Button (Top Left)
                        IconButton(
                            onClick = {
                                WhatsAppHelper.openChat(
                                    context = context,
                                    phoneNumber = contact.number
                                )
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = surfaceColor,
                            ),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .size(40.dp)
                                .clip(CircleShape)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.whatsapp),
                                contentDescription = "WhatsApp",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(35.dp)
                            )
                        }

                        // Share Code Button (Top Right)
                        IconButton(
                            onClick = { showShareCodeDialog = true },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(surfaceColor)
                        ) {
                            Icon(
                                Icons.Default.QrCode,
                                "Share CRM ID",
                                tint = crmColor,
                                modifier = Modifier.size(20.dp)
                            )
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

                    // Call Buttons
                    val showDualButtons = isDualSim && SimManager.workSimSlot == null

                    if (showDualButtons) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { onCall(0) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp),
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
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp),
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
                            onClick = { onCall(null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .shadow(
                                    8.dp,
                                    RoundedCornerShape(20.dp),
                                    ambientColor = crmColor,
                                    spotColor = crmColor
                                ),
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

                            // --- Masked Mobile Logic ---
                            if (isNumberVisible) {
                                Box(
                                    modifier = Modifier.combinedClickable(
                                        onClick = {},
                                        onLongClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val clipboard =
                                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip =
                                                ClipData.newPlainText("Phone Number", contact.number)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT)
                                                .show()
                                        }
                                    )
                                ) {
                                    ModernDetailRow(
                                        Icons.Default.Phone,
                                        "Mobile Number",
                                        contact.number,
                                        Color(0xFF10B981)
                                    )
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Masked Info
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Phone,
                                                null,
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(
                                                "Mobile Number",
                                                fontSize = 11.sp,
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontWeight = FontWeight.Medium
                                            )
                                            val masked =
                                                if (contact.number.length > 2) "******" + contact.number.takeLast(
                                                    2
                                                ) else "******"
                                            Text(
                                                masked,
                                                fontSize = 16.sp,
                                                color = Color.White,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    // View Button
                                    Button(
                                        onClick = {
                                            // Verify Storage Permission
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                                                Toast.makeText(
                                                    context,
                                                    "Permission required",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                try {
                                                    val intent =
                                                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                                    intent.data =
                                                        Uri.parse("package:${context.packageName}")
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                }
                                                return@Button
                                            }

                                            if (ViewLimitManager.canViewNumber()) {
                                                ViewLimitManager.incrementViewCount()
                                                remainingViews = ViewLimitManager.getRemainingViews()
                                                isNumberVisible = true
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Daily limit exhausted",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                        contentPadding = PaddingValues(
                                            horizontal = 16.dp,
                                            vertical = 0.dp
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text(
                                            "View ($remainingViews)",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
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
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = textSecondary.copy(alpha = 0.1f)
                            )

                            // Custom Multi-line Row for Product/Subject
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF60A5FA).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Inventory,
                                        null,
                                        tint = Color(0xFF60A5FA),
                                        modifier = Modifier.size(18.dp)
                                    )
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
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
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
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("CRM ID", contact.recordId)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                contact.recordId,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = crmColor
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Icon(
                                Icons.Default.ContentCopy,
                                null,
                                tint = textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
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
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "CRM Record ID: ${contact.recordId}\nName: ${contact.name}"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share CRM ID"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = crmColor)
                ) { Text("Share") }
            },
            dismissButton = {
                TextButton(onClick = { showShareCodeDialog = false }) {
                    Text(
                        "Close",
                        color = textSecondary
                    )
                }
            }
        )
    }
}