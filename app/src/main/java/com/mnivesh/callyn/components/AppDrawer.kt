package com.mnivesh.callyn.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.mnivesh.callyn.MainActivity
import com.mnivesh.callyn.api.version
import com.mnivesh.callyn.managers.AuthManager
import com.mnivesh.callyn.managers.FeatureBadgeKey
import com.mnivesh.callyn.managers.FeatureBadgeManager
import com.mnivesh.callyn.ui.theme.AppTheme
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp

// --- Access Lists ---
private val ADMIN_EMAILS = hashSetOf(
    "aayushman@niveshonline.com",
    "ishika@niveshonline.com",
    "sagar@niveshonline.com",
    "ved@niveshonline.com"
)

private val MANAGEMENT_ROLES = hashSetOf("Management", "IT Desk")

private data class DrawerAction(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val badgeKey: FeatureBadgeKey? = null,
    val onClick: () -> Unit
)

@Composable
fun AppDrawer(
    userName: String,
    onSync: () -> Unit,
    onLogout: () -> Unit,
    onClose: () -> Unit,
    onShowRequests: () -> Unit,
    onShowUserDetails: () -> Unit,
    onShowDirectory: () -> Unit,
    onShowCallLogs: () -> Unit,
    onShowEditQuickReplies: () -> Unit,
    onShowHandsFree: () -> Unit,
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val authManager = remember { AuthManager(context) }
    val department = remember { authManager.getDepartment() ?: "" }
    val email = remember { authManager.getUserEmail() ?: "" }
    val workPhone = remember { authManager.getWorkPhone() ?: "Not Alloted" }

    val configuration = LocalConfiguration.current
    val drawerWidth = min(320.sdp(), configuration.screenWidthDp.dp * 0.85f)

    val directoryItems = remember(department) {
        val list = mutableListOf<DrawerAction>()
        list.add(
            DrawerAction(
                label = "Employee directory",
                icon = Icons.Default.Groups,
                tint = Color(0xFFF472B6), // Pink
                onClick = onShowDirectory
            )
        )
//        list.add(
//            DrawerAction(
//                label = "Sync work contacts",
//                icon = Icons.Default.Sync,
//                tint = Color(0xFF38BDF8), // Sky Blue
//                onClick = {
//                    Toast.makeText(context, "Syncing Work Contacts...", Toast.LENGTH_SHORT).show()
//                    onSync()
//                }
//            )
//        )
        if (MANAGEMENT_ROLES.contains(department)) {
            list.add(
                DrawerAction(
                    label = "User status",
                    icon = Icons.Default.Group,
                    tint = Color(0xFFA5B4FC), // Lavender / Indigo
                    onClick = onShowUserDetails
                )
            )
        }
        list
    }

    val callingItems = remember(department, email) {
        val list = mutableListOf<DrawerAction>()
        list.add(
            DrawerAction(
                label = "Hands-free calling",
                icon = Icons.Default.HeadsetMic,
                tint = Color(0xFF38BDF8), // Sky Blue
                badgeKey = FeatureBadgeKey.HANDS_FREE,
                onClick = onShowHandsFree
            )
        )
        if (department != "GUEST") {
            val isManagement = department == "Management" || ADMIN_EMAILS.contains(email)
            val callLogLabel = if (isManagement) "Call logs" else "Call notes"
            val callLogIcon = if (isManagement) Icons.Default.History else Icons.Default.EditNote
            val callLogColor = if (isManagement) Color(0xFF34D399) else Color(0xFF818CF8) // Emerald / Purple
            list.add(
                DrawerAction(
                    label = callLogLabel,
                    icon = callLogIcon,
                    tint = callLogColor,
                    onClick = onShowCallLogs
                )
            )
        }
        list.add(
            DrawerAction(
                label = "Quick replies",
                icon = Icons.Default.Message,
                tint = Color(0xFF3B82F6), // Blue
                badgeKey = FeatureBadgeKey.QUICK_REPLIES,
                onClick = onShowEditQuickReplies
            )
        )
        list
    }

    ModalDrawerSheet(
        drawerContainerColor = AppTheme.colors.surface,
        drawerContentColor = AppTheme.colors.textPrimary,
        modifier = Modifier.width(drawerWidth),
        drawerShape = RoundedCornerShape(topEnd = 24.sdp(), bottomEnd = 24.sdp())
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(vertical = 12.sdp())
        ) {
            // Header
            DrawerHeader(
                userName = userName,
                email = email,
                department = department,
                workPhone = workPhone
            )

            Spacer(modifier = Modifier.height(12.sdp()))

            // Scrollable Sections taking up full middle height
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // Directory Section
                DrawerSectionHeader(title = "Directory")
                directoryItems.forEach { item ->
                    DrawerActionRow(item = item, onClose = onClose)
                }

                // Divider between Directory and Calling
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.sdp(), vertical = 8.sdp()),
                    color = AppTheme.colors.border.copy(alpha = 0.5f),
                    thickness = 1.sdp()
                )

                // Calling Section
                DrawerSectionHeader(title = "Calling")
                callingItems.forEach { item ->
                    DrawerActionRow(item = item, onClose = onClose)
                }

                // Divider between Calling and Preferences
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.sdp(), vertical = 8.sdp()),
                    color = AppTheme.colors.border.copy(alpha = 0.5f),
                    thickness = 1.sdp()
                )

                // Preferences Section
                DrawerSectionHeader(title = "Preferences")
                DrawerThemeToggleRow(
                    isDarkTheme = isDarkTheme,
                    onThemeToggle = onThemeToggle
                )
            }

            // Divider before Logout
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.sdp(), vertical = 8.sdp()),
                color = AppTheme.colors.border.copy(alpha = 0.5f),
                thickness = 1.sdp()
            )

            // Bottom Area: Logout & Version
            DrawerLogoutRow(
                onLogout = {
                    onClose()
                    onLogout()
                }
            )
            Spacer(modifier = Modifier.height(4.sdp()))
            DrawerVersionRow()
        }
    }
}

