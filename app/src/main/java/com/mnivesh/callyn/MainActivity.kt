package com.mnivesh.callyn

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.util.Log
import android.webkit.CookieManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
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
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.mnivesh.callyn.api.RetrofitInstance
import com.mnivesh.callyn.api.VersionResponse
import com.mnivesh.callyn.api.version
import com.mnivesh.callyn.components.AppDrawer
import com.mnivesh.callyn.components.DeviceContact
import com.mnivesh.callyn.components.DeviceNumber
import com.mnivesh.callyn.db.AppContact
import com.mnivesh.callyn.managers.AuthManager
import com.mnivesh.callyn.managers.SimManager
import com.mnivesh.callyn.managers.VersionManager
import com.mnivesh.callyn.screens.ContactsScreen
import com.mnivesh.callyn.screens.DialerScreen
import com.mnivesh.callyn.screens.PersonalRequestsScreen
import com.mnivesh.callyn.screens.RecentCallsScreen
import com.mnivesh.callyn.screens.ShowCallLogsScreen
import com.mnivesh.callyn.screens.UserDetailsScreen
import com.mnivesh.callyn.ui.EmployeeDirectoryScreen
import com.mnivesh.callyn.ui.UpdateDialog
import com.mnivesh.callyn.ui.ZohoLoginScreen
import com.mnivesh.callyn.ui.theme.CallynTheme
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
import com.mnivesh.callyn.viewmodels.SmsViewModel
import com.mnivesh.callyn.workers.SyncUserDetailsWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
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
    data class Preparing(val userName: String) : MainActivityUiState()
    data class ResolvingConflicts(
        val userName: String,
        val conflicts: List<DeviceContact>,
        val workContacts: List<AppContact> // Added workContacts to state
    ) : MainActivityUiState()
}

class MainActivity : ComponentActivity() {

    private lateinit var authManager: AuthManager
    private var uiState by mutableStateOf<MainActivityUiState>(MainActivityUiState.Loading)
    public var incomingDialNumber by mutableStateOf<String?>(null)
    // Version Check States
    private var updateState by mutableStateOf(UpdateState())
    private var showUpdateDialog by mutableStateOf(false)

    private lateinit var smsViewModel: SmsViewModel

    // Permissions
    private val permissionsToRequest = mutableListOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.WRITE_CALL_LOG,
    ).apply {
        add(Manifest.permission.READ_PHONE_NUMBERS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val multiplePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            isPermissionRequestInProgress = false
            // Check if ALL required were granted
            val allGranted = results.values.all { it }
            if (allGranted) {
                // Retry login now that we have permissions
                checkLoginState()
            } else {
                // OPTIONAL: If denied, force logout or show a Toast explanation
                Toast.makeText(
                    this,
                    "Contacts permission is required to continue.",
                    Toast.LENGTH_LONG
                ).show()
                // We leave them on 'Loading' or you can set uiState = LoggedOut
            }
        }
    private val defaultDialerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Log.d(TAG, "Dialer callback received.")
    }

    // Launcher for Conflict Resolution (Write Contacts)
    private val writeContactPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // Just toast, user will press button again
                Toast.makeText(this, "Permission granted. Tap Continue again.", Toast.LENGTH_SHORT)
                    .show()
            }
        }

