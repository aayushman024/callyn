package com.mnivesh.callyn.sheets

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.mnivesh.callyn.R
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnivesh.callyn.components.ModernDetailRow
import com.mnivesh.callyn.components.getColorForName
import com.mnivesh.callyn.components.getInitials
import com.mnivesh.callyn.db.AppContact
import com.mnivesh.callyn.managers.AuthManager
import com.mnivesh.callyn.managers.SimManager
import com.mnivesh.callyn.managers.ViewLimitManager
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableIntStateOf

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ModernBottomSheet(
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

                Box(modifier = Modifier.align(Alignment.TopStart)) {

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
                }

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
                            val haptics = LocalHapticFeedback.current

                            // --- 1. PHONE NUMBER SECTION (Conditional) ---
                            if (isNumberVisible) {
                                // State: Visible Number
                                Box(
                                    modifier = Modifier.combinedClickable(
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
                            } else {
                                // State: Masked Number + View Button
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier.size(36.dp)
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
                                                color = textSecondary,
                                                fontWeight = FontWeight.Medium
                                            )
                                            val masked =
                                                if (contact.number.length > 2) "******" + contact.number.takeLast(
                                                    2
                                                ) else "******"
                                            Text(
                                                masked,
                                                fontSize = 16.sp,
                                                color = textPrimary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            if (isLoading) return@Button
                                            coroutineScope.launch {
                                                isLoading = true
                                                try {
                                                    val statusRes =
                                                        ViewLimitManager.getStatus(authManager)
                                                    if (statusRes.isSuccessful && statusRes.body()?.canView == true) {
                                                        val incRes =
                                                            ViewLimitManager.increment(authManager)
                                                        if (incRes.isSuccessful) {
                                                            isNumberVisible = true
                                                            remainingViews =
                                                                incRes.body()?.remaining ?: 0
                                                            Toast.makeText(
                                                                context,
                                                                "Remaining views: $remainingViews",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                    } else {
                                                        Toast.makeText(
                                                            context,
                                                            "Daily limit exhausted.",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                } catch (e: Exception) {
                                                    Toast.makeText(
                                                        context,
                                                        "Network error",
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
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(36.dp).wrapContentWidth(),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        enabled = !isLoading
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = textPrimary,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text(
                                                "View ($remainingViews)",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }

                            // --- 2. COMMON FIELDS (Outside the IF/ELSE) ---
                            // These fields will now remain visible regardless of the phone number's state.

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = textSecondary.copy(alpha = 0.1f)
                            )

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

                            Row(modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.weight(1f)) {
                                    ModernDetailRow(
                                        Icons.Default.CurrencyRupee,
                                        "AUM",
                                        "₹ ${contact.aum}",
                                        Color(0xFF60A5FA)
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    ModernDetailRow(
                                        Icons.Default.Money,
                                        "Family AUM",
                                        "₹ ${contact.familyAum}",
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