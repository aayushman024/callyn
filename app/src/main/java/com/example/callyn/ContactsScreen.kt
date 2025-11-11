package com.example.callyn

import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
// MODIFICATION: Import Pager composables
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
// END MODIFICATION
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.callyn.db.AppContact
import com.example.callyn.ui.ContactsViewModel
import com.example.callyn.ui.ContactsViewModelFactory
import kotlinx.coroutines.launch
import com.example.callyn.CallynApplication
import com.example.callyn.AuthManager
// MODIFICATION: Import Dispatchers for background contact query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
// END MODIFICATION

// --- Data class for device contacts ---
data class DeviceContact(
    val id: String,
    val name: String,
    val number: String
)

// --- Helper functions ---

private fun getColorForName(name: String): Color {
    val palette = listOf(
        Color(0xFF6366F1), Color(0xFFEC4899), Color(0xFF8B5CF6),
        Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFFEF4444),
        Color(0xFF3B82F6), Color(0xFF14B8A6), Color(0xFFF97316)
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

// --- MODIFICATION: fetchDeviceContacts function removed ---
// This logic is now inside the ContactsScreen composable

// --- Main Contact Screen Composable ---

// MODIFICATION: Add OptIn for Pager
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(
    userName: String,
    onContactClick: (String) -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as? CallynApplication

    if (application == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Application context not available.")
        }
        return
    }

    val viewModel: ContactsViewModel = viewModel(
        factory = ContactsViewModelFactory(application.repository)
    )

    var searchQuery by remember { mutableStateOf("") }

    // MODIFICATION: Set up Pager state
    val pagerState = rememberPagerState(initialPage = 0) { 2 } // 2 tabs
    val selectedTabIndex = pagerState.currentPage
    // END MODIFICATION

    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasContactsPermission = isGranted
    }

    val authManager = remember { AuthManager(context) }
    val token by remember(authManager) { mutableStateOf(authManager.getToken()) }

    val uiState by viewModel.uiState.collectAsState()
    val workContacts by viewModel.localContacts.collectAsState()

    // --- MODIFICATION: Device contacts logic moved here ---
    var deviceContacts by remember { mutableStateOf<List<DeviceContact>>(emptyList()) }
    val contentResolver = LocalContext.current.contentResolver

    LaunchedEffect(hasContactsPermission) {
        if (hasContactsPermission) {
            withContext(Dispatchers.IO) { // Run query on a background thread
                val contactsList = mutableListOf<DeviceContact>()
                val cursor = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    null,
                    null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                )

                cursor?.use {
                    val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    while (it.moveToNext()) {
                        val id = it.getString(idIndex)
                        val name = it.getString(nameIndex) ?: "Unknown"
                        val number = it.getString(numberIndex)?.replace("\\s".toRegex(), "") ?: ""

                        if (number.isNotEmpty()) {
                            contactsList.add(DeviceContact(id, name, number))
                        }
                    }
                }
                deviceContacts = contactsList.distinctBy { it.number }
            }
        } else {
            deviceContacts = emptyList()
        }
    }
    // --- END MODIFICATION ---


    LaunchedEffect(key1 = userName, key2 = token) {
        token?.let {
            viewModel.onRefresh(it, userName)
        }
    }

    // Filter based on selected tab
    val filteredWorkContacts = remember(searchQuery, workContacts) {
        if (searchQuery.isBlank()) {
            workContacts
        } else {
            workContacts.filter {
                it.name.contains(searchQuery, true) || it.number.contains(searchQuery)
            }
        }
    }

    val filteredDeviceContacts = remember(searchQuery, deviceContacts) {
        if (searchQuery.isBlank()) {
            deviceContacts
        } else {
            deviceContacts.filter {
                it.name.contains(searchQuery, true) || it.number.contains(searchQuery)
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope() // This scope is now used for tabs too
    var selectedWorkContact by remember { mutableStateOf<AppContact?>(null) }
    var selectedDeviceContact by remember { mutableStateOf<DeviceContact?>(null) }

    // Animation values
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F172A),
                            Color(0xFF1E293B)
                        )
                    )
                )
        ) {
            // ----- Modern Header with Glassmorphism -----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF3B82F6).copy(alpha = 0.3f),
                                Color(0xFF8B5CF6).copy(alpha = 0.3f)
                            )
                        )
                    )
                    .padding(top = 40.dp, bottom = 20.dp, start = 24.dp, end = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Hey, $userName 👋",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = (-0.5).sp
                        )
                        val totalCount = if (selectedTabIndex == 0) deviceContacts.size else workContacts.size
                        Text(
                            text = "$totalCount ${if (selectedTabIndex == 0) "personal" else "work"} contact${if (totalCount != 1) "s" else ""}",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable {
                                token?.let {
                                    viewModel.onRefresh(it, userName)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // ----- Modern Tab Row with Glassmorphism -----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TabButton(
                        text = "Personal",
                        count = deviceContacts.size,
                        isSelected = selectedTabIndex == 0,
                        onClick = {
                            // MODIFICATION: Animate pager to page 0
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(0)
                            }
                            // END MODIFICATION
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TabButton(
                        text = "Work",
                        count = workContacts.size,
                        isSelected = selectedTabIndex == 1,
                        onClick = {
                            // MODIFICATION: Animate pager to page 1
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(1)
                            }
                            // END MODIFICATION
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ----- Modern Search Bar -----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    placeholder = {
                        Text(
                            "Search ${if (selectedTabIndex == 0) "personal" else "work"} contacts",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 15.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.White.copy(alpha = 0.1f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ----- MODIFICATION: Replaced Box/when with HorizontalPager -----
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.Top // This ensures lists align to the top
            ) { page ->
                when (page) {
                    0 -> {
                        // Personal Contacts Tab
                        if (!hasContactsPermission) {
                            PermissionRequiredCard(
                                onGrantPermission = {
                                    permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                }
                            )
                        } else if (filteredDeviceContacts.isEmpty()) {
                            EmptyStateCard(
                                message = if (searchQuery.isBlank())
                                    "No contacts found on your device"
                                else
                                    "No matching personal contacts",
                                icon = Icons.Default.PersonOff
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filteredDeviceContacts, key = { it.id }) { contact ->
                                    ModernDeviceContactCard(contact, onClick = {
                                        selectedDeviceContact = contact
                                        coroutineScope.launch { sheetState.show() }
                                    })
                                }
                            }
                        }
                    }
                    1 -> {
                        // Work Contacts Tab
                        if (uiState.isLoading && workContacts.isEmpty()) {
                            LoadingCard(shimmerOffset)
                        } else if (uiState.errorMessage != null) {
                            ErrorCard(uiState.errorMessage!!)
                        } else if (filteredWorkContacts.isEmpty()) {
                            EmptyStateCard(
                                message = if (searchQuery.isBlank())
                                    "No work contacts synced yet"
                                else
                                    "No matching work contacts",
                                icon = Icons.Default.BusinessCenter
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filteredWorkContacts, key = { it.id }) { contact ->
                                    ModernWorkContactCard(contact, onClick = {
                                        selectedWorkContact = contact
                                        coroutineScope.launch { sheetState.show() }
                                    })
                                }
                            }
                        }
                    }
                }
            }
            // --- END MODIFICATION ---
        }
    }

    // ----- Modal Bottom Sheet for Work Contact -----
    if (selectedWorkContact != null) {
        ModernBottomSheet(
            contact = selectedWorkContact!!,
            sheetState = sheetState,
            onDismiss = {
                coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                    selectedWorkContact = null
                }
            },
            onCall = {
                coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                    onContactClick(selectedWorkContact!!.number)
                    selectedWorkContact = null
                }
            },
            isWorkContact = true
        )
    }

    // ----- Modal Bottom Sheet for Device Contact -----
    if (selectedDeviceContact != null) {
        ModernDeviceBottomSheet(
            contact = selectedDeviceContact!!,
            sheetState = sheetState,
            onDismiss = {
                coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                    selectedDeviceContact = null
                }
            },
            onCall = {
                coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                    onContactClick(selectedDeviceContact!!.number)
                    selectedDeviceContact = null
                }
            }
        )
    }
}

