package com.mnivesh.callyn.sheets

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnivesh.callyn.components.*
import com.mnivesh.callyn.db.AppContact
import com.mnivesh.callyn.managers.SimManager
import com.mnivesh.callyn.managers.ViewLimitManager

// [Paste ModernBottomSheet function here]
// [Paste ModernDeviceBottomSheet function here]
// [Paste EmployeeBottomSheet function here]
// NOTE: Ensure these functions use the imported components from com.mnivesh.callyn.components
// I have not pasted the full body to save space in the chat, but you should move the FULL content of these 3 functions
// exactly as they were in the original file to this file.

//Work Contact Sheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ModernBottomSheet(
    contact: AppContact,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onCall: (Int?) -> Unit,
    isWorkContact: Boolean,
    isDualSim: Boolean,
    department: String?,
    onRequestSubmit: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showShareCodeDialog by remember { mutableStateOf(false) }
    var showRequestDialog by remember { mutableStateOf(false) }
    var requestReason by remember { mutableStateOf("") }
    val context = LocalContext.current

    var isNumberVisible by remember { mutableStateOf(false) }
    var remainingViews by remember { mutableIntStateOf(ViewLimitManager.getRemainingViews()) }

    // Automatically show number for Management or IT Desk
    LaunchedEffect(department) {
        if (department == "Management") {
            isNumberVisible = true
        }
    }

    // --- Modern Theme Palette ---
    val backgroundColor = Color(0xFF0F172A) // Deep Slate Background
    val surfaceColor = Color(0xFF1E293B)    // Lighter Surface
    val primaryColor = Color(0xFF10B981)    // Emerald Green
    val workColor = Color(0xFF60A5FA)       // Blue
    val warningColor = Color(0xFFFFB74D)    // Orange/Gold
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

            // --- Header Section: Avatar & Menu ---
            Box(modifier = Modifier.fillMaxWidth()) {
                // Top Right Menu Button
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    Row {
                        IconButton(
                            onClick = { showShareCodeDialog = true },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = surfaceColor,
                                contentColor = textSecondary
                            ),
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                        ) {
                            Icon(Icons.Default.Share, "Share Code", modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))

                        if(department == "ConflictContactPaused")
                        IconButton(
                            onClick = { showMenu = true },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = surfaceColor,
                                contentColor = textSecondary
                            ),
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                        ) {
                            Icon(Icons.Default.MoreVert, "Options", modifier = Modifier.size(20.dp))
                        }
                    }

                    // Custom Dropdown
                        MaterialTheme(
                            shapes = MaterialTheme.shapes.copy(
                                extraSmall = RoundedCornerShape(
                                    12.dp
                                )
                            )
                        ) {
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(surfaceColor),
                                offset = DpOffset((-12).dp, 0.dp)
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Raise request to mark as personal",
                                            color = textPrimary,
                                            fontSize = 14.sp
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        showRequestDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Edit,
                                            null,
                                            tint = workColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                            }
                        }
                }

                // Centered Avatar
                Box(
                    modifier = Modifier
                        .size(80.dp)
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
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Contact Name ---
            Text(
                text = contact.name,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // --- Type Pill (Work/Personal) ---
            val pillColor = if (isWorkContact) workColor else primaryColor
            Surface(
                color = pillColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = if (isWorkContact) Icons.Default.BusinessCenter else Icons.Default.Person,
                        contentDescription = null,
                        tint = pillColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isWorkContact) "Work Contact" else "Personal Contact",
                        fontSize = 13.sp,
                        color = pillColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            val showDualButtons = isDualSim && SimManager.workSimSlot == null

            if (showDualButtons) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // SIM 1 Button
                    Button(
                        onClick = { onCall(0) }, // Slot 0
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .shadow(
                                8.dp,
                                RoundedCornerShape(20.dp),
                                ambientColor = Color(0xFF3B82F6),
                                spotColor = Color(0xFF3B82F6)
                            ),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)), // Blue
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 4.dp
                        )
                    ) {
                        Row(horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Phone, contentDescription = null)
                            Text("  SIM 1", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // SIM 2 Button
                    Button(
                        onClick = { onCall(1) }, // Slot 1
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .shadow(
                                8.dp,
                                RoundedCornerShape(20.dp),
                                ambientColor = Color(0xFF10B981),
                                spotColor = Color(0xFF10B981)
                            ),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)), // Green
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 4.dp
                        )
                    ) {
                        Row(horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Phone, contentDescription = null)
                            Text("  SIM 2", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Original Single Button
                Button(
                    onClick = { onCall(null) }, // Pass null to trigger smart dial logic
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .shadow(
                            12.dp,
                            RoundedCornerShape(20.dp),
                            ambientColor = primaryColor,
                            spotColor = primaryColor
                        ),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 4.dp)
                ) {
                    Icon(Icons.Default.Call, null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        // Update text to show if we are using a specific SIM automatically
                        text = if(isWorkContact && SimManager.workSimSlot != null) "Call (Work SIM)" else "Call",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }


            Spacer(modifier = Modifier.height(24.dp))

            // --- Info Cards Section ---
            if (isWorkContact) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "CLIENT DETAILS",
                        color = textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                    )

                    Surface(
                        color = surfaceColor,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val context = LocalContext.current
                            val haptics = LocalHapticFeedback.current

                            if (isNumberVisible) {
                                // 1. VISIBLE STATE (Standard View)
                                Box(
                                    modifier = Modifier
                                        .combinedClickable(
                                            onClick = {},
                                            onLongClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                val clipboard =
                                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText(
                                                    "Phone Number",
                                                    contact.number.takeLast(10)
                                                )
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(
                                                    context,
                                                    "Number copied",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        )
                                ) {
                                    ModernDetailRow(
                                        Icons.Default.Phone,
                                        "Phone Number",
                                        contact.number.takeLast(10),
                                        Color(0xFF10B981)
                                    )
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = textSecondary.copy(alpha = 0.1f)
                                )
                            } else {
                                // 2. MASKED STATE (Hidden with Button)
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
                                                "Phone Number",
                                                fontSize = 11.sp,
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontWeight = FontWeight.Medium
                                            )
                                            // Show only last 2 digits
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

                                            // Check Limit
                                            if (ViewLimitManager.canViewNumber()) {
                                                ViewLimitManager.incrementViewCount()
                                                remainingViews =
                                                    ViewLimitManager.getRemainingViews()
                                                isNumberVisible = true
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Daily limit exhausted",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(
                                                0xFF3B82F6
                                            )
                                        ),
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
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = textSecondary.copy(alpha = 0.1f)
                                )
                            }
                            ModernDetailRow(
                                Icons.Default.CreditCard,
                                "PAN",
                                contact.pan,
                                warningColor
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = textSecondary.copy(alpha = 0.1f)
                            )
                            Row(){
                                Box(modifier = Modifier.weight(1f)) {
                                    ModernDetailRow(
                                        Icons.Default.CurrencyRupee,
                                        "AUM",
                                        "₹ " + contact.aum,
                                        Color(0xFF60A5FA)
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    ModernDetailRow(
                                        Icons.Default.Money,
                                        "Family AUM",
                                        "₹ " + contact.familyAum,
                                        Color(0xFF60A5FA)
                                    )
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = textSecondary.copy(alpha = 0.1f)
                            )
                            ModernDetailRow(
                                Icons.Default.FamilyRestroom,
                                "Family Head",
                                contact.familyHead,
                                Color(0xFF81C784)
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = textSecondary.copy(alpha = 0.1f)
                            )
                            ModernDetailRow(
                                Icons.Default.AccountBox,
                                "Relationship Manager",
                                contact.rshipManager ?: "N/A",
                                Color(0xFFC084FC)
                            )
                        }
                    }
                }
            } else if (department != "Management") {
                // For Personal contacts (non-management), show number in big card
                Surface(
                    color = surfaceColor,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            contact.number,
                            fontSize = 20.sp,
                            color = textPrimary,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        IconButton(
                            onClick = {
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Phone Number", contact.number)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                "Copy",
                                tint = textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

        }
    }

    // --- Request Dialog Logic ---
    if (showRequestDialog) {
        AlertDialog(
            onDismissRequest = { showRequestDialog = false },
            containerColor = surfaceColor,
            icon = {
                Icon(Icons.Default.Edit, null, tint = workColor, modifier = Modifier.size(28.dp))
            },
            title = {
                Text(
                    "Request Change",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = textPrimary,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Why do you want to mark this contact as Personal?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(bottom = 20.dp)
                            .fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = requestReason,
                        onValueChange = { requestReason = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "Type your reason here...",
                                color = textSecondary.copy(alpha = 0.5f)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = backgroundColor, // Inset effect
                            unfocusedContainerColor = backgroundColor,
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            cursorColor = primaryColor
                        ),
                        shape = RoundedCornerShape(16.dp),
                        minLines = 3,
                        maxLines = 5,
                        textStyle = MaterialTheme.typography.bodyLarge
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (requestReason.isNotBlank()) {
                            onRequestSubmit(requestReason)
                            showRequestDialog = false
                            requestReason = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Submit Request", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRequestDialog = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text("Cancel", color = textSecondary, fontWeight = FontWeight.Medium)
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    //hex code dialog
    // ... existing showRequestDialog logic ...

    // [!code ++] Add Share Code Dialog
    if (showShareCodeDialog) {
        AlertDialog(
            onDismissRequest = { showShareCodeDialog = false },
            containerColor = surfaceColor,
            icon = {
                Icon(
                    Icons.Default.QrCode,
                    null,
                    tint = primaryColor,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    "Contact Code",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = textPrimary,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Code Display with Copy
                    Surface(
                        onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Contact Code", contact.uniqueCode)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Code copied!", Toast.LENGTH_SHORT).show()
                        },
                        color = backgroundColor,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = contact.uniqueCode,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                color = primaryColor
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Icon(
                                Icons.Default.ContentCopy,
                                "Copy",
                                tint = textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Share this code with other Callyn users to get this contact.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Share Button
                    Button(
                        onClick = {
                            val shareText =
                                "Hey! Find this contact by entering ${contact.uniqueCode} in the Callyn App."
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Share Contact Code")
                            context.startActivity(shareIntent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Share",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Close Button
                    Button(
                        onClick = { showShareCodeDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            })
    }
}

// --- Helper Composable for Details ---
@Composable
private fun ModernDetailRow(icon: ImageVector, label: String, value: String, iconColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                label,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.5f),
                fontWeight = FontWeight.Medium
            )
            Text(value,
                maxLines = 1, // [!code ++]
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Medium)
        }
    }
}


//Personal Contacts Sheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ModernDeviceBottomSheet(
    contact: DeviceContact,
    sheetState: SheetState,
    isDualSim: Boolean,
    onDismiss: () -> Unit,
    onCall: (String, Int?) -> Unit
) {
    val context = LocalContext.current

    // Modern Dark Theme Colors
    val backgroundColor = Color(0xFF0F172A) // Deep Slate
    val surfaceColor = Color(0xFF1E293B)    // Lighter Slate
    val primaryColor = Color(0xFF10B981)    // Emerald
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
                .padding(bottom = 24.dp), // Bottom padding for safety
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // --- Header Section: Avatar & Actions ---
            Box(modifier = Modifier.fillMaxWidth()) {
                // Top Right Actions (Edit/View) - Styled as Glassmorphic Buttons
                Row(
                    modifier = Modifier.align(Alignment.TopEnd),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val actionButtonColors = IconButtonDefaults.iconButtonColors(
                        containerColor = surfaceColor,
                        contentColor = textSecondary
                    )

                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_EDIT).apply {
                                    data = Uri.withAppendedPath(
                                        ContactsContract.Contacts.CONTENT_URI,
                                        contact.id
                                    )
                                    putExtra("finishActivityOnSaveCompleted", true)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "Could not edit contact",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        colors = actionButtonColors,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    ) { Icon(Icons.Default.Edit, "Edit Contact", modifier = Modifier.size(20.dp)) }

                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.withAppendedPath(
                                        ContactsContract.Contacts.CONTENT_URI,
                                        contact.id
                                    )
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "Could not open contact",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        colors = actionButtonColors,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            Icons.Default.OpenInNew,
                            "View Contact",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Centered Avatar
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .align(Alignment.Center)
                        .border(4.dp, backgroundColor, CircleShape) // "Cutout" effect
                        .padding(4.dp) // Spacing between border and avatar
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
                        fontSize = 40.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Name & Type ---
            Text(
                text = contact.name,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // "Personal Contact" Pill
            Surface(
                color = primaryColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        null,
                        tint = primaryColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Personal Contact",
                        fontSize = 13.sp,
                        color = primaryColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- Numbers Logic ---
            val defaultNumberObj = contact.numbers.find { it.isDefault }
            val effectiveDefault =
                if (contact.numbers.size == 1) contact.numbers.first() else defaultNumberObj

            if (contact.numbers.size > 1) {
                // --- Multiple Numbers Card ---
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "PHONE NUMBERS",
                        color = textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                    )

                    Surface(
                        color = surfaceColor,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 250.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            itemsIndexed(contact.numbers) { index, numObj ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onCall(
                                                numObj.number,
                                                null
                                            )
                                        } // Default to system choice/auto
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            numObj.number,
                                            fontSize = 17.sp,
                                            color = textPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (numObj.isDefault) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                "Default",
                                                fontSize = 11.sp,
                                                color = Color(0xFF60A5FA),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                val clipboard =
                                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(
                                                    ClipData.newPlainText(
                                                        "Phone Number",
                                                        numObj.number
                                                    )
                                                )
                                                Toast.makeText(
                                                    context,
                                                    "Copied",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                null,
                                                tint = textSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(4.dp))

                                        // Small Call Button
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(primaryColor)
                                                .clickable { onCall(numObj.number, null) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Call,
                                                null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                // Divider between items (except last)
                                if (index < contact.numbers.lastIndex) {
                                    HorizontalDivider(
                                        color = textSecondary.copy(alpha = 0.1f),
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // --- Single Number Card ---
                val number = contact.numbers.firstOrNull()?.number ?: ""
                Surface(
                    color = surfaceColor,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            number,
                            fontSize = 20.sp,
                            color = textPrimary,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        IconButton(
                            onClick = {
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Phone Number", number)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                "Copy",
                                tint = textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Main Call Button (Split for Dual Sim) ---
            if (effectiveDefault != null) {
                if (isDualSim) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // SIM 1
                        Button(
                            onClick = { onCall(effectiveDefault.number, 0) },
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .shadow(
                                    8.dp,
                                    RoundedCornerShape(20.dp),
                                    ambientColor = Color(0xFF3B82F6),
                                    spotColor = Color(0xFF3B82F6)
                                ),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 4.dp
                            )
                        ) {
                            Row(horizontalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.Phone, contentDescription = null)
                                Text("  SIM 1", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // SIM 2
                        Button(
                            onClick = { onCall(effectiveDefault.number, 1) },
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .shadow(
                                    8.dp,
                                    RoundedCornerShape(20.dp),
                                    ambientColor = Color(0xFF10B981),
                                    spotColor = Color(0xFF10B981)
                                ),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 4.dp
                            )
                        ) {
                            Row(horizontalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.Phone, contentDescription = null)
                                Text("  SIM 2", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Original Single Button
                    Button(
                        onClick = { onCall(effectiveDefault.number, null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .shadow(
                                12.dp,
                                RoundedCornerShape(20.dp),
                                ambientColor = primaryColor,
                                spotColor = primaryColor
                            ),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 4.dp
                        )
                    ) {
                        Icon(Icons.Default.Call, null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (contact.numbers.size > 1) "Call Default" else "Call",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}


//Employee Bottom Sheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeBottomSheet(
    contact: AppContact, // Changed from EmployeeDirectory to AppContact to match your list
    sheetState: SheetState,
    isDualSim: Boolean,
    onDismiss: () -> Unit,
    onCall: (Int?) -> Unit
) {
    val context = LocalContext.current

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
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Avatar
            Box(
                modifier = Modifier
                    .size(110.dp)
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
                    fontSize = 40.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Name
            Text(
                text = contact.name,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Pill
            Surface(
                color = secondaryColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(
                        Icons.Default.Badge,
                        null,
                        tint = secondaryColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Employee",
                        fontSize = 13.sp,
                        color = secondaryColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Details Card
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "CONTACT DETAILS",
                    color = textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                )

                Surface(
                    color = surfaceColor,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Phone Row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(primaryColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Phone,
                                    null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Mobile",
                                    fontSize = 11.sp,
                                    color = textSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    contact.number,
                                    fontSize = 16.sp,
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
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = textSecondary.copy(alpha = 0.1f)
                        )

                        // Department Row (Using familyHead as Department per schema)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF8B5CF6).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Apartment,
                                    null,
                                    tint = Color(0xFF8B5CF6),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    "Department",
                                    fontSize = 11.sp,
                                    color = textSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    if (contact.familyHead.isNotBlank()) contact.familyHead else "N/A",
                                    fontSize = 16.sp,
                                    color = textPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // LOGIC: Show Dual Buttons ONLY if it's Dual SIM AND we assume we don't know the Work Slot yet.
            // If SimManager.workSimSlot is set, we skip the selection and show one "Call Work" button.
            val showDualButtons = isDualSim && SimManager.workSimSlot == null

            if (showDualButtons) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // SIM 1
                    Button(
                        onClick = { onCall(0) },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .shadow(
                                8.dp,
                                RoundedCornerShape(20.dp),
                                ambientColor = Color(0xFF3B82F6),
                                spotColor = Color(0xFF3B82F6)
                            ),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 4.dp
                        )
                    ) {
                        Row(horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Phone, null)
                            Text("  SIM 1", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    // SIM 2
                    Button(
                        onClick = { onCall(1) },
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .shadow(
                                8.dp,
                                RoundedCornerShape(20.dp),
                                ambientColor = Color(0xFF10B981),
                                spotColor = Color(0xFF10B981)
                            ),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 4.dp
                        )
                    ) {
                        Row(horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Phone, null)
                            Text("  SIM 2", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
                            12.dp,
                            RoundedCornerShape(20.dp),
                            ambientColor = primaryColor,
                            spotColor = primaryColor
                        ),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 4.dp
                    )
                ) {
                    Icon(Icons.Default.Call, null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    // Update Text to reflect if we are using Work SIM
                    val buttonText = if (SimManager.workSimSlot != null) "Call (Work SIM)" else "Call"
                    Text(text = buttonText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}