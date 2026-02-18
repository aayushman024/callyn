package com.mnivesh.callyn.tabs

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox // [!code ++]
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnivesh.callyn.components.EmptyStateCard
import com.mnivesh.callyn.components.ErrorCard
import com.mnivesh.callyn.components.getColorForName
import com.mnivesh.callyn.components.getInitials
import com.mnivesh.callyn.db.CrmContact
import com.mnivesh.callyn.viewmodels.CrmUiState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class) // [!code ++]
@Composable
fun CrmTabContent(
    uiState: CrmUiState,
    searchQuery: String,
    onContactSelected: (CrmContact) -> Unit,
    onRefresh: () -> Unit // [!code ++] Added callback
) {
    // --- Filters ---
    var selectedFilter by remember { mutableStateOf("Tickets") } // Default
    val filters = listOf("Tickets", "Investment_leads", "Insurance_Leads")

    // --- Data Selection Logic ---
    val contactsToDisplay = remember(uiState, selectedFilter, searchQuery) {
        val baseList = when (selectedFilter) {
            "Tickets" -> uiState.tickets
            "Investment_leads" -> uiState.investmentLeads
            "Insurance_Leads" -> uiState.insuranceLeads
            else -> emptyList()
        }

        if (searchQuery.isBlank()) {
            baseList
        } else {
            baseList.filter {
                it.name.contains(searchQuery, true) ||
                        it.number.contains(searchQuery) ||
                        it.recordId.contains(searchQuery, true)
            }
        }
    }

    // --- 45 Second Progress Bar Logic ---
    var currentProgress by remember { mutableFloatStateOf(0f) }

    // Reset progress when loading starts
    LaunchedEffect(uiState.isLoading) {
        if (uiState.isLoading) {
            currentProgress = 0f
            val startTime = System.currentTimeMillis()
            val duration = 90000L // 90 Seconds

            while (currentProgress < 0.95f) {
                val elapsed = System.currentTimeMillis() - startTime
                val rawProgress = elapsed.toFloat() / duration
                currentProgress = rawProgress.coerceAtMost(0.95f) // Cap at 95% until actually done
                delay(100) // Update every 100ms
            }
        } else {
            currentProgress = 1f // Jump to completion
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = currentProgress,
        animationSpec = tween(durationMillis = 500, easing = LinearEasing),
        label = "progress"
    )

    // [!code ++] Wrap Content in PullToRefreshBox
    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // --- Loader Section ---
            if (uiState.isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.sdp(), vertical = 8.sdp())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Refreshing CRM Data of last 3 months...",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.ssp()
                        )
                        Text(
                            text = "${(animatedProgress * 100).toInt()}%",
                            color = Color(0xFF3B82F6),
                            fontSize = 12.ssp(),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.sdp()))
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.sdp())
                            .clip(RoundedCornerShape(3.sdp())),
                        color = Color(0xFF3B82F6),
                        trackColor = Color.White.copy(alpha = 0.1f),
                    )
                }
            }

            // --- Filter Chips ---
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 1.sdp(), vertical = 4.sdp()),
                horizontalArrangement = Arrangement.spacedBy(8.sdp())
            ) {
                items(filters) { filter ->
                    val isSelected = selectedFilter == filter
                    val label = filter.replace("_", " ").replace("Leads", "Leads") // Format text
                    val count = when (filter) {
                        "Tickets" -> uiState.tickets.size
                        "Investment_leads" -> uiState.investmentLeads.size
                        "Insurance_Leads" -> uiState.insuranceLeads.size
                        else -> 0
                    }
                    val displayCount = when {
                        count > 999 -> "999+"
                        else -> count.toString()
                    }

                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    label,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(6.sdp()))
                                // Count Badge
                                Box(
                                    modifier = Modifier
                                        .size(24.sdp())
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) Color.White
                                            else Color.White.copy(alpha = 0.15f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = displayCount,
                                        fontSize = when {
                                            displayCount.length >= 4 -> 8.ssp()
                                            displayCount.length == 3 -> 9.ssp()
                                            else -> 11.ssp()
                                        },
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        color = if (isSelected)
                                            Color(0xFF3B82F6)
                                        else
                                            Color.White
                                    )
                                }
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF3B82F6),
                            selectedLabelColor = Color.White,
                            containerColor = Color.White.copy(alpha = 0.1f),
                            labelColor = Color.White.copy(alpha = 0.7f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = Color.Transparent,
                            enabled = true,
                            selected = isSelected
                        ),
                        shape = RoundedCornerShape(50)
                    )
                }
            }

            // --- Content List ---
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 1.sdp(), vertical = 8.sdp()),
                verticalArrangement = Arrangement.spacedBy(12.sdp())
            ) {
                if (uiState.errorMessage != null) {
                    // [!code ++] Render Error as a full-screen list item
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            ErrorCard(uiState.errorMessage)
                        }
                    }
                } else if (contactsToDisplay.isEmpty() && !uiState.isLoading) {
                    // [!code ++] Render Empty State as a full-screen list item
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyStateCard(
                                if (searchQuery.isNotEmpty()) "No matches found" else "No $selectedFilter found",
                                Icons.Default.FolderOpen
                            )
                        }
                    }
                } else {
                    // [!code ++] Standard Data
                    items(contactsToDisplay, key = { it.localId }) { contact ->
                        CrmContactCard(contact, onClick = { onContactSelected(contact) })
                    }
                }
            }
        }
    }
}

@Composable
fun CrmContactCard(
    contact: CrmContact,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(16.sdp()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.sdp()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(50.sdp())
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                getColorForName(contact.name),
                                getColorForName(contact.name).copy(alpha = 0.5f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    getInitials(contact.name),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.ssp()
                )
            }

            Spacer(modifier = Modifier.width(16.sdp()))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.ssp(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.sdp()))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ConfirmationNumber,
                        null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(12.sdp())
                    )
                    Spacer(modifier = Modifier.width(4.sdp()))
                    Text(
                        text = "ID: ${contact.recordId}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.ssp()
                    )
                }

                if (contact.product != null) {
                    Spacer(modifier = Modifier.height(2.sdp()))
                    Text(
                        text = contact.product,
                        color = Color(0xFF60A5FA), // Light Blue
                        fontSize = 11.ssp(),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Arrow
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f)
            )
        }
    }
}