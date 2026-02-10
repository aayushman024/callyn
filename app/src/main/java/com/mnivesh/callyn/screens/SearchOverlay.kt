package com.mnivesh.callyn.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mnivesh.callyn.R
import com.mnivesh.callyn.components.*
import com.mnivesh.callyn.db.AppContact
import com.mnivesh.callyn.db.CrmContact
import com.mnivesh.callyn.tabs.CrmContactCard
import com.mnivesh.callyn.viewmodels.CrmUiState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    deviceContacts: List<DeviceContact>,
    workContacts: List<AppContact>,
    myContacts: List<AppContact>,
    crmUiState: CrmUiState,
    department: String?,
    userName: String,
    onSelectDeviceContact: (DeviceContact) -> Unit,
    onSelectWorkContact: (AppContact) -> Unit,
    onSelectEmployeeContact: (AppContact) -> Unit,
    onSelectCrmContact: (CrmContact) -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(300)) + fadeIn(tween(300)),
        exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(300)) + fadeOut(tween(300)),
        modifier = Modifier.fillMaxSize().zIndex(999f)
    ) {
        val context = LocalContext.current
        var internalQuery by remember { mutableStateOf("") }
        var debouncedQuery by remember { mutableStateOf("") }
        var searchHistory by remember { mutableStateOf(SearchHistoryManager.getHistory(context)) }
        var selectedFilter by remember { mutableStateOf("All") }
        var searchCrmData by remember { mutableStateOf(false) }

        // --- Layout & Scroll State for Sliver Logic ---
        val density = LocalDensity.current
        var searchBarHeight by remember { mutableIntStateOf(0) }
        var filtersHeight by remember { mutableIntStateOf(0) }
        var filtersOffsetPx by remember { mutableFloatStateOf(0f) }

        // Ensure offset stays valid if height changes
        LaunchedEffect(filtersHeight) {
            filtersOffsetPx = filtersOffsetPx.coerceIn(-filtersHeight.toFloat(), 0f)
        }

        // Nested Scroll Connection to handle "Quick Return" logic
        val nestedScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val delta = available.y
                    val newOffset = (filtersOffsetPx + delta).coerceIn(-filtersHeight.toFloat(), 0f)
                    val consumed = newOffset - filtersOffsetPx
                    filtersOffsetPx = newOffset
                    // Consume the scroll used to move the header so the list moves in sync
                    return Offset(0f, consumed)
                }
            }
        }

        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        val searchFocusRequester = remember { FocusRequester() }

        // Debounce Logic
        LaunchedEffect(internalQuery) {
            if (internalQuery.isBlank()) {
                debouncedQuery = ""
            } else {
                delay(300)
                debouncedQuery = internalQuery
            }
        }

        LaunchedEffect(Unit) { searchFocusRequester.requestFocus() }

        // --- Enhanced Filtering Logic Including CRM ---
        val combinedResults = remember(
            debouncedQuery,
            selectedFilter,
            myContacts,
            deviceContacts,
            workContacts,
            department,
            searchCrmData,
            crmUiState
        ) {
            if (debouncedQuery.isBlank()) emptyList<Any>() else {
                val isCodeSearch = debouncedQuery.length == 6
                val results = mutableListOf<Any>()

                fun AppContact.matches(): Boolean {
                    return name.contains(debouncedQuery, true) ||
                            familyHead.contains(debouncedQuery, true) ||
                            pan.contains(debouncedQuery, true) ||
                            (department == "Management" && number.contains(debouncedQuery)) ||
                            (isCodeSearch && uniqueCode.equals(debouncedQuery, ignoreCase = true))
                }

                if (selectedFilter == "All" || selectedFilter == "Personal") {
                    results.addAll(deviceContacts.filter { contact ->
                        contact.name.contains(debouncedQuery, true) ||
                                contact.numbers.any { it.number.contains(debouncedQuery) }
                    })
                }

                if (selectedFilter == "All" || selectedFilter == "Work") {
                    results.addAll(myContacts.filter { it.matches() })
                }

                if (selectedFilter == "All" || selectedFilter == "Employee") {
                    results.addAll(workContacts.filter {
                        it.rshipManager.equals("Employee", ignoreCase = true) && it.matches()
                    })
                }

                if (searchCrmData) {
                    val crmList = crmUiState.tickets + crmUiState.investmentLeads + crmUiState.insuranceLeads
                    val filteredCrm = crmList.filter { contact ->
                        val passModule = when (selectedFilter) {
                            "All" -> true
                            "Tickets" -> contact.module.equals("Tickets", true)
                            "Investment Leads" -> contact.module.equals("Investment_leads", true)
                            "Insurance Leads" -> contact.module.equals("Insurance_Leads", true)
                            "Personal", "Work", "Employee" -> false
                            else -> false
                        }
                        if (!passModule) return@filter false

                        contact.name.contains(debouncedQuery, true) ||
                                contact.number.contains(debouncedQuery) ||
                                contact.recordId.contains(debouncedQuery, true) ||
                                (contact.product?.contains(debouncedQuery, true) == true) ||
                                contact.ownerName.contains(debouncedQuery, true)
                    }
                    results.addAll(filteredCrm)
                }

                results.sortedWith(
                    compareBy<Any> { item ->
                        val isNumericSearch = debouncedQuery.all { it.isDigit() }
                        if (isNumericSearch) {
                            if (item is DeviceContact) 0 else 1
                        } else {
                            0
                        }
                    }.thenBy { item ->
                        if (item is AppContact && item.rshipManager.equals(userName, ignoreCase = true)) 0 else 1
                    }.thenBy { item ->
                        if (isCodeSearch && item is AppContact && item.uniqueCode.equals(debouncedQuery, ignoreCase = true)) 0 else 1
                    }.thenBy { item ->
                        when (item) {
                            is AppContact -> item.name.lowercase()
                            is DeviceContact -> item.name.lowercase()
                            is CrmContact -> item.name.lowercase()
                            else -> ""
                        }
                    }
                )
            }
        }

        BackHandler { onDismiss() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .nestedScroll(nestedScrollConnection)
        ) {

            // --- Content List ---
            // Calculate padding based on Header Heights
            val topPadding = with(density) { (searchBarHeight + filtersHeight + 30).toDp() }
            // Add extra bottom padding so last item is accessible even when list is translated up
            val bottomPadding = with(density) { (filtersHeight + 100).toDp() }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(x = 0, y = filtersOffsetPx.roundToInt()) },
                contentPadding = PaddingValues(top = topPadding, start = 16.dp, end = 16.dp, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (internalQuery.isBlank()) {
                    if (searchHistory.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Recent Searches", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Clear All", color = Color(0xFF60A5FA), fontSize = 12.sp, modifier = Modifier.clickable { SearchHistoryManager.clearHistory(context); searchHistory = emptyList() })
                            }
                        }
                        items(searchHistory) { historyItem ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { internalQuery = historyItem }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.History, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(historyItem, color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp, modifier = Modifier.weight(1f))
                                Icon(Icons.Default.NorthWest, null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                } else {
                    if (combinedResults.isEmpty() && debouncedQuery.isNotEmpty()) {
                        item { Box(modifier = Modifier.fillMaxWidth().padding(top = 50.dp), contentAlignment = Alignment.Center) { Text("No matching contacts", color = Color.White.copy(alpha = 0.5f)) } }
                    }

                    items(combinedResults) { item ->
                        val onResultClick: () -> Unit = {
                            SearchHistoryManager.addSearch(context, debouncedQuery)
                            searchHistory = SearchHistoryManager.getHistory(context)
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }

                        when (item) {
                            is AppContact -> {
                                if (item.rshipManager.equals("Employee", ignoreCase = true)) {
                                    ModernEmployeeCard(contact = item, highlightQuery = debouncedQuery, onClick = { onSelectEmployeeContact(item); onResultClick() })
                                } else {
                                    ModernWorkContactCard(contact = item, highlightQuery = debouncedQuery, onClick = { onSelectWorkContact(item); onResultClick() })
                                }
                            }
                            is DeviceContact -> {
                                ModernDeviceContactCard(contact = item, highlightQuery = debouncedQuery, onClick = { onSelectDeviceContact(item); onResultClick() })
                            }
                            is CrmContact -> {
                                CrmContactCard(contact = item, onClick = { onSelectCrmContact(item); onResultClick() })
                            }
                        }
                    }
                }
            }

            // --- Search Bar (Pinned at Top) ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .onGloballyPositioned { searchBarHeight = it.size.height }
                    .zIndex(2f) // Higher Z-Index to stay on top
                    .background(Color(0xFF1E293B)) // Opaque
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, start = 8.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                        }
                        TextField(
                            value = internalQuery,
                            onValueChange = { internalQuery = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(searchFocusRequester)
                                .clip(RoundedCornerShape(16.dp)),
                            placeholder = { Text("Search...", color = Color.White.copy(alpha = 0.5f)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color.White.copy(alpha = 0.1f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = Color(0xFF3B82F6)
                            ),
                            trailingIcon = {
                                if (internalQuery.isNotEmpty()) {
                                    IconButton(onClick = { internalQuery = "" }) {
                                        Icon(Icons.Default.Close, "Clear", tint = Color.White.copy(alpha = 0.6f))
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // --- Filters (Collapsible / Slivers) ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = with(density) { searchBarHeight.toDp() }) // Starts below Search Bar
                    .offset { IntOffset(x = 0, y = filtersOffsetPx.roundToInt()) } // Translates UP behind Search Bar
                    .onGloballyPositioned { filtersHeight = it.size.height }
                    .zIndex(1f) // Below Search Bar
                    .background(Color(0xFF1E293B)) // Opaque
            ) {
                Column {
                    // 1. CRM Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.zoho_logo),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            "  Include CRM data   ",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp
                        )
                        Switch(
                            checked = searchCrmData,
                            onCheckedChange = { searchCrmData = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF3B82F6),
                                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier.scale(0.8f)
                        )
                    }

                    // 2. Filter Chips
                    val filterOptions = remember(searchCrmData) {
                        val base = mutableListOf("All", "Personal", "Work", "Employee")
                        if (searchCrmData) {
                            base.addAll(listOf("Tickets", "Investment Leads", "Insurance Leads"))
                        }
                        base
                    }

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                            .animateContentSize(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        filterOptions.forEach { filter ->
                            val isSelected = selectedFilter == filter
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter) },
                                enabled = true,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF3B82F6),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color.White.copy(alpha = 0.05f),
                                    labelColor = Color.White.copy(alpha = 0.7f)
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = Color.Transparent,
                                    enabled = true,
                                    selected = isSelected
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                }
            }
        }
    }
}