//    private val smsPermissionLauncher =
//        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
//            val allGranted = results.values.all { it }
//            if (allGranted) {
//                Log.d("SMS_DEBUG", "SMS Permissions GRANTED by user.")
//                checkLoginState() // Re-check to proceed
//            } else {
//                Log.e("SMS_DEBUG", "SMS Permissions DENIED by user.")
//                Toast.makeText(this, "SMS Permission is required for Management features", Toast.LENGTH_LONG).show()
//            }
//        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authManager = AuthManager(this)
        smsViewModel = ViewModelProvider(this)[SmsViewModel::class.java]

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
            false

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
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        is MainActivityUiState.Preparing -> {
                            LoadingDetailsScreen(
                                userName = state.userName,
                                onFinished = {
                                    authManager.setSetupCompleted(true)
                                    uiState = MainActivityUiState.LoggedIn(state.userName)
                                },
                                onConflictsFound = { conflicts, workContacts ->
                                    uiState = MainActivityUiState.ResolvingConflicts(
                                        state.userName,
                                        conflicts,
                                        workContacts
                                    )
                                }
                            )
                        }

                        is MainActivityUiState.ResolvingConflicts -> {
                            ConflictResolutionScreen(
                                initialConflicts = state.conflicts,
                                workContacts = state.workContacts,
                                userName = state.userName,
                                onFinished = {
                                    uiState = MainActivityUiState.LoggedIn(state.userName)
                                },
                                onDeletePermissionRequest = {
                                    writeContactPermissionLauncher.launch(Manifest.permission.WRITE_CONTACTS)
                                }
                            )
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
        if (!isPermissionRequestInProgress && (uiState is MainActivityUiState.Loading || uiState is MainActivityUiState.LoggedOut)) {
            checkLoginState()
        }
       // checkStoragePermission()
        checkForUpdates()
        syncDeviceDetails()

        //check for sim data
        if (checkAllPermissions()) {
            val workPhone = authManager.getWorkPhone()
            SimManager.detectSimRoles(this, workPhone)
        }

        lifecycleScope.launch {
            val token = authManager.getToken()
            val department = authManager.getDepartment()

            // Assuming only IT Desk/Management should pull these logs
            if (!token.isNullOrBlank() && (department == "IT Desk" || department == "Management")) {
                smsViewModel.fetchSmsLogs(token)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    //check storage permission
    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        }
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
                        Toast.makeText(
                            this@MainActivity,
                            "You are already on the latest version",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else if (isManualCheck) {
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to check for updates",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                if (isManualCheck) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error: ${e.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
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
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncUserDetailsWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            "SyncUserDetailsWork",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    private var isPermissionRequestInProgress = false

    private fun checkLoginState() {
        lifecycleScope.launch {
            val token = authManager.getToken()
            val userName = authManager.getUserName()
            val department = authManager.getDepartment() // [!code ++] Get Department

            if (!token.isNullOrBlank() && !userName.isNullOrBlank()) {

//                if (department == "IT Desk") {
//                    val hasReceiveSms = ContextCompat.checkSelfPermission(
//                        this@MainActivity,
//                        Manifest.permission.RECEIVE_SMS
//                    ) == PackageManager.PERMISSION_GRANTED
//
//                    val hasReadSms = ContextCompat.checkSelfPermission(
//                        this@MainActivity,
//                        Manifest.permission.READ_SMS
//                    ) == PackageManager.PERMISSION_GRANTED
//
//                    if (!hasReceiveSms || !hasReadSms) {
//                        Log.d("SMS_DEBUG", "Management user missing SMS permissions. Requesting now...")
//                        if (!isPermissionRequestInProgress) {
//                            isPermissionRequestInProgress = true
//                            smsPermissionLauncher.launch(
//                                arrayOf(
//                                    Manifest.permission.RECEIVE_SMS,
//                                    Manifest.permission.READ_SMS
//                                )
//                            )
//                        }
//                        return@launch // Stop here, wait for callback
//                    } else {
//                        Log.d("SMS_DEBUG", "SMS Permissions already present.")
//                    }
//                }

                // [!code ++] Skip check for Management/IT Desk
                if (department != "Management" && department != "IT Desk") {
                    val hasRead = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.READ_CONTACTS
                    ) == PackageManager.PERMISSION_GRANTED
                    val hasWrite = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.WRITE_CONTACTS
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!hasRead || !hasWrite) {
                        if (!isPermissionRequestInProgress) {
                            isPermissionRequestInProgress = true
                            multiplePermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_CONTACTS,
                                    Manifest.permission.WRITE_CONTACTS
                                )
                            )
                        }
                        // Stop here. Wait for callback or onResume.
                        return@launch
                    }
                }

                // Permissions granted (or not needed), proceed.
                //uiState = MainActivityUiState.Preparing(userName)

                uiState = if (authManager.isSetupCompleted()) {

                    MainActivityUiState.LoggedIn(userName)
                } else {
                    MainActivityUiState.Preparing(userName)
                }

            } else {
                uiState = MainActivityUiState.LoggedOut
            }
        }
    }

    private fun handleIntent(intent: Intent?): Boolean {
        val data: Uri? = intent?.data

        if (intent?.action == Intent.ACTION_DIAL || intent?.action == Intent.ACTION_VIEW) {
            if (data?.scheme == "tel") {
                // Extract number (e.g., tel:12345 -> 12345)
                val number = Uri.decode(data.schemeSpecificPart)
                incomingDialNumber = number
                return true
            }
        }

        if (data != null && "callyn" == data.scheme && "auth" == data.host) {

            // 1. Check if the Store App sent an error
            val error = data.getQueryParameter("error")
            if (error != null) {
                if (error == "not_logged_in") {
                    Toast.makeText(this, "Please log into mNivesh Central first", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Login failed: $error", Toast.LENGTH_LONG).show()
                }
                return false // Stop execution
            }

            // 2. Proceed with normal login handling
            val token = data.getQueryParameter("token")
            val department = data.getQueryParameter("department")
            val email = data.getQueryParameter("email")
            val name = data.getQueryParameter("name")
            // Note: The Store app doesn't save work_phone yet based on AuthManager, but we'll accept it if added later
            val workPhone = data.getQueryParameter("work_phone")

            if (!token.isNullOrEmpty()) {
                authManager.saveToken(token)
                authManager.setSetupCompleted(false)
                // Save Department if present
                if (department != null) {
                    authManager.saveUserName(name)
                    authManager.saveDepartment(department)
                    authManager.saveUserEmail(email)
                    authManager.saveWorkPhone(workPhone)
                }

                Log.d(TAG, "Saved details: Name= $name Dept=$department, Email=$email, Phone=$workPhone")

                return true
            }
        }
        return false
    }

    private fun fetchUserName(token: String, isFreshLogin: Boolean = false) {
        lifecycleScope.launch {
            uiState = MainActivityUiState.Loading
            try {
                val response = RetrofitInstance.api.getMe(token)
                if (response.isSuccessful && response.body() != null) {
                    val name = response.body()!!.name
                    authManager.saveUserName(name)
                    if (isFreshLogin) {
                        uiState = MainActivityUiState.Preparing(name)
                    } else {
                        uiState = MainActivityUiState.LoggedIn(name)
                    }
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

    fun offerDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)

            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) && !isDefaultDialer()) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                defaultDialerLauncher.launch(intent)
            }

            val roleScreening = RoleManager.ROLE_CALL_SCREENING
            if (roleManager.isRoleAvailable(roleScreening) && !roleManager.isRoleHeld(roleScreening)) {
                val intent = roleManager.createRequestRoleIntent(roleScreening)
                defaultDialerLauncher.launch(intent)
            }
        } else {
            if (!isDefaultDialer()) {
                try {
                    val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                        .putExtra(
                            TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                            packageName
                        )
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
    fun dialSmart(number: String, isWorkCall: Boolean, specificSlot: Int? = null) {
        if (!isDefaultDialer()) { offerDefaultDialer(); return }
        if (!checkAllPermissions()) { requestPermissions(); return }

        val numberToDial = if (number.filter { it.isDigit() }.length >= 11 && !number.startsWith('+')) "+${number.filter { it.isDigit() }}" else number

        try {
            val telecomManager = getSystemService(TelecomManager::class.java)
            val subscriptionManager = getSystemService(SubscriptionManager::class.java)
            val activeSims = subscriptionManager.activeSubscriptionInfoList

            if (activeSims.isNullOrEmpty()) {
                Toast.makeText(this, "No SIM card found", Toast.LENGTH_SHORT).show()
                return
            }

            // --- STRICT SLOT SELECTION ---
            // 1. Use specific slot if user forced it (via fallback UI).
            // 2. Else, use the Auto-Detected Work/Personal Slot.
            val targetSlotIndex = specificSlot ?: if (isWorkCall) SimManager.workSimSlot else SimManager.personalSimSlot

            val selectedSim = if (targetSlotIndex != null) activeSims.find { it.simSlotIndex == targetSlotIndex } else null

            val uri = Uri.fromParts("tel", numberToDial, null)
            val intent = Intent(Intent.ACTION_CALL, uri)

            if (selectedSim != null) {
                // Force call through the specific SIM
                val targetHandle = findHandleForSubId(telecomManager, selectedSim.subscriptionId)
                if (targetHandle != null) {
                    intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, targetHandle)
                }
                Toast.makeText(this, "Dialing via ${selectedSim.displayName} (${if(isWorkCall) "Work" else "Personal"})", Toast.LENGTH_SHORT).show()
            } else {
                // Fallback: System chooser (only if detection failed and no specific slot passed)
                Log.d(TAG, "Smart Dial: No specific SIM determined. Using system default.")
            }

            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Smart dial failed", e)
            Toast.makeText(this, "Call failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun findHandleForSubId(
        telecomManager: TelecomManager,
        subId: Int
    ): PhoneAccountHandle? {
        return telecomManager.callCapablePhoneAccounts.firstOrNull { handle ->
            handle.id.contains(subId.toString())
        }
    }
}

// --- LOADING SCREEN WITH CONFLICT CHECK ---

@Composable
fun LoadingDetailsScreen(
    userName: String,
    onFinished: () -> Unit,
    onConflictsFound: (List<DeviceContact>, List<AppContact>) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as CallynApplication
    val authManager = remember { AuthManager(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // 1. State to track if we are ready to load data
    var arePermissionsGranted by remember { mutableStateOf(false) }
    var shouldCheckPermissions by remember { mutableStateOf(true) }

    // 2. Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // We don't rely solely on callback, OnResume handles the check cleanly
    }

    // 3. Lifecycle Observer: Re-check permissions whenever user returns to app
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val dept = authManager.getDepartment() ?: ""

                // If Management/IT, we treat permissions as "granted" (not needed)
                if (dept == "Management") {
                    arePermissionsGranted = true
                    shouldCheckPermissions = false
                } else {
                    // For others, check actual Android permissions
                    val hasRead = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_CONTACTS
                    ) == PackageManager.PERMISSION_GRANTED
                    val hasWrite = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_CONTACTS
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasRead && hasWrite) {
                        arePermissionsGranted = true
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 4. Initial Trigger: Request Permissions if missing
    LaunchedEffect(Unit) {
        val dept = authManager.getDepartment() ?: ""
        if (dept != "Management") {
            val hasRead = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
            val hasWrite = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasRead || !hasWrite) {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.WRITE_CONTACTS,
                        Manifest.permission.READ_PHONE_STATE
                    )
                )
            } else {
                arePermissionsGranted = true
            }
        } else {
            arePermissionsGranted = true
        }
    }

    // 5. Main Logic: Runs ONLY when arePermissionsGranted becomes true
    LaunchedEffect(arePermissionsGranted) {
        if (!arePermissionsGranted) return@LaunchedEffect

        // --- START LOADING DATA ---
        val token = authManager.getToken()
        if (token != null) {
            // Sync Data
            lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                app.repository.refreshCrmData(token)
            }
            withContext(Dispatchers.IO) {
                app.repository.syncInitialData(token, userName)
            }
            // Small delay for UX/Animation
            delay(1500)

            // Conflict Check
            val dept = authManager.getDepartment()
//            if (dept != "Management" && dept != "IT Desk") {
            if (dept == "ConflictCheckPaused") {
                // We know permissions are granted now because of the check above
                val workContacts = withContext(Dispatchers.IO) {
                    app.repository.allContacts.first()
                        // Ensure we don't flag "Employee" contacts as conflicts
                        .filter { !(it.rshipManager ?: "").equals("Employee", ignoreCase = true) }
                }

                val deviceContacts = loadDeviceContacts(context)

                // Use the updated findConflicts function (ensure it's the one that handles nulls/formatting)
                val conflicts = findConflicts(deviceContacts, workContacts)

                if (conflicts.isNotEmpty()) {
                    onConflictsFound(conflicts, workContacts)
                    return@LaunchedEffect // Stop! Don't call onFinished
                }
            }
        }
        onFinished()
    }

    // --- UI ---
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.loading))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        ComposeColor(0xFF1E293B),
                        ComposeColor(0xFF0F172A)
                    )
                )
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.size(200.sdp())
        )
        Spacer(modifier = Modifier.height(24.sdp()))
        Text(
            "Loading your details...",
            fontSize = 18.ssp(),
            fontWeight = FontWeight.Medium,
            color = ComposeColor.White
        )

        if (!arePermissionsGranted) {
            Spacer(modifier = Modifier.height(8.sdp()))
            Text("Waiting for permissions...", fontSize = 14.ssp(), color = ComposeColor(0xFFF59E0B))
        }
    }
}

