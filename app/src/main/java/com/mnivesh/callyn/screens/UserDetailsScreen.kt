package com.mnivesh.callyn.screens

import android.text.format.DateUtils
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mnivesh.callyn.api.DeviceMetrics
import com.mnivesh.callyn.api.UserDetailsResponse
import com.mnivesh.callyn.managers.AuthManager
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
import com.mnivesh.callyn.viewmodels.UserDetailsUiState
import com.mnivesh.callyn.viewmodels.UserDetailsViewModel
import java.text.SimpleDateFormat
import java.util.*

// ─── Color Palette ────────────────────────────────────────────
val CardBg        = Color(0xFF1E293B)
val AccentBlue    = Color(0xFF3B82F6)
val AccentPurple  = Color(0xFF8B5CF6)
val AccentGreen   = Color(0xFF10B981)
val AccentOrange  = Color(0xFFF59E0B)
val AccentRed     = Color(0xFFEF4444)
val AccentCyan    = Color(0xFF06B6D4)
val Text1         = Color(0xFFF1F5F9)
val Text2         = Color(0xFF94A3B8)
val DeptChipBg    = Color(0xFF334155)
val ScreenBg      = Color(0xFF0F172A)
val DialogBg      = Color(0xFF162032)
val MetricRowBg   = Color(0xFF0F172A)

enum class SortOrder { LATEST_FIRST, OLDEST_FIRST }

