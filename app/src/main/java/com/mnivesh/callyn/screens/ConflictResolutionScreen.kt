package com.mnivesh.callyn.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import com.mnivesh.callyn.CallynApplication
import com.mnivesh.callyn.components.DeviceContact
import com.mnivesh.callyn.db.AppContact
import com.mnivesh.callyn.managers.AuthManager
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
import com.mnivesh.callyn.ui.theme.AppTheme
import com.mnivesh.callyn.utils.ContactUtils.sanitizePhoneNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Screen to handle resolution of contacts that exist on both device and work server.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictResolutionScreen(
    initialConflicts: List<DeviceContact>,
    workContacts: List<AppContact>,
    userName: String,
    onFinished: () -> Unit,
    onDeletePermissionRequest: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as CallynApplication
    val authManager = remember { AuthManager(context) }
    val scope = rememberCoroutineScope()

    // 1. FAILSAFE: Ensure we have a valid user name
    val effectiveUserName = remember { authManager.getUserName() ?: "" }

    var conflicts by remember { mutableStateOf(initialConflicts) }
    var searchQuery by remember { mutableStateOf("") }
    var isDeleting by remember { mutableStateOf(false) }

    // Track which IDs have successfully sent requests
    val sentRequestIds = remember { mutableStateListOf<String>() }

    // Dialog States
    var showRequestDialog by remember { mutableStateOf(false) }
    var contactNameForRequest by remember { mutableStateOf<String?>(null) }
    var contactIdForRequest by remember { mutableStateOf<String?>(null) }
    var requestReason by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) showDeleteConfirm = true
        else Toast.makeText(context, "Permission needed to clear conflicts", Toast.LENGTH_SHORT)
            .show()
    }

    val filteredConflicts = remember(searchQuery, conflicts) {
        if (searchQuery.isBlank()) conflicts
        else conflicts.filter {
            it.name.contains(searchQuery, true) ||
                    it.numbers.any { n -> n.number.contains(searchQuery) }
        }
    }

    Scaffold(
        containerColor = AppTheme.colors.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppTheme.colors.background)
                    .systemBarsPadding()
                    .padding(horizontal = 24.sdp(), vertical = 26.sdp())
            ) {
                Text(
                    "Resolve Conflicts",
                    fontSize = 25.ssp(),
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(8.sdp()))
                Text(
                    "We found ${conflicts.size} contacts that are already assigned by your company. Please delete the duplicates from your device.",
                    fontSize = 14.ssp(),
                    color = AppTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(16.sdp()))

                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.sdp())),
                    placeholder = {
                        Text(
                            "Search conflicts...",
                            color = AppTheme.colors.textSecondary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            "Search",
                            tint = AppTheme.colors.textSecondary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Default.Close,
                                    "Clear",
                                    tint = AppTheme.colors.textSecondary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = AppTheme.colors.textPrimary,
                        unfocusedTextColor = AppTheme.colors.textPrimary,
                        focusedContainerColor = AppTheme.colors.surfaceVariant,
                        unfocusedContainerColor = AppTheme.colors.surfaceVariant.copy(alpha = 0.8f),
                        focusedIndicatorColor = ComposeColor.Transparent,
                        unfocusedIndicatorColor = ComposeColor.Transparent,
                        cursorColor = AppTheme.colors.textPrimary
                    )
                )
            }
        },
        bottomBar = {
            Column(modifier = Modifier
                .background(AppTheme.colors.background)
                .padding(44.sdp())) {
                Button(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.WRITE_CONTACTS
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            showDeleteConfirm = true
                        } else {
                            writePermissionLauncher.launch(Manifest.permission.WRITE_CONTACTS)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.sdp()),
                    shape = RoundedCornerShape(16.sdp()),
                    colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFFEF4444))
                ) {
                    if (isDeleting) CircularProgressIndicator(
                        color = ComposeColor.White,
                        modifier = Modifier.size(24.sdp())
                    )
                    else Text(
                        "Delete All (${conflicts.size})",
                        fontSize = 16.ssp(),
                        fontWeight = FontWeight.Bold,
                        color = ComposeColor.White
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.sdp()),
            verticalArrangement = Arrangement.spacedBy(12.sdp()),
            contentPadding = PaddingValues(top = 16.sdp(), bottom = 16.sdp())
        ) {
            items(filteredConflicts) { contact ->
                ModernConflictItem(
                    contact = contact,
                    isRequestSent = sentRequestIds.contains(contact.id),
                    onRequestClick = {
                        // Work Match Logic
                        val workMatch = workContacts.firstOrNull { work ->
                            contact.numbers.any { numObj ->
                                sanitizePhoneNumber(work.number) == sanitizePhoneNumber(
                                    numObj.number
                                )
                            }
                        }

                        contactNameForRequest = workMatch?.name ?: contact.name
                        contactIdForRequest = contact.id
                        showRequestDialog = true
                    }
                )
            }
            if (filteredConflicts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.sdp()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No conflicts found", color = AppTheme.colors.textSecondary)
                    }
                }
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                containerColor = AppTheme.colors.surface,
                title = {
                    Text(
                        "Confirm Deletion",
                        color = AppTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        "Are you sure you want to delete these ${conflicts.size} contacts? This ensures your Work contacts are synced correctly.",
                        color = AppTheme.colors.textSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirm = false
                            isDeleting = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    conflicts.forEach { contact ->
                                        val uri = Uri.withAppendedPath(
                                            ContactsContract.Contacts.CONTENT_URI,
                                            contact.id
                                        )
                                        context.contentResolver.delete(uri, null, null)
                                    }
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            "Cleaned up contacts",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        onFinished()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) { isDeleting = false }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ComposeColor(0xFFEF4444)
                        )
                    ) { Text("Delete", fontWeight = FontWeight.Bold, color = ComposeColor.White) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text(
                            "Cancel",
                            color = AppTheme.colors.textSecondary
                        )
                    }
                }
            )
        }

        if (showRequestDialog) {
            AlertDialog(
                onDismissRequest = { showRequestDialog = false },
                containerColor = AppTheme.colors.surface,
                title = {
                    Text(
                        "Mark as Personal",
                        color = AppTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            "Why should ${contactNameForRequest ?: "this contact"} remain on your device as Personal?",
                            color = AppTheme.colors.textSecondary,
                            fontSize = 14.ssp(),
                            modifier = Modifier.padding(bottom = 16.sdp())
                        )
                        OutlinedTextField(
                            value = requestReason,
                            onValueChange = { requestReason = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    "Reason (e.g. Relative, Friend)",
                                    color = AppTheme.colors.textSecondary.copy(alpha = 0.5f)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ComposeColor(0xFF3B82F6),
                                unfocusedBorderColor = AppTheme.colors.border,
                                focusedTextColor = AppTheme.colors.textPrimary,
                                unfocusedTextColor = AppTheme.colors.textPrimary,
                                cursorColor = ComposeColor(0xFF3B82F6),
                                focusedContainerColor = AppTheme.colors.surfaceVariant.copy(alpha = 0.3f),
                                unfocusedContainerColor = AppTheme.colors.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(12.sdp()),
                            minLines = 3,
                            maxLines = 5
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val reasonToSend = requestReason
                            val contactToSend = contactNameForRequest
                            val idToSend = contactIdForRequest
                            val token = authManager.getToken()

                            if (reasonToSend.isNotBlank() && contactToSend != null && token != null && effectiveUserName.isNotBlank()) {
                                scope.launch(Dispatchers.IO) {
                                    val success = app.repository.submitPersonalRequest(
                                        token = token,
                                        contactName = contactToSend,
                                        userName = effectiveUserName,
                                        reason = reasonToSend
                                    )
                                    withContext(Dispatchers.Main) {
                                        if (success && idToSend != null) {
                                            sentRequestIds.add(idToSend)
                                            Toast.makeText(
                                                context,
                                                "Request Submitted",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Failed to submit request",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                                showRequestDialog = false
                                requestReason = ""
                            } else {
                                Toast.makeText(context, "Please enter a reason", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ComposeColor(0xFF3B82F6)
                        )
                    ) { Text("Submit", color = ComposeColor.White) }
                },
                dismissButton = {
                    TextButton(onClick = { showRequestDialog = false }) {
                        Text(
                            "Cancel",
                            color = AppTheme.colors.textSecondary
                        )
                    }
                }
            )
        }
    }
}

/**
 * Renders an individual conflict item in the list.
 */
@Composable
fun ModernConflictItem(
    contact: DeviceContact,
    isRequestSent: Boolean,
    onRequestClick: () -> Unit
) {
    val palette = listOf(
        ComposeColor(0xFF6366F1), ComposeColor(0xFFEC4899), ComposeColor(0xFF8B5CF6),
        ComposeColor(0xFF10B981), ComposeColor(0xFFF59E0B), ComposeColor(0xFFEF4444),
        ComposeColor(0xFF3B82F6), ComposeColor(0xFF14B8A6), ComposeColor(0xFFF97316)
    )
    val avatarColor = palette[abs(contact.name.hashCode()) % palette.size]

    val initials = contact.name.split(" ")
        .mapNotNull { word -> word.firstOrNull { it.isLetter() }?.uppercaseChar() }
        .take(2)
        .joinToString("")
        .ifEmpty { contact.name.firstOrNull { it.isLetter() }?.uppercase() ?: "" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.sdp()),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground),
        border = if (AppTheme.colors.isDark) null else BorderStroke(1.sdp(), AppTheme.colors.border)
    ) {
        Row(
            modifier = Modifier.padding(12.sdp()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.sdp())
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                avatarColor,
                                avatarColor.copy(alpha = 0.7f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initials,
                    color = ComposeColor.White,
                    fontSize = 18.ssp(),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(16.sdp()))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    contact.name,
                    color = AppTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.ssp()
                )
            }

            if (isRequestSent) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Sent",
                    tint = ComposeColor(0xFF10B981),
                    modifier = Modifier
                        .size(28.sdp())
                        .padding(end = 8.sdp())
                )
            } else {
                OutlinedButton(
                    onClick = onRequestClick,
                    border = BorderStroke(
                        1.sdp(),
                        ComposeColor(0xFF60A5FA)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ComposeColor(0xFF60A5FA)
                    ),
                    shape = RoundedCornerShape(8.sdp()),
                    contentPadding = PaddingValues(horizontal = 12.sdp(), vertical = 8.sdp())
                ) {
                    Text("Mark Personal", fontSize = 11.ssp())
                }
            }
        }
    }
}
