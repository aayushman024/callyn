package com.mnivesh.callyn.screens

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mnivesh.callyn.viewmodels.UserDetailsUiState
import com.mnivesh.callyn.viewmodels.UserDetailsViewModel
import com.mnivesh.callyn.api.UserDetailsResponse
import com.mnivesh.callyn.managers.AuthManager
import java.text.SimpleDateFormat
import java.util.*

// --- ENHANCED COLORS ---
val CardBg = Color(0xFF1E293B)
val AccentBlue = Color(0xFF3B82F6)
val AccentPurple = Color(0xFF8B5CF6)
val AccentGreen = Color(0xFF10B981)
val AccentOrange = Color(0xFFF59E0B)
val Text1 = Color(0xFFF1F5F9)
val Text2 = Color(0xFF94A3B8)
val DeptChipBg = Color(0xFF334155)
val ScreenBg = Color(0xFF0F172A)

enum class SortOrder {
    LATEST_FIRST,
    OLDEST_FIRST
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserDetailsScreen(
    onNavigateBack: () -> Unit,
    viewModel: UserDetailsViewModel = viewModel()
) {
    val context = LocalContext.current
    val authManager = remember { AuthManager(context) }
    val uiState = viewModel.uiState

    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(SortOrder.LATEST_FIRST) }
    var showSortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val token = authManager.getToken()
        if (token != null) viewModel.fetchUserDetails(token)
    }

    Scaffold(
        containerColor = ScreenBg
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (uiState) {
                is UserDetailsUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = AccentBlue
                    )
                }
                is UserDetailsUiState.Error -> {
                    // Error Content (Kept same as original)
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.sdp()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.sdp())
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444).copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CloudOff, null, tint = Color(0xFFEF4444), modifier = Modifier.size(40.sdp()))
                        }
                        Spacer(modifier = Modifier.height(24.sdp()))
                        Text(uiState.message, color = Text1, fontSize = 16.ssp(), fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(32.sdp()))
                        Button(
                            onClick = { authManager.getToken()?.let { viewModel.fetchUserDetails(it) } },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                            shape = RoundedCornerShape(12.sdp()),
                            modifier = Modifier.height(48.sdp())
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(20.sdp()))
                            Spacer(modifier = Modifier.width(8.sdp()))
                            Text("Retry Connection", fontSize = 15.ssp(), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                is UserDetailsUiState.Success -> {
                    val filteredAndSortedUsers = remember(uiState.users, searchQuery, sortOrder) {
                        val filtered = if (searchQuery.isBlank()) {
                            uiState.users
                        } else {
                            uiState.users.filter { user ->
                                user.username.contains(searchQuery, ignoreCase = true) ||
                                        user.department.contains(searchQuery, ignoreCase = true) ||
                                        user.appVersion?.contains(searchQuery, ignoreCase = true) == true ||
                                        user.phoneModel?.contains(searchQuery, ignoreCase = true) == true ||
                                        user.osLevel?.contains(searchQuery, ignoreCase = true) == true
                            }
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
                        // 1. Header (Scrolls Away)
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.sdp(), top = 16.sdp(), bottom = 16.sdp()),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = onNavigateBack) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                                }
                                Spacer(modifier = Modifier.width(4.sdp()))
                                Icon(
                                    Icons.Default.Group,
                                    contentDescription = null,
                                    tint = AccentBlue,
                                    modifier = Modifier.size(24.sdp())
                                )
                                Spacer(modifier = Modifier.width(8.sdp()))
                                Text(
                                    "Team Status",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.ssp()
                                )
                            }
                        }

                        // 2. Sticky Search Bar + Sort Button (Single Line)
                        stickyHeader {
                            Surface(
                                color = ScreenBg, // Matches background so it looks transparent/integrated
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.sdp(), vertical = 12.sdp()),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.sdp())
                                ) {
                                    // Search Bar (Takes available width)
                                    SearchBar(
                                        query = searchQuery,
                                        onQueryChange = { searchQuery = it },
                                        onClear = { searchQuery = "" },
                                        modifier = Modifier.weight(1f)
                                    )

                                    // Sort Button Box
                                    Box {
                                        Surface(
                                            onClick = { showSortMenu = true },
                                            color = CardBg,
                                            shape = RoundedCornerShape(12.sdp()),
                                            border = BorderStroke(1.sdp(), Color.White.copy(alpha = 0.1f)),
                                            modifier = Modifier.height(50.sdp()) // Match search bar height roughly
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(horizontal = 12.sdp())
                                            ) {
                                                Icon(
                                                    Icons.Default.Sort,
                                                    contentDescription = "Sort",
                                                    tint = AccentBlue,
                                                    modifier = Modifier.size(22.sdp())
                                                )
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

                        // 3. User Count (Optional info, scrolls with list)
                        item {
                            Text(
                                text = "${filteredAndSortedUsers.size} user${if (filteredAndSortedUsers.size != 1) "s" else ""}",
                                color = Text2,
                                fontSize = 14.ssp(),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 20.sdp(), vertical = 8.sdp())
                            )
                        }

                        // 4. User List
                        if (filteredAndSortedUsers.isEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 40.sdp()),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.SearchOff, null, tint = AccentBlue, modifier = Modifier.size(40.sdp()))
                                    Spacer(modifier = Modifier.height(16.sdp()))
                                    Text("No results found", color = Text1)
                                }
                            }
                        } else {
                            items(filteredAndSortedUsers) { user ->
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

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier, // Use passed modifier here
        shape = RoundedCornerShape(16.sdp()),
        color = CardBg,
        border = BorderStroke(1.sdp(), Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.sdp(), vertical = 14.sdp()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = "Search",
                tint = AccentBlue,
                modifier = Modifier.size(22.sdp())
            )

            Spacer(modifier = Modifier.width(12.sdp()))

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    color = Text1,
                    fontSize = 15.ssp(),
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(AccentBlue),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            "Search...",
                            color = Text2,
                            fontSize = 15.ssp(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
            )

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(24.sdp())
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = Text2,
                        modifier = Modifier.size(16.sdp())
                    )
                }
            }
        }
    }
}

