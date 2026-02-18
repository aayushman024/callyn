package com.mnivesh.callyn.sheets

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.util.Log
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
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
import com.mnivesh.callyn.managers.AuthManager
import com.mnivesh.callyn.managers.SimManager
import com.mnivesh.callyn.managers.ViewLimitManager
import kotlinx.coroutines.launch

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

    val coroutineScope = rememberCoroutineScope()
    val authManager = remember { AuthManager(context) }
    var isLoading by remember { mutableStateOf(false) }
    var remainingViews by remember { mutableIntStateOf(0) } // Starts at 0, updated by LaunchedEffect

// Fetch initial status when the bottom sheet opens
    LaunchedEffect(Unit) {
        try {
            val response = ViewLimitManager.getStatus(authManager)
            if (response.isSuccessful) {
                remainingViews = response.body()?.remaining ?: 0
            }
        } catch (e: Exception) {
            Log.e("ModernBottomSheet", "Failed to fetch initial status", e)
        }
    }

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
                    .padding(vertical = 16.sdp())
                    .width(48.sdp())
                    .height(6.sdp())
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
    ) {
        // Changed Column to LazyColumn for full sheet scrolling
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 24.sdp(), vertical = 24.sdp())
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
                                .size(40.sdp())
                                .clip(CircleShape)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.whatsapp),
                                contentDescription = "WhatsApp",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(35.sdp())
                            )
                        }

                        // Share Code Button (Top Right)
                        IconButton(
                            onClick = { showShareCodeDialog = true },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(40.sdp())
                                .clip(CircleShape)
                                .background(surfaceColor)
                        ) {
                            Icon(
                                Icons.Default.QrCode,
                                "Share CRM ID",
                                tint = crmColor,
                                modifier = Modifier.size(20.sdp())
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

                    // Name
                    Text(
                        text = contact.name,
                        fontSize = 24.ssp(),
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.sdp()))

                    // Module Pill
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
                                    .height(64.sdp()),
                                shape = RoundedCornerShape(20.sdp()),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Phone, null)
                                    Text("SIM 1", fontSize = 12.ssp())
                                }
                            }
                            Button(
                                onClick = { onCall(1) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.sdp()),
                                shape = RoundedCornerShape(20.sdp()),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Phone, null)
                                    Text("SIM 2", fontSize = 12.ssp())
                                }
                            }
                        }
                    } else {
                        Button(
                            onClick = { onCall(null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.sdp())
                                .shadow(
                                    8.sdp(),
                                    RoundedCornerShape(20.sdp()),
                                    ambientColor = crmColor,
                                    spotColor = crmColor
                                ),
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

                    // Info Card
                    Surface(
                        color = surfaceColor,
                        shape = RoundedCornerShape(20.sdp()),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.sdp())) {

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
                                        .padding(vertical = 4.sdp()),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Masked Info
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.sdp())
                                                .clip(RoundedCornerShape(10.sdp()))
                                                .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Phone,
                                                null,
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(18.sdp())
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.sdp()))
                                        Column {
                                            Text(
                                                "Mobile Number",
                                                fontSize = 11.ssp(),
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontWeight = FontWeight.Medium
                                            )
                                            val masked =
                                                if (contact.number.length > 2) "******" + contact.number.takeLast(
                                                    2
                                                ) else "******"
                                            Text(
                                                masked,
                                                fontSize = 16.ssp(),
                                                color = Color.White,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    // View Button
                                    Button(
                                        onClick = {
                                            if (isLoading) return@Button
                                            coroutineScope.launch {
                                                isLoading = true
                                                try {
                                                    // 1. Check if user can view
                                                    val statusRes =
                                                        ViewLimitManager.getStatus(authManager)
                                                    if (statusRes.isSuccessful) {
                                                        val status = statusRes.body()

                                                        if (status?.canView == true) {
                                                            // 2. Perform the increment on server
                                                            val incRes = ViewLimitManager.increment(
                                                                authManager
                                                            )
                                                            if (incRes.isSuccessful) {
                                                                isNumberVisible = true
                                                                remainingViews =
                                                                    incRes.body()?.remaining ?: 0
                                                                Toast.makeText(
                                                                    context,
                                                                    "Remaining views for today: $remainingViews",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            }
                                                        } else {
                                                            Toast.makeText(
                                                                context,
                                                                "You have exhausted daily view.",
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                        }
                                                    } else {
                                                        Toast.makeText(
                                                            context,
                                                            "Server error. Please try again.",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                } catch (e: Exception) {
                                                    Toast.makeText(
                                                        context,
                                                        "Network error: ${e.message}",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } finally {
                                                    isLoading = false
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(
                                                0xFF3B82F6
                                            )
                                        ),
                                        contentPadding = PaddingValues(
                                            horizontal = 16.sdp(),
                                            vertical = 0.sdp()
                                        ),
                                        shape = RoundedCornerShape(8.sdp()),
                                        modifier = Modifier.height(36.sdp()),
                                        enabled = !isLoading // Disable while loading
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.sdp()),
                                                color = Color.White,
                                                strokeWidth = 2.sdp()
                                            )
                                        } else {
                                            Text(
                                                "View ($remainingViews)",
                                                fontSize = 13.ssp(),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
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

                            // Custom Multi-line Row for Product/Subject
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.sdp())
                                        .clip(RoundedCornerShape(10.sdp()))
                                        .background(Color(0xFF60A5FA).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Inventory,
                                        null,
                                        tint = Color(0xFF60A5FA),
                                        modifier = Modifier.size(18.sdp())
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.sdp()))
                                Column {
                                    Text(
                                        "Product / Subject",
                                        fontSize = 11.ssp(),
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = contact.product ?: "N/A",
                                        fontSize = 16.ssp(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

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
                    Text("Share this ID with other users.", color = textSecondary, fontSize = 14.ssp())
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