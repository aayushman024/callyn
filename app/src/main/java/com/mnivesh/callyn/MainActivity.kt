package com.mnivesh.callyn
//after conflict push
import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.Settings // [!code ++] Added missing import
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.util.Log
import android.webkit.CookieManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Warning // [!code ++] Added missing import
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card // [!code ++] Fixed import (was Wear Card)
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mnivesh.callyn.api.RetrofitInstance
import com.mnivesh.callyn.api.VersionResponse
import com.mnivesh.callyn.api.version
import com.mnivesh.callyn.ui.UpdateDialog
import com.mnivesh.callyn.ui.ZohoLoginScreen
import com.mnivesh.callyn.ui.theme.CallynTheme
import com.mnivesh.callyn.utils.VersionManager
import com.mnivesh.callyn.workers.SyncUserDetailsWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color as ComposeColor

private const val TAG = "MainActivity"

// --- STATE CLASSES ---
data class UpdateState(
    val isUpdateAvailable: Boolean = false,
    val isHardUpdate: Boolean = false,
    val versionInfo: VersionResponse? = null
)

sealed class MainActivityUiState {
    object Loading : MainActivityUiState()
    object LoggedOut : MainActivityUiState()
    data class LoggedIn(val userName: String) : MainActivityUiState()
}

class MainActivity : ComponentActivity() {

    private lateinit var authManager: AuthManager
    private var uiState by mutableStateOf<MainActivityUiState>(MainActivityUiState.Loading)

    // Version Check States
    private var updateState by mutableStateOf(UpdateState())
    private var showUpdateDialog by mutableStateOf(false)

