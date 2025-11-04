package com.example.callyn

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ----- Color generator -----
private fun getColorForName(name: String): Color {
    val palette = listOf(
        Color(0xFF6366F1),
        Color(0xFFEC4899),
        Color(0xFF8B5CF6),
        Color(0xFF10B981),
        Color(0xFFF59E0B),
        Color(0xFFEF4444),
        Color(0xFF3B82F6),
        Color(0xFF14B8A6)
    )
    return palette[name.hashCode().mod(palette.size)]
}

// ----- Initials extractor -----
private fun getInitials(name: String): String {
    return name.split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { name.take(1).uppercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(onContactClick: (String) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("My Contacts") }
    val allContacts = remember { ContactRepository.getAllContacts() }

    val filteredContacts = remember(searchQuery, selectedFilter, allContacts) {
        val baseList = when (selectedFilter) {
            "Work" -> allContacts.filter { it.type == "work" }
            else -> allContacts.filter { it.type == "default" }
        }
        if (searchQuery.isBlank()) baseList
        else baseList.filter { it.name.contains(searchQuery, true) }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    var selectedContact by remember { mutableStateOf<AppContact?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB))
    ) {
        // ----- Gradient Header -----
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
            Column {
                Text(
                    text = "Callyn",
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
        }

        // ----- Search Bar -----
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

        // ----- Filter buttons -----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val filters = listOf("My Contacts", "Work")
            filters.forEach { label ->
                val isSelected = selectedFilter == label
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilter = label },
                    label = { Text(label) },
                    leadingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.White,
                        labelColor = Color(0xFF374151),
                        selectedContainerColor = Color.Blue.copy(alpha = 0.8f),
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ----- Contacts List -----
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(filteredContacts, key = { _, c -> c.number }) { index, contact ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 })
                ) {
                    ModernContactCard(contact, onClick = {
                        selectedContact = contact
                        coroutineScope.launch { sheetState.show() }
                    }, index)
                }
            }

            if (filteredContacts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No results found",
                                color = Color(0xFF6B7280),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Try searching a different name",
                                color = Color(0xFF9CA3AF),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // ----- Modal Bottom Sheet -----
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
            val contact = selectedContact!!
            val avatarColor = getColorForName(contact.name)
            val gradient = Brush.linearGradient(
                listOf(
                    avatarColor.copy(alpha = 0.9f),
                    avatarColor.copy(alpha = 0.6f)
                )
            )

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
                        .background(gradient),
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

                if (contact.type == "default") {
                    Text(
                        text = contact.number,
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Text(
                        text = "Work Contact",
                        fontSize = 14.sp,
                        color = Color(0xFF60A5FA),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

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
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF10B981), Color(0xFF059669))
                            )
                        ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White
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

// ----- Contact Card -----
@Composable
private fun ModernContactCard(contact: AppContact, onClick: () -> Unit, index: Int) {
    val avatarColor = getColorForName(contact.name)
    val gradient = Brush.linearGradient(
        listOf(
            avatarColor.copy(alpha = 0.9f),
            avatarColor.copy(alpha = 0.6f)
        )
    )

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
                    .background(gradient),
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

                if (contact.type == "default") {
                    Text(
                        text = contact.number,
                        fontSize = 14.sp,
                        color = Color(0xFF6B7280),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                } else if (contact.type == "work") {
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
            }

            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF10B981), Color(0xFF059669))
                        )
                    )
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
