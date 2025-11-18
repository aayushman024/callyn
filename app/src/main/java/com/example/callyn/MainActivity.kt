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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
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

    // *** UPDATE: Added RECORD_AUDIO ***
    private val permissionsToRequest = mutableListOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.WRITE_CALL_LOG,
        Manifest.permission.RECORD_AUDIO // <--- NEW
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val multiplePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            Log.d(TAG, "Permission launcher callback received.")
        }
    private val defaultDialerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Log.d(TAG, "Default dialer launcher callback received with code: ${it.resultCode}")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authManager = AuthManager(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

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
                            MainScreenWithDialerLogic(state.userName)
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

    private fun checkLoginState() {
        val token = authManager.getToken()
        if (token != null) {
            fetchUserName("Bearer $token")
        } else {
            uiState = MainActivityUiState.LoggedOut
        }
    }

    private fun handleIntent(intent: Intent?): Boolean {
        val data: Uri? = intent?.data
        if (data != null && "callyn" == data.scheme && "auth" == data.host) {
            val token = data.getQueryParameter("token")
            if (!token.isNullOrEmpty()) {
                authManager.saveToken(token)
                fetchUserName("Bearer $token")
                return true
            } else if (data.getQueryParameter("login") == "failed") {
                uiState = MainActivityUiState.LoggedOut
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
                    //authManager.saveUserName(name)

                    uiState = MainActivityUiState.LoggedIn(name)

                    // Trigger Sync
                    val app = application as CallynApplication
                  //  app.repository.syncPendingLogs(token, name)

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

    fun checkAllPermissions(): Boolean {
        return permissionsToRequest.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
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
            } catch (e: Exception) { Log.e(TAG, "RoleManager failed", e) }
        } else {
            try {
                val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                    .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                defaultDialerLauncher.launch(intent)
            } catch (e: Exception) { Log.e(TAG, "TelecomManager failed", e) }
        }
    }
    @SuppressLint("MissingPermission")
    fun dialNumber(number: String) {
        if (!isDefaultDialer()) { offerDefaultDialer(); return }
        if (!checkAllPermissions()) { requestPermissions(); return }
        try {
            val uri = Uri.fromParts("tel", number, null)
            val intent = Intent(Intent.ACTION_CALL, uri)
            startActivity(intent)
        } catch (e: Exception) { Log.e(TAG, "Failed to call", e) }
    }
}

@Composable
fun MainActivity.MainScreenWithDialerLogic(userName: String) {
    var hasAllPermissions by remember { mutableStateOf(checkAllPermissions()) }
    var isDefaultDialer by remember { mutableStateOf(isDefaultDialer()) }

    MainScreen(
        userName = userName,
        hasAllPermissions = hasAllPermissions,
        isDefaultDialer = isDefaultDialer,
        onRequestPermissions = { requestPermissions() },
        onRequestDefaultDialer = { offerDefaultDialer() },
        onContactClick = { number -> dialNumber(number) }
    )
}

sealed class Screen(val route: String) {
    object Contacts : Screen("contacts")
    object Recents : Screen("recents")
    object Dialer : Screen("dialer")
}

@Composable
fun MainScreen(
    userName: String,
    hasAllPermissions: Boolean,
    isDefaultDialer: Boolean,
    onRequestPermissions: () -> Unit,
    onRequestDefaultDialer: () -> Unit,
    onContactClick: (String) -> Unit
) {
    if (isDefaultDialer && hasAllPermissions) {
        MainScreenContent(userName, onContactClick)
    } else {
        SetupScreen(isDefaultDialer, hasAllPermissions, onRequestPermissions, onRequestDefaultDialer)
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScreenContent(
    userName: String,
    onContactClick: (String) -> Unit
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
            composable(Screen.Contacts.route) {
                ContactsScreen(userName = userName, onContactClick = onContactClick)
            }
            composable(Screen.Recents.route) {
                RecentCallsScreen(onCallClick = onContactClick)
            }
            composable(Screen.Dialer.route) {
                DialerScreen()
            }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavController) {
    val items = listOf(Screen.Contacts, Screen.Recents, Screen.Dialer)
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
                        Screen.Contacts -> Icon(Icons.Filled.Contacts, "Contacts")
                        Screen.Recents -> Icon(Icons.Filled.History, "Recents")
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
            }
            if (isDefaultDialer && !hasAllPermissions) {
                SetupCard("Grant Permissions", "Required for contacts & logs.", "Grant", onRequestPermissions)
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
            .background(ComposeColor(0xFF1E293B).copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ComposeColor.White, modifier = Modifier.padding(bottom = 12.dp))
        Text(description, fontSize = 14.sp, color = ComposeColor.White.copy(alpha = 0.7f), textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 24.dp))
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF3B82F6))) {
            Text(buttonText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}