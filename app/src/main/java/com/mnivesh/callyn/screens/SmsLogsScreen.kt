package com.mnivesh.callyn.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mnivesh.callyn.api.SmsLogResponse
import com.mnivesh.callyn.managers.AuthManager
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// --- Robust Timestamp Parsing ---
fun parseServerTimestamp(ts: String): Long {
    return ts.toLongOrNull() ?: try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            java.time.Instant.parse(ts).toEpochMilli()
        } else { 0L }
    } catch (e: Exception) { 0L }
}

fun formatDisplayTime(ts: String): String {
    val ms = parseServerTimestamp(ts)
    if (ms == 0L) return "--"
    val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    return sdf.format(Date(ms))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsLogsScreen(
    logs: List<SmsLogResponse>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    // Theme Colors (Matched to Callyn but utilizing modern accents)
    val backgroundColor = Color(0xFF0F172A)
    val bubbleColor = Color(0xFF1E293B)
    val primaryColor = Color(0xFF3B82F6)
    val borderSubtle = Color(0xFF334155)

    val context = LocalContext.current
    var showWhitelistDialog by remember { mutableStateOf(false) }
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // --- 1. GLOBAL TICKER FOR LIVE TIMERS ---
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = System.currentTimeMillis()
        }
    }

    // --- 2. LIFECYCLE OBSERVER (Auto-Refresh on Resume) ---
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

    // --- 3. FILTER LIVE MESSAGES ---
    // Only show messages that haven't expired (5 minutes = 300,000 ms)
    val activeLogs = logs.filter { log ->
        val ts = parseServerTimestamp(log.timestamp)
        (ts + 5 * 60 * 1000) > currentTime
    }.sortedByDescending { parseServerTimestamp(it.timestamp) }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Live SMS Logs",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.ssp()
                        )
                        if (isRefreshing) {
                            Text(
                                "Refreshing...",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.ssp()
                            )
                        } else {
                            Text(
                                "Auto-expires in 5 mins",
                                color = primaryColor.copy(alpha = 0.8f),
                                fontSize = 12.ssp(),
                                fontWeight = FontWeight.Medium
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

                    IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.sdp()),
                                color = Color.White,
                                strokeWidth = 2.sdp()
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
        if (activeLogs.isEmpty() && !isRefreshing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.Timer,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(48.sdp())
                    )
                    Spacer(modifier = Modifier.height(12.sdp()))
                    Text(
                        "No active messages",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.ssp(),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Messages are deleted after 5 minutes",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 13.ssp(),
                        modifier = Modifier.padding(top = 4.sdp())
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.sdp()),
                verticalArrangement = Arrangement.spacedBy(16.sdp()),
                contentPadding = PaddingValues(top = 12.sdp(), bottom = 24.sdp())
            ) {
                items(activeLogs) { log ->
                    SmsBubbleItem(
                        log = log,
                        currentTime = currentTime,
                        bubbleColor = bubbleColor,
                        primaryColor = primaryColor,
                        borderSubtle = borderSubtle
                    )
                }
            }
        }
    }

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
                Spacer(modifier = Modifier.height(8.sdp()))
                Text(
                    text = "New messages from this sender will be visible to respective users for 5 mins.",
                    fontSize = 12.ssp(),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    lineHeight = 16.ssp()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun saveToWhitelist(context: Context, sender: String) {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val currentSet = prefs.getStringSet("whitelist_senders", emptySet()) ?: emptySet()
    val newSet = currentSet.toMutableSet()
    newSet.add(sender)
    prefs.edit().putStringSet("whitelist_senders", newSet).apply()
}

@Composable
fun SmsBubbleItem(
    log: SmsLogResponse,
    currentTime: Long,
    bubbleColor: Color,
    primaryColor: Color,
    borderSubtle: Color
) {
    val timestampMs = parseServerTimestamp(log.timestamp)
    val expiryTime = timestampMs + (5 * 60 * 1000)
    val remainingMs = (expiryTime - currentTime).coerceAtLeast(0)

    val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(remainingMs) % 60
    val timeString = String.format("%02d:%02d", minutes, seconds)

    // Fractions for progress bar and color shift
    val remainingFraction = (remainingMs / (5f * 60 * 1000)).coerceIn(0f, 1f)
    val elapsedFraction = 1f - remainingFraction

    val timerColor = when {
        remainingFraction > 0.5f -> Color(0xFF22C55E) // Green
        remainingFraction > 0.25f -> Color(0xFFF59E0B) // Orange
        else -> Color(0xFFEF4444) // Red
    }

    Surface(
        color = bubbleColor,
        shape = RoundedCornerShape(16.sdp()),
        border = BorderStroke(1.sdp(), borderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.sdp())) {

            // --- HEADER ROW: Uploader & Timer ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Uploaded By Badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.AccountCircle,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(16.sdp())
                    )
                    Spacer(Modifier.width(6.sdp()))
                    Text(
                        text = log.uploadedBy,
                        color = primaryColor,
                        fontSize = 14.ssp(),
                        fontWeight = FontWeight.Bold
                    )
                }

                // Countdown Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(timerColor.copy(alpha = 0.12f), RoundedCornerShape(8.sdp()))
                        .padding(horizontal = 10.sdp(), vertical = 4.sdp())
                ) {
                    Icon(
                        Icons.Rounded.Timer,
                        contentDescription = null,
                        tint = timerColor,
                        modifier = Modifier.size(13.sdp())
                    )
                    Spacer(Modifier.width(4.sdp()))
                    Text(
                        text = timeString,
                        color = timerColor,
                        fontSize = 13.ssp(),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.height(12.sdp()))

            // --- PROGRESS BAR ---
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.sdp())
                    .clip(RoundedCornerShape(2.sdp()))
                    .background(Color.White.copy(alpha = 0.05f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(elapsedFraction) // Fills from left to right as time passes
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.sdp()))
                        .background(
                            Brush.horizontalGradient(listOf(timerColor, timerColor.copy(alpha = 0.6f)))
                        )
                )
            }

            Spacer(Modifier.height(14.sdp()))

            // --- SENDER PILL ---
            // Highlighting sender info in a distinct readable pill
            Surface(
                color = borderSubtle.copy(alpha = 0.4f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.sdp(), borderSubtle.copy(alpha = 0.8f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.sdp(), vertical = 6.sdp()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "From: ",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.ssp()
                    )
                    Text(
                        text = log.sender,
                        color = Color.White,
                        fontSize = 14.ssp(),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(Modifier.height(12.sdp()))

            // --- MESSAGE BODY (Selectable) ---
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.sdp()))
                    .background(Color(0xFF0F172A).copy(alpha = 0.5f)) // Darker inset for message
                    .padding(12.sdp())
            ) {
                SelectionContainer {
                    Text(
                        text = log.message,
                        color = Color.White,
                        fontSize = 15.ssp(),
                        lineHeight = 22.ssp()
                    )
                }
            }

            Spacer(Modifier.height(12.sdp()))

            // --- FOOTER: Timestamp ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(12.sdp())
                )
                Spacer(Modifier.width(4.sdp()))
                Text(
                    text = formatDisplayTime(log.timestamp),
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.ssp()
                )
            }
        }
    }
}