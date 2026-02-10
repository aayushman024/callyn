package com.mnivesh.callyn.screens.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.mnivesh.callyn.screens.RecentCallUiItem
import com.mnivesh.callyn.screens.formatTime
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp

@Composable
fun CallHistoryRow(log: RecentCallUiItem) {
    val icon = when {
        log.isMissed -> Icons.Default.CallMissed
        log.isIncoming -> Icons.Default.CallReceived
        else -> Icons.Default.CallMade
    }
    val iconColor =
        if (log.isMissed) Color(0xFFEF4444) else if (log.isIncoming) Color(0xFF10B981) else Color(
            0xFF60A5FA
        )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.sdp()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.sdp())
        )
        Spacer(modifier = Modifier.width(16.sdp()))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatTime(log.date),
                color = Color.White,
                fontSize = 14.ssp()
            )
            if (!log.simSlot.isNullOrBlank()) {
                Text(
                    text = log.simSlot,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.ssp()
                )
            }
        }
        Text(
            text = log.duration,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.ssp()
        )
    }
}

@Composable
fun ContactDetailRow(icon: ImageVector, label: String, value: String, labelColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.sdp()))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(10.sdp()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = labelColor, modifier = Modifier.size(20.sdp()))
        Spacer(modifier = Modifier.width(10.sdp()))
        Column {
            Text(
                label,
                fontSize = 11.ssp(),
                fontWeight = FontWeight.Medium,
                color = labelColor.copy(alpha = 0.8f)
            )
            Text(
                value.ifBlank { "N/A" },
                fontSize = 14.ssp(),
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}