// ... Rest of UserDetailCard, StatusPill, InfoRowModern, formatLastSeen remain exactly the same ...
// Include them here if this is a single file paste, otherwise assume they exist in the file.
@Composable
fun UserDetailCard(user: UserDetailsResponse) {
    // ... (Keep existing UserDetailCard implementation)
    val lastSeenText = remember(user.lastSeen) {
        formatLastSeen(user.lastSeen)
    }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 8.sdp()),
        shape = RoundedCornerShape(24.sdp()),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.sdp(), Color.White.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.sdp())) {

            // --- HEADER SECTION ---
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Avatar with gradient
                Box(
                    modifier = Modifier
                        .size(64.sdp())
                        .background(
                            Brush.linearGradient(
                                listOf(AccentBlue, AccentPurple)
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.username.firstOrNull()?.uppercase() ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.ssp()
                    )
                }

                Spacer(modifier = Modifier.width(16.sdp()))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.username,
                        color = Text1,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.ssp()
                    )
                    Spacer(modifier = Modifier.height(8.sdp()))

                    // Department Pill
                    Surface(
                        color = DeptChipBg,
                        shape = RoundedCornerShape(20.sdp()),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.sdp(), vertical = 6.sdp()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = user.department ?: "General",
                                color = Color(0xFFCBD5E1),
                                fontSize = 13.ssp(),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.sdp()))

            // --- STATUS PILLS ROW ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.sdp())
            ) {
                // Version Pill (smaller, fixed size)
                StatusPill(
                    icon = Icons.Default.Apps,
                    label = "v${(user.appVersion + " ")}",
                    color = AccentBlue,
                    modifier = Modifier.wrapContentWidth()
                )

                // Device Pill (takes remaining space)
                StatusPill(
                    icon = Icons.Default.Smartphone,
                    label = user.phoneModel ?: "Unknown",
                    color = AccentPurple,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.sdp()))

            // --- INFO SECTION ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.sdp()))
                    .background(Color(0xFF0F172A).copy(alpha = 0.6f))
                    .padding(16.sdp()),
                verticalArrangement = Arrangement.spacedBy(12.sdp())
            ) {
                // OS Info
                InfoRowModern(
                    icon = Icons.Default.Android,
                    label = "Operating System",
                    value = user.osLevel?.substringBefore("(")?.trim() ?: "Unknown OS",
                    color = AccentGreen
                )

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.05f),
                    thickness = 1.sdp()
                )

                // Last Seen
                InfoRowModern(
                    icon = Icons.Default.History,
                    label = "Last Active",
                    value = lastSeenText,
                    color = AccentOrange
                )
            }
        }
    }
}

@Composable
fun StatusPill(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.sdp()),
        border = BorderStroke(1.sdp(), color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.sdp(), vertical = 10.sdp()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.sdp())
            )
            Spacer(modifier = Modifier.width(8.sdp()))
            Text(
                text = label,
                color = Text1,
                fontSize = 13.ssp(),
                fontWeight = FontWeight.SemiBold,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun InfoRowModern(
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
                .size(36.sdp())
                .clip(RoundedCornerShape(10.sdp()))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.sdp())
            )
        }

        Spacer(modifier = Modifier.width(12.sdp()))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = Text2,
                fontSize = 11.ssp(),
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(2.sdp()))
            Text(
                text = value,
                color = Text1,
                fontSize = 14.ssp(),
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun formatLastSeen(dateString: String?): String {
    if (dateString == null) return "Never"

    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")

        val date = inputFormat.parse(dateString) ?: return "Unknown"
        val time = date.time
        val now = System.currentTimeMillis()

        val relativeTime = DateUtils.getRelativeTimeSpanString(
            time,
            now,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()

        val outputFormat = SimpleDateFormat("hh:mm a, dd/MM/yyyy", Locale.getDefault())
        val absoluteTime = outputFormat.format(date)

        "$relativeTime ($absoluteTime)"

    } catch (e: Exception) {
        "Unknown"
    }
}