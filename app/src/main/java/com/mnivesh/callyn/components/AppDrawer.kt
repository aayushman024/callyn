package com.mnivesh.callyn.components

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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.mnivesh.callyn.MainActivity
import com.mnivesh.callyn.api.version
import com.mnivesh.callyn.managers.AuthManager

// Define specific access lists outside the composable to prevent recreation.
private val ADMIN_EMAILS = hashSetOf(
    "aayushman@niveshonline.com",
    "ishika@niveshonline.com",
    "sagar@niveshonline.com",
    "ved@niveshonline.com"
)

private val MANAGEMENT_ROLES = hashSetOf("Management", "IT Desk")

// --- Data Model ---
// Decoupling data from UI logic
sealed class DrawerItemType {
    data class Action(
        val label: String,
        val icon: ImageVector,
        val tint: Color,
        val isDestructive: Boolean = false,
        val onClick: () -> Unit
    ) : DrawerItemType()

    data object Divider : DrawerItemType()
    data object VersionInfo : DrawerItemType()
}

@Composable
fun AppDrawer(
    userName: String,
    onSync: () -> Unit,
    onLogout: () -> Unit,
    onClose: () -> Unit,
    onShowRequests: () -> Unit,
    onShowUserDetails: () -> Unit,
    onShowDirectory: () -> Unit,
    onShowCallLogs: () -> Unit
) {
    val context = LocalContext.current
    val authManager = remember { AuthManager(context) }
    // Fetch once and remember
    val department = remember { authManager.getDepartment() ?: "" }
    val email = remember { authManager.getUserEmail() ?: "" }

    val configuration = LocalConfiguration.current
    val drawerWidth = min(320.sdp(), configuration.screenWidthDp.dp * 0.85f)

    // --- Logic Optimization ---
    // Prepare the list in a derived state or remember block.
    // This runs only when department or email changes, not on every recomposition.
    val drawerItems = remember(department, email) {
        val list = mutableListOf<DrawerItemType>()

        // 1. Standard Items
        list.add(
            DrawerItemType.Action("Sync Work Contacts", Icons.Default.Refresh, Color(0xFF38BDF8)) {
                Toast.makeText(context, "Syncing Work Contacts...", Toast.LENGTH_SHORT).show()
                onSync()
            }
        )
        list.add(
            DrawerItemType.Action("Employee Directory", Icons.Default.Badge, Color(0xFFF472B6), onClick = onShowDirectory)
        )

        // 2. Role Based Items (O(1) Lookup)
        if (MANAGEMENT_ROLES.contains(department)) {
            list.add(
                DrawerItemType.Action("User Status", Icons.Default.Group, Color(0xFFA5B4FC), onClick = onShowUserDetails)
            )
        }

        // 3. Email/Dept Logic
        val callLogLabel = if (department == "Management" || ADMIN_EMAILS.contains(email)) "View Call Logs" else "View Call Notes"
        val callLogIcon = if (department == "Management" || ADMIN_EMAILS.contains(email)) Icons.Default.List else Icons.Default.EditNote
        val callLogColor = if (department == "Management" || ADMIN_EMAILS.contains(email)) Color(0xFF34D399) else Color(0xFF673AB7)

        list.add(
            DrawerItemType.Action(callLogLabel, callLogIcon, callLogColor, onClick = onShowCallLogs)
        )

        list.add(
            DrawerItemType.Action("Personal Contact Requests", Icons.Default.AssignmentInd, Color(0xFFFACC15), onClick = onShowRequests)
        )

        // 4. Footer Items
        list.add(DrawerItemType.Divider)
        list.add(
            DrawerItemType.Action(
                "Logout",
                Icons.AutoMirrored.Filled.Logout,
                Color(0xFFF44336),
                isDestructive = true,
                onClick = onLogout
            )
        )
        list.add(DrawerItemType.VersionInfo)

        list // Return the list
    }

    ModalDrawerSheet(
        drawerContainerColor = Color(0xFF0B1220),
        drawerContentColor = Color.White,
        modifier = Modifier.width(drawerWidth)
    ) {
        // Use LazyColumn for efficient rendering (Windowing)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.sdp()) // Bottom padding for scrolling
        ) {
            // Header is a single item
            item {
                DrawerHeader(userName, email, department)
            }

            item {
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            }

            // Render list items dynamically
            items(drawerItems) { item ->
                when (item) {
                    is DrawerItemType.Action -> {
                        DrawerActionItem(
                            item = item,
                            onClose = onClose
                        )
                    }
                    is DrawerItemType.Divider -> {
                        Spacer(modifier = Modifier.height(16.sdp()))
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.08f),
                            modifier = Modifier.padding(horizontal = 24.sdp())
                        )
                        Spacer(modifier = Modifier.height(16.sdp()))
                    }
                    is DrawerItemType.VersionInfo -> {
                        Spacer(modifier = Modifier.height(24.sdp()))
                        DrawerVersionItem()
                    }
                }
            }
        }
    }
}

