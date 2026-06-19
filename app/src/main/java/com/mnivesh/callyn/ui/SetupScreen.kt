package com.mnivesh.callyn.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
import com.mnivesh.callyn.ui.theme.AppTheme
import com.mnivesh.callyn.utils.AppUtils.isXiaomiDevice

/**
 * Handles the Setup Screen logic for granting permissions and setting default dialer.
 */
@Composable
fun SetupScreen(
    isDefaultDialer: Boolean,
    hasAllPermissions: Boolean,
    missingPermissions: List<String>,
    onRequestPermissions: () -> Unit,
    onRequestDefaultDialer: () -> Unit
) {
    val context = LocalContext.current
    val openAppSettings = {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open settings", Toast.LENGTH_SHORT).show()
        }
    }

    val isDark = AppTheme.colors.isDark

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (isDark) {
                        listOf(ComposeColor(0xFF1E293B), ComposeColor(0xFF0F172A))
                    } else {
                        listOf(ComposeColor(0xFFE2E8F0), ComposeColor(0xFFF1F5F9))
                    }
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(24.sdp())
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Callyn",
                fontSize = 48.ssp(),
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(32.sdp()))

            if (!isDefaultDialer) {
                SetupCard(
                    "Set as Default",
                    "Required to make and receive calls.",
                    "Set Default",
                    onRequestDefaultDialer
                )

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    Spacer(modifier = Modifier.height(24.sdp()))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = ComposeColor(0xFFF59E0B).copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(16.sdp()),
                        border = BorderStroke(
                            1.sdp(),
                            ComposeColor(0xFFF59E0B).copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.sdp()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = ComposeColor(0xFFF59E0B),
                                modifier = Modifier.size(24.sdp())
                            )
                            Spacer(modifier = Modifier.height(8.sdp()))
                            Text(
                                text = "Blocked by Android?",
                                fontWeight = FontWeight.Bold,
                                color = ComposeColor(0xFFF59E0B),
                                fontSize = 16.ssp()
                            )
                            Spacer(modifier = Modifier.height(8.sdp()))
                            Text(
                                text = "If you are unable to set this app as default, you likely need to allow 'Restricted Settings'.",
                                color = AppTheme.colors.textPrimary.copy(alpha = 0.8f),
                                fontSize = 13.ssp(),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.sdp()))

                            OutlinedButton(
                                onClick = openAppSettings,
                                border = BorderStroke(
                                    1.sdp(),
                                    ComposeColor(0xFFF59E0B)
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = ComposeColor(0xFFF59E0B)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("1. Open Settings")
                            }

                            Spacer(modifier = Modifier.height(8.sdp()))
                            Text(
                                text = "Go to 'App Info' > Tap 3 dots (top right) > 'Allow restricted settings', then come back here.",
                                color = AppTheme.colors.textSecondary,
                                fontSize = 11.ssp(),
                                textAlign = TextAlign.Center,
                                lineHeight = 14.ssp()
                            )
                        }
                    }
                }

                if (isXiaomiDevice()) {
                    Spacer(modifier = Modifier.height(24.sdp()))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = ComposeColor(0xFFFF6900).copy(alpha = 0.12f)
                        ),
                        shape = RoundedCornerShape(16.sdp()),
                        border = BorderStroke(
                            1.sdp(),
                            ComposeColor(0xFFFF6900).copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.sdp()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = ComposeColor(0xFFFF6900),
                                modifier = Modifier.size(24.sdp())
                            )
                            Spacer(modifier = Modifier.height(8.sdp()))
                            Text(
                                text = "Xiaomi / POCO / Redmi Device Detected",
                                fontWeight = FontWeight.Bold,
                                color = ComposeColor(0xFFFF6900),
                                fontSize = 15.ssp(),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.sdp()))
                            Text(
                                text = "To receive incoming call screens, you must enable \"Display pop-up windows while running in background\" for Callyn.",
                                color = AppTheme.colors.textPrimary.copy(alpha = 0.8f),
                                fontSize = 13.ssp(),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.sdp()))
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                                            setClassName(
                                                "com.miui.securitycenter",
                                                "com.miui.permcenter.permissions.PermissionsEditorActivity"
                                            )
                                            putExtra("extra_pkgname", context.packageName)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // Fallback: open generic app settings
                                        try {
                                            context.startActivity(
                                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                    data = Uri.fromParts("package", context.packageName, null)
                                                }
                                            )
                                        } catch (ex: Exception) {
                                            Toast.makeText(context, "Open Settings > Apps > Callyn > Other Permissions manually", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.sdp()),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ComposeColor(0xFFFF6900)
                                )
                            ) {
                                Text(
                                    "2. Enable Pop-up Permission",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.ssp()
                                )
                            }
                            Spacer(modifier = Modifier.height(8.sdp()))
                            Text(
                                text = "Settings → Apps → Callyn → Other permissions → Display pop-up windows while running in background",
                                color = AppTheme.colors.textSecondary,
                                fontSize = 11.ssp(),
                                textAlign = TextAlign.Center,
                                lineHeight = 15.ssp()
                            )
                        }
                    }
                }

            } else if (!hasAllPermissions) {
                val formatted = if (missingPermissions.isNotEmpty()) {
                    missingPermissions.joinToString(", ") {
                        formatPermission(it)
                    }
                } else {
                    "Required for contacts & logs."
                }

                val description = "Missing: $formatted"

                SetupCard(
                    "Grant Permissions",
                    description,
                    "Grant",
                    onRequestPermissions
                )
            }
        }
    }
}

/**
 * Formats a permission string to a human-readable format.
 */
fun formatPermission(permission: String): String {
    return permission
        .removePrefix("android.permission.")
        .split("_")
        .joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
}

/**
 * Renders a setup card for SetupScreen.
 */
@Composable
fun SetupCard(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit
) {
    val isDark = AppTheme.colors.isDark
    val bgModifier = if (isDark) {
        Modifier.background(ComposeColor(0xFF12223E).copy(alpha = 0.6f), RoundedCornerShape(24.sdp()))
    } else {
        Modifier
            .background(AppTheme.colors.surface, RoundedCornerShape(24.sdp()))
            .border(1.sdp(), AppTheme.colors.border, RoundedCornerShape(24.sdp()))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(bgModifier)
            .padding(24.sdp()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            fontSize = 22.ssp(),
            fontWeight = FontWeight.Bold,
            color = AppTheme.colors.textPrimary,
            modifier = Modifier.padding(bottom = 12.sdp())
        )
        Text(
            text = description,
            fontSize = 14.ssp(),
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.sdp())
        )
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.sdp()),
            colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF3B82F6))
        ) {
            Text(buttonText, fontSize = 16.ssp(), fontWeight = FontWeight.SemiBold, color = ComposeColor.White)
        }
    }
}
