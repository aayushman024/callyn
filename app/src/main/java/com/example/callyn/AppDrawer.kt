package com.example.callyn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppDrawer(
    userName: String,
    onSync: () -> Unit,
    onLogout: () -> Unit,
    onClose: () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = Color(0xFF1E293B), // Dark Theme Background
        drawerContentColor = Color.White,
        modifier = Modifier.width(300.dp)
    ) {
        // --- Minimal Header ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 30.dp, start = 28.dp, end = 28.dp, bottom = 32.dp)
        ) {
            Text(
                text = "Welcome,",
                fontSize = 22.sp,
                color = Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = userName,
                fontSize = 32.sp,
                fontWeight = FontWeight.Light, // Aesthetic thin font
                color = Color.White,
                lineHeight = 40.sp
            )
        }

        // --- Drawer Items ---

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        Spacer(modifier = Modifier.height(16.dp))

        NavigationDrawerItem(
            label = { Text("Sync Contacts", fontSize = 16.sp, fontWeight = FontWeight.Medium) },
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
                onSync()
            },
            shape = RoundedCornerShape(12.dp),
            colors = NavigationDrawerItemDefaults.colors(
                unselectedContainerColor = Color.Transparent,
                unselectedTextColor = Color.White.copy(alpha = 0.9f),
                unselectedIconColor = Color.White.copy(alpha = 0.9f)
            ),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        // --- Minimal Footer ---
        Text(
            text = "Callyn v1.0",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.2f),
            letterSpacing = 1.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 32.dp)
        )
    }
}