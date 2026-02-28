package com.mnivesh.callyn.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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

// --- Access Lists (Unchanged) ---
private val ADMIN_EMAILS = hashSetOf(
    "aayushman@niveshonline.com",
    "ishika@niveshonline.com",
    "sagar@niveshonline.com",
    "ved@niveshonline.com"
)

private val MANAGEMENT_ROLES = hashSetOf("Management", "IT Desk")

// --- Data Model (Unchanged) ---
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
    val department = remember { authManager.getDepartment() ?: "" }
    val email = remember { authManager.getUserEmail() ?: "" }
    val workPhone = remember { authManager.getWorkPhone() ?: "Not Alloted" }

    val configuration = LocalConfiguration.current
    val drawerWidth = min(320.sdp(), configuration.screenWidthDp.dp * 0.85f)

    // --- Logic (Unchanged) ---
    val drawerItems = remember(department, email) {
        val list = mutableListOf<DrawerItemType>()

        list.add(
            DrawerItemType.Action("Sync Work Contacts", Icons.Default.Refresh, Color(0xFF38BDF8)) {
                Toast.makeText(context, "Syncing Work Contacts...", Toast.LENGTH_SHORT).show()
                onSync()
            }
        )
        list.add(
            DrawerItemType.Action("Employee Directory", Icons.Default.Badge, Color(0xFFF472B6), onClick = onShowDirectory)
        )

        if (MANAGEMENT_ROLES.contains(department)) {
            list.add(
                DrawerItemType.Action("User Status", Icons.Default.Group, Color(0xFFA5B4FC), onClick = onShowUserDetails)
            )
        }

        val callLogLabel = if (department == "Management" || ADMIN_EMAILS.contains(email)) "View Call Logs" else "View Call Notes"
        val callLogIcon = if (department == "Management" || ADMIN_EMAILS.contains(email)) Icons.Default.List else Icons.Default.EditNote
        val callLogColor = if (department == "Management" || ADMIN_EMAILS.contains(email)) Color(0xFF34D399) else Color(0xFF673AB7)

        list.add(
            DrawerItemType.Action(callLogLabel, callLogIcon, callLogColor, onClick = onShowCallLogs)
        )

        list.add(
            DrawerItemType.Action("Personal Contact Requests", Icons.Default.AssignmentInd, Color(0xFFFACC15), onClick = onShowRequests)
        )

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

        list
    }

    ModalDrawerSheet(
        drawerContainerColor = Color(0xFF0B1220),
        drawerContentColor = Color.White,
        modifier = Modifier.width(drawerWidth),
        // Modern rounded edge for the drawer sheet itself
        drawerShape = RoundedCornerShape(topEnd = 24.sdp(), bottomEnd = 24.sdp())
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.sdp())
        ) {
            item {
                DrawerHeader(userName, email, department, workPhone)
            }

            // Spacing before list starts
            item {
                Spacer(modifier = Modifier.height(8.sdp()))
            }

            items(drawerItems) { item ->
                when (item) {
                    is DrawerItemType.Action -> {
                        DrawerActionItem(item = item, onClose = onClose)
                    }
                    is DrawerItemType.Divider -> {
                        // Soft divider
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 16.sdp(), horizontal = 32.sdp()),
                            color = Color.White.copy(alpha = 0.04f),
                            thickness = 1.sdp()
                        )
                    }
                    is DrawerItemType.VersionInfo -> {
                        Spacer(modifier = Modifier.height(16.sdp()))
                        DrawerVersionItem()
                    }
                }
            }
        }
    }
}

// --- Modernized Components ---

