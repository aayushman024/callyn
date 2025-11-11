package com.example.callyn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.callyn.db.AppContact
import com.example.callyn.ui.ContactsViewModel
import com.example.callyn.ui.ContactsViewModelFactory
import kotlinx.coroutines.launch
import com.example.callyn.CallynApplication
import com.example.callyn.AuthManager

// --- Helper functions (can be moved) ---

private fun getColorForName(name: String): Color {
    val palette = listOf(
        Color(0xFF6366F1), Color(0xFFEC4899), Color(0xFF8B5CF6),
        Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFFEF4444),
        Color(0xFF3B82F6), Color(0xFF14B8A6)
    )
    return palette[kotlin.math.abs(name.hashCode()) % palette.size]
}

private fun getInitials(name: String): String {
    return name.split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { name.take(1).uppercase() }
}

// --- Main Contact Screen Composable ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    userName: String, // 1. Received from MainActivity
    onContactClick: (String) -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as? CallynApplication

    if (application == null) {
        // Show a loading or error state if the application is not available
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Application context not available.")
        }
        return
    }

    // 2. Initialize the ViewModel safely
    val viewModel: ContactsViewModel = viewModel(
        factory = ContactsViewModelFactory(application.repository)
    )

    var searchQuery by remember { mutableStateOf("") }

    // 3. Get the token for API calls
    val authManager = remember { AuthManager(context) }
    val token by remember(authManager) { mutableStateOf(authManager.getToken()) }

    // 4. Observe the UI state and the local contacts flow from the ViewModel
    val uiState by viewModel.uiState.collectAsState()
    val localContacts by viewModel.localContacts.collectAsState()

    // 5. Trigger an initial refresh when the screen first loads
    LaunchedEffect(key1 = userName, key2 = token) {
        token?.let {
            viewModel.onRefresh(it, userName)
        }
    }

    // 6. Search logic now uses the localContacts list
    val filteredContacts = remember(searchQuery, localContacts) {
        if (searchQuery.isBlank()) {
            localContacts
        } else {
            localContacts.filter {
                it.name.contains(searchQuery, true) ||
                        it.number.contains(searchQuery)
            }
        }
    }

    // --- Bottom Sheet state ---
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    var selectedContact by remember { mutableStateOf<AppContact?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB))
    ) {
        // ----- Gradient Header with REFRESH button -----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF2563EB), Color(0xFF1E3A8A))
                    )
                )
                .padding(top = 56.dp, bottom = 28.dp, start = 20.dp, end = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Hi, $userName", // Show logged-in user's name
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${filteredContacts.size} contact${if (filteredContacts.size != 1) "s" else ""}",
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                // --- 7. REFRESH BUTTON ---
                IconButton(onClick = {
                    token?.let {
                        viewModel.onRefresh(it, userName)
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Contacts",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // ----- Search Bar (Unchanged) -----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(6.dp, RoundedCornerShape(30.dp))
                    .clip(RoundedCornerShape(30.dp)),
                placeholder = { Text("Search contacts...", color = Color.Gray) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF6B7280))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                        }
                    }
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    disabledContainerColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ----- 8. Contacts List Area (Updated) -----
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            // Show loading spinner *over* the list if refreshing
            if (uiState.isLoading && filteredContacts.isEmpty()) {
                CircularProgressIndicator()
            } else if (uiState.errorMessage != null) {
                uiState.errorMessage?.let { errorMsg ->
                    Text(
                        text = errorMsg,
                        color = Color.Red,
                        fontSize = 16.sp
                    )
                }
            } else if (filteredContacts.isEmpty()) {
                Text(
                    text = if (searchQuery.isNotBlank()) "No results found" else "You have no contacts.",
                    color = Color(0xFF6B7280),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(filteredContacts, key = { it.id }) { contact ->
                        ModernContactCard(contact, onClick = {
                            selectedContact = contact
                            coroutineScope.launch { sheetState.show() }
                        })
                    }
                }
            }
        }
    }

    // ----- Modal Bottom Sheet (Updated to use AppContact) -----
    if (selectedContact != null) {
        ModalBottomSheet(
            onDismissRequest = {
                coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                    selectedContact = null
                }
            },
            sheetState = sheetState,
            containerColor = Color(0xFF1C1C1E),
            contentColor = Color.White
        ) {
            val contact = selectedContact
            if (contact != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(getColorForName(contact.name)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = getInitials(contact.name),
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = contact.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Text(
                        text = "Work Contact",
                        fontSize = 14.sp,
                        color = Color(0xFF60A5FA),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = contact.number,
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                                onContactClick(contact.number)
                                selectedContact = null
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(28.dp)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981)
                        )
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Call", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

// ----- Contact Card (Updated to take AppContact) -----
@Composable
private fun ModernContactCard(contact: AppContact, onClick: () -> Unit) {
    val avatarColor = getColorForName(contact.name)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getInitials(contact.name),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF111827),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .background(Color(0xFF2563EB).copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Work",
                        fontSize = 12.sp,
                        color = Color(0xFF2563EB),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981)),
            ) {
                Icon(
                    Icons.Default.Call,
                    contentDescription = "Call ${contact.name}",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