// --- CONFLICT RESOLUTION SCREEN ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictResolutionScreen(
    initialConflicts: List<DeviceContact>,
    workContacts: List<AppContact>,
    userName: String,
    onFinished: () -> Unit,
    onDeletePermissionRequest: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as CallynApplication
    val authManager = remember { AuthManager(context) }
    val scope = rememberCoroutineScope()

    // 1. FAILSAFE: Ensure we have a valid user name
    val effectiveUserName = remember { authManager.getUserName() ?: "" }

    var conflicts by remember { mutableStateOf(initialConflicts) }
    var searchQuery by remember { mutableStateOf("") }
    var isDeleting by remember { mutableStateOf(false) }

    // Track which IDs have successfully sent requests
    val sentRequestIds = remember { mutableStateListOf<String>() }

    // Dialog States
    var showRequestDialog by remember { mutableStateOf(false) }
    var contactNameForRequest by remember { mutableStateOf<String?>(null) }
    var contactIdForRequest by remember { mutableStateOf<String?>(null) }
    var requestReason by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) showDeleteConfirm = true
        else Toast.makeText(context, "Permission needed to clear conflicts", Toast.LENGTH_SHORT)
            .show()
    }

    val filteredConflicts = remember(searchQuery, conflicts) {
        if (searchQuery.isBlank()) conflicts
        else conflicts.filter {
            it.name.contains(searchQuery, true) ||
                    it.numbers.any { n -> n.number.contains(searchQuery) }
        }
    }

    Scaffold(
        containerColor = ComposeColor(0xFF0F172A),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ComposeColor(0xFF0F172A))
                    .systemBarsPadding()
                    .padding(horizontal = 24.sdp(), vertical = 26.sdp())
            ) {
                Text(
                    "Resolve Conflicts",
                    fontSize = 25.ssp(),
                    fontWeight = FontWeight.Bold,
                    color = ComposeColor.White
                )
                Spacer(modifier = Modifier.height(8.sdp()))
                Text(
                    "We found ${conflicts.size} contacts that are already assigned by your company. Please delete the duplicates from your device.",
                    fontSize = 14.ssp(),
                    color = ComposeColor.White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(16.sdp()))

                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.sdp())),
                    placeholder = {
                        Text(
                            "Search conflicts...",
                            color = ComposeColor.White.copy(alpha = 0.5f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            "Search",
                            tint = ComposeColor.White.copy(alpha = 0.6f)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Default.Close,
                                    "Clear",
                                    tint = ComposeColor.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = ComposeColor.White,
                        unfocusedTextColor = ComposeColor.White,
                        focusedContainerColor = ComposeColor.White.copy(alpha = 0.1f),
                        unfocusedContainerColor = ComposeColor.White.copy(alpha = 0.08f),
                        focusedIndicatorColor = ComposeColor.Transparent,
                        unfocusedIndicatorColor = ComposeColor.Transparent,
                        cursorColor = ComposeColor.White
                    )
                )
            }
        },
        bottomBar = {
            Column(modifier = Modifier
                .background(ComposeColor(0xFF0F172A))
                .padding(44.sdp())) {
                Button(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.WRITE_CONTACTS
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            showDeleteConfirm = true
                        } else {
                            writePermissionLauncher.launch(Manifest.permission.WRITE_CONTACTS)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.sdp()),
                    shape = RoundedCornerShape(16.sdp()),
                    colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFFEF4444))
                ) {
                    if (isDeleting) CircularProgressIndicator(
                        color = ComposeColor.White,
                        modifier = Modifier.size(24.sdp())
                    )
                    else Text(
                        "Delete All (${conflicts.size})",
                        fontSize = 16.ssp(),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.sdp()),
            verticalArrangement = Arrangement.spacedBy(12.sdp()),
            contentPadding = PaddingValues(top = 16.sdp(), bottom = 16.sdp())
        ) {
            items(filteredConflicts) { contact ->
                ModernConflictItem(
                    contact = contact,
                    isRequestSent = sentRequestIds.contains(contact.id),
                    onRequestClick = {
                        // Work Match Logic
                        val workMatch = workContacts.firstOrNull { work ->
                            contact.numbers.any { numObj ->
                                sanitizePhoneNumber(work.number) == sanitizePhoneNumber(
                                    numObj.number
                                )
                            }
                        }

                        contactNameForRequest = workMatch?.name ?: contact.name
                        contactIdForRequest = contact.id
                        showRequestDialog = true
                    }
                )
            }
            if (filteredConflicts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.sdp()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No conflicts found", color = ComposeColor.White.copy(alpha = 0.5f))
                    }
                }
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                containerColor = ComposeColor(0xFF1E293B),
                title = {
                    Text(
                        "Confirm Deletion",
                        color = ComposeColor.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        "Are you sure you want to delete these ${conflicts.size} contacts? This ensures your Work contacts are synced correctly.",
                        color = ComposeColor.White.copy(alpha = 0.8f)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirm = false
                            isDeleting = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    conflicts.forEach { contact ->
                                        val uri = Uri.withAppendedPath(
                                            ContactsContract.Contacts.CONTENT_URI,
                                            contact.id
                                        )
                                        context.contentResolver.delete(uri, null, null)
                                    }
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            "Cleaned up contacts",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        onFinished()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) { isDeleting = false }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ComposeColor(
                                0xFFEF4444
                            )
                        )
                    ) { Text("Delete", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text(
                            "Cancel",
                            color = ComposeColor.White.copy(alpha = 0.6f)
                        )
                    }
                }
            )
        }

        if (showRequestDialog) {
            AlertDialog(
                onDismissRequest = { showRequestDialog = false },
                containerColor = ComposeColor(0xFF1E293B),
                title = {
                    Text(
                        "Mark as Personal",
                        color = ComposeColor.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            "Why should ${contactNameForRequest ?: "this contact"} remain on your device as Personal?",
                            color = ComposeColor.White.copy(alpha = 0.8f),
                            fontSize = 14.ssp(),
                            modifier = Modifier.padding(bottom = 16.sdp())
                        )
                        OutlinedTextField(
                            value = requestReason,
                            onValueChange = { requestReason = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    "Reason (e.g. Relative, Friend)",
                                    color = ComposeColor.Gray
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ComposeColor(0xFF3B82F6),
                                unfocusedBorderColor = ComposeColor.White.copy(alpha = 0.2f),
                                focusedTextColor = ComposeColor.White,
                                unfocusedTextColor = ComposeColor.White,
                                cursorColor = ComposeColor(0xFF3B82F6),
                                focusedContainerColor = ComposeColor.White.copy(alpha = 0.05f),
                                unfocusedContainerColor = ComposeColor.White.copy(alpha = 0.05f)
                            ),
                            shape = RoundedCornerShape(12.sdp()),
                            minLines = 3,
                            maxLines = 5
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val reasonToSend = requestReason
                            val contactToSend = contactNameForRequest
                            val idToSend = contactIdForRequest
                            val token = authManager.getToken()

                            if (reasonToSend.isNotBlank() && contactToSend != null && token != null && effectiveUserName.isNotBlank()) {
                                scope.launch(Dispatchers.IO) {
                                    val success = app.repository.submitPersonalRequest(
                                        token = token,
                                        contactName = contactToSend,
                                        userName = effectiveUserName,
                                        reason = reasonToSend
                                    )
                                    withContext(Dispatchers.Main) {
                                        if (success && idToSend != null) {
                                            sentRequestIds.add(idToSend)
                                            Toast.makeText(
                                                context,
                                                "Request Submitted",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Failed to submit request",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                                showRequestDialog = false
                                requestReason = ""
                            } else {
                                Toast.makeText(context, "Please enter a reason", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ComposeColor(
                                0xFF3B82F6
                            )
                        )
                    ) { Text("Submit") }
                },
                dismissButton = {
                    TextButton(onClick = { showRequestDialog = false }) {
                        Text(
                            "Cancel",
                            color = ComposeColor.White.copy(alpha = 0.6f)
                        )
                    }
                }
            )
        }
    }
}

// --- HELPER FUNCTIONS ---

suspend fun loadDeviceContacts(context: Context): List<DeviceContact> =
    withContext(Dispatchers.IO) {
        val contactsMap = mutableMapOf<String, DeviceContact>()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.STARRED,
                ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY
            ),
            null, null, null
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val starredIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)
            val defaultIndex =
                it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY)

            while (it.moveToNext()) {
                val id = it.getString(idIndex)
                val name = it.getString(nameIndex) ?: "Unknown"
                val rawNumber = it.getString(numberIndex)?.replace("\\s".toRegex(), "") ?: ""
                val isStarred = it.getInt(starredIndex) == 1
                val isDefault = it.getInt(defaultIndex) > 0

                if (rawNumber.isNotEmpty()) {
                    val numberObj = DeviceNumber(rawNumber, isDefault)
                    if (contactsMap.containsKey(id)) {
                        val existing = contactsMap[id]!!
                        if (existing.numbers.none { n -> n.number == rawNumber }) {
                            contactsMap[id] = existing.copy(numbers = existing.numbers + numberObj)
                        }
                    } else {
                        contactsMap[id] = DeviceContact(id, name, listOf(numberObj), isStarred)
                    }
                }
            }
        }
        contactsMap.values.toList()
    }

