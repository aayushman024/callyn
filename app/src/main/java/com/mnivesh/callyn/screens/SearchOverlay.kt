// [!code fileName: aayushman024/callyn/callyn-ca082e83a09094de41d32fe802b43f4e3d39dbc5/app/src/main/java/com/mnivesh/callyn/screens/SearchOverlay.kt]
package com.mnivesh.callyn.screens

import android.content.Context
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
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
import com.mnivesh.callyn.ui.theme.AppTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
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
import com.mnivesh.callyn.managers.SearchHistoryManager
import com.mnivesh.callyn.tabs.CrmContactCard
import com.mnivesh.callyn.utils.ParsedSearchQuery
import com.mnivesh.callyn.utils.SearchEngine
import com.mnivesh.callyn.viewmodels.CrmUiState
import com.mnivesh.callyn.viewmodels.RecentCallUiItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

fun String.matchesQuery(query: String): Boolean {
    val tokens = query.lowercase().split(" ").filter { it.isNotBlank() }
    val text = this.lowercase()
    return tokens.all { token -> text.contains(token) }
}

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
    callLogs: List<RecentCallUiItem> = emptyList(),
    onSelectDeviceContact: (DeviceContact) -> Unit,
    onSelectWorkContact: (AppContact) -> Unit,
    onSelectEmployeeContact: (AppContact) -> Unit,
    onSelectCrmContact: (CrmContact) -> Unit,
    onCallLogClick: ((RecentCallUiItem) -> Unit)? = null,
    onMakeCall: ((String, Boolean, Int?) -> Unit)? = null
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
        val sharedPrefs = remember { context.getSharedPreferences("callyn_prefs", Context.MODE_PRIVATE) }
        var searchCrmData by remember {
            mutableStateOf(sharedPrefs.getBoolean("pref_crm_search_enabled", false))
        }
        // [!code ++] Call Log specific filter state
        var callTypeFilter by remember { mutableStateOf("All Calls") }
        var isCallFilterExpanded by remember { mutableStateOf(false) }

        // --- Layout & Scroll State for Sliver Logic ---
        val density = LocalDensity.current
        var searchBarHeight by remember { mutableIntStateOf(0) }
        var filtersHeight by remember { mutableIntStateOf(0) }
        var filtersOffsetPx by remember { mutableFloatStateOf(0f) }

        // Ensure offset stays valid if height changes
        LaunchedEffect(filtersHeight) {
            filtersOffsetPx = filtersOffsetPx.coerceIn(-filtersHeight.toFloat(), 0f)
        }

        // Nested Scroll Connection
        val nestedScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val delta = available.y
                    val newOffset = (filtersOffsetPx + delta).coerceIn(-filtersHeight.toFloat(), 0f)
                    val consumed = newOffset - filtersOffsetPx
                    filtersOffsetPx = newOffset
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
                delay(250)
                debouncedQuery = internalQuery
            }
        }

        LaunchedEffect(Unit) { searchFocusRequester.requestFocus() }

        // Background Search Computation (Dispatched off Main Thread)
        var filteredCallLogs by remember { mutableStateOf(emptyList<RecentCallUiItem>()) }
        var combinedResults by remember { mutableStateOf(emptyList<Any>()) }

        LaunchedEffect(
            debouncedQuery,
            callLogs,
            selectedFilter,
            callTypeFilter,
            myContacts,
            deviceContacts,
            workContacts,
            department,
            searchCrmData,
            crmUiState,
            userName
        ) {
            if (debouncedQuery.isBlank()) {
                filteredCallLogs = emptyList()
                combinedResults = emptyList()
            } else {
                withContext(Dispatchers.Default) {
                    val parsed = ParsedSearchQuery.parse(debouncedQuery)

                    val logs = SearchEngine.filterCallLogs(
                        callLogs = callLogs,
                        query = parsed,
                        selectedFilter = selectedFilter,
                        callTypeFilter = callTypeFilter,
                        maxResults = 50
                    )

                    val contacts = SearchEngine.filterContacts(
                        query = parsed,
                        selectedFilter = selectedFilter,
                        myContacts = myContacts,
                        deviceContacts = deviceContacts,
                        workContacts = workContacts,
                        crmUiState = crmUiState,
                        searchCrmData = searchCrmData,
                        department = department,
                        userName = userName,
                        maxResults = 80
                    )

                    filteredCallLogs = logs
                    combinedResults = contacts
                }
            }
        }

        BackHandler { onDismiss() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.background)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .nestedScroll(nestedScrollConnection)
        ) {
            val topPadding = with(density) { (searchBarHeight + filtersHeight + 30).toDp() }
            val bottomPadding = with(density) { (filtersHeight + 100).toDp() }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(x = 0, y = filtersOffsetPx.roundToInt()) },
                contentPadding = PaddingValues(top = topPadding, start = 16.sdp(), end = 16.sdp(), bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(12.sdp())
            ) {
                if (internalQuery.isBlank()) {
                    if (searchHistory.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 15.sdp()),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Recent Searches", color = AppTheme.colors.textSecondary, fontSize = 13.ssp(), fontWeight = FontWeight.Bold)
                                Text("Clear All", color = if (AppTheme.colors.isDark) Color(0xFF60A5FA) else Color(0xFF2563EB), fontSize = 12.ssp(), modifier = Modifier.clickable { SearchHistoryManager.clearHistory(context); searchHistory = emptyList() })
                            }
                        }
                        items(searchHistory) { historyItem ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { internalQuery = historyItem }.padding(vertical = 12.sdp()),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.History, null, tint = AppTheme.colors.textSecondary.copy(alpha = 0.6f), modifier = Modifier.size(20.sdp()))
                                Spacer(modifier = Modifier.width(16.sdp()))
                                Text(historyItem, color = AppTheme.colors.textPrimary, fontSize = 16.ssp(), modifier = Modifier.weight(1f))
                                Icon(Icons.Default.NorthWest, null, tint = AppTheme.colors.textSecondary.copy(alpha = 0.4f), modifier = Modifier.size(16.sdp()))
                            }
                        }
                    }
                } else {
                    // Call Logs Section
                    // [!code ++] Always show header/filter if logs are provided (even if filtered out, to show empty state/controls) or at least if query matches something
                    // Note: If filters result in empty list, user might want to change filters.
                    // However, original logic was "if filteredCallLogs.isNotEmpty".
                    // Let's stick to showing it if we have any matches before type filtering?
                    // Or just use the filtered list. If empty, section disappears. That's fine.

                    if (filteredCallLogs.isNotEmpty()) {
                        item {
                            // [!code ++] Header Row with Dropdown
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 8.sdp())
                            ) {
                                // Call Logs Pill
                                Surface(
                                    color = Color(0xFF3B82F6).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = "Call Logs",
                                        color = Color(0xFF3B82F6),
                                        fontSize = 11.ssp(),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.sdp(), vertical = 4.sdp())
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.sdp()))

                                // [!code ++] Call Type Dropdown
                                Box {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.sdp()))
                                            .clickable { isCallFilterExpanded = true }
                                            .padding(horizontal = 4.sdp(), vertical = 2.sdp()),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = callTypeFilter,
                                            color = AppTheme.colors.textSecondary,
                                            fontSize = 12.ssp(),
                                            fontWeight = FontWeight.Medium
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = AppTheme.colors.textSecondary,
                                            modifier = Modifier.size(18.sdp())
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = isCallFilterExpanded,
                                        onDismissRequest = { isCallFilterExpanded = false },
                                        containerColor = AppTheme.colors.surface,
                                        border = androidx.compose.foundation.BorderStroke(1.sdp(), AppTheme.colors.border)
                                    ) {
                                        listOf("All Calls", "Incoming", "Outgoing", "Missed").forEach { type ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        type,
                                                        color = if(callTypeFilter == type) Color(0xFF3B82F6) else AppTheme.colors.textPrimary
                                                    )
                                                },
                                                onClick = {
                                                    callTypeFilter = type
                                                    isCallFilterExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        items(
                            items = filteredCallLogs,
                            key = { "call_${it.id}_${it.number}_${it.date}" }
                        ) { log ->
                            RecentCallItem(
                                log = log,
                                onBodyClick = {
                                    onCallLogClick?.invoke(log)
                                    com.mnivesh.callyn.managers.SearchHistoryManager.addSearch(context, debouncedQuery)
                                },
                                onCallClick = {
                                    onMakeCall?.invoke(log.number, log.type == "Work", log.simSlot?.filter { it.isDigit() }?.toIntOrNull()?.minus(1))
                                },
                                onDelete = {},
                                onBlock = {}
                            )
                        }
                        item {
                            HorizontalDivider(color = AppTheme.colors.border, modifier = Modifier.padding(vertical = 8.sdp()))
                        }
                    }

                    if (combinedResults.isEmpty() && debouncedQuery.isNotEmpty() && filteredCallLogs.isEmpty()) {
                        item { Box(modifier = Modifier.fillMaxWidth().padding(top = 50.sdp()), contentAlignment = Alignment.Center) { Text("No matching results", color = AppTheme.colors.textSecondary) } }
                    }

                    // All Contacts Header Pill
                    if (combinedResults.isNotEmpty()) {
                        item {
                            Surface(
                                color = AppTheme.colors.surfaceVariant,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.padding(top = 8.sdp(), bottom = 4.sdp())
                            ) {
                                Text(
                                    text = "All Contacts",
                                    color = AppTheme.colors.textSecondary,
                                    fontSize = 11.ssp(),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.sdp(), vertical = 4.sdp())
                                )
                            }
                        }
                    }

                    items(
                        items = combinedResults,
                        key = { item ->
                            when (item) {
                                is AppContact -> "app_${item.id}"
                                is DeviceContact -> "device_${item.id}"
                                is CrmContact -> "crm_${item.localId}_${item.recordId}"
                                else -> item.hashCode()
                            }
                        }
                    ) { item ->
                        val onResultClick: () -> Unit = {
                            com.mnivesh.callyn.managers.SearchHistoryManager.addSearch(context, debouncedQuery)
                            searchHistory = com.mnivesh.callyn.managers.SearchHistoryManager.getHistory(context)
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
                    .zIndex(2f)
                    .background(AppTheme.colors.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(bottom = 12.sdp())
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.sdp(), start = 8.sdp(), end = 16.sdp()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = AppTheme.colors.textPrimary)
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (AppTheme.colors.isDark) Modifier else Modifier.customShadow(
                                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.05f),
                                        borderRadius = 24.sdp(),
                                        blurRadius = 16.sdp(),
                                        offsetY = 4.sdp()
                                    )
                                )
                                .clip(RoundedCornerShape(24.sdp()))
                                .background(AppTheme.colors.surfaceVariant)
                                .border(1.sdp(), AppTheme.colors.border, RoundedCornerShape(24.sdp()))
                                .padding(horizontal = 16.sdp(), vertical = 10.sdp()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = internalQuery,
                                onValueChange = { internalQuery = it },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = AppTheme.colors.textPrimary,
                                    fontSize = 14.ssp(),
                                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                        includeFontPadding = false
                                    )
                                ),
                                singleLine = true,
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF3B82F6)),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(searchFocusRequester),
                                decorationBox = { innerTextField ->
                                    Box(
                                        contentAlignment = Alignment.CenterStart,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (internalQuery.isEmpty()) {
                                            Text(
                                                "Search...",
                                                color = AppTheme.colors.textSecondary,
                                                fontSize = 14.ssp(),
                                                style = androidx.compose.ui.text.TextStyle(
                                                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                                        includeFontPadding = false
                                                    )
                                                )
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                            if (internalQuery.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(8.sdp()))
                                Icon(
                                    Icons.Default.Close,
                                    "Clear",
                                    tint = AppTheme.colors.textSecondary,
                                    modifier = Modifier
                                        .size(20.sdp())
                                        .clickable { internalQuery = "" }
                                )
                            }
                        }
                    }
                }
            }

            // --- Filters (Collapsible / Slivers) ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = with(density) { searchBarHeight.toDp() })
                    .offset { IntOffset(x = 0, y = filtersOffsetPx.roundToInt()) }
                    .onGloballyPositioned { filtersHeight = it.size.height }
                    .zIndex(1f)
                    .background(AppTheme.colors.surface)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.sdp(), vertical = 4.sdp()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.zoho_logo),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(28.sdp())
                        )
                        Text(
                            "  Include CRM data   ",
                            color = AppTheme.colors.textPrimary,
                            fontSize = 13.ssp()
                        )
                        Switch(
                            checked = searchCrmData,
                            onCheckedChange = { isChecked ->
                                searchCrmData = isChecked
                                // [!code ++] Save to prefs immediately (apply is async)
                                sharedPrefs.edit().putBoolean("pref_crm_search_enabled", isChecked).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF3B82F6),
                                uncheckedThumbColor = if (AppTheme.colors.isDark) Color.White.copy(alpha = 0.6f) else Color.Gray,
                                uncheckedTrackColor = if (AppTheme.colors.isDark) Color.White.copy(alpha = 0.1f) else AppTheme.colors.border
                            ),
                            modifier = Modifier.scale(0.8f)
                        )
                    }

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
                            .padding(horizontal = 16.sdp(), vertical = 2.sdp())
                            .animateContentSize(),
                        horizontalArrangement = Arrangement.spacedBy(4.sdp()),
                        verticalArrangement = Arrangement.spacedBy(1.sdp())
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
                                    containerColor = AppTheme.colors.surfaceVariant,
                                    labelColor = AppTheme.colors.textSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = if (isSelected) Color.Transparent else AppTheme.colors.border,
                                    enabled = true,
                                    selected = isSelected
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.sdp()))
                    HorizontalDivider(color = AppTheme.colors.border)
                }
            }
        }
    }
}