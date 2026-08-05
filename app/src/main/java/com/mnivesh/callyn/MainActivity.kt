package com.mnivesh.callyn

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.webkit.CookieManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mnivesh.callyn.api.RetrofitInstance
import com.mnivesh.callyn.components.CallLogSyncTrigger
import com.mnivesh.callyn.managers.AuthManager
import com.mnivesh.callyn.managers.CallManager
import com.mnivesh.callyn.managers.DialerManager
import com.mnivesh.callyn.managers.PermissionManager
import com.mnivesh.callyn.managers.SimManager
import com.mnivesh.callyn.managers.ThemeManager
import com.mnivesh.callyn.screens.ConflictResolutionScreen
import com.mnivesh.callyn.screens.LoadingDetailsScreen
import com.mnivesh.callyn.screens.MainScreenWithDialerLogic
import com.mnivesh.callyn.ui.UpdateDialog
import com.mnivesh.callyn.ui.ZohoLoginScreen
import com.mnivesh.callyn.ui.theme.CallynTheme
import com.mnivesh.callyn.viewmodels.MainActivityUiState
import com.mnivesh.callyn.viewmodels.MainViewModel
import com.mnivesh.callyn.viewmodels.SmsViewModel
import com.mnivesh.callyn.workers.SyncUserDetailsWorker
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.graphics.Color
import android.app.NotificationManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private const val TAG = "MainActivity"

/**
 * Main activity handling startup, auth logic, and orchestrating the app's UI.
 */
class MainActivity : ComponentActivity() {

    private lateinit var authManager: AuthManager
    private val mainViewModel: MainViewModel by viewModels()
    private val smsViewModel: SmsViewModel by viewModels()

    lateinit var permissionManager: PermissionManager
    lateinit var dialerManager: DialerManager
    
    private var incomingDialNumber by mutableStateOf<String?>(null)
    private var isPermissionRequestInProgress = false