// ----- Modern Tab Button -----
@Composable
private fun TabButton(
    text: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "scale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) {
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6))
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.Transparent)
                    )
                }
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isSelected) Color.White.copy(alpha = 0.25f)
                        else Color.White.copy(alpha = 0.1f)
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = count.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

// ----- Modern Work Contact Card -----
@Composable
private fun ModernWorkContactCard(contact: AppContact, onClick: () -> Unit) {
    val avatarColor = getColorForName(contact.name)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                avatarColor,
                                avatarColor.copy(alpha = 0.7f)
                            )
                        )
                    ),
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
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        Icons.Default.BusinessCenter,
                        contentDescription = null,
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Work",
                        fontSize = 13.sp,
                        color = Color(0xFF60A5FA),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF10B981), Color(0xFF059669))
                        )
                    )
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Call,
                    contentDescription = "Call",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ----- Modern Device Contact Card -----
@Composable
private fun ModernDeviceContactCard(contact: DeviceContact, onClick: () -> Unit) {
    val avatarColor = getColorForName(contact.name)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                avatarColor,
                                avatarColor.copy(alpha = 0.7f)
                            )
                        )
                    ),
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
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = contact.number,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF10B981), Color(0xFF059669))
                        )
                    )
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Call,
                    contentDescription = "Call",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ----- Permission Required Card -----
