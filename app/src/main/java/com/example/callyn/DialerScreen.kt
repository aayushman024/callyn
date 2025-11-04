package com.example.callyn

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// Map for dialer button letters
private val dialerLetters = mapOf(
    "1" to "", "2" to "ABC", "3" to "DEF",
    "4" to "GHI", "5" to "JKL", "6" to "MNO",
    "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
    "*" to "", "0" to "+", "#" to "" // "0" now has "+" subtext
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DialerScreen() {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    var phoneNumber by remember { mutableStateOf("") }

    // State for Modal Bottom Sheet
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var contactName by remember { mutableStateOf("") }


    val buttons = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("*", "0", "#")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(horizontal = 20.dp)
            .padding(bottom = 100.dp) // prevents overlap with bottom nav
            .navigationBarsPadding(),// This is the robust layout
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Display entered number (uses weight to fill available space)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = phoneNumber.ifEmpty { "Enter Number" },
                color = if (phoneNumber.isEmpty()) Color.Gray else Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Dialer Buttons
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(bottom = 16.dp)
                .navigationBarsPadding() // <-- ***** THIS IS THE FIX *****
        ) {
            buttons.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEach { label ->
                        DialerButton(
                            label = label,
                            subtext = dialerLetters[label] ?: "",
                            onClick = { phoneNumber += label },
                            // Add long-click for "0" to add "+"
                            onLongClick = if (label == "0") {
                                { phoneNumber += "+" }
                            } else {
                                null
                            }
                        )
                    }
                }
            }

            // Last row: Add Contact, Call, Backspace
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Add Contact Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFF1E1E1E), CircleShape)
                        .clickable(enabled = phoneNumber.isNotEmpty()) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            contactName = "" // Clear name field
                            showBottomSheet = true // Show the sheet
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PersonAdd,
                        contentDescription = "Add to Contacts",
                        // Grey out icon when disabled
                        tint = if (phoneNumber.isNotEmpty()) Color.White else Color.Gray,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Call Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFF00C853), CircleShape)
                        .clickable(enabled = phoneNumber.isNotEmpty()) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val intent = Intent(Intent.ACTION_CALL)
                            intent.data = Uri.parse("tel:$phoneNumber")
                            try {
                                context.startActivity(intent)
                            } catch (e: SecurityException) {
                                // Handle missing CALL_PHONE permission
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = "Call",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Backspace/Close Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFF1E1E1E), CircleShape)
                        .combinedClickable(
                            enabled = phoneNumber.isNotEmpty(),
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (phoneNumber.isNotEmpty()) {
                                    phoneNumber = phoneNumber.dropLast(1)
                                }
                            },
                            onLongClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                phoneNumber = ""
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Backspace (Tap) / Clear (Long Press)",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }

    // --- Modal Bottom Sheet ---
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF242424) // Dark background for the sheet
        ) {
            // Sheet content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding(), // <-- Also add padding here for the sheet content
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Add to Contacts",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                // Show the number being added
                Text(
                    phoneNumber,
                    fontSize = 18.sp,
                    color = Color.LightGray
                )

                OutlinedTextField(
                    value = contactName,
                    onValueChange = { contactName = it },
                    label = { Text("Contact Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors( // Theming
                        focusedBorderColor = Color(0xFF00C853),
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.Gray,
                        cursorColor = Color(0xFF00C853),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Button(
                    onClick = {
                        // --- Start Contact Saving Logic ---
                        val intent = Intent(ContactsContract.Intents.Insert.ACTION)
                        intent.type = ContactsContract.RawContacts.CONTENT_TYPE

                        intent.putExtra(ContactsContract.Intents.Insert.NAME, contactName)
                        intent.putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)

                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Handle exception (e.g., no contacts app)
                        }
                        // --- End Contact Saving Logic ---

                        // Hide the sheet
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                showBottomSheet = false
                            }
                        }
                    },
                    enabled = contactName.isNotBlank(), // Enable button only if name is not empty
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00C853),
                        disabledContainerColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Contact")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialerButton(
    label: String,
    subtext: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null // Make long click optional
) {
    val hapticFeedback = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .size(80.dp)
            .background(Color(0xFF1E1E1E), CircleShape)
            .combinedClickable(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                // Only assign long click if it's not null
                onLongClick = onLongClick?.let { longClick ->
                    {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        longClick()
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (subtext.isNotEmpty()) {
                Text(
                    text = subtext,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.LightGray,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}