package com.example.callyn

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
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
import com.example.callyn.ui.ZohoLoginScreen
import com.example.callyn.ui.theme.CallynTheme
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"

sealed class MainActivityUiState {
    object Loading : MainActivityUiState()
    object LoggedOut : MainActivityUiState()
    data class LoggedIn(val userName: String) : MainActivityUiState()
}

class MainActivity : ComponentActivity() {

    private lateinit var authManager: AuthManager
    private var uiState by mutableStateOf<MainActivityUiState>(MainActivityUiState.Loading)

    // Critical permissions for a Dialer App
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
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        // 1. Check if opened via Deep Link (Login redirect), otherwise check session
        if (!handleIntent(intent)) {
            checkLoginState()
        }

        setContent {
            CallynTheme {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun performLogout() {
        lifecycleScope.launch {
            uiState = MainActivityUiState.Loading
            val app = application as CallynApplication
            app.repository.clearAllData()
            authManager.logout() // Use clearSession from AuthManager

            // Clear Cookies (for Zoho WebView if applicable)
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()

            uiState = MainActivityUiState.LoggedOut
        }
    }

    private fun checkLoginState() {
        val token = authManager.getToken()
        if (token != null) {
            fetchUserName("Bearer $token")
        } else {
            uiState = MainActivityUiState.LoggedOut
        }
    }

    // Handles callyn://auth?token=...
    private fun handleIntent(intent: Intent?): Boolean {
        val data: Uri? = intent?.data
        if (data != null && "callyn" == data.scheme && "auth" == data.host) {
            val token = data.getQueryParameter("token")
            if (!token.isNullOrEmpty()) {
                authManager.saveToken(token)
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

                    // --- IMPORTANT FIX: Save User Name ---
                    // This allows ContactsScreen to read it from AuthManager
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

    // --- Permissions & Dialer Logic ---

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
        val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
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
        if (isDefaultDialer()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                    defaultDialerLauncher.launch(intent)
                }
            } catch (e: Exception) {}
        } else {
            try {
                val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                    .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                defaultDialerLauncher.launch(intent)
            } catch (e: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    fun dialNumber(number: String) {
        if (!isDefaultDialer()) { offerDefaultDialer(); return }
        if (!checkAllPermissions()) { requestPermissions(); return }
        try {
            val numberToDial = if (number.filter { it.isDigit() }.length >= 11 && !number.startsWith('+')) {
                "+${number.filter { it.isDigit() }}"
            } else {
                number
            }

            val uri = Uri.fromParts("tel", numberToDial, null)
            val intent = Intent(Intent.ACTION_CALL, uri)
            startActivity(intent)
        } catch (e: Exception) { Log.e(TAG, "Failed to call", e) }
    }
}

// --- Composable Helpers ---

@Composable
fun MainActivity.MainScreenWithDialerLogic(userName: String, onLogout: () -> Unit) {
    var hasAllPermissions by remember { mutableStateOf(checkAllPermissions()) }
    var isDefaultDialer by remember { mutableStateOf(isDefaultDialer()) }
    var missingPermissions by remember { mutableStateOf(getMissingPermissions()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAllPermissions = checkAllPermissions()
                isDefaultDialer = isDefaultDialer()
                missingPermissions = getMissingPermissions()
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
        onRequestPermissions = { requestPermissions() },
        onRequestDefaultDialer = { offerDefaultDialer() },
        onContactClick = { number -> dialNumber(number) },
        onLogout = onLogout
    )
}

sealed class Screen(val route: String) {
    object Recents : Screen("recents")
    object Contacts : Screen("contacts")
    object Dialer : Screen("dialer")
}

@Composable
fun MainScreen(
    userName: String,
    hasAllPermissions: Boolean,
    isDefaultDialer: Boolean,
    missingPermissions: List<String>,
    onRequestPermissions: () -> Unit,
    onRequestDefaultDialer: () -> Unit,
    onContactClick: (String) -> Unit,
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
        MainScreenContent(userName, onContactClick, onLogout)
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScreenContent(
    userName: String,
    onContactClick: (String) -> Unit,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = { BottomNavigationBar(navController) }
    ) { padding ->
        NavHost(
            navController,
            startDestination = Screen.Contacts.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Recents.route) {
                RecentCallsScreen(onCallClick = onContactClick)
            }
            composable(Screen.Contacts.route) {
                // --- FIXED: Do not pass userName here ---
                // ContactsScreen now fetches it internally via AuthManager
                ContactsScreen(
                    onContactClick = onContactClick,
                    onLogout = onLogout
                )
            }
            composable(Screen.Dialer.route) {
                DialerScreen()
            }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavController) {
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
                    when (screen) {
                        Screen.Recents -> Icon(Icons.Filled.History, "Recents")
                        Screen.Contacts -> Icon(Icons.Filled.Contacts, "Contacts")
                        Screen.Dialer -> Icon(Icons.Filled.Dialpad, "Dialer")
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ComposeColor(0xFF1E293B), ComposeColor(0xFF0F172A)))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp).systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Callyn", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = ComposeColor.White)
            Spacer(modifier = Modifier.height(48.dp))

            if (!isDefaultDialer) {
                SetupCard("Set as Default", "Required to make calls.", "Set Default", onRequestDefaultDialer)
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