// ─── Main Screen ──────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserDetailsScreen(
    onNavigateBack: () -> Unit,
    viewModel: UserDetailsViewModel = viewModel()
) {
    val context     = LocalContext.current
    val authManager = remember { AuthManager(context) }
    val uiState     = viewModel.uiState

    var searchQuery  by remember { mutableStateOf("") }
    var sortOrder    by remember { mutableStateOf(SortOrder.LATEST_FIRST) }
    var showSortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        authManager.getToken()?.let { viewModel.fetchUserDetails(it) }
    }

    Scaffold(containerColor = ScreenBg) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (uiState) {

                is UserDetailsUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = AccentBlue
                    )
                }

                is UserDetailsUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.sdp()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.sdp())
                                .clip(CircleShape)
                                .background(AccentRed.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CloudOff, null, tint = AccentRed, modifier = Modifier.size(40.sdp()))
                        }
                        Spacer(Modifier.height(24.sdp()))
                        Text(uiState.message, color = Text1, fontSize = 16.ssp(), fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(32.sdp()))
                        Button(
                            onClick = { authManager.getToken()?.let { viewModel.fetchUserDetails(it) } },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                            shape = RoundedCornerShape(12.sdp()),
                            modifier = Modifier.height(48.sdp())
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(20.sdp()))
                            Spacer(Modifier.width(8.sdp()))
                            Text("Retry Connection", fontSize = 15.ssp(), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                is UserDetailsUiState.Success -> {
                    val filteredAndSorted = remember(uiState.users, searchQuery, sortOrder) {
                        val filtered = if (searchQuery.isBlank()) uiState.users
                        else uiState.users.filter { u ->
                            u.username.contains(searchQuery, true) ||
                                    u.department?.contains(searchQuery, true) == true ||
                                    u.appVersion?.contains(searchQuery, true) == true ||
                                    u.phoneModel?.contains(searchQuery, true) == true ||
                                    u.osLevel?.contains(searchQuery, true) == true
                        }
                        when (sortOrder) {
                            SortOrder.LATEST_FIRST -> filtered.sortedByDescending { it.lastSeen }
                            SortOrder.OLDEST_FIRST -> filtered.sortedBy { it.lastSeen }
                        }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 100.sdp()),
                        modifier = Modifier.fillMaxSize()
                    ) {

                        // Header
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.sdp(), top = 16.sdp(), bottom = 16.sdp()),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = onNavigateBack) {
                                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                                }
                                Spacer(Modifier.width(4.sdp()))
                                Icon(Icons.Default.Group, null, tint = AccentBlue, modifier = Modifier.size(24.sdp()))
                                Spacer(Modifier.width(8.sdp()))
                                Text("Team Status", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.ssp())
                            }
                        }

                        // Sticky Search + Sort
                        stickyHeader {
                            Surface(color = ScreenBg, modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.sdp(), vertical = 12.sdp()),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.sdp())
                                ) {
                                    SearchBar(
                                        query = searchQuery,
                                        onQueryChange = { searchQuery = it },
                                        onClear = { searchQuery = "" },
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box {
                                        Surface(
                                            onClick = { showSortMenu = true },
                                            color = CardBg,
                                            shape = RoundedCornerShape(12.sdp()),
                                            border = BorderStroke(1.sdp(), Color.White.copy(alpha = 0.1f)),
                                            modifier = Modifier.height(50.sdp())
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(horizontal = 12.sdp())
                                            ) {
                                                Icon(Icons.Default.Sort, null, tint = AccentBlue, modifier = Modifier.size(22.sdp()))
                                            }
                                        }
                                        DropdownMenu(
                                            expanded = showSortMenu,
                                            onDismissRequest = { showSortMenu = false },
                                            modifier = Modifier.background(CardBg)
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Latest First", color = Text1) },
                                                onClick = { sortOrder = SortOrder.LATEST_FIRST; showSortMenu = false },
                                                leadingIcon = { Icon(Icons.Default.ArrowDownward, null, tint = AccentBlue) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Oldest First", color = Text1) },
                                                onClick = { sortOrder = SortOrder.OLDEST_FIRST; showSortMenu = false },
                                                leadingIcon = { Icon(Icons.Default.ArrowUpward, null, tint = AccentBlue) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Count
                        item {
                            Text(
                                "${filteredAndSorted.size} user${if (filteredAndSorted.size != 1) "s" else ""}",
                                color = Text2,
                                fontSize = 14.ssp(),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 20.sdp(), vertical = 8.sdp())
                            )
                        }

                        // Empty state
                        if (filteredAndSorted.isEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(top = 40.sdp()),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.SearchOff, null, tint = AccentBlue, modifier = Modifier.size(40.sdp()))
                                    Spacer(Modifier.height(16.sdp()))
                                    Text("No results found", color = Text1)
                                }
                            }
                        } else {
                            items(filteredAndSorted) { user ->
                                Box(modifier = Modifier.padding(horizontal = 20.sdp(), vertical = 10.sdp())) {
                                    UserDetailCard(user)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Card ─────────────────────────────────────────────────────
@Composable
fun UserDetailCard(user: UserDetailsResponse) {
    var showMetricsDialog by remember { mutableStateOf(false) }

    val lastSeenText = remember(user.lastSeen) { formatLastSeen(user.lastSeen) }

    if (showMetricsDialog) {
        DeviceMetricsDialog(
            username = user.username,
            metrics  = user.deviceMetrics,
            phoneModel  = user.phoneModel,   // add
            osLevel     = user.osLevel,
            onDismiss = { showMetricsDialog = false }
        )
    }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 8.sdp()),
        shape = RoundedCornerShape(24.sdp()),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.sdp(), Color.White.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.sdp())) {

            // Avatar + Name + Dept
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(56.sdp())
                        .background(Brush.linearGradient(listOf(AccentBlue, AccentPurple)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.username.firstOrNull()?.uppercase() ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.ssp()
                    )
                }
                Spacer(Modifier.width(16.sdp()))
                Column(modifier = Modifier.weight(1f)) {
                    Text(user.username, color = Text1, fontWeight = FontWeight.Bold, fontSize = 18.ssp())
                    Spacer(Modifier.height(4.sdp()))
                    Text(
                        user.email ?: "—",
                        color = Text2,
                        fontSize = 12.ssp(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Department chip
                Surface(
                    color = DeptChipBg,
                    shape = RoundedCornerShape(20.sdp())
                ) {
                    Text(
                        text = user.department ?: "General",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.ssp(),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.sdp(), vertical = 5.sdp())
                    )
                }
            }

            Spacer(Modifier.height(20.sdp()))

            // Info block: App version + Last Seen
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.sdp()))
                    .background(Color(0xFF0F172A).copy(alpha = 0.6f))
                    .padding(16.sdp()),
                verticalArrangement = Arrangement.spacedBy(12.sdp())
            ) {
                InfoRowModern(
                    icon  = Icons.Default.Apps,
                    label = "App Version",
                    value = "v${user.appVersion ?: "—"}",
                    color = AccentBlue
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f), thickness = 1.sdp())
                InfoRowModern(
                    icon  = Icons.Default.History,
                    label = "Last Active",
                    value = lastSeenText,
                    color = AccentOrange
                )
            }

            Spacer(Modifier.height(16.sdp()))

            // Device Metrics Button
            Surface(
                onClick = { showMetricsDialog = true },
                color = AccentBlue.copy(alpha = 0.08f),
                shape = RoundedCornerShape(14.sdp()),
                border = BorderStroke(1.sdp(), AccentBlue.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.sdp(), vertical = 13.sdp()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Phonelink, null, tint = AccentBlue, modifier = Modifier.size(18.sdp()))
                    Spacer(Modifier.width(8.sdp()))
                    Text(
                        "View Device Metrics",
                        color = AccentBlue,
                        fontSize = 13.ssp(),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.sdp()))
                    Icon(Icons.Default.ChevronRight, null, tint = AccentBlue.copy(alpha = 0.6f), modifier = Modifier.size(16.sdp()))
                }
            }
        }
    }
}