    // Permissions
    private val permissionsToRequest = mutableListOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.WRITE_CALL_LOG,
        Manifest.permission.RECORD_AUDIO
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val multiplePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            Log.d(TAG, "Permissions callback received.")
        }
    private val defaultDialerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Log.d(TAG, "Dialer callback received.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authManager = AuthManager(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        if (!handleIntent(intent)) {
            checkLoginState()
        }

        setContent {
            CallynTheme {
                if (showUpdateDialog && updateState.versionInfo != null) {
                    UpdateDialog(
                        versionInfo = updateState.versionInfo!!,
                        isHardUpdate = updateState.isHardUpdate,
                        onDismiss = { showUpdateDialog = false },
                        onUpdate = { url ->
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                startActivity(intent)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to open update link", e)
                            }
                        }
                    )
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    when (val state = uiState) {
                        is MainActivityUiState.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }

                        is MainActivityUiState.LoggedIn -> {
                            MainScreenWithDialerLogic(
                                userName = state.userName,
                                onLogout = { performLogout() }
                            )
                        }

                        is MainActivityUiState.LoggedOut -> {
                            ZohoLoginScreen()
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkForUpdates()
        syncDeviceDetails()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    // --- LOGIC: Updates ---

    private fun checkForUpdates(isManualCheck: Boolean = false) {
        if (isManualCheck) {
            Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show()
        }

        lifecycleScope.launch {
            try {
                val currentVersion = version
                val token = authManager.getToken()

                val response = RetrofitInstance.api.getLatestVersion("Bearer $token")
                Log.d(TAG, "${response.body()}")
                if (response.isSuccessful && response.body() != null) {
                    val remote = response.body()!!

                    if (VersionManager.isUpdateNeeded(currentVersion, remote.latestVersion)) {
                        updateState = UpdateState(
                            isUpdateAvailable = true,
                            isHardUpdate = remote.updateType == "hard",
                            versionInfo = remote
                        )
                        showUpdateDialog = true
                    } else if (isManualCheck) {
                        Toast.makeText(this@MainActivity, "You are already on the latest version", Toast.LENGTH_SHORT).show()
                    }
                } else if (isManualCheck) {
                    Toast.makeText(this@MainActivity, "Failed to check for updates", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (isManualCheck) {
                    Toast.makeText(this@MainActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
                Log.e(TAG, "Update check failed", e)
            }
        }
    }

    fun manualUpdateCheck() {
        checkForUpdates(isManualCheck = true)
    }

    // --- LOGIC: Auth (Offline First) ---

    private fun performLogout() {
        lifecycleScope.launch {
            uiState = MainActivityUiState.Loading
            val app = application as CallynApplication
            app.repository.clearAllData()
            authManager.logout()
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            uiState = MainActivityUiState.LoggedOut
        }
    }

    private fun verifyTokenInBackground(token: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.getMe(token)
                if (response.isSuccessful && response.body() != null) {
                    authManager.saveUserName(response.body()!!.name)
                } else {
                    Log.e(TAG, "Token invalid. Logging out.")
                    performLogout()
                }
            } catch (e: Exception) {
                Log.d(TAG, "Offline mode active: ${e.message}")
            }
        }
    }


    internal fun syncDeviceDetails() {
        // 1. Define constraints (run only when internet is available)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // 2. Create the work request
        val syncRequest = OneTimeWorkRequestBuilder<SyncUserDetailsWorker>()
            .setConstraints(constraints)
            .build()

        // 3. Enqueue Unique Work
        // "KEEP" means: If a sync is already running or queued, don't start a new one.
        // This prevents spamming the API on screen rotations.
        WorkManager.getInstance(this).enqueueUniqueWork(
            "SyncUserDetailsWork",
            ExistingWorkPolicy.KEEP,
            syncRequest
        )
    }


    private fun checkLoginState() {
        val token = authManager.getToken()
        val savedName = authManager.getUserName()

        if (token != null) {
            if (savedName != null) {
                uiState = MainActivityUiState.LoggedIn(savedName)
                verifyTokenInBackground("Bearer $token")
            } else {
                fetchUserName("Bearer $token")
            }
        } else {
            uiState = MainActivityUiState.LoggedOut
        }
    }

    private fun handleIntent(intent: Intent?): Boolean {
        val data: Uri? = intent?.data
        if (data != null && "callyn" == data.scheme && "auth" == data.host) {
            val token = data.getQueryParameter("token")
            val department = data.getQueryParameter("department")
            val email = data.getQueryParameter("email")

            if (!token.isNullOrEmpty()) {
                authManager.saveToken(token)

                // Save Department if present
                if (department != null) {
                    authManager.saveDepartment(department)
                    authManager.saveUserEmail(email)
                    Log.d(TAG, "Department saved from deep link: $department")
                    Log.d(TAG, "Email saved from deep link: $email")
                }

                fetchUserName("Bearer $token")
                return true
            }
        }
        return false
    }

    private fun fetchUserName(token: String) {
        lifecycleScope.launch {
            uiState = MainActivityUiState.Loading
            try {
                val response = RetrofitInstance.api.getMe(token)
                if (response.isSuccessful && response.body() != null) {
                    val name = response.body()!!.name
                    authManager.saveUserName(name)
                    uiState = MainActivityUiState.LoggedIn(name)
                } else {
                    authManager.logout()
                    uiState = MainActivityUiState.LoggedOut
                }
            } catch (e: Exception) {
                authManager.logout()
                uiState = MainActivityUiState.LoggedOut
            }
        }
    }

    // --- LOGIC: Permissions & Default Dialer ---

    fun checkAllPermissions(): Boolean {
        return permissionsToRequest.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getMissingPermissions(): List<String> {
        return permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.map { it.substringAfterLast(".") }
    }

    fun isDefaultDialer(): Boolean {
        val telecomManager = getSystemService(TELECOM_SERVICE) as? TelecomManager
        return telecomManager?.defaultDialerPackage == packageName
    }

    fun requestPermissions() {
        val permissionsToAsk = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissionsToAsk.isNotEmpty()) {
            multiplePermissionLauncher.launch(permissionsToAsk.toTypedArray())
        }
    }

    // --- UPDATED: Offer Call Screening Role + Dialer Role ---
    fun offerDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)

            // 1. Default Dialer
            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) && !isDefaultDialer()) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                defaultDialerLauncher.launch(intent)
            }

            // 2. Call Screening (Required for blocking system logs effectively)
            val roleScreening = RoleManager.ROLE_CALL_SCREENING
            if (roleManager.isRoleAvailable(roleScreening) && !roleManager.isRoleHeld(roleScreening)) {
                val intent = roleManager.createRequestRoleIntent(roleScreening)
                defaultDialerLauncher.launch(intent)
            }
        } else {
            // Pre-Android 10
            if (!isDefaultDialer()) {
                try {
                    val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                        .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                    defaultDialerLauncher.launch(intent)
                } catch (e: Exception) {
                }
            }
        }
    }

    // --- LOGIC: Missed Calls ---

    @SuppressLint("MissingPermission")
    fun getUnreadMissedCallsCount(): Int {
        if (!checkAllPermissions()) return 0
        var count = 0
        try {
            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                null,
                "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.IS_READ} = ?",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString(), "0"),
                null
            )
            count = cursor?.count ?: 0
            cursor?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error counting missed calls", e)
        }
        return count
    }

    @SuppressLint("MissingPermission")
    fun markMissedCallsAsRead() {
        if (!checkAllPermissions()) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val values = ContentValues().apply {
                    put(CallLog.Calls.IS_READ, 1)
                }
                contentResolver.update(
                    CallLog.Calls.CONTENT_URI,
                    values,
                    "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.IS_READ} = ?",
                    arrayOf(CallLog.Calls.MISSED_TYPE.toString(), "0")
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error marking calls as read", e)
            }
        }
    }

    // --- LOGIC: Smart Dialing (Sim 1 vs Sim 2) ---

    @SuppressLint("MissingPermission")
