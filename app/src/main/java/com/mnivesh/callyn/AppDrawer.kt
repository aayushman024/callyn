package com.mnivesh.callyn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnivesh.callyn.api.version
import android.widget.Toast
import androidx.compose.material.icons.filled.Group
import com.mnivesh.callyn.ui.theme.sdp

@Composable
fun AppDrawer(
    userName: String,
    onSync: () -> Unit,
    onLogout: () -> Unit,
    onClose: () -> Unit,
    onShowRequests: () -> Unit,
    onShowUserDetails: () -> Unit
) {
    val context = LocalContext.current
    val department = remember { AuthManager(context).getDepartment() }

    ModalDrawerSheet(
        drawerContainerColor = Color(0xFF1E293B), // Dark Theme Background
        drawerContentColor = Color.White,
        modifier = Modifier.width(300.dp)
    ) {
        // --- Redesigned Header ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                    )
                )
                .padding(top = 40.dp, bottom = 24.dp, start = 24.dp, end = 24.dp)
        ) {
            // 1. User Avatar (Initials)
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF3B82F6), Color(0xFF60A5FA))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (userName.isNotEmpty()) userName.take(1).uppercase() else "U",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. User Name
            Text(
                text = userName,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            // 3. Department Badge
            if (!department.isNullOrBlank() && department != "N/A") {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.BusinessCenter,
                            contentDescription = "Department",
                            tint = Color(0xFF94A3B8), // Slate-400
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = department,
                            fontSize = 13.sp,
                            color = Color(0xFFCBD5E1), // Slate-300
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        // --- Drawer Items ---
        Column(modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp)) {

            NavigationDrawerItem(
                label = { Text("Sync Work Contacts", fontSize = 16.sp, fontWeight = FontWeight.Medium) },
                icon = {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                },
                selected = false,
                onClick = {
                    onClose()
                    Toast.makeText(context, "Syncing Work Contacts...", Toast.LENGTH_SHORT).show()
                    onSync()
                },
                shape = RoundedCornerShape(12.dp),
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = Color.Transparent,
                    unselectedTextColor = Color.White.copy(alpha = 0.9f),
                    unselectedIconColor = Color.White.copy(alpha = 0.9f)
                ),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // [!code ++] New Item: Personal Contact Requests (Only for Management)
            if (department == "Management" || department == "IT Desk") {
                NavigationDrawerItem(
                    label = { Text("Personal Contact Requests", fontSize = 15.sp, fontWeight = FontWeight.Medium) },
                    icon = {
                        Icon(
                            Icons.Default.AssignmentInd,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    selected = false,
                    onClick = {
                        onClose()
                        onShowRequests()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = Color.White.copy(alpha = 0.9f),
                        unselectedIconColor = Color.White.copy(alpha = 0.9f)
                    ),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (department == "Management" || department == "IT Desk") {
                NavigationDrawerItem(
                    label = { Text("User Status", fontSize = 15.sp, fontWeight = FontWeight.Medium) },
                    icon = { Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(22.dp)) },
                    selected = false,
                    onClick = {
                        onClose()
                        onShowUserDetails()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = Color.White.copy(alpha = 0.9f),
                        unselectedIconColor = Color.White.copy(alpha = 0.9f)
                    ),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                NavigationDrawerItem(
                    label = { Text("Logout", fontSize = 16.sp, fontWeight = FontWeight.Medium) },
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    selected = false,
                    onClick = {
                        onClose()
                        onLogout()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = Color(0xFFEF4444), // Minimal Red
                        unselectedIconColor = Color(0xFFEF4444)
                    ),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // --- Version Pill ---
        Surface(
            onClick = {
                (context as? MainActivity)?.manualUpdateCheck()
            },
            shape = RoundedCornerShape(50),
            color = Color.White.copy(alpha = 0.05f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 80.sdp())
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = "Check for updates",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Callyn v$version",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}