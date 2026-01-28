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

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val drawerWidth = min(320.dp, screenWidth * 0.85f)
    val scrollState = rememberScrollState()

    ModalDrawerSheet(
        drawerContainerColor = Color(0xFF0B1220),
        drawerContentColor = Color.White,
        modifier = Modifier.width(drawerWidth)
            .systemBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header remains fixed at the top
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF020617), Color(0xFF0B1220), Color(0xFF111827))
                        )
                    )
                    .padding(top = 40.dp, bottom = 25.dp, start = 24.dp, end = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(70.dp)
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
                        fontSize = 26.sp,
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
                                null,
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

            // All items (Navigation + Actions + Version) are now in the scrollable list
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .systemBarsPadding()
                    .padding(vertical = 12.dp, horizontal = 14.dp)
            ) {

                @Composable
                fun drawerItem(
                    label: String,
                    icon: @Composable () -> Unit,
                    textColor: Color = Color.White.copy(alpha = 0.92f),
                    iconColor: Color = Color.White.copy(alpha = 0.92f),
                    containerColor: Color = Color.White.copy(alpha = 0.04f),
                    onClick: () -> Unit
                ) {
                    NavigationDrawerItem(
                        label = {
                            Text(text = label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        },
                        icon = icon,
                        selected = false,
                        onClick = {
                            onClose()
                            onClick()
                        },
                        shape = RoundedCornerShape(18.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = containerColor,
                            unselectedTextColor = textColor,
                            unselectedIconColor = iconColor,
                        ),
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .fillMaxWidth()
                    )
                }

                drawerItem(
                    label = "Sync Work Contacts",
                    icon = { Icon(Icons.Default.Refresh, null, Modifier.size(22.dp), tint = Color(0xFF38BDF8)) }
                ) {
                    Toast.makeText(context, "Syncing Work Contacts...", Toast.LENGTH_SHORT).show()
                    onSync()
                }

                drawerItem(
                    label = "Employee Directory",
                    icon = { Icon(Icons.Default.Badge, null, Modifier.size(22.dp), tint = Color(0xFFF472B6)) }
                ) { onShowDirectory() }

//                if(department != "Management") {
//                    drawerItem(
//                        label = "View Call Notes",
//                        icon = {
//                            Icon(
//                                Icons.Default.EditNote,
//                                null,
//                                Modifier.size(22.dp),
//                                tint = Color(0xFF673AB7)
//                            )
//                        }
//                    ) { onShowCallLogs() }
//                }

                if (department == "Management" || department == "IT Desk") {
                    drawerItem(
                        "User Status",
                        { Icon(Icons.Default.Group, null, Modifier.size(22.dp), tint = Color(0xFFA5B4FC)) }
                    ) { onShowUserDetails() }
                }

                if (department == "Management" || email == "aayushman@niveshonline.com" || email == "ishika@niveshonline.com" || email == "sagar@niveshonline.com" || email == "ved@niveshonline.com") {
                    drawerItem(
                        "View Call Logs",
                        { Icon(Icons.Default.List, null, Modifier.size(22.dp), tint = Color(0xFF34D399)) }
                    ) { onShowCallLogs() }
                }

                drawerItem(
                    "Personal Contact Requests",
                    { Icon(Icons.Default.AssignmentInd, null, Modifier.size(22.dp), tint = Color(0xFFFACC15)) }
                ) { onShowRequests() }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 8.dp))
                Spacer(modifier = Modifier.height(16.dp))

                // Logout moved into the list
                drawerItem(
                    label = "Logout",
                    textColor = Color(0xFFF44336),
                    iconColor = Color(0xFFF44336),
                    containerColor = Color(0xFFF44336).copy(alpha = 0.1f),
                    icon = { Icon(Icons.AutoMirrored.Filled.Logout, null, Modifier.size(22.dp)) }
                ) { onLogout() }

                Spacer(modifier = Modifier.height(24.dp))

                // Version info at the end of the scrollable list
                Surface(
                    onClick = { (context as? MainActivity)?.manualUpdateCheck() },
                    shape = RoundedCornerShape(30),
                    color = Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.SystemUpdate,
                            null,
                            tint = Color(0xFF93C5FD),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Callyn v$version",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Extra padding for bottom visibility
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}