// ─── Device Metrics Dialog ────────────────────────────────────
@Composable
fun DeviceMetricsDialog(
    username: String,
    phoneModel: String?,        // add
    osLevel: String?,           // add
    metrics: DeviceMetrics?,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.sdp()),
            color = DialogBg,
            border = BorderStroke(1.sdp(), Color.White.copy(alpha = 0.08f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.sdp())
        ) {
            Column(modifier = Modifier.padding(24.sdp())
                .verticalScroll(rememberScrollState())) {

                // Dialog Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.sdp())
                            .clip(RoundedCornerShape(12.sdp()))
                            .background(AccentBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Phonelink, null, tint = AccentBlue, modifier = Modifier.size(22.sdp()))
                    }
                    Spacer(Modifier.width(12.sdp()))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Device Metrics", color = Text1, fontWeight = FontWeight.Bold, fontSize = 17.ssp())
                        Text(username, color = Text2, fontSize = 12.ssp())
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.sdp())
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                    ) {
                        Icon(Icons.Default.Close, null, tint = Text2, modifier = Modifier.size(16.sdp()))
                    }
                }

                Spacer(Modifier.height(20.sdp()))
                MetricSection(title = "Device Info", icon = Icons.Default.Smartphone, iconColor = AccentPurple) {
                    MetricRow(
                        icon  = Icons.Default.PhoneAndroid,
                        label = "Phone Model",
                        value = phoneModel ?: "—",
                        color = AccentPurple
                    )
                    MetricRow(
                        icon  = Icons.Default.Android,
                        label = "Android Version",
                        value = osLevel?: "—",
                        color = AccentGreen
                    )
                }

                Spacer(Modifier.height(20.sdp()))

                if (metrics == null) {
                    // No metrics available state
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.sdp()),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SignalWifiStatusbarConnectedNoInternet4, null, tint = Text2, modifier = Modifier.size(36.sdp()))
                            Spacer(Modifier.height(12.sdp()))
                            Text("No metrics available", color = Text2, fontSize = 14.ssp(), textAlign = TextAlign.Center)
                            Spacer(Modifier.height(4.sdp()))
                            Text(
                                "This user hasn't synced device data yet.",
                                color = Text2.copy(alpha = 0.6f),
                                fontSize = 12.ssp(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.sdp())) {

                        // RAM Section
                        MetricSection(title = "Memory", icon = Icons.Default.Memory, iconColor = AccentPurple) {
                            MetricRow(
                                icon = Icons.Default.CheckCircle,
                                label = "RAM Available",
                                value = "${metrics.ramAvailableMb ?: "—"} MB",
                                color = AccentGreen
                            )
                            MetricRow(
                                icon = Icons.Default.Code,
                                label = "App Heap Used",
                                value = "${metrics.appCacheMb ?: "—"} MB",
                                color = AccentCyan
                            )
                        }

                        // Storage Section
                        MetricSection(title = "Storage", icon = Icons.Default.SdStorage, iconColor = AccentBlue) {
                            MetricRow(
                                icon = Icons.Default.FolderOpen,
                                label = "Free Space",
                                value = metrics.storageAvailableGb ?: "—",
                                color = AccentBlue
                            )
                        }

                        // Battery Section
                        MetricSection(title = "Power", icon = Icons.Default.BatteryChargingFull, iconColor = AccentGreen) {
                            val batteryPct = metrics.batteryPercent
                            val batteryColor = when {
                                batteryPct == null  -> Text2
                                batteryPct >= 60    -> AccentGreen
                                batteryPct >= 25    -> AccentOrange
                                else                -> AccentRed
                            }
                            MetricRow(
                                icon = Icons.Default.BatteryFull,
                                label = "Battery Level",
                                value = if (batteryPct != null) "$batteryPct%" else "—",
                                color = batteryColor
                            )
                        }

                        // Device Health Section
                        MetricSection(title = "Device Health", icon = Icons.Default.DeviceThermostat, iconColor = AccentOrange) {
                            val thermalColor = when (metrics.thermalStatus?.lowercase()) {
                                "none"      -> AccentGreen
                                "light"     -> AccentGreen
                                "moderate"  -> AccentOrange
                                "severe"    -> AccentRed
                                "critical",
                                "emergency",
                                "shutdown"  -> AccentRed
                                else        -> Text2
                            }
                            MetricRow(
                                icon = Icons.Default.Thermostat,
                                label = "Thermal Status",
                                value = metrics.thermalStatus?.replaceFirstChar { it.uppercase() } ?: "—",
                                color = thermalColor
                            )
                            val networkColor = when (metrics.networkStatus?.lowercase()) {
                                "wifi"      -> AccentGreen
                                "cellular"  -> AccentBlue
                                "ethernet"  -> AccentCyan
                                "offline"   -> AccentRed
                                else        -> Text2
                            }
                            val networkIcon = when (metrics.networkStatus?.lowercase()) {
                                "wifi"     -> Icons.Default.Wifi
                                "cellular" -> Icons.Default.SignalCellularAlt
                                "ethernet" -> Icons.Default.Cable
                                "offline"  -> Icons.Default.WifiOff
                                else       -> Icons.Default.NetworkCheck
                            }
                            MetricRow(
                                icon = networkIcon,
                                label = "Network",
                                value = metrics.networkStatus?.replaceFirstChar { it.uppercase() } ?: "—",
                                color = networkColor
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.sdp()))

                // Close Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(46.sdp()),
                    shape = RoundedCornerShape(14.sdp()),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue.copy(alpha = 0.15f))
                ) {
                    Text("Close", color = AccentBlue, fontWeight = FontWeight.SemiBold, fontSize = 14.ssp())
                }
            }
        }
    }
}

