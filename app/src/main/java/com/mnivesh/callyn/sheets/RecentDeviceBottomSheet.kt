package com.mnivesh.callyn.screens.sheets

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import com.mnivesh.callyn.components.DeviceContact
import com.mnivesh.callyn.components.getColorForName
import com.mnivesh.callyn.components.getInitials
import com.mnivesh.callyn.managers.SimManager
import com.mnivesh.callyn.screens.RecentCallUiItem
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentDeviceBottomSheet(
    contact: DeviceContact,
    history: List<RecentCallUiItem>,
    isLoading: Boolean,
    sheetState: SheetState,
    isDualSim: Boolean,
    onDismiss: () -> Unit,
    onCall: (Int?) -> Unit
) {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    val number = contact.numbers.firstOrNull()?.number ?: ""
    var contactUri by remember { mutableStateOf<Uri?>(null) }
    val isUnknown = contactUri == null

    LaunchedEffect(number) {
        if (number.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.withAppendedPath(
                        ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                        Uri.encode(number)
                    )
                    val cursor = contentResolver.query(
                        uri,
                        arrayOf(
                            ContactsContract.PhoneLookup._ID,
                            ContactsContract.PhoneLookup.LOOKUP_KEY
                        ),
                        null, null, null
                    )
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val id =
                                it.getLong(it.getColumnIndexOrThrow(ContactsContract.PhoneLookup._ID))
                            val lookupKey =
                                it.getString(it.getColumnIndexOrThrow(ContactsContract.PhoneLookup.LOOKUP_KEY))
                            contactUri = ContactsContract.Contacts.getLookupUri(id, lookupKey)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.sdp(), vertical = 16.sdp()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(100.sdp())
                        .align(Alignment.Center)
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
                        text = getInitials(contact.name),
                        color = Color.White,
                        fontSize = 36.ssp(),
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!isUnknown && contactUri != null) {
                    Row(modifier = Modifier.align(Alignment.TopEnd)) {
                        IconButton(onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_EDIT).apply {
                                    data = contactUri
                                    putExtra("finishActivityOnSaveCompleted", true)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot edit contact", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }) {
                            Icon(Icons.Default.Edit, "Edit", tint = Color.White.copy(alpha = 0.7f))
                        }

                        IconButton(onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = contactUri
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot open contact", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }) {
                            Icon(
                                Icons.Default.OpenInNew,
                                "View",
                                tint = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.sdp()))

            if (!isUnknown) {
                Text(
                    text = contact.name,
                    fontSize = 24.ssp(),
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            } else {
                Text(
                    text = number,
                    fontSize = 24.ssp(),
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.sdp())
            ) {
                Icon(
                    Icons.Default.Person,
                    null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(16.sdp())
                )
                Spacer(modifier = Modifier.width(6.sdp()))
                Text(
                    "Personal Contact",
                    fontSize = 15.ssp(),
                    color = Color(0xFF10B981),
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.sdp())
            ) {
                if (!isUnknown) {
                    Text(
                        number,
                        fontSize = 16.ssp(),
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(10.sdp()))
                }

                if (isUnknown) {
                    Text(
                        "Copy Number",
                        fontSize = 14.ssp(),
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(8.sdp()))
                }

                Icon(
                    Icons.Default.ContentCopy,
                    "Copy",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(18.sdp())
                        .clickable {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Phone Number", number)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        }
                )
            }

            if (isUnknown) {
                Spacer(modifier = Modifier.height(16.sdp()))
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_INSERT).apply {
                                type = ContactsContract.Contacts.CONTENT_TYPE
                                putExtra(ContactsContract.Intents.Insert.PHONE, number)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Unable to add contact", Toast.LENGTH_SHORT)
                                .show()
                        }
                    },
                    border = BorderStroke(1.sdp(), Color(0xFF3B82F6)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF3B82F6)
                    ),
                    shape = RoundedCornerShape(12.sdp())
                ) {
                    Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.sdp()))
                    Spacer(modifier = Modifier.width(8.sdp()))
                    Text("Add to Contacts", fontSize = 14.ssp())
                }
            }

            Spacer(modifier = Modifier.height(32.sdp()))

            // Dual SIM Logic
            if (isDualSim) {
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
                        .height(58.sdp()),
                    shape = RoundedCornerShape(16.sdp()),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Icon(Icons.Default.Call, null, modifier = Modifier.size(24.sdp()))
                    Spacer(modifier = Modifier.width(12.sdp()))
                    Text("Call Now", fontSize = 18.ssp(), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.sdp()))
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
}