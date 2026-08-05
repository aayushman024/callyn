package com.mnivesh.callyn.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Message
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mnivesh.callyn.managers.FeatureBadgeKey
import com.mnivesh.callyn.managers.FeatureBadgeManager
import com.mnivesh.callyn.managers.QuickReplyManager
import com.mnivesh.callyn.ui.theme.AppTheme
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditQuickRepliesScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val featureBadgeManager = remember { FeatureBadgeManager(context) }

    LaunchedEffect(Unit) {
        featureBadgeManager.markFeatureVisited(FeatureBadgeKey.QUICK_REPLIES)
    }

    var quickReplies by remember { mutableStateOf(QuickReplyManager.getQuickReplies(context)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newReplyText by remember { mutableStateOf("") }

    val isDark = AppTheme.colors.isDark
    val surfaceColor = AppTheme.colors.surface
    val textPrimary = AppTheme.colors.textPrimary
    val textSecondary = AppTheme.colors.textSecondary
    val primaryBlue = Color(0xFF3B82F6)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Edit Quick Replies",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.ssp(),
                        color = textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textPrimary
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            newReplyText = ""
                            showAddDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = primaryBlue,
                            modifier = Modifier.size(18.sdp())
                        )
                        Spacer(modifier = Modifier.width(4.sdp()))
                        Text(
                            text = "Add",
                            color = primaryBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.ssp()
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = surfaceColor
                )
            )
        },
        containerColor = surfaceColor
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (quickReplies.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.sdp()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Message,
                        contentDescription = null,
                        tint = textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.sdp())
                    )
                    Spacer(modifier = Modifier.height(16.sdp()))
                    Text(
                        "No Quick Replies Available",
                        fontSize = 16.ssp(),
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.sdp()))
                    Text(
                        "Tap the + button in the app bar to add your first quick reply.",
                        fontSize = 13.ssp(),
                        color = textSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.sdp(), vertical = 12.sdp()),
                    verticalArrangement = Arrangement.spacedBy(10.sdp())
                ) {
                    itemsIndexed(quickReplies) { index, reply ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDark) Color(0xFF1E293B) else AppTheme.colors.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.sdp()),
                            border = BorderStroke(1.sdp(), AppTheme.colors.border)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.sdp(), vertical = 14.sdp()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = reply,
                                    color = textPrimary,
                                    fontSize = 14.ssp(),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(12.sdp()))
                                IconButton(
                                    onClick = {
                                        quickReplies = QuickReplyManager.removeQuickReply(context, index)
                                    },
                                    modifier = Modifier.size(36.sdp())
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove quick reply",
                                        tint = Color(0xFFFF453A),
                                        modifier = Modifier.size(20.sdp())
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        val dialogBg = if (isDark) Color(0xFF1E293B) else AppTheme.colors.surface
        val isSaveEnabled = newReplyText.trim().isNotEmpty()

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = dialogBg,
            shape = RoundedCornerShape(16.sdp()),
            title = {
                Text(
                    text = "Add Custom Quick Reply",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = textPrimary,
                    fontSize = 18.ssp()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.sdp()),
                    verticalArrangement = Arrangement.spacedBy(8.sdp())
                ) {
                    OutlinedTextField(
                        value = newReplyText,
                        onValueChange = { newReplyText = it },
                        placeholder = {
                            Text(
                                "Type your message here...",
                                color = textSecondary.copy(alpha = 0.5f),
                                fontSize = 14.ssp()
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.sdp(), max = 180.sdp()),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            focusedContainerColor = if (isDark) Color(0xFF0F172A) else AppTheme.colors.surfaceVariant,
                            unfocusedContainerColor = if (isDark) Color(0xFF0F172A) else AppTheme.colors.surfaceVariant,
                            focusedBorderColor = primaryBlue,
                            unfocusedBorderColor = AppTheme.colors.border,
                            cursorColor = primaryBlue
                        ),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 14.ssp(),
                            fontWeight = FontWeight.Normal
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (isSaveEnabled) {
                                    quickReplies = QuickReplyManager.addQuickReply(context, newReplyText)
                                    showAddDialog = false
                                }
                            }
                        ),
                        shape = RoundedCornerShape(12.sdp())
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isSaveEnabled) {
                            quickReplies = QuickReplyManager.addQuickReply(context, newReplyText)
                            showAddDialog = false
                        }
                    },
                    enabled = isSaveEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryBlue,
                        disabledContainerColor = primaryBlue.copy(alpha = 0.3f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.sdp())
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddDialog = false },
                    shape = RoundedCornerShape(8.sdp())
                ) {
                    Text("Discard", color = textSecondary, fontWeight = FontWeight.Medium)
                }
            }
        )
    }
}
