package com.mnivesh.callyn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mnivesh.callyn.api.PendingRequest
import com.mnivesh.callyn.ui.RequestsViewModel
import com.mnivesh.callyn.ui.RequestsViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalRequestsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as? CallynApplication

    // Auth & ViewModel Setup
    val authManager = remember { AuthManager(context) }
    val token = remember { authManager.getToken() }

    val viewModel: RequestsViewModel = viewModel(
        factory = RequestsViewModelFactory(application!!.repository)
    )

    val uiState by viewModel.uiState.collectAsState()

    // --- Local State for Dialogs & Progress ---
    var actionRequest by remember { mutableStateOf<PendingRequest?>(null) }
    var actionType by remember { mutableStateOf<String?>(null) } // "approved" or "rejected"
    var processingRequestId by remember { mutableStateOf<String?>(null) }

    // Initial Load
    LaunchedEffect(token) {
        if (token != null) {
            viewModel.loadRequests(token)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Personal Contact Requests", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E293B)))
                )
                .padding(innerPadding)
        ) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF3B82F6))
                }
            } else if (uiState.requests.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No pending requests", color = Color.White.copy(alpha = 0.5f), fontSize = 16.sp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.requests, key = { it._id }) { request ->
                        RequestCard(
                            request = request,
                            isProcessing = processingRequestId == request._id,
                            onApprove = {
                                actionRequest = request
                                actionType = "approved"
                            },
                            onDeny = {
                                actionRequest = request
                                actionType = "rejected"
                            }
                        )
                    }
                }
            }
        }
    }

    // --- Confirmation Dialog ---
    if (actionRequest != null && actionType != null) {
        val isApprove = actionType == "approved"
        val title = if (isApprove) "Approve Request?" else "Reject Request?"
        val message = if (isApprove)
            "Are you sure you want to allow access to this contact?"
        else
            "Are you sure you want to reject this request?"

        AlertDialog(
            onDismissRequest = {
                if (processingRequestId == null) {
                    actionRequest = null
                    actionType = null
                }
            },
            containerColor = Color(0xFF1E293B),
            title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text(message, color = Color.White.copy(alpha = 0.8f)) },
            confirmButton = {
                Button(
                    onClick = {
                        val reqId = actionRequest?._id ?: return@Button
                        val type = actionType ?: return@Button

                        // Close dialog and start processing
                        actionRequest = null
                        actionType = null
                        processingRequestId = reqId

                        if (token != null) {
                            viewModel.updateStatus(token, reqId, type)
                            // Note: ViewModel uses optimistic updates.
                            // We reset processing state after a short delay or let the item disappear.
                            processingRequestId = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isApprove) Color(0xFF10B981) else Color(0xFFEF4444)
                    )
                ) {
                    Text(if (isApprove) "Approve" else "Reject")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    actionRequest = null
                    actionType = null
                }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                }
            }
        )
    }
}

@Composable
fun RequestCard(
    request: PendingRequest,
    isProcessing: Boolean,
    onApprove: () -> Unit,
    onDeny: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header: RM Info
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF2563EB)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (request.requestedBy.isNotEmpty()) request.requestedBy.take(1).uppercase() else "R",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = request.requestedBy, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Client Info
            Text(
                text = "Requesting access for:",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = request.requestedContact,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Reason Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(text = "Reason:", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\"${request.reason}\"",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontStyle = FontStyle.Italic,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons or Loader
            if (isProcessing) {
                Box(modifier = Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFF3B82F6), strokeWidth = 2.dp)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = onDeny,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp),
                        elevation = ButtonDefaults.buttonElevation(0.dp)
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reject", color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Approve", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}