//    fun dialSmart(number: String, isWorkCall: Boolean) {
    fun dialSmart(number: String, specificSlot: Int?) {
        if (!isDefaultDialer()) {
            offerDefaultDialer(); return
        }
        if (!checkAllPermissions()) {
            requestPermissions(); return
        }

        val numberToDial = if (number.filter { it.isDigit() }.length >= 11 && !number.startsWith('+')) {
            "+${number.filter { it.isDigit() }}"
        } else {
            number
        }

        try {
            val telecomManager = getSystemService(TelecomManager::class.java)
            val subscriptionManager = getSystemService(SubscriptionManager::class.java)
            val activeSims = subscriptionManager.activeSubscriptionInfoList

            if (activeSims.isNullOrEmpty()) {
                Toast.makeText(this, "No SIM card found", Toast.LENGTH_SHORT).show()
                return
            }

//            val targetSlotIndex = if (isWorkCall) 1 else 0
//            val selectedSim = activeSims.find { it.simSlotIndex == targetSlotIndex }
//                ?: activeSims.first()

            val selectedSim = if (specificSlot != null) {
                activeSims.find { it.simSlotIndex == specificSlot } ?: activeSims.first()
            } else {
                // If no slot specified (single sim device or simple call), use default
                activeSims.first()
            }

            val simName = selectedSim.displayName ?: "SIM ${selectedSim.simSlotIndex + 1}"
//            val type = if (isWorkCall) "Work" else "Personal"
//            Toast.makeText(this, "Dialing $type call via $simName", Toast.LENGTH_SHORT).show()

            Toast.makeText(this, "Dialing via $simName", Toast.LENGTH_SHORT).show() // [!code ++]

            val uri = Uri.fromParts("tel", numberToDial, null)
            val intent = Intent(Intent.ACTION_CALL, uri)

            val targetHandle = findHandleForSubId(telecomManager, selectedSim.subscriptionId)
            if (targetHandle != null) {
                intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, targetHandle)
            }

            startActivity(intent)

        } catch (e: Exception) {
            Log.e(TAG, "Smart dial failed", e)
            Toast.makeText(this, "Call failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun findHandleForSubId(telecomManager: TelecomManager, subId: Int): PhoneAccountHandle? {
        return telecomManager.callCapablePhoneAccounts.firstOrNull { handle ->
            handle.id.contains(subId.toString())
        }
    }
}

// --- COMPOSABLE HELPERS ---

