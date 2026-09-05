package com.mnivesh.callyn.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.mnivesh.callyn.managers.FeatureBadgeKey
import com.mnivesh.callyn.managers.FeatureBadgeManager
import com.mnivesh.callyn.managers.HandsFreeManager
import com.mnivesh.callyn.managers.HandsFreeConnectionState
import com.mnivesh.callyn.services.HandsFreeService
import com.mnivesh.callyn.components.HandsFreeStatusIndicator
import com.mnivesh.callyn.ui.theme.AppTheme
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
import androidx.core.content.ContextCompat
import android.content.Intent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandsFreeScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val featureBadgeManager = remember { FeatureBadgeManager(context) }

    LaunchedEffect(Unit) {
        featureBadgeManager.markFeatureVisited(FeatureBadgeKey.HANDS_FREE)
    }

    val connectionState by HandsFreeManager.connectionState.collectAsState()
    val isEnabled = connectionState == HandsFreeConnectionState.CONNECTING ||
        connectionState == HandsFreeConnectionState.CONNECTED

    Scaffold(
        containerColor = AppTheme.colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Hands-free calling",
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.colors.textPrimary
                        )
                        HandsFreeStatusIndicator(modifier = Modifier.padding(start = 8.sdp()))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AppTheme.colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppTheme.colors.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = 20.sdp(), vertical = 12.sdp()),
            verticalArrangement = Arrangement.spacedBy(16.sdp())
        ) {
            Card(
                shape = RoundedCornerShape(22.sdp()),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground)
            ) {
                Column(modifier = Modifier.padding(20.sdp())) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.HeadsetMic,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(30.sdp())
                            )
                            Spacer(modifier = Modifier.size(14.sdp()))
                            Column {
                                Text(
                                    text = "Hands-free mode",
                                    color = AppTheme.colors.textPrimary,
                                    fontSize = 17.ssp(),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.sdp()))
                                Text(
                                    text = when (connectionState) {
                                        HandsFreeConnectionState.CONNECTED -> "Ready for Operations calls"
                                        HandsFreeConnectionState.CONNECTING -> "Connecting to calling service..."
                                        HandsFreeConnectionState.UNAVAILABLE -> "Hands-free unavailable"
                                        HandsFreeConnectionState.DISABLED -> "Currently turned off"
                                    },
                                    color = if (connectionState == HandsFreeConnectionState.CONNECTED) {
                                        Color(0xFF10B981)
                                    } else {
                                        AppTheme.colors.textSecondary
                                    },
                                    fontSize = 12.ssp()
                                )
                            }
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { enabled ->
                                val serviceIntent = Intent(context, HandsFreeService::class.java).apply {
                                    action = if (enabled) {
                                        HandsFreeService.ACTION_START
                                    } else {
                                        HandsFreeService.ACTION_STOP
                                    }
                                }
                                if (enabled) {
                                    HandsFreeManager.setConnectionState(HandsFreeConnectionState.CONNECTING)
                                    try {
                                        ContextCompat.startForegroundService(context, serviceIntent)
                                    } catch (error: Exception) {
                                        HandsFreeManager.setConnectionState(HandsFreeConnectionState.UNAVAILABLE)
                                    }
                                } else {
                                    context.startService(serviceIntent)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF10B981),
                                uncheckedThumbColor = AppTheme.colors.textSecondary,
                                uncheckedTrackColor = AppTheme.colors.surfaceVariant
                            )
                        )
                    }
                }
            }

            InfoCard(
                icon = Icons.Default.Info,
                title = "How it works",
                body = "Use the Call button on the Operations Dashboard to automatically call the client using your mobile phone."
            )

            Card(
                shape = RoundedCornerShape(18.sdp()),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surfaceVariant.copy(alpha = 0.55f))
            ) {
                Column(modifier = Modifier.padding(18.sdp())) {
                    Text(
                        text = "For the best experience",
                        color = AppTheme.colors.textPrimary,
                        fontSize = 15.ssp(),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.sdp()))
                    Text(
                        text = "We recommend using headphones for a smoother calling experience. You may turn off your screen after enabling hands-free mode.",
                        color = AppTheme.colors.textSecondary,
                        fontSize = 13.ssp(),
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                    )
                }
            }

            HorizontalDivider(color = AppTheme.colors.border)
            Text(
                text = when (connectionState) {
                    HandsFreeConnectionState.CONNECTED -> "Connected to the calling service."
                    HandsFreeConnectionState.CONNECTING -> "Connecting to the calling service."
                    HandsFreeConnectionState.UNAVAILABLE -> "The calling service is unavailable. Turn the switch on to retry."
                    HandsFreeConnectionState.DISABLED -> "Hands-free mode is disconnected."
                },
                color = AppTheme.colors.textSecondary,
                fontSize = 11.ssp(),
                modifier = Modifier.padding(horizontal = 4.sdp())
            )
        }
    }
}

@Composable
private fun InfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String
) {
    Card(
        shape = RoundedCornerShape(18.sdp()),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBackground)
    ) {
        Row(modifier = Modifier.padding(18.sdp())) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFA78BFA),
                modifier = Modifier.size(24.sdp())
            )
            Spacer(modifier = Modifier.size(14.sdp()))
            Column {
                Text(
                    text = title,
                    color = AppTheme.colors.textPrimary,
                    fontSize = 15.ssp(),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.sdp()))
                Text(
                    text = body,
                    color = AppTheme.colors.textSecondary,
                    fontSize = 13.ssp()
                )
            }
        }
    }
}
