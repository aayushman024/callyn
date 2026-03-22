//adb command to reverse tcp and forward localhost on device
adb -s ZD222BRSCR reverse tcp:5500 tcp:5500

# ContactsScreen Refactoring - Component Structure

## Overview
This refactoring breaks down the monolithic ContactsScreen.kt file into smaller, independent, reusable composables organized by responsibility.

## Directory Structure

```
com.mnivesh.callyn.screens/
├── ContactsScreen.kt                    # Main screen coordinator
├── utils/
│   └── ScreenUtils.kt                   # Data classes and helper functions
├── components/
│   ├── ContactsTopBar.kt                # Top app bar with notifications
│   ├── ContactsContent.kt               # Main content wrapper with tabs & pager
│   ├── ContactsTabs.kt                  # Tab row component
│   ├── SearchBar.kt                     # Search input field
│   ├── PersonalContactsTab.kt           # Personal contacts list view
│   └── WorkContactsTab.kt               # Work contacts list view
├── cards/
│   └── ContactCards.kt                  # All contact card variants
│       ├── FavoriteContactItem
│       ├── ModernWorkContactCard
│       ├── ModernDeviceContactCard
│       ├── ModernEmployeeCard
│       ├── PermissionRequiredCard
│       ├── EmptyStateCard
│       ├── LoadingCard
│       └── ErrorCard
├── bottomsheets/
│   ├── ModernBottomSheet.kt             # Work contact bottom sheet
│   ├── ModernDeviceBottomSheet.kt       # Personal contact bottom sheet
│   ├── EmployeeBottomSheet.kt           # Employee contact bottom sheet
│   └── ConflictBottomSheet.kt           # Duplicate contacts sheet
├── dialogs/
│   ├── WorkRequestDialog.kt             # Request to mark as personal
│   ├── ShareCodeDialog.kt               # Share contact code dialog
│   └── GlobalRequestDialog.kt           # Global request dialog
└── screens/
    ├── FullSearchScreen.kt              # Full-screen search overlay
    ├── SearchHistoryManager.kt          # Search history persistence
    └── SmsLogsScreen.kt                 # (Assumed to exist already)

```

## File Descriptions

### 1. ContactsScreen.kt (Main Screen)
**Purpose**: Acts as the main coordinator that brings all components together.

**Key Responsibilities**:
- State management (viewModels, permissions, etc.)
- Authentication and user data
- Coordinating between different bottom sheets and dialogs
- Managing refresh and data fetching logic

**Key States**:
- `searchQuery`, `isRefreshing`, `showFullSearch`
- `selectedWorkContact`, `selectedDeviceContact`, `selectedEmployeeContact`
- Permission states and SIM configuration

---

### 2. utils/ScreenUtils.kt
**Purpose**: Contains data classes and pure utility functions.

**Contains**:
- `DeviceNumber` - Represents a phone number with default flag
- `DeviceContact` - Personal contact data model
- Helper functions:
    - `getColorForName()` - Generates avatar color from name
    - `getInitials()` - Extracts initials from name
    - `sanitizePhoneNumber()` - Normalizes phone numbers
    - `getHighlightedText()` - Highlights search query in text

---

### 3. components/ContactsTopBar.kt
**Purpose**: Top app bar with navigation, title, and notification badge.

**Props**:
- `department`, `userEmail` - For conditional rendering
- `hasSmsNotification` - Shows notification badge
- `onOpenDrawer`, `onShowSmsScreen` - Action callbacks
- `scrollBehavior` - For scroll-aware behavior

---

### 4. components/ContactsContent.kt
**Purpose**: Main content container that orchestrates tabs, search, and pager.

**Props**:
- All filtered lists and state
- Callback functions for user interactions
- List states for scroll position

**Children**:
- `ContactsTabs` - Tab selector
- `SearchBar` - Search input
- `HorizontalPager` with `PersonalContactsTab` and `WorkContactsTab`

---

### 5. components/ContactsTabs.kt
**Purpose**: Tab row for switching between Personal and Work contacts.

**Props**:
- `selectedTabIndex` - Current tab
- `deviceContactsCount`, `myContactsCount` - Badge counts
- `onTabChange` - Tab selection callback

**Contains**:
- `CustomTabContent` - Reusable tab content with icon, text, and count badge

---

### 6. components/SearchBar.kt
**Purpose**: Disabled search field that opens full search on click.

**Props**:
- `searchQuery` - Current query
- `onSearchQueryChange` - Query change callback
- `onShowFullSearch` - Opens full search overlay