// --- Header Component ---

@Composable
private fun DrawerHeader(
    userName: String,
    email: String,
    department: String,
    workPhone: String
) {
    val isDark = AppTheme.colors.isDark

    val avatarBg = if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.45f) else Color(0xFFDBEAFE)
    val avatarTextColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D4ED8)

    val deptBg = if (isDark) Color(0xFF818CF8).copy(alpha = 0.2f) else Color(0xFFEEF2FF)
    val deptIconColor = if (isDark) Color(0xFF818CF8) else Color(0xFF6366F1)
    val deptTextColor = if (isDark) Color(0xFFC7D2FE) else Color(0xFF4338CA)

    val phoneBg = if (isDark) Color(0xFF34D399).copy(alpha = 0.2f) else Color(0xFFECFDF5)
    val phoneIconColor = if (isDark) Color(0xFF34D399) else Color(0xFF10B981)
    val phoneTextColor = if (isDark) Color(0xFFA7F3D0) else Color(0xFF065F46)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.sdp(), vertical = 8.sdp())
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.sdp())
                    .clip(CircleShape)
                    .background(avatarBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (userName.isNotEmpty()) userName.take(1).uppercase() else "U",
                    fontSize = 20.ssp(),
                    fontWeight = FontWeight.Bold,
                    color = avatarTextColor
                )
            }

            Spacer(modifier = Modifier.width(14.sdp()))

            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = userName.ifEmpty { "User" },
                    fontSize = 16.ssp(),
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (email.isNotBlank() && email != "N/A") {
                    Spacer(modifier = Modifier.height(2.sdp()))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = AppTheme.colors.textSecondary,
                            modifier = Modifier.size(12.sdp())
                        )
                        Spacer(modifier = Modifier.width(5.sdp()))
                        Text(
                            text = email,
                            fontSize = 12.ssp(),
                            color = AppTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Info Pills (Department & Phone)
        val hasDepartment = department.isNotBlank() && department != "N/A"
        val hasPhone = workPhone.isNotBlank() && workPhone != "Not Alloted"

        if (hasDepartment || hasPhone) {
            Spacer(modifier = Modifier.height(14.sdp()))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.sdp()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasDepartment) {
                    Surface(
                        color = deptBg,
                        shape = RoundedCornerShape(12.sdp())
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.sdp(), vertical = 5.sdp()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Apartment,
                                contentDescription = null,
                                tint = deptIconColor,
                                modifier = Modifier.size(13.sdp())
                            )
                            Spacer(modifier = Modifier.width(5.sdp()))
                            Text(
                                text = department,
                                fontSize = 11.ssp(),
                                color = deptTextColor,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (hasPhone) {
                    Surface(
                        color = phoneBg,
                        shape = RoundedCornerShape(12.sdp())
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.sdp(), vertical = 5.sdp()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = phoneIconColor,
                                modifier = Modifier.size(12.sdp())
                            )
                            Spacer(modifier = Modifier.width(5.sdp()))
                            Text(
                                text = workPhone,
                                fontSize = 11.ssp(),
                                color = phoneTextColor,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Section Header ---

@Composable
private fun DrawerSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 12.ssp(),
        fontWeight = FontWeight.Medium,
        color = AppTheme.colors.textSecondary.copy(alpha = 0.8f),
        modifier = Modifier.padding(horizontal = 20.sdp(), vertical = 6.sdp())
    )
}

// --- Action Row Item ---

@Composable
private fun DrawerActionRow(
    item: DrawerAction,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val featureBadgeManager = remember { FeatureBadgeManager(context) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClose()
                item.onClick()
            }
            .padding(horizontal = 20.sdp(), vertical = 11.sdp()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = item.tint,
            modifier = Modifier.size(20.sdp())
        )

        Spacer(modifier = Modifier.width(16.sdp()))

        Text(
            text = item.label,
            fontSize = 14.ssp(),
            fontWeight = FontWeight.Medium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.weight(1f)
        )

        if (item.badgeKey != null && featureBadgeManager.isFeatureUnvisited(item.badgeKey)) {
            Surface(
                color = Color(0xFFEF4444),
                shape = RoundedCornerShape(4.sdp()),
                modifier = Modifier.padding(end = 6.sdp())
            ) {
                Text(
                    text = "NEW",
                    color = Color.White,
                    fontSize = 9.ssp(),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.sdp(), vertical = 2.sdp())
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = AppTheme.colors.textSecondary.copy(alpha = 0.4f),
            modifier = Modifier.size(18.sdp())
        )
    }
}

// --- Preferences Theme Toggle Row ---

@Composable
private fun DrawerThemeToggleRow(
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onThemeToggle(!isDarkTheme) }
            .padding(horizontal = 20.sdp(), vertical = 6.sdp()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
            contentDescription = null,
            tint = if (isDarkTheme) Color(0xFFFACC15) else Color(0xFFFBBF24),
            modifier = Modifier.size(20.sdp())
        )

        Spacer(modifier = Modifier.width(16.sdp()))

        Text(
            text = "Dark mode",
            fontSize = 14.ssp(),
            fontWeight = FontWeight.Medium,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.weight(1f)
        )

        Switch(
            checked = isDarkTheme,
            onCheckedChange = onThemeToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF3B82F6),
                checkedTrackColor = Color(0xFF3B82F6).copy(alpha = 0.5f),
                uncheckedThumbColor = AppTheme.colors.textSecondary,
                uncheckedTrackColor = AppTheme.colors.border
            )
        )
    }
}

// --- Logout Row ---

@Composable
private fun DrawerLogoutRow(
    onLogout: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onLogout() }
            .padding(horizontal = 20.sdp(), vertical = 12.sdp()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Logout,
            contentDescription = null,
            tint = Color(0xFFEF4444),
            modifier = Modifier.size(20.sdp())
        )

        Spacer(modifier = Modifier.width(16.sdp()))

        Text(
            text = "Log out",
            fontSize = 14.ssp(),
            fontWeight = FontWeight.Medium,
            color = Color(0xFFEF4444)
        )
    }
}

// --- Version Row ---

@Composable
private fun DrawerVersionRow() {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { (context as? MainActivity)?.manualUpdateCheck() }
            .padding(vertical = 4.sdp()),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "v$version",
            fontSize = 12.ssp(),
            color = AppTheme.colors.textSecondary.copy(alpha = 0.6f),
            fontWeight = FontWeight.Normal
        )
    }
}
