package com.mnivesh.callyn.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mnivesh.callyn.managers.WhatsNewFeature
import com.mnivesh.callyn.managers.WhatsNewVersion
import com.mnivesh.callyn.ui.theme.AppTheme
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp

@Composable
fun WhatsNewDialog(
    versionInfo: WhatsNewVersion,
    onDismiss: () -> Unit
) {
    val isDark = AppTheme.colors.isDark
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.sdp(), vertical = 24.sdp()),
            shape = RoundedCornerShape(24.sdp()),
            color = AppTheme.colors.surface,
            border = BorderStroke(1.sdp(), AppTheme.colors.border),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.sdp())
            ) {
                // Top Header Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.sdp())
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF2563EB), Color(0xFF38BDF8))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.sdp())
                        )
                    }

                    Spacer(modifier = Modifier.width(12.sdp()))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "What's New",
                            fontSize = 18.ssp(),
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = "Version ${versionInfo.versionName}",
                            fontSize = 12.ssp(),
                            fontWeight = FontWeight.Medium,
                            color = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.sdp()))

                // Release highlights list
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 340.sdp())
                        .verticalScroll(scrollState)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.sdp()),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        versionInfo.features.forEach { feature ->
                            WhatsNewFeatureItem(feature = feature, isDark = isDark)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.sdp()))

                // Bottom Action Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.sdp()),
                    shape = RoundedCornerShape(14.sdp()),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) Color(0xFF2563EB) else Color(0xFF3B82F6),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Got it",
                        fontSize = 14.ssp(),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun WhatsNewFeatureItem(
    feature: WhatsNewFeature,
    isDark: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.sdp())
                .size(20.sdp())
                .clip(CircleShape)
                .background(
                    if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.5f)
                    else Color(0xFFEFF6FF)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB),
                modifier = Modifier.size(12.sdp())
            )
        }

        Spacer(modifier = Modifier.width(12.sdp()))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = feature.title,
                fontSize = 14.ssp(),
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(2.sdp()))
            Text(
                text = feature.description,
                fontSize = 12.ssp(),
                lineHeight = 16.ssp(),
                color = AppTheme.colors.textSecondary
            )
        }
    }
}