@Composable
private fun PermissionRequiredCard(onGrantPermission: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ContactPage,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Contact Permission Required",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Allow access to view your personal contacts",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onGrantPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3B82F6)
                )
            ) {
                Text(
                    text = "Grant Permission",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ----- Empty State Card -----
@Composable
private fun EmptyStateCard(message: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = message,
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
    }
}

// ----- Loading Card -----
@Composable
private fun LoadingCard(shimmerOffset: Float) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        repeat(5) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.05f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.4f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                        )
                    }
                }
            }
        }
    }
}

// ----- Error Card -----
@Composable
private fun ErrorCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFEF4444).copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                color = Color(0xFFEF4444),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ----- Modern Bottom Sheet for Work Contact -----
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernBottomSheet(
    contact: AppContact,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onCall: () -> Unit,
    isWorkContact: Boolean
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E293B),
        contentColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                getColorForName(contact.name),
                                getColorForName(contact.name).copy(alpha = 0.7f)
                            )
                        )
                    )
                    .shadow(8.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getInitials(contact.name),
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = contact.name,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(
                    if (isWorkContact) Icons.Default.BusinessCenter else Icons.Default.Person,
                    contentDescription = null,
                    tint = if (isWorkContact) Color(0xFF60A5FA) else Color(0xFF10B981),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isWorkContact) "Work Contact" else "Personal Contact",
                    fontSize = 15.sp,
                    color = if (isWorkContact) Color(0xFF60A5FA) else Color(0xFF10B981),
                    fontWeight = FontWeight.Medium
                )
            }

            if (!isWorkContact) {
                Text(
                    text = contact.number,
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onCall,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981)
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 8.dp
                )
            ) {
                Icon(
                    Icons.Default.Call,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Call Now",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ----- Modern Bottom Sheet for Device Contact -----
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernDeviceBottomSheet(
    contact: DeviceContact,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onCall: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E293B),
        contentColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                getColorForName(contact.name),
                                getColorForName(contact.name).copy(alpha = 0.7f)
                            )
                        )
                    )
                    .shadow(8.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getInitials(contact.name),
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = contact.name,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Personal Contact",
                    fontSize = 15.sp,
                    color = Color(0xFF10B981),
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = contact.number,
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onCall,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981)
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 8.dp
                )
            ) {
                Icon(
                    Icons.Default.Call,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Call Now",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}