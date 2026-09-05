package com.mnivesh.callyn.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import com.mnivesh.callyn.managers.HandsFreeConnectionState
import com.mnivesh.callyn.managers.HandsFreeManager
import com.mnivesh.callyn.ui.theme.sdp

@Composable
fun HandsFreeStatusIndicator(modifier: Modifier = Modifier) {
    val connectionState by HandsFreeManager.connectionState.collectAsState()
    if (connectionState != HandsFreeConnectionState.CONNECTED) {
        return
    }

    val transition = rememberInfiniteTransition(label = "hands-free-status")
    val dotAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hands-free-pulse"
    )

    Icon(
        imageVector = Icons.Default.HeadsetMic,
        contentDescription = "Hands-free active",
        tint = Color(0xFF10B981),
        modifier = modifier
            .size(18.sdp())
            .alpha(dotAlpha)
    )
}