**Note**: Field is disabled and has a clickable overlay to trigger full search.

---

### 7. components/PersonalContactsTab.kt
**Purpose**: Displays personal (device) contacts with favorites section.

**Props**:
- `hasContactsPermission` - Permission state
- `filteredDeviceContacts`, `favoriteContacts` - Contact lists
- `onDeviceContactClick`, `onGrantPermission` - Callbacks

**Features**:
- Horizontal favorites carousel
- LazyColumn for all contacts
- Permission request UI

---

### 8. components/WorkContactsTab.kt
**Purpose**: Displays work contacts assigned to the user.

**Props**:
- `uiState` - Loading/error state
- `filteredWorkContacts` - Contact list
- `onWorkContactClick` - Selection callback

**States**:
- Loading shimmer
- Error display
- Empty state
- Contact list

---

### 9. cards/ContactCards.kt
**Purpose**: All contact card UI variants.

**Components**:

#### FavoriteContactItem
Circular avatar for favorites carousel.

#### ModernWorkContactCard
Work contact with name, family head, and call button. Supports highlighting.

#### ModernDeviceContactCard
Personal contact with name, number(s), and call button. Supports highlighting.

#### ModernEmployeeCard
Employee contact with name, department, and call button. Supports highlighting.

#### PermissionRequiredCard
Prompts user to grant contacts permission.

#### EmptyStateCard
Generic empty state with icon and message.

#### LoadingCard
Shimmer loading placeholder.

#### ErrorCard
Error display with icon and message.

---

### 10. bottomsheets/ModernBottomSheet.kt
**Purpose**: Detailed view for work contacts.

**Props**:
- `contact` - Work contact data
- `department` - For conditional features
- `isDualSim` - Shows SIM selection
- `onCall`, `onRequestSubmit` - Action callbacks

**Features**:
- Avatar and name display
- View count for number visibility (non-management)
- Client details (PAN, AUM, Family Head, etc.)
- Share code dialog
- Request to mark as personal dialog
- Dual SIM call buttons

**Sub-components**:
- `ModernDetailRow` - Detail row with icon and label/value
- `WorkRequestDialog` - Popup for personal request
- `ShareCodeDialog` - Share contact code

---

### 11. bottomsheets/ModernDeviceBottomSheet.kt
**Purpose**: Detailed view for personal (device) contacts.

**Props**:
- `contact` - Device contact data
- `isDualSim` - Shows SIM selection
- `onCall` - Call callback with number and SIM selection

**Features**:
- Edit and view contact actions
- Multiple numbers display
- Copy to clipboard
- Dual SIM call buttons

---

### 12. bottomsheets/EmployeeBottomSheet.kt
**Purpose**: Detailed view for employee contacts.

**Props**:
- `contact` - Employee contact (AppContact)
- `isDualSim` - Shows SIM selection
- `onCall` - Call callback

**Features**:
- Employee badge
- Department information
- Phone number with copy
- Dual SIM call buttons

---

### 13. bottomsheets/ConflictBottomSheet.kt
**Purpose**: Shows duplicate contacts and allows cleanup.

**Props**:
- `conflictingContacts` - List of duplicates
- `hasWritePermission` - Permission state
- `onDeleteContacts`, `onMarkPersonal`, `onRequestPermission` - Actions

**Features**:
- Non-dismissible modal
- Batch delete action
- Mark as personal option per contact

---

### 14. dialogs/WorkRequestDialog.kt
**Purpose**: Request dialog for marking work contact as personal.

**Props**:
- `requestReason` - User's reason
- `onReasonChange`, `onSubmit`, `onDismiss` - Actions
- Color theme props

---

### 15. dialogs/ShareCodeDialog.kt
**Purpose**: Displays and allows sharing of contact code.

**Props**:
- `contactCode` - 6-digit code
- `onDismiss` - Close callback
- Color theme props

**Features**:
- Copy code on tap
- System share intent
- QR code icon

---

### 16. dialogs/GlobalRequestDialog.kt
**Purpose**: General request dialog for conflict resolution.

**Props**:
- `contactName` - Contact to mark
- `reason` - User's reason
- `onSubmit`, `onDismiss` - Actions

---

### 17. screens/FullSearchScreen.kt
**Purpose**: Full-screen search overlay with filters and history.

**Props**:
- `visible` - Show/hide state
- Contact lists and user data
- Click callbacks for each contact type

