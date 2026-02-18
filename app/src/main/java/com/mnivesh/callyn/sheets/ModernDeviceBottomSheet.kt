package com.mnivesh.callyn.sheets

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnivesh.callyn.components.DeviceContact
import com.mnivesh.callyn.components.getColorForName
import com.mnivesh.callyn.components.getInitials

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernDeviceBottomSheet(
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
                .padding(bottom = 24.sdp()), // Bottom padding for safety
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // --- Header Section: Avatar & Actions ---
            Box(modifier = Modifier.fillMaxWidth()) {
                // Top Right Actions (Edit/View) - Styled as Glassmorphic Buttons
                Row(
                    modifier = Modifier.align(Alignment.TopEnd),
                    horizontalArrangement = Arrangement.spacedBy(8.sdp())
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
                            .size(40.sdp())
                            .clip(CircleShape)
                    ) { Icon(Icons.Default.Edit, "Edit Contact", modifier = Modifier.size(20.sdp())) }

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
                            .size(40.sdp())
                            .clip(CircleShape)
                    ) {
                        Icon(
                            Icons.Default.OpenInNew,
                            "View Contact",
                            modifier = Modifier.size(20.sdp())
                        )
                    }
                }

                // Centered Avatar
                Box(
                    modifier = Modifier
                        .size(110.sdp())
                        .align(Alignment.Center)
                        .border(4.sdp(), backgroundColor, CircleShape) // "Cutout" effect
                        .padding(4.sdp()) // Spacing between border and avatar
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
            }

            Spacer(modifier = Modifier.height(16.sdp()))

            // --- Name & Type ---
            Text(
                text = contact.name,
                fontSize = 26.ssp(),
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.sdp()))

            // "Personal Contact" Pill
            Surface(
                color = primaryColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(32.sdp())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.sdp())
                ) {
                    Icon(
                        Icons.Default.Person,
                        null,
                        tint = primaryColor,
                        modifier = Modifier.size(14.sdp())
                    )
                    Spacer(modifier = Modifier.width(6.sdp()))
                    Text(
                        "Personal Contact",
                        fontSize = 13.ssp(),
                        color = primaryColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.sdp()))

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
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 250.sdp()),
                            contentPadding = PaddingValues(vertical = 8.sdp())
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
                                        .padding(horizontal = 16.sdp(), vertical = 12.sdp()),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            numObj.number,
                                            fontSize = 17.ssp(),
                                            color = textPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (numObj.isDefault) {
                                            Spacer(modifier = Modifier.height(4.sdp()))
                                            Text(
                                                "Default",
                                                fontSize = 11.ssp(),
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
                                            modifier = Modifier.size(36.sdp())
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                null,
                                                tint = textSecondary,
                                                modifier = Modifier.size(18.sdp())
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(4.sdp()))

                                        // Small Call Button
                                        Box(
                                            modifier = Modifier
                                                .size(36.sdp())
                                                .clip(CircleShape)
                                                .background(primaryColor)
                                                .clickable { onCall(numObj.number, null) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Call,
                                                null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.sdp())
                                            )
                                        }
                                    }
                                }

                                // Divider between items (except last)
                                if (index < contact.numbers.lastIndex) {
                                    HorizontalDivider(
                                        color = textSecondary.copy(alpha = 0.1f),
                                        modifier = Modifier.padding(horizontal = 16.sdp())
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
                    shape = RoundedCornerShape(20.sdp()),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.sdp())
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            number,
                            fontSize = 20.ssp(),
                            color = textPrimary,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(12.sdp()))
                        IconButton(
                            onClick = {
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Phone Number", number)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.sdp())
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                "Copy",
                                tint = textSecondary,
                                modifier = Modifier.size(16.sdp())
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.sdp()))

            // --- Main Call Button (Split for Dual Sim) ---
            if (effectiveDefault != null) {
                if (isDualSim) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.sdp())
                    ) {
                        // SIM 1
                        Button(
                            onClick = { onCall(effectiveDefault.number, 0) },
                            modifier = Modifier
                                .weight(1f)
                                .height(64.sdp())
                                .shadow(
                                    8.sdp(),
                                    RoundedCornerShape(20.sdp()),
                                    ambientColor = Color(0xFF3B82F6),
                                    spotColor = Color(0xFF3B82F6)
                                ),
                            shape = RoundedCornerShape(20.sdp()),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 0.sdp(),
                                pressedElevation = 4.sdp()
                            )
                        ) {
                            Row(horizontalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.Phone, contentDescription = null)
                                Text("  SIM 1", fontSize = 16.ssp(), fontWeight = FontWeight.Bold)
                            }
                        }

                        // SIM 2
                        Button(
                            onClick = { onCall(effectiveDefault.number, 1) },
                            modifier = Modifier
                                .weight(1f)
                                .height(64.sdp())
                                .shadow(
                                    8.sdp(),
                                    RoundedCornerShape(20.sdp()),
                                    ambientColor = Color(0xFF10B981),
                                    spotColor = Color(0xFF10B981)
                                ),
                            shape = RoundedCornerShape(20.sdp()),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 0.sdp(),
                                pressedElevation = 4.sdp()
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
                        onClick = { onCall(effectiveDefault.number, null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.sdp())
                            .shadow(
                                12.sdp(),
                                RoundedCornerShape(20.sdp()),
                                ambientColor = primaryColor,
                                spotColor = primaryColor
                            ),
                        shape = RoundedCornerShape(20.sdp()),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.sdp(),
                            pressedElevation = 4.sdp()
                        )
                    ) {
                        Icon(Icons.Default.Call, null, modifier = Modifier.size(24.sdp()))
                        Spacer(modifier = Modifier.width(12.sdp()))
                        Text(
                            text = if (contact.numbers.size > 1) "Call Default" else "Call",
                            fontSize = 18.ssp(),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.sdp()))
        }
    }
}