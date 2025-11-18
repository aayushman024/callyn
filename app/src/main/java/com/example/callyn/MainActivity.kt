package com.example.callyn

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.example.callyn.ui.theme.CallynTheme

// --- Original imports ---
import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.callyn.ContactsScreen
import com.example.callyn.DialerScreen
import com.example.callyn.ui.ZohoLoginScreen
import kotlinx.coroutines.launch
// --- End of original imports ---


private const val TAG = "MainActivity"

// --- NEW UI State Class ---
sealed class MainActivityUiState {
    object Loading : MainActivityUiState()
    object LoggedOut : MainActivityUiState()
    data class LoggedIn(val userName: String) : MainActivityUiState()
}


// --- Main Activity ---
class MainActivity : ComponentActivity() {

    private lateinit var authManager: AuthManager
    private var uiState by mutableStateOf<MainActivityUiState>(MainActivityUiState.Loading)

    // --- Original launchers (Unchanged) ---
    private val permissionsToRequest = mutableListOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.WRITE_CALL_LOG
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
        Log.d(TAG, "onCreate: Setting up content...")

        authManager = AuthManager(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false

        // Handle deep link if app was JUST opened by it
        if (!handleIntent(intent)) {
            // If not opened by deep link, check existing token
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
        Log.d(TAG, "onNewIntent called")
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
                Log.d(TAG, "SUCCESS! Received token from deep link.")
                authManager.saveToken(token)
                fetchUserName("Bearer $token")
                return true
            } else {
                val loginError = data.getQueryParameter("login")
                if (loginError == "failed") {
                    Log.e(TAG, "Zoho login failed (from backend)")
                    uiState = MainActivityUiState.LoggedOut
                }
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
                    val userName = response.body()!!.name
                    Log.d(TAG, "Logged in as: $userName")
                    uiState = MainActivityUiState.LoggedIn(userName)
                } else {
                    Log.e(TAG, "Token was invalid, logging out.")
                    authManager.logout()
                    uiState = MainActivityUiState.LoggedOut
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch user name", e)
                authManager.logout()
                uiState = MainActivityUiState.LoggedOut
            }
        }
    }

    // --- All your original helper functions (checkAllPermissions, etc.) ---
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
            } catch (e: Exception) {
                Log.e(TAG, "RoleManager launch failed", e)
            }
        } else {
            try {
                @Suppress("DEPRECATION")
                val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                    .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                defaultDialerLauncher.launch(intent)
            } catch (e: Exception) {
                Log.e(TAG, "TelecomManager launch failed", e)
            }
        }
    }
    @SuppressLint("MissingPermission")
    fun dialNumber(number: String) {
        if (!isDefaultDialer()) {
            offerDefaultDialer()
            return
        }
        if (!checkAllPermissions()) {
            requestPermissions()
            return
        }
        try {
            val uri = Uri.fromParts("tel", number, null)
            val intent = Intent(Intent.ACTION_CALL, uri)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate call", e)
        }
    }
}


// =================================================================

@Composable
fun MainActivity.MainScreenWithDialerLogic(userName: String) {
    var hasAllPermissions by remember { mutableStateOf(checkAllPermissions()) }
    var isDefaultDialer by remember { mutableStateOf(isDefaultDialer()) }

    MainScreen(
        userName = userName,
        hasAllPermissions = hasAllPermissions,
        isDefaultDialer = isDefaultDialer,
        onRequestPermissions = {
            Log.d(TAG, "Requesting runtime permissions...")
            requestPermissions()
        },
        onRequestDefaultDialer = {
            Log.d(TAG, "Requesting default dialer role...")
            offerDefaultDialer()
        },
        onContactClick = { number ->
            Log.d(TAG, "Dialing number: $number")
            dialNumber(number)
        }
    )
}

sealed class Screen(val route: String) {
    object Contacts : Screen("contacts")
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
        MainScreenContent(
            userName = userName,
            onContactClick = onContactClick
        )
    } else {
        SetupScreen(
            isDefaultDialer = isDefaultDialer,
            hasAllPermissions = hasAllPermissions,
            onRequestPermissions = onRequestPermissions,
            onRequestDefaultDialer = onRequestDefaultDialer
        )
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
    ) {
        NavHost(navController, startDestination = Screen.Contacts.route, modifier = Modifier.padding(it)) {
            composable(Screen.Contacts.route) {
                ContactsScreen(
                    userName = userName,
                    onContactClick = onContactClick
                )
            }
            composable(Screen.Dialer.route) {
                DialerScreen() // DialerScreen is unchanged
            }
        }
    }
}

// ... (BottomNavigationBar, SetupScreen, and SetupCard are unchanged) ...
// (You provided this code in the prompt)
@Composable
fun BottomNavigationBar(navController: NavController) {
    val items = listOf(Screen.Contacts, Screen.Dialer)
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
                        Screen.Contacts -> Icon(
                            imageVector = Icons.Filled.Contacts,
                            contentDescription = "Contacts",
                            tint = if (selected) ComposeColor(0xFF3B82F6) else ComposeColor.Gray
                        )
                        Screen.Dialer -> Icon(
                            imageVector = Icons.Filled.Dialpad,
                            contentDescription = "Dialer",
                            tint = if (selected) ComposeColor(0xFF3B82F6) else ComposeColor.Gray
                        )
                    }
                },
                label = {
                    Text(
                        text = screen.route.replaceFirstChar { it.uppercase() },
                        color = if (selected) ComposeColor(0xFF3B82F6) else ComposeColor.Gray
                    )
                },
                selected = selected,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                alwaysShowLabel = true,
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
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ComposeColor(0xFF1E293B),
                        ComposeColor(0xFF0F172A)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Callyn",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = ComposeColor.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Your Personal + Work Dialer",
                fontSize = 16.sp,
                color = ComposeColor.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 48.dp)
            )

            if (!isDefaultDialer) {
                SetupCard(
                    title = "Set as Default Dialer",
                    description = "Callyn needs to be your default dialer app to make and receive calls.",
                    buttonText = "Set as Default",
                    onClick = onRequestDefaultDialer
                )
            }

            if (isDefaultDialer && !hasAllPermissions) {
                SetupCard(
                    title = "Grant Permissions",
                    description = "Callyn needs access to phone, contacts, and notifications to work properly.",
                    buttonText = "Grant Permissions",
                    onClick = onRequestPermissions
                )
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
            .background(
                color = ComposeColor(0xFF1E293B).copy(alpha = 0.6f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = ComposeColor.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Text(
            text = description,
            fontSize = 14.sp,
            color = ComposeColor.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ComposeColor(0xFF3B82F6)
            )
        ) {
            Text(
                text = buttonText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}