fun findConflicts(
    deviceContacts: List<DeviceContact>,
    workContacts: List<AppContact>
): List<DeviceContact> {
    return deviceContacts.filter { device ->
        device.numbers.any { numObj ->
            val deviceNum = sanitizePhoneNumber(numObj.number)
            if (deviceNum.length < 5) false
            else workContacts.any { work ->
                // [!code ++]
                !work.rshipManager.equals("Employee", ignoreCase = true) &&
                        // [!code --]
                        sanitizePhoneNumber(work.number) == deviceNum
            }
        }
    }
}

private fun sanitizePhoneNumber(number: String): String {
    val digits = number.filter { it.isDigit() }
    return if (digits.length > 10) digits.takeLast(10) else digits
}

@Composable
fun ModernConflictItem(
    contact: DeviceContact,
    isRequestSent: Boolean,
    onRequestClick: () -> Unit
) {
    val palette = listOf(
        ComposeColor(0xFF6366F1), ComposeColor(0xFFEC4899), ComposeColor(0xFF8B5CF6),
        ComposeColor(0xFF10B981), ComposeColor(0xFFF59E0B), ComposeColor(0xFFEF4444),
        ComposeColor(0xFF3B82F6), ComposeColor(0xFF14B8A6), ComposeColor(0xFFF97316)
    )
    val avatarColor = palette[abs(contact.name.hashCode()) % palette.size]

    val initials = contact.name.split(" ")
        .mapNotNull { word -> word.firstOrNull { it.isLetter() }?.uppercaseChar() }
        .take(2)
        .joinToString("")
        .ifEmpty { contact.name.firstOrNull { it.isLetter() }?.uppercase() ?: "" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.sdp()),
        colors = CardDefaults.cardColors(containerColor = ComposeColor.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.padding(12.sdp()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.sdp())
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                avatarColor,
                                avatarColor.copy(alpha = 0.7f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initials,
                    color = ComposeColor.White,
                    fontSize = 18.ssp(),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(16.sdp()))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    contact.name,
                    color = ComposeColor.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.ssp()
                )
//                Text(
//                    contact.numbers.firstOrNull()?.number ?: "",
//                    color = ComposeColor.White.copy(alpha = 0.6f),
//                    fontSize = 13.ssp()
//                )
            }

            if (isRequestSent) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Sent",
                    tint = ComposeColor(0xFF10B981),
                    modifier = Modifier
                        .size(28.sdp())
                        .padding(end = 8.sdp())
                )
            } else {
                OutlinedButton(
                    onClick = onRequestClick,
                    border = BorderStroke(
                        1.sdp(),
                        ComposeColor(0xFF60A5FA)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ComposeColor(
                            0xFF60A5FA
                        )
                    ),
                    shape = RoundedCornerShape(8.sdp()),
                    contentPadding = PaddingValues(horizontal = 12.sdp(), vertical = 8.sdp())
                ) {
                    Text("Mark Personal", fontSize = 11.ssp())
                }
            }
        }
    }
}

