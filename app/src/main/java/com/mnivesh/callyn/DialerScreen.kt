package com.mnivesh.callyn

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.telephony.SubscriptionManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mnivesh.callyn.db.AppContact

data class ContactResult(val name: String, val number: String, val isWork: Boolean = false)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DialerScreen(
    // false = Personal (SIM1), true = Work (SIM2)
    onCallClick: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val application = context.applicationContext as CallynApplication

    // [!code ++] Auth & Work Contacts Setup
    val authManager = remember { AuthManager(context) }
    val department = remember { authManager.getDepartment() }
    val workContacts by application.repository.allContacts .collectAsState(initial = emptyList<AppContact>())

    var phoneNumber by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ContactResult>>(emptyList()) }
    // SIM sheet
    var showSimSheet by remember { mutableStateOf(false) }
    val simSheetState = rememberModalBottomSheetState()

    // ---------------- SEARCH ----------------
    LaunchedEffect(phoneNumber, workContacts) {
        if (phoneNumber.isEmpty()) {
            searchResults = emptyList()
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            val combinedResults = mutableListOf<ContactResult>()

            // 1. Search Device Contacts
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    val cursor = context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                        ),
                        "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                        arrayOf("%$phoneNumber%", "%$phoneNumber%"),
                        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
                    )

                    cursor?.use {
                        val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                        while (it.moveToNext() && combinedResults.size < 4) {
                            combinedResults.add(
                                ContactResult(
                                    name = it.getString(nameIdx) ?: "Unknown",
                                    number = it.getString(numIdx) ?: "",
                                    isWork = false
                                )
                            )
                        }
                    }
                } catch (_: Exception) { }
            }

            // [!code ++] 2. Search Work Contacts (If Management)
            if (department == "Management") {
                val matches = workContacts.filter {
                    it.name.contains(phoneNumber, ignoreCase = true) ||
                            it.number.contains(phoneNumber) // Assuming 'number' property exists on AppContact
                }.take(4) // Limit work results

                matches.forEach { appContact ->
                    combinedResults.add(
                        ContactResult(
                            name = appContact.name,
                            number = appContact.number,
                            isWork = true
                        )
                    )
                }
            }

            // Update State (Deduplicate based on number if needed, or just show all)
            searchResults = combinedResults.distinctBy { it.number }
        }
    }

    // ---------------- CALL LOGIC ----------------
    fun checkSimsAndCall() {
        if (phoneNumber.isBlank()) return

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val subManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val activeSims = subManager.activeSubscriptionInfoList

            if (activeSims != null && activeSims.size > 1) {
                showSimSheet = true
            } else {
                onCallClick(phoneNumber, false)
            }
        } else {
            onCallClick(phoneNumber, false)
        }
    }

    // ---------------- UI ----------------
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(horizontal = 24.dp)
            .padding(bottom = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // -------- SEARCH RESULTS --------
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            items(searchResults) { contact ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { phoneNumber = contact.number },
                    color = Color(0xFF1A1A1A),
                    tonalElevation = 2.dp,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    // [!code ++] Different Gradient for Work Contacts
                                    if (contact.isWork) {
                                        Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF2563EB)))
                                    } else {
                                        Brush.linearGradient(listOf(Color(0xFF2A2A2A), Color(0xFF1F1F1F)))
                                    },
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = contact.name.firstOrNull()?.uppercase() ?: "?",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = highlightMatch(contact.name, phoneNumber),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = highlightMatch(contact.number, phoneNumber),
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                                // [!code ++] Work Badge
                                if (contact.isWork) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "WORK",
                                        fontSize = 10.sp,
                                        color = Color(0xFF60A5FA),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Icon(
                            Icons.Filled.ChevronRight,
                            null,
                            tint = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }

        // -------- NUMBER DISPLAY --------
        Text(
            text = phoneNumber.ifEmpty { "Enter Number" },
            color = if (phoneNumber.isEmpty()) Color.Gray else Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 14.dp)
        )

        // -------- KEYPAD --------
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("*", "0", "#")
            )

            keys.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEach {
                        DialerButton(
                            label = it,
                            onClick = { phoneNumber += it },
                            onLongClick = if (it == "0") {
                                { phoneNumber += "+" }
                            } else null
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // -------- ACTION ROW --------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {

                IconButton(
                    enabled = phoneNumber.isNotEmpty(),
                    onClick = {
                        try {
                            // Create an Intent to insert a new contact
                            val intent = Intent(Intent.ACTION_INSERT).apply {
                                type = ContactsContract.Contacts.CONTENT_TYPE
                                putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace() // Handle case where no contact app is found
                        }
                    }
                ) {
                    Icon(Icons.Filled.PersonAdd, null,
                        modifier = Modifier.size(35.dp),
                        tint = if (phoneNumber.isNotEmpty()) Color.White else Color.Gray)
                }

                Box(
                    modifier = Modifier
                        .size(85.dp)
                        .background(Color(0xFF00C853), CircleShape)
                        .clickable(enabled = phoneNumber.isNotEmpty()) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            checkSimsAndCall()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Call, null, tint = Color.White, modifier = Modifier.size(45.dp))
                }

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            enabled = phoneNumber.isNotEmpty(),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                phoneNumber = phoneNumber.dropLast(1)
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                phoneNumber = ""
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Backspace, null,    modifier = Modifier.size(35.dp),)
                }
            }
        }
    }

    // ---------------- SIM BOTTOM SHEET ----------------
    if (showSimSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSimSheet = false },
            sheetState = simSheetState,
            containerColor = Color(0xFF242424)
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {

                Text(
                    "Select SIM",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )

                // SIM 1
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch { simSheetState.hide() }.invokeOnCompletion {
                                showSimSheet = false
                                onCallClick(phoneNumber, false)
                            }
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.SimCard, null, tint = Color(0xFF00C853))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Personal", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("SIM 1", color = Color.Gray, fontSize = 12.sp)
                    }
                }

                Divider(color = Color.White.copy(alpha = 0.08f))

                // SIM 2
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch { simSheetState.hide() }.invokeOnCompletion {
                                showSimSheet = false
                                onCallClick(phoneNumber, true)
                            }
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.SimCard, null, tint = Color(0xFF3B82F6))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Work", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("SIM 2", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ---------------- HIGHLIGHT ----------------
@Composable
fun highlightMatch(text: String, query: String): AnnotatedString {
    if (query.isEmpty()) return AnnotatedString(text)

    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()

    return buildAnnotatedString {
        var start = 0
        var index = lowerText.indexOf(lowerQuery)

        while (index >= 0) {
            append(text.substring(start, index))

            pushStyle(
                SpanStyle(
                    color = Color(0xFF3B82F6),
                    fontWeight = FontWeight.SemiBold
                )
            )
            append(text.substring(index, index + query.length))
            pop()

            start = index + query.length
            index = lowerText.indexOf(lowerQuery, start)
        }
        append(text.substring(start))
    }
}

// ---------------- BUTTON ----------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialerButton(
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .size(72.dp)
            .background(Color(0xFF1E1E1E), CircleShape)
            .combinedClickable(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongClick = onLongClick?.let {
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        it()
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 32.sp, color = Color.White)
    }
}