@Composable
private fun DrawerHeader(userName: String, email: String, department: String, workPhone: String) {
    // 1. Dynamic Gradients for visual pop
    val headerBg = remember {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A)) // Richer Slate Gradient
        )
    }

    val avatarBorder = remember {
        Brush.sweepGradient(
            colors = listOf(
                Color(0xFFF472B6), // Pink
                Color(0xFF818CF8), // Indigo
                Color(0xFF34D399), // Emerald
                Color(0xFFF472B6)  // Wrap back to Pink
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerBg)
            .statusBarsPadding()
            .padding(start = 24.sdp(), end = 24.sdp(), top = 28.sdp(), bottom = 24.sdp())
    ) {
        // --- Top Row: Avatar & Name ---
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.sdp())
                    .padding(4.sdp()) // Spacing between border and image
                    .clip(CircleShape)
                    .background(Color(0xFF212A38)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (userName.isNotEmpty()) userName.take(1).uppercase() else "U",
                    fontSize = 26.ssp(),
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(16.sdp()))

            // Name & Email Block
            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = userName,
                    fontSize = 19.ssp(),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )

                if (email.isNotBlank() && email != "N/A") {
                    Spacer(modifier = Modifier.height(2.sdp()))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Mail,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(11.sdp())
                        )
                        Spacer(modifier = Modifier.width(4.sdp()))
                        Text(
                            text = email,
                            fontSize = 12.ssp(),
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.sdp()))

        // --- Bottom Row: Info Pills (Department & Phone) ---
        // We use a Row to place them side-by-side or wrap if needed
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.sdp())
        ) {
            // 1. Department Pill (Indigo Theme)
            if (department.isNotBlank() && department != "N/A") {
                Surface(
                    color = Color(0xFF818CF8).copy(alpha = 0.15f), // Indigo Tint
                    shape = CircleShape,
                    border = BorderStroke(1.sdp(), Color(0xFF818CF8).copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1f, fill = false) // Allow flexible width
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.sdp(), vertical = 8.sdp()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.BusinessCenter,
                            null,
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(14.sdp())
                        )
                        Spacer(modifier = Modifier.width(6.sdp()))
                        Text(
                            text = department,
                            fontSize = 11.ssp(),
                            color = Color(0xFFE0E7FF),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 2. Work Phone Pill (Emerald Theme)
            if (workPhone != "Not Alloted") {
                Surface(
                    color = Color(0xFF34D399).copy(alpha = 0.15f), // Emerald Tint
                    shape = CircleShape,
                    border = BorderStroke(1.sdp(), Color(0xFF34D399).copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.sdp(), vertical = 8.sdp()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PhoneIphone,
                            null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(14.sdp())
                        )
                        Spacer(modifier = Modifier.width(6.sdp()))
                        Text(
                            text = workPhone,
                            fontSize = 11.ssp(),
                            color = Color(0xFFD1FAE5),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
    // The "Pill" Logic
    // 1. Container Shape = CircleShape (Stadium/Pill)
    // 2. Subtle Border for definition
    // 3. Floating effect via padding

    val backgroundColor = if (item.isDestructive)
        item.tint.copy(alpha = 0.1f)
    else
        Color.Transparent

    val borderColor = if (item.isDestructive)
        item.tint.copy(alpha = 0.2f)
    else
        Color.White.copy(alpha = 0.08f)

    Surface(
        onClick = {
            onClose()
            item.onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.sdp(), vertical = 4.sdp()), // Floating margins
        shape = CircleShape, // Makes it a Pill
        color = backgroundColor,
        border = BorderStroke(1.sdp(), borderColor)
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 12.sdp(), horizontal = 16.sdp())
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = item.tint,
                modifier = Modifier.size(20.sdp())
            )

            Spacer(modifier = Modifier.width(16.sdp()))

            // Label
            Text(
                text = item.label,
                fontSize = 14.ssp(),
                fontWeight = FontWeight.Medium,
                color = if (item.isDestructive) item.tint else Color(0xFFE2E8F0),
                modifier = Modifier.weight(1f)
            )

            // Chevron (Visual affordance for navigation)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(18.sdp())
            )
        }
    }
}

@Composable
private fun DrawerVersionItem() {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Version Pill
        Surface(
            onClick = { (context as? MainActivity)?.manualUpdateCheck() },
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.2f),
            border = BorderStroke(1.sdp(), Color.White.copy(alpha = 0.05f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.sdp(), vertical = 6.sdp()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "v$version",
                    fontSize = 11.ssp(),
                    color = Color.White.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(6.sdp()))
                // Small dot
                Box(modifier = Modifier.size(4.sdp()).clip(CircleShape).background(Color(0xFF38BDF8)))
            }
        }
    }
}