// --- COMPOSABLE HELPERS (MainScreenWithDialerLogic, etc.) ---

@Composable
fun MainActivity.MainScreenWithDialerLogic(userName: String, onLogout: () -> Unit) {

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
        incomingNumber = incomingDialNumber,
        onConsumeIncomingNumber = { incomingDialNumber = null },
        hasAllPermissions = hasAllPermissions,
        isDefaultDialer = isDefaultDialer,
        missingPermissions = missingPermissions,
        missedCallCount = missedCallCount,
        onRequestPermissions = { requestPermissions() },
        onRequestDefaultDialer = { offerDefaultDialer() },
        onSmartDial = { number, isWork, slot -> this.dialSmart(number, isWork, slot) }, // [!code updated]
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
    object EmployeeDirectory : Screen("employee_directory")
}

@Composable
fun MainScreen(
    userName: String,
    incomingNumber: String?,
    onConsumeIncomingNumber: () -> Unit,
    hasAllPermissions: Boolean,
    isDefaultDialer: Boolean,
    missingPermissions: List<String>,
    missedCallCount: Int,
    onRequestPermissions: () -> Unit,
    onRequestDefaultDialer: () -> Unit,
    onSmartDial: (String, Boolean, Int?) -> Unit, // [!code updated] Added Boolean for isWork
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
        MainScreenContent(
            userName,
            onSmartDial,
            onLogout,
            incomingNumber,
            onConsumeIncomingNumber,
            missedCallCount,
            onResetMissedCount
        )
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScreenContent(
    userName: String,
    onSmartDial: (String, Boolean, Int?) -> Unit,
    onLogout: () -> Unit,
    incomingNumber: String?,
    onConsumeIncomingNumber: () -> Unit,
    missedCallCount: Int,
    onResetMissedCount: () -> Unit
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(incomingNumber) {
        if (!incomingNumber.isNullOrEmpty()) {
            // 1. Switch to Dialer Tab
            navController.navigate(Screen.Dialer.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Move Drawer to the Root
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                userName = userName,
                onSync = {
                    scope.launch {
                        val token = AuthManager(context).getToken()
                        if (token != null) {
                            Toast.makeText(context, "Syncing Work Contacts...", Toast.LENGTH_SHORT)
                                .show()
                            val app = context.applicationContext as CallynApplication
                            // Use repository to sync contacts
                            val isSuccess = app.repository.refreshContacts(token, userName)

                            if (isSuccess) {
                                Toast.makeText(context, "Sync Successful!", Toast.LENGTH_SHORT)
                                    .show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Sync Failed. Check Internet.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            Toast.makeText(context, "Authentication Error", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                },
                onLogout = onLogout,
                onClose = { scope.launch { drawerState.close() } },
                onShowRequests = {
                    scope.launch { drawerState.close() }
                    navController.navigate(Screen.Requests.route)
                },
                onShowUserDetails = {
                    scope.launch { drawerState.close() }
                    navController.navigate(Screen.UserDetails.route)
                },
                onShowDirectory = {
                    scope.launch { drawerState.close() }
                    navController.navigate(Screen.EmployeeDirectory.route)
                },
                onShowCallLogs = {
                    scope.launch { drawerState.close() }
                    navController.navigate(Screen.ShowCallLogs.route)
                }
            )
        }
    ) {
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
                        onCallClick = { number, isWork, slot -> onSmartDial(number, isWork, slot) },
                        onScreenEntry = onResetMissedCount
                    )
                }
                composable(Screen.Contacts.route) {
                    ContactsScreen(
                        onContactClick = { number, isWork, slot -> onSmartDial(number, isWork, slot) },
//                        onLogout = onLogout,
//                        onShowUserDetails = { navController.navigate(Screen.UserDetails.route) },
//                        onShowCallLogs = { navController.navigate(Screen.ShowCallLogs.route) },
//                        onShowRequests = { navController.navigate(Screen.Requests.route) },
//                        onShowDirectory = { navController.navigate(Screen.EmployeeDirectory.route) },
                        onOpenDrawer = { scope.launch { drawerState.open() } }
                    )
                }
                composable(Screen.Dialer.route) {
                    DialerScreen(
                        onCallClick = { number, isWork, slot -> onSmartDial(number, isWork, slot)},
                                incomingNumber = incomingNumber,
                        onConsumeIncomingNumber = onConsumeIncomingNumber
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
                composable(Screen.EmployeeDirectory.route) {
                    EmployeeDirectoryScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onCallClick = { number, slot ->
                            // Employees are ALWAYS Work contacts
                            onSmartDial(number, true, slot)
                        }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomNavigationBar(navController: NavController, missedCallCount: Int) {
    val items = listOf(Screen.Recents, Screen.Contacts, Screen.Dialer)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(
        containerColor = ComposeColor(0xFF080C17),
        contentColor = ComposeColor.White,
        tonalElevation = 0.sdp()
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
            .background(
                Brush.verticalGradient(
                    listOf(
                        ComposeColor(0xFF1E293B),
                        ComposeColor(0xFF0F172A)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(24.sdp())
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Callyn",
                fontSize = 48.ssp(),
                fontWeight = FontWeight.Bold,
                color = ComposeColor.White
            )
            Spacer(modifier = Modifier.height(32.sdp()))

            if (!isDefaultDialer) {
                SetupCard(
                    "Set as Default",
                    "Required to make and receive calls.",
                    "Set Default",
                    onRequestDefaultDialer
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Spacer(modifier = Modifier.height(24.sdp()))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = ComposeColor(0xFFF59E0B).copy(
                                alpha = 0.15f
                            )
                        ),
                        shape = RoundedCornerShape(16.sdp()),
                        border = androidx.compose.foundation.BorderStroke(
                            1.sdp(),
                            ComposeColor(0xFFF59E0B).copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.sdp()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = ComposeColor(0xFFF59E0B),
                                modifier = Modifier.size(24.sdp())
                            )
                            Spacer(modifier = Modifier.height(8.sdp()))
                            Text(
                                text = "Blocked by Android?",
                                fontWeight = FontWeight.Bold,
                                color = ComposeColor(0xFFF59E0B),
                                fontSize = 16.ssp()
                            )
                            Spacer(modifier = Modifier.height(8.sdp()))
                            Text(
                                text = "If you are unable to set this app as default, you likely need to allow 'Restricted Settings'.",
                                color = ComposeColor.White.copy(alpha = 0.8f),
                                fontSize = 13.ssp(),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.sdp()))

                            OutlinedButton(
                                onClick = openAppSettings,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.sdp(),
                                    ComposeColor(0xFFF59E0B)
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = ComposeColor(
                                        0xFFF59E0B
                                    )
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("1. Open Settings")
                            }

                            Spacer(modifier = Modifier.height(8.sdp()))
                            Text(
                                text = "Go to 'App Info' > Tap 3 dots (top right) > 'Allow restricted settings', then come back here.",
                                color = ComposeColor.White.copy(alpha = 0.6f),
                                fontSize = 11.ssp(),
                                textAlign = TextAlign.Center,
                                lineHeight = 14.ssp()
                            )
                        }
                    }
                }

            } else if (!hasAllPermissions) {

                val formatted = if (missingPermissions.isNotEmpty()) {
                    missingPermissions.joinToString(", ") {
                        formatPermission(it)
                    }
                } else {
                    "Required for contacts & logs."
                }

                val description = "Missing: $formatted"

                SetupCard(
                    "Grant Permissions",
                    description,
                    "Grant",
                    onRequestPermissions
                )
            }
        }
    }
}

fun formatPermission(permission: String): String {
    return permission
        .removePrefix("android.permission.")
        .split("_")
        .joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
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
            .background(ComposeColor(0xFF12223E).copy(alpha = 0.6f), RoundedCornerShape(24.sdp()))
            .padding(24.sdp()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            fontSize = 22.ssp(),
            fontWeight = FontWeight.Bold,
            color = ComposeColor.White,
            modifier = Modifier.padding(bottom = 12.sdp())
        )
        Text(
            text = description,
            fontSize = 14.ssp(),
            color = ComposeColor.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.sdp())
        )
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.sdp()),
            colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF3B82F6))
        ) {
            Text(buttonText, fontSize = 16.ssp(), fontWeight = FontWeight.SemiBold)
        }
    }
}