// File: app/src/main/java/com/mnivesh/callyn/screens/SmsLogsScreen.kt
// File: app/src/main/java/com/mnivesh/callyn/screens/SmsLogsScreen.kt

package com.mnivesh.callyn.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext // Added for Context
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mnivesh.callyn.api.SmsLogResponse
import com.mnivesh.callyn.managers.AuthManager
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsLogsScreen(
    logs: List<SmsLogResponse>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    // Theme Colors
    val backgroundColor = Color(0xFF0F172A)
    val bubbleColor = Color(0xFF334155)
    val primaryColor = Color(0xFF3B82F6)

    val context = LocalContext.current // Need context for AuthManager & SharedPrefs
    var showWhitelistDialog by remember { mutableStateOf(false) } // Dialog state

    // --- 1. LIFECYCLE OBSERVER (Auto-Refresh on Resume) ---
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "SMS Logs",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        if (isRefreshing) {
                            Text(
                                "Refreshing...",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    // Check Dept. Fix: Use remember to avoid re-reading prefs on every recompose
                    val department = remember { AuthManager(context).getDepartment() }

                    if (department == "Management" || department == "IT Desk") {
                        IconButton(onClick = { showWhitelistDialog = true }) {
                            Icon(Icons.Default.Add, "Whitelist Sender", tint = Color.White)
                        }
                    }

                    // --- 2. MANUAL REFRESH BUTTON ---
                    IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, "Refresh", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor
                )
            )
        }
    ) { padding ->
        if (logs.isEmpty() && !isRefreshing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No SMS found", color = Color.White.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(logs) { log ->
                    SmsBubbleItem(log, bubbleColor, primaryColor)
                }
            }
        }
    }

    // --- 3. WHITELIST DIALOG ---
    if (showWhitelistDialog) {
        WhitelistSenderDialog(
            onDismiss = { showWhitelistDialog = false },
            onConfirm = { senderHeader ->
                saveToWhitelist(context, senderHeader)
                showWhitelistDialog = false
            }
        )
    }
}

@Composable
fun WhitelistSenderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Whitelist Sender") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Sender Header (e.g. HDFC-T)") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Subtext requirement
                Text(
                    text = "New messages from this sender will be visible to respective users for 5 mins.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (text.isNotBlank()) onConfirm(text.trim())
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Logic to append to SharedPrefs set
private fun saveToWhitelist(context: Context, sender: String) {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    // IMPORTANT: getStringSet returns a reference. We must copy it to a new MutableSet
    // to ensure SharedPreferences detects the change when we write it back.
    val currentSet = prefs.getStringSet("whitelist_senders", emptySet()) ?: emptySet()
    val newSet = currentSet.toMutableSet()

    newSet.add(sender)

    prefs.edit().putStringSet("whitelist_senders", newSet).apply()
}

// ... SmsBubbleItem and formatTimestamp remain unchanged below

@Composable
fun SmsBubbleItem(
    log: SmsLogResponse,
    bubbleColor: Color,
    primaryColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.Start
    ) {

        // Uploaded By (Primary Identity)
        Text(
            text = log.uploadedBy,
            color = primaryColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
        )

        Surface(
            color = bubbleColor,
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(
                topStart = 6.dp,
                topEnd = 16.dp,
                bottomEnd = 16.dp,
                bottomStart = 16.dp
            ),
            modifier = Modifier
                .fillMaxWidth(0.92f)
        ) {

            Column(
                modifier = Modifier.padding(14.dp)
            ) {

                // Sender (Secondary Info)
                Text(
                    text = log.sender,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Message
                Text(
                    text = log.message,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    // Time
                    Text(
                        text = formatTimestamp(log.timestamp),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Upload Icon
                    Icon(
                        Icons.Default.CloudUpload,
                        null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(12.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Uploaded By (Repeat for emphasis)
                    Text(
                        text = log.uploadedBy,
                        color = primaryColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}


private fun formatTimestamp(ts: String): String {
    return try {
        val millis = ts.toLong()

        val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("Asia/Kolkata") // IST

        sdf.format(Date(millis))
    } catch (e: Exception) {
        ts.take(16).replace("T", " ")
    }
}