    private val multiplePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            isPermissionRequestInProgress = false
            val allGranted = results.values.all { it }
            if (allGranted) {
                checkLoginState()
            } else {
                Toast.makeText(this, "Contacts permission is required to continue.", Toast.LENGTH_LONG).show()
            }
        }

    private val defaultDialerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Log.d(TAG, "Dialer callback received.")
    }

    /**
     * Initializes activity, sets up managers, and sets the Compose content.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        authManager = AuthManager(this)
        permissionManager = PermissionManager(this)
        permissionManager.setLauncher(multiplePermissionLauncher)
        
        dialerManager = DialerManager(this, permissionManager)
        dialerManager.setLauncher(defaultDialerLauncher)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        if (!handleIntent(intent)) {
            checkLoginState()
        }

        setContent {
            val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
            val updateState by mainViewModel.updateState.collectAsStateWithLifecycle()
            val showUpdateDialog by mainViewModel.showUpdateDialog.collectAsStateWithLifecycle()

            val themeManager = remember { ThemeManager(this@MainActivity) }
            var isDarkTheme by remember { mutableStateOf<Boolean>(themeManager.isDarkTheme()) }

            CallynTheme(darkTheme = isDarkTheme) {
                if (SimManager.showSimSelectionDialog) {
                    Dialog(
                        onDismissRequest = {}, // Indismissible
                        properties = DialogProperties(
                            dismissOnBackPress = false,
                            dismissOnClickOutside = false
                        )
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SimCard,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "Select Work SIM",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "We detected dual SIMs but couldn't auto-identify your work number. Please select your Work SIM slot to sync work calls correctly.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    SimManager.availableSimsForSelection.forEach { sim ->
                                        OutlinedButton(
                                            onClick = {
                                                SimManager.saveManualWorkSim(this@MainActivity, sim.slotIndex)
                                                SimManager.showSimSelectionDialog = false
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = sim.displayName,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    if (!sim.number.isNullOrBlank()) {
                                                        Text(
                                                            text = sim.number,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "Slot ${sim.slotIndex + 1}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(start = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showUpdateDialog && updateState.versionInfo != null) {
                    UpdateDialog(
                        versionInfo = updateState.versionInfo!!,
                        isHardUpdate = updateState.isHardUpdate,
                        onDismiss = { mainViewModel.dismissUpdateDialog() },
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
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                androidx.compose.material3.CircularProgressIndicator()
                            }
                        }
                        is MainActivityUiState.Preparing -> {
                            LoadingDetailsScreen(
                                userName = state.userName,
                                onFinished = {
                                    authManager.setSetupCompleted(true)
                                    mainViewModel.setUiState(MainActivityUiState.LoggedIn(state.userName))
                                },
                                onConflictsFound = { conflicts, workContacts ->
                                    mainViewModel.setUiState(MainActivityUiState.ResolvingConflicts(
                                        state.userName,
                                        conflicts,
                                        workContacts
                                    ))
                                }
                            )
                        }
                        is MainActivityUiState.ResolvingConflicts -> {
                            ConflictResolutionScreen(
                                initialConflicts = state.conflicts,
                                workContacts = state.workContacts,
                                userName = state.userName,
                                onFinished = {
                                    mainViewModel.setUiState(MainActivityUiState.LoggedIn(state.userName))
                                },
                                onDeletePermissionRequest = {
                                    // Managed inside the screen itself now via rememberLauncherForActivityResult
                                }
                            )
                        }
                        is MainActivityUiState.LoggedIn -> {
                            val app = application as CallynApplication
                            val department = authManager.getDepartment()
                            
                            if(department != "Management") {
                                CallLogSyncTrigger(repository = app.repository)
                            }

                            MainScreenWithDialerLogic(
                                userName = state.userName,
                                incomingDialNumber = incomingDialNumber,
                                onConsumeIncomingNumber = { incomingDialNumber = null },
                                onLogout = { performLogout() },
                                callManager = dialerManager,
                                permissionManager = permissionManager,
                                isDarkTheme = isDarkTheme,
                                onThemeToggle = { dark ->
                                    themeManager.setDarkTheme(dark)
                                    isDarkTheme = dark
                                }
                            )
                        }
                        is MainActivityUiState.LoggedOut -> {
                            ZohoLoginScreen(onGuestLoginSuccess = {
                                checkLoginState()
                            })
                        }
                    }
                }
            }
        }
    }

    /**
     * Handles background checks when activity resumes.
     */
    override fun onResume() {
        super.onResume()
        if (!isPermissionRequestInProgress && (mainViewModel.uiState.value is MainActivityUiState.Loading || mainViewModel.uiState.value is MainActivityUiState.LoggedOut)) {
            checkLoginState()
        }
        if (authManager.isLoggedIn()) {
            mainViewModel.checkForUpdates()
            syncDeviceDetails()
        }

        if (permissionManager.checkAllPermissions()) {
            val workPhone = authManager.getWorkPhone()
            SimManager.detectSimRoles(this, workPhone)
        }

        lifecycleScope.launch {
            val token = authManager.getToken()
            val department = authManager.getDepartment()
            if (!token.isNullOrBlank() && (department == "IT Desk" || department == "Management")) {
                smsViewModel.fetchSmsLogs(token)
            }
        }
    }

    /**
     * Handles incoming intents from deep links or dial actions.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Checks storage permission compatibility for older devices.
     */
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

    /**
     * Manual update check triggered by user.
     */
    fun manualUpdateCheck() {
        mainViewModel.checkForUpdates(isManualCheck = true) { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Logs out the user and clears all data.
     */
    private fun performLogout() {
        lifecycleScope.launch {
            mainViewModel.setUiState(MainActivityUiState.Loading)
            val app = application as CallynApplication
            app.repository.clearAllData()
            authManager.logout()
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            mainViewModel.setUiState(MainActivityUiState.LoggedOut)
        }
    }

    /**
     * Verifies the auth token in background silently.
     */
    private fun verifyTokenInBackground(token: String) {
        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.getMe(token)
                if (response.isSuccessful && response.body() != null) {
                    authManager.saveUserName(response.body()!!.name)
                } else {
                    val errorMsg = "verifyTokenInBackground failed: HTTP code=${response.code()} - Error=${response.errorBody()?.string() ?: response.message()}"
                    Log.e(TAG, errorMsg)
                    com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().log(errorMsg)
                    com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(Exception("Token verification failed: HTTP ${response.code()}"))
                    performLogout()
                }
            } catch (e: Exception) {
                Log.d(TAG, "Offline mode active: ${e.message}")
            }
        }
    }

    /**
     * Syncs device details to server using a background worker.
     */
    internal fun syncDeviceDetails() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncUserDetailsWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            "SyncUserDetailsWork",
            ExistingWorkPolicy.KEEP,
            syncRequest
        )
    }

    /**
     * Verifies the user's login state and permissions.
     */
    private fun checkLoginState() {
        lifecycleScope.launch {
            val token = authManager.getToken()
            val userName = authManager.getUserName()
            val department = authManager.getDepartment() 

            if (!token.isNullOrBlank() && !userName.isNullOrBlank()) {
                if (department != "Management" && department != "IT Desk") {
                    val hasRead = androidx.core.content.ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.READ_CONTACTS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    val hasWrite = androidx.core.content.ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.WRITE_CONTACTS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

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

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            val notifManager = getSystemService(NotificationManager::class.java)
                            if (!notifManager.canUseFullScreenIntent()) {
                                startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                    data = Uri.parse("package:$packageName")
                                })
                            }
                        }
                        return@launch
                    }
                }

                if (authManager.isSetupCompleted()) {
                    mainViewModel.setUiState(MainActivityUiState.LoggedIn(userName))
                } else {
                    mainViewModel.setUiState(MainActivityUiState.Preparing(userName))
                }

            } else {
                mainViewModel.setUiState(MainActivityUiState.LoggedOut)
            }
        }
    }

    /**
     * Extracts deep link or dial parameters from intent.
     */
    private fun handleIntent(intent: Intent?): Boolean {
        val data: Uri? = intent?.data

        if (intent?.action == Intent.ACTION_DIAL || intent?.action == Intent.ACTION_VIEW) {
            if (data?.scheme == "tel") {
                val number = Uri.decode(data.schemeSpecificPart)
                incomingDialNumber = number
                return true
            }
        }

        if (data != null && "callyn" == data.scheme && "auth" == data.host) {
            val error = data.getQueryParameter("error")
            if (error != null) {
                if (error == "not_logged_in") {
                    Toast.makeText(this, "Please log into mNivesh Central first", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Login failed: $error", Toast.LENGTH_LONG).show()
                }
                return false
            }

            val token = data.getQueryParameter("accessToken")?.trim()?.takeIf { it.isNotEmpty() && it != "null" && it != "undefined" }
            val refreshToken = data.getQueryParameter("refreshToken")?.trim()?.takeIf { it.isNotEmpty() && it != "null" && it != "undefined" }
            val department = data.getQueryParameter("departmentName")
            val email = data.getQueryParameter("email")
            val name = data.getQueryParameter("name")
            val workPhone = data.getQueryParameter("associatedNumber")

            if (!token.isNullOrEmpty()) {
                authManager.saveToken(token)
                if (!refreshToken.isNullOrEmpty()) {
                    authManager.saveRefreshToken(refreshToken)
                }
                authManager.setSetupCompleted(false)
                authManager.saveUserName(name)
                authManager.saveUserEmail(email)
                if(!workPhone.isNullOrEmpty()) {
                    authManager.saveWorkPhone(workPhone)
                }
                if (department != null) {
                    authManager.saveDepartment(department)
                }
                return true
            }
        }
        return false
    }
}