// ─── Metric Section Container ─────────────────────────────────
@Composable
fun MetricSection(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.sdp()))
            .background(MetricRowBg.copy(alpha = 0.8f))
            .padding(16.sdp()),
        verticalArrangement = Arrangement.spacedBy(10.sdp())
    ) {
        // Section label
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(14.sdp()))
            Spacer(Modifier.width(6.sdp()))
            Text(
                title.uppercase(),
                color = iconColor.copy(alpha = 0.8f),
                fontSize = 10.ssp(),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.05f), thickness = 1.sdp())
        content()
    }
}

// ─── Single Metric Row ────────────────────────────────────────
@Composable
fun MetricRow(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.sdp())
                .clip(RoundedCornerShape(8.sdp()))
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.sdp()))
        }
        Spacer(Modifier.width(12.sdp()))
        Text(label, color = Text2, fontSize = 13.ssp(), modifier = Modifier.weight(1f))
        Surface(
            color = color.copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.sdp()),
            border = BorderStroke(1.sdp(), color.copy(alpha = 0.2f))
        ) {
            Text(
                value,
                color = color,
                fontSize = 12.ssp(),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.sdp(), vertical = 4.sdp())
            )
        }
    }
}

// ─── Reused components ────────────────────────────────────────
@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.sdp()), color = CardBg, border = BorderStroke(1.sdp(), Color.White.copy(alpha = 0.1f))) {
        Row(modifier = Modifier.padding(horizontal = 16.sdp(), vertical = 14.sdp()), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = AccentBlue, modifier = Modifier.size(22.sdp()))
            Spacer(Modifier.width(12.sdp()))
            BasicTextField(value = query, onValueChange = onQueryChange, modifier = Modifier.weight(1f), textStyle = TextStyle(color = Text1, fontSize = 15.ssp(), fontWeight = FontWeight.Medium), cursorBrush = SolidColor(AccentBlue), singleLine = true, decorationBox = { inner -> if (query.isEmpty()) Text("Search...", color = Text2, fontSize = 15.ssp(), maxLines = 1, overflow = TextOverflow.Ellipsis); inner() })
            if (query.isNotEmpty()) { IconButton(onClick = onClear, modifier = Modifier.size(24.sdp())) { Icon(Icons.Default.Close, null, tint = Text2, modifier = Modifier.size(16.sdp())) } }
        }
    }
}

@Composable
fun InfoRowModern(icon: ImageVector, label: String, value: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(36.sdp()).clip(RoundedCornerShape(10.sdp())).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.sdp()))
        }
        Spacer(Modifier.width(12.sdp()))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Text2, fontSize = 11.ssp(), fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(2.sdp()))
            Text(value, color = Text1, fontSize = 14.ssp(), fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

fun formatLastSeen(dateString: String?): String {
    if (dateString == null) return "Never"
    return try {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val date = fmt.parse(dateString) ?: return "Unknown"
        val relative = DateUtils.getRelativeTimeSpanString(date.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
        val absolute = SimpleDateFormat("hh:mm a, dd/MM/yyyy", Locale.getDefault()).format(date)
        "$relative ($absolute)"
    } catch (e: Exception) { "Unknown" }
}