**Features**:
- Auto-focus search field
- Debounced search (300ms)
- Filter chips (All, Personal, Work, Employee)
- Search history display
- Sorted results (numeric search prioritizes personal, text prioritizes assigned work contacts)
- Smart highlighting in results

**Uses**:
- `SearchHistoryManager` - For persisting history

---

### 18. screens/SearchHistoryManager.kt
**Purpose**: Manages search history persistence.

**Functions**:
- `getHistory(context)` - Retrieves history list
- `addSearch(context, query)` - Adds to history (max 10)
- `clearHistory(context)` - Clears all history

**Storage**: SharedPreferences with pipe-separated values.

---

## Usage Example

```kotlin
// In your navigation/main activity
@Composable
fun ContactsRoute(onContactClick: (String, Boolean, Int?) -> Unit, onOpenDrawer: () -> Unit) {
    ContactsScreen(
        onContactClick = onContactClick,
        onOpenDrawer = onOpenDrawer
    )
}
```

All state management and coordination happens inside `ContactsScreen.kt`. Each component is independently testable and reusable.

---

## Migration Notes

### What Changed:
1. **Monolithic file split into 18+ files** organized by responsibility
2. **All existing logic preserved** - no functionality changes
3. **Props-based communication** between components
4. **Clear separation of concerns**:
    - State management → ContactsScreen.kt
    - UI components → components/ folder
    - Data models & utilities → utils/ folder
    - Bottom sheets → bottomsheets/ folder
    - Dialogs → dialogs/ folder

### What Stayed the Same:
- All business logic
- All state management logic
- All data filtering and transformation
- All callback behaviors
- All styling and theming

---

## Testing Recommendations

Each component can now be tested in isolation:

```kotlin
@Test
fun testWorkContactCard() {
    composeTestRule.setContent {
        ModernWorkContactCard(
            contact = mockWorkContact,
            onClick = { /* assert */ }
        )
    }
    // Test click, display, etc.
}
```

---

## Future Improvements

1. **Extract ViewModels**: Move filtering logic to dedicated ViewModels
2. **Theming**: Extract color values to a theme system
3. **Navigation**: Use Compose Navigation for sheet/dialog state
4. **Dependency Injection**: Inject managers (Auth, Sim, ViewLimit)
5. **Preview Annotations**: Add @Preview to each component

---

## File Checklist

✅ ContactsScreen.kt
✅ utils/ScreenUtils.kt
✅ components/ContactsTopBar.kt
✅ components/ContactsContent.kt
✅ components/ContactsTabs.kt
✅ components/SearchBar.kt
✅ components/PersonalContactsTab.kt
✅ components/WorkContactsTab.kt
✅ cards/ContactCards.kt
✅ bottomsheets/ModernBottomSheet.kt
✅ bottomsheets/ModernDeviceBottomSheet.kt
✅ bottomsheets/EmployeeBottomSheet.kt
✅ bottomsheets/ConflictBottomSheet.kt (see REMAINING_COMPONENTS.kt)
✅ dialogs/WorkRequestDialog.kt (see REMAINING_COMPONENTS.kt)
✅ dialogs/ShareCodeDialog.kt (see REMAINING_COMPONENTS.kt)
✅ dialogs/GlobalRequestDialog.kt (see REMAINING_COMPONENTS.kt)
✅ screens/FullSearchScreen.kt (see REMAINING_COMPONENTS.kt)
✅ screens/SearchHistoryManager.kt (see REMAINING_COMPONENTS.kt)

---

## Package Structure

Recommended package organization:
```
com.mnivesh.callyn
├── screens
│   ├── ContactsScreen.kt
│   ├── FullSearchScreen.kt
│   ├── SearchHistoryManager.kt
│   ├── components
│   │   ├── ContactsTopBar.kt
│   │   ├── ContactsContent.kt
│   │   ├── ContactsTabs.kt
│   │   ├── SearchBar.kt
│   │   ├── PersonalContactsTab.kt
│   │   └── WorkContactsTab.kt
│   ├── cards
│   │   └── ContactCards.kt
│   ├── bottomsheets
│   │   ├── ModernBottomSheet.kt
│   │   ├── ModernDeviceBottomSheet.kt
│   │   ├── EmployeeBottomSheet.kt
│   │   └── ConflictBottomSheet.kt
│   ├── dialogs
│   │   ├── WorkRequestDialog.kt
│   │   ├── ShareCodeDialog.kt
│   │   └── GlobalRequestDialog.kt
│   └── utils
│       └── ScreenUtils.kt
```