// --- Extracted Composables for Smart Recomposition ---

@Composable
private fun DrawerHeader(userName: String, email: String, department: String) {
    // Memoize the background brush to avoid recreation on every frame
    val bgBrush = remember {
        Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF0B1220), Color(0xFF111827)))
    }
    val avatarBrush = remember {
        Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF60A5FA)))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgBrush)
            .statusBarsPadding() // Better than systemBarsPadding for top alignment
            .padding(top = 24.sdp(), bottom = 25.sdp(), start = 24.sdp(), end = 24.sdp())
    ) {
        Box(
            modifier = Modifier
                .size(70.sdp())
                .clip(CircleShape)
                .background(avatarBrush),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (userName.isNotEmpty()) userName.take(1).uppercase() else "U",
                fontSize = 26.ssp(),
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(14.sdp()))

        Text(
            text = userName,
            fontSize = 21.ssp(),
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFF1F5F9)
        )

        if (email.isNotBlank() && email != "N/A") {
            Spacer(modifier = Modifier.height(4.sdp()))
            Text(
                text = email,
                fontSize = 13.ssp(),
                fontWeight = FontWeight.Medium,
                color = Color(0xFF9CA3AF),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        }

        if (department.isNotBlank() && department != "N/A") {
            Spacer(modifier = Modifier.height(10.sdp()))
            Surface(
                color = Color(0xFF1F2937),
                shape = RoundedCornerShape(20.sdp()),
                border = BorderStroke(1.sdp(), Color.White.copy(alpha = 0.06f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.sdp(), vertical = 6.sdp()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.BusinessCenter,
                        null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.sdp())
                    )
                    Spacer(modifier = Modifier.width(8.sdp()))
                    Text(
                        text = department,
                        fontSize = 13.ssp(),
                        color = Color(0xFFD1D5DB),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerActionItem(
    item: DrawerItemType.Action,
    onClose: () -> Unit
) {
    val textColor = if (item.isDestructive) Color(0xFFF44336) else Color.White.copy(alpha = 0.92f)
    val containerColor = if (item.isDestructive) Color(0xFFF44336).copy(alpha = 0.1f) else Color.White.copy(alpha = 0.04f)

    NavigationDrawerItem(
        label = {
            Text(text = item.label, fontSize = 15.ssp(), fontWeight = FontWeight.Medium)
        },
        icon = {
            Icon(item.icon, null, Modifier.size(22.sdp()), tint = if(item.isDestructive) item.tint else item.tint)
        },
        selected = false,
        onClick = {
            onClose()
            item.onClick()
        },
        shape = RoundedCornerShape(18.sdp()),
        colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = containerColor,
            unselectedTextColor = textColor,
            unselectedIconColor = item.tint, // Use the specific tint from data
        ),
        modifier = Modifier
            .padding(horizontal = 14.sdp(), vertical = 4.sdp())
            .fillMaxWidth()
    )
}

@Composable
private fun DrawerVersionItem() {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            onClick = { (context as? MainActivity)?.manualUpdateCheck() },
            shape = RoundedCornerShape(30),
            color = Color.White.copy(alpha = 0.06f),
            border = BorderStroke(1.sdp(), Color.White.copy(alpha = 0.12f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.sdp(), vertical = 8.sdp()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.SystemUpdate,
                    null,
                    tint = Color(0xFF93C5FD),
                    modifier = Modifier.size(14.sdp())
                )
                Spacer(modifier = Modifier.width(10.sdp()))
                Text(
                    text = "Callyn v$version",
                    fontSize = 12.ssp(),
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}