@Composable
fun MainActivity.MainScreenWithDialerLogic(userName: String, onLogout: () -> Unit) {

//    androidx.compose.runtime.LaunchedEffect(Unit) {
//        syncDeviceDetails()
//    }

    var hasAllPermissions by remember { mutableStateOf(checkAllPermissions()) }
    var isDefaultDialer by remember { mutableStateOf(isDefaultDialer()) }
    var missingPermissions by remember { mutableStateOf(getMissingPermissions()) }
    var missedCallCount by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAllPermissions = checkAllPermissions()
                isDefaultDialer = isDefaultDialer()
                missingPermissions = getMissingPermissions()
                if (hasAllPermissions) {
                    missedCallCount = getUnreadMissedCallsCount()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    MainScreen(
        userName = userName,
        hasAllPermissions = hasAllPermissions,
        isDefaultDialer = isDefaultDialer,
        missingPermissions = missingPermissions,
        missedCallCount = missedCallCount,
        onRequestPermissions = { requestPermissions() },
        onRequestDefaultDialer = { offerDefaultDialer() },
//        onSmartDial = { number, slot -> this.dialSmart(number, slot) }, // [!code ++]
        onSmartDial = { number, slot -> this.dialSmart(number, slot) }, // [!code ++]
        onResetMissedCount = {
            missedCallCount = 0
            markMissedCallsAsRead()
        },
        onLogout = onLogout
    )
}

sealed class Screen(val route: String) {
    object Recents : Screen("recents")
    object Contacts : Screen("contacts")
    object Dialer : Screen("dialer")
    object Requests : Screen("requests")
    object UserDetails : Screen("user_details")
    object ShowCallLogs : Screen("show_call_logs")
}

@Composable
fun MainScreen(
    userName: String,
    hasAllPermissions: Boolean,
    isDefaultDialer: Boolean,
    missingPermissions: List<String>,
    missedCallCount: Int,
    onRequestPermissions: () -> Unit,
    onRequestDefaultDialer: () -> Unit,
//    onSmartDial: (String, Boolean) -> Unit,
    onSmartDial: (String, Int?) -> Unit,
    onResetMissedCount: () -> Unit,
    onLogout: () -> Unit
) {
    if (!isDefaultDialer) {
        SetupScreen(
            isDefaultDialer = false,
            hasAllPermissions = hasAllPermissions,
            missingPermissions = emptyList(),
            onRequestPermissions = onRequestPermissions,
            onRequestDefaultDialer = onRequestDefaultDialer
        )
    } else if (!hasAllPermissions) {
        SetupScreen(
            isDefaultDialer = true,
            hasAllPermissions = false,
            missingPermissions = missingPermissions,
            onRequestPermissions = onRequestPermissions,
            onRequestDefaultDialer = onRequestDefaultDialer
        )
    } else {
        MainScreenContent(userName, onSmartDial, onLogout, missedCallCount, onResetMissedCount)
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScreenContent(
    userName: String,
//    onSmartDial: (String, Boolean) -> Unit,
    onSmartDial: (String, Int?) -> Unit, // [!code ++]
    onLogout: () -> Unit,
    missedCallCount: Int,
    onResetMissedCount: () -> Unit
) {
    val navController = rememberNavController()
    Scaffold(
        containerColor = ComposeColor(0xFF0F172A),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { BottomNavigationBar(navController, missedCallCount) }
    ) { padding ->
        NavHost(
            navController,
            startDestination = Screen.Contacts.route,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable(Screen.Recents.route) {
                RecentCallsScreen(
//           onCallClick = { num, isWork -> onSmartDial(num, isWork) },
                    onCallClick = { num, slot -> onSmartDial(num, slot) }, // [!code ++] Temporary fix for recents
                    onScreenEntry = onResetMissedCount
                )
            }
            composable(Screen.Contacts.route) {
                ContactsScreen(
//                    onContactClick = { number, isWorkCall ->
//                        onSmartDial(number, isWorkCall)
//                    },
                    onContactClick = { number, slot -> onSmartDial(number, slot) },
                    onLogout = onLogout,
                    onShowUserDetails = { navController.navigate(Screen.UserDetails.route) },
                    onShowCallLogs = { navController.navigate(Screen.ShowCallLogs.route) },
                    onShowRequests = { navController.navigate(Screen.Requests.route) }
                )
            }
            composable(Screen.Dialer.route) {
                DialerScreen(
                    onCallClick = { num, slot -> onSmartDial(num, slot) }, // [!code ++] Temporary fix for dialer
                )
            }
            composable(Screen.Requests.route) {
                PersonalRequestsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.UserDetails.route) {
                UserDetailsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.ShowCallLogs.route) {
                ShowCallLogsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomNavigationBar(navController: NavController, missedCallCount: Int) {
    val items = listOf(Screen.Recents, Screen.Contacts, Screen.Dialer)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(
        containerColor = ComposeColor(0xFF0F172A),
        contentColor = ComposeColor.White,
        tonalElevation = 0.dp
    ) {
        items.forEach { screen ->
            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

            NavigationBarItem(
                icon = {
                    if (screen == Screen.Recents && missedCallCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = ComposeColor(0xFFEF4444),
                                    contentColor = ComposeColor.White
                                ) {
                                    Text(text = missedCallCount.toString())
                                }
                            }
                        ) {
                            Icon(Icons.Filled.History, "Recents")
                        }
                    } else {
                        when (screen) {
                            Screen.Recents -> Icon(Icons.Filled.History, "Recents")
                            Screen.Contacts -> Icon(Icons.Filled.Contacts, "Contacts")
                            Screen.Dialer -> Icon(Icons.Filled.Dialpad, "Dialer")
                            else -> {}
                        }
                    }
                },
                label = { Text(screen.route.replaceFirstChar { it.uppercase() }) },
                selected = selected,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = ComposeColor.Transparent,
                    selectedIconColor = ComposeColor(0xFF3B82F6),
                    unselectedIconColor = ComposeColor.Gray,
                    selectedTextColor = ComposeColor(0xFF3B82F6),
                    unselectedTextColor = ComposeColor.Gray
                )
            )
        }
    }
}

@Composable
private fun SetupScreen(
    isDefaultDialer: Boolean,
    hasAllPermissions: Boolean,
    missingPermissions: List<String>,
    onRequestPermissions: () -> Unit,
    onRequestDefaultDialer: () -> Unit
) {
    val context = LocalContext.current
    val openAppSettings = {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open settings", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ComposeColor(0xFF1E293B), ComposeColor(0xFF0F172A)))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Callyn", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = ComposeColor.White)
            Spacer(modifier = Modifier.height(32.dp))

            if (!isDefaultDialer) {
                // 1. Main Action Card
                SetupCard(
                    "Set as Default",
                    "Required to make and receive calls.",
                    "Set Default",
                    onRequestDefaultDialer
                )

                // 2. Android 13+ Restricted Settings Warning Box
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Spacer(modifier = Modifier.height(24.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = ComposeColor(0xFFF59E0B).copy(alpha = 0.15f)), // Orange/Warning Tint
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ComposeColor(0xFFF59E0B).copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = ComposeColor(0xFFF59E0B),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Blocked by Android?",
                                fontWeight = FontWeight.Bold,
                                color = ComposeColor(0xFFF59E0B),
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "If you are unable to set this app as default, you likely need to allow 'Restricted Settings'.",
                                color = ComposeColor.White.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedButton(
                                onClick = openAppSettings,
                                border = androidx.compose.foundation.BorderStroke(1.dp, ComposeColor(0xFFF59E0B)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ComposeColor(0xFFF59E0B)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("1. Open Settings")
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Go to 'App Info' > Tap 3 dots (top right) > 'Allow restricted settings', then come back here.",
                                color = ComposeColor.White.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

            } else if (!hasAllPermissions) {
                val description = if (missingPermissions.isNotEmpty()) {
                    "Missing: ${missingPermissions.joinToString(", ")}"
                } else {
                    "Required for contacts & logs."
                }
                SetupCard("Grant Permissions", description, "Grant", onRequestPermissions)
            }
        }
    }
}

@Composable
private fun SetupCard(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ComposeColor(0xFF12223E).copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ComposeColor.White, modifier = Modifier.padding(bottom = 12.dp))
        Text(
            text = description,
            fontSize = 14.sp,
            color = ComposeColor.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF3B82F6))) {
            Text(buttonText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}