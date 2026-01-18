package com.mnivesh.callyn.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.mnivesh.callyn.ui.theme.sdp

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
    val department = remember { AuthManager(context).getDepartment() }
    val email = remember { AuthManager(context).getUserEmail() }

    // RESPONSIVE: Get screen width to calculate dynamic drawer width
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    // Drawer takes 85% of screen width, but caps at 320dp for larger screens
    val drawerWidth = min(320.dp, screenWidth * 0.85f)
    val scrollState = rememberScrollState()

    ModalDrawerSheet(
        drawerContainerColor = Color(0xFF0B1220),
        drawerContentColor = Color.White,
        modifier = Modifier.width(drawerWidth)
    ) {
        // Use a Column with fillMaxSize to manage vertical spacing properly
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // HEADER (Fixed at top)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF020617),
                                Color(0xFF0B1220),
                                Color(0xFF111827)
                            )
                        )
                    )
                    .padding(top = 44.dp, bottom = 28.dp, start = 24.dp, end = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF2563EB), Color(0xFF60A5FA))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (userName.isNotEmpty()) userName.take(1).uppercase() else "U",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = userName,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFF1F5F9)
                )

                // EMAIL TEXT
                if (!email.isNullOrBlank() && email != "N/A") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = email,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF9CA3AF),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                }

                if (!department.isNullOrBlank() && department != "N/A") {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFF1F2937),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.BusinessCenter,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = department,
                                fontSize = 13.sp,
                                color = Color(0xFFD1D5DB),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            // CONTENT (Flexible height + Scrollable)
            // This takes up all available remaining space. If content is too long, it scrolls.
            // If content is short, it pushes the footer to the bottom.
            Column(
                modifier = Modifier
                    .weight(1f) // RESPONSIVE: Takes available space
                    .verticalScroll(scrollState) // RESPONSIVE: Handles small height screens
                    .padding(vertical = 14.dp, horizontal = 14.dp)
            ) {

                @Composable
                fun drawerItem(
                    label: String,
                    icon: @Composable () -> Unit,
                    onClick: () -> Unit
                ) {
                    NavigationDrawerItem(
                        label = {
                            Text(
                                text = label,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        icon = icon,
                        selected = false,
                        onClick = {
                            onClose()
                            onClick()
                        },
                        shape = RoundedCornerShape(18.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.White.copy(alpha = 0.04f),
                            unselectedTextColor = Color.White.copy(alpha = 0.92f),
                            unselectedIconColor = Color.White.copy(alpha = 0.92f),
                            selectedContainerColor = Color.White.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier
                            .padding(vertical = 5.dp)
                            .fillMaxWidth()
                    )
                }

                drawerItem(
                    label = "Sync Work Contacts",
                    icon = {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = Color(0xFF38BDF8)
                        )
                    }
                ) {
                    Toast.makeText(context, "Syncing Work Contacts...", Toast.LENGTH_SHORT).show()
                    onSync()
                }

                drawerItem(
                    label = "Employee Directory",
                    icon = {
                        Icon(
                            Icons.Default.Badge,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = Color(0xFFF472B6)
                        )
                    }
                ) { onShowDirectory() }

                if (department == "Management" || department == "IT Desk") {
                    drawerItem(
                        "User Status",
                        {
                            Icon(
                                Icons.Default.Group,
                                null,
                                Modifier.size(22.dp),
                                tint = Color(0xFFA5B4FC)
                            )
                        }
                    ) { onShowUserDetails() }

                    drawerItem(
                        "View Call Logs",
                        {
                            Icon(
                                Icons.Default.List,
                                null,
                                Modifier.size(22.dp),
                                tint = Color(0xFF34D399)
                            )
                        }
                    ) { onShowCallLogs() }

                    drawerItem(
                        "Personal Contact Requests",
                        {
                            Icon(
                                Icons.Default.AssignmentInd,
                                null,
                                Modifier.size(22.dp),
                                tint = Color(0xFFFACC15)
                            )
                        }
                    ) { onShowRequests() }
                }

                NavigationDrawerItem(
                    label = { Text("Logout", fontSize = 15.sp, fontWeight = FontWeight.Medium) },
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            null,
                            Modifier.size(22.dp),
                            tint = Color(0xFFF87171)
                        )
                    },
                    selected = false,
                    onClick = {
                        onClose()
                        onLogout()
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.White.copy(alpha = 0.03f),
                        unselectedTextColor = Color(0xFFF87171),
                        unselectedIconColor = Color(0xFFF87171)
                    ),
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                )
            }

            // VERSION PILL (Fixed at bottom)
            Surface(
                onClick = { (context as? MainActivity)?.manualUpdateCheck() },
                shape = RoundedCornerShape(30),
                color = Color.White.copy(alpha = 0.06f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 80.sdp()) // Kept your sdp usage
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = Color(0xFF93C5FD),
                        modifier = Modifier.size(14.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "Callyn v$version",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        letterSpacing = 0.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}