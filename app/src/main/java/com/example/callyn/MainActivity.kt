package com.example.callyn

// --- ADDED IMPORTS ---
import android.graphics.Color
import androidx.core.view.WindowCompat
// --- END ADDED IMPORTS ---

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor // Renamed to avoid conflict
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private const val TAG = "MainActivity"

sealed class Screen(val route: String) {
    object Contacts : Screen("contacts")
    object Dialer : Screen("dialer")
}

class MainActivity : ComponentActivity() {

    // --- LAUNCHERS ---
    private val permissionsToRequest = mutableListOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val multiplePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            Log.d(TAG, "Permission launcher callback received.")
            result.entries.forEach { entry ->
                Log.d(TAG, "Permission ${entry.key}: ${if (entry.value) "GRANTED" else "DENIED"}")
            }
            updatePermissionState()
        }

    private val defaultDialerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "Default dialer launcher callback received with code: ${result.resultCode}")
    }

    // --- STATE ---
    private val isDefaultDialerState = mutableStateOf(false)
    private val hasAllPermissionsState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Setting up content...")

        // --- ADDED FOR EDGE-TO-EDGE ---
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        // Set status bar icons to light (since your background is dark)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false // Also for nav bar
        // --- END OF ADDED LINES ---

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val isDefault by isDefaultDialerState
                    val hasAllPermissions by hasAllPermissionsState

                    MainScreen(
                        hasAllPermissions = hasAllPermissions,
                        isDefaultDialer = isDefault,
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
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionState()
        updateDialerState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent: action=${intent.action}, data=${intent.data}")

        when (intent.action) {
            Intent.ACTION_DIAL -> {
                val number = intent.data?.schemeSpecificPart
                Log.d(TAG, "DIAL intent received with number: $number")
            }
            Intent.ACTION_VIEW -> {
                if (intent.data?.scheme == "tel") {
                    val number = intent.data?.schemeSpecificPart
                    Log.d(TAG, "VIEW tel: intent received with number: $number")
                }
            }
        }
    }

    // --- HELPER FUNCTIONS ---

    private fun updatePermissionState() {
        hasAllPermissionsState.value = checkAllPermissions()
        Log.d(TAG, "updatePermissionState: hasPermissions=${hasAllPermissionsState.value}")
    }

    private fun updateDialerState() {
        isDefaultDialerState.value = isDefaultDialer()
        Log.d(TAG, "updateDialerState: isDefaultDialer=${isDefaultDialerState.value}")
    }

    private fun checkAllPermissions(): Boolean {
        return permissionsToRequest.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isDefaultDialer(): Boolean {
        val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val isDefault = telecomManager?.defaultDialerPackage == packageName
        Log.d(TAG, "isDefaultDialer: current default=${telecomManager?.defaultDialerPackage}, this app=$packageName, result=$isDefault")
        return isDefault
    }

    private fun requestPermissions() {
        val permissionsToAsk = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToAsk.isNotEmpty()) {
            Log.d(TAG, "Requesting ${permissionsToAsk.size} permissions: ${permissionsToAsk.joinToString()}")
            multiplePermissionLauncher.launch(permissionsToAsk.toTypedArray())
        } else {
            Log.d(TAG, "All permissions already granted")
            updatePermissionState()
        }
    }

    private fun offerDefaultDialer() {
        if (isDefaultDialer()) {
            Log.d(TAG, "Already the default dialer")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.d(TAG, "Using RoleManager (API 29+)")
            try {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                    defaultDialerLauncher.launch(intent)
                    Log.d(TAG, "RoleManager intent launched successfully.")
                } else {
                    Log.e(TAG, "ROLE_DIALER not available on this device")
                }
            } catch (e: Exception) {
                Log.e(TAG, "RoleManager launch failed: ${e.message}", e)
            }
        } else {
            Log.d(TAG, "Using old TelecomManager (API < 29)")
            try {
                @Suppress("DEPRECATION")
                val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                    .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                defaultDialerLauncher.launch(intent)
                Log.d(TAG, "TelecomManager intent launched successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "TelecomManager launch failed: ${e.message}", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun dialNumber(number: String) {
        if (!isDefaultDialer()) {
            Log.w(TAG, "Cannot dial - not default dialer")
            offerDefaultDialer()
            return
        }

        if (!checkAllPermissions()) {
            Log.w(TAG, "Cannot dial - missing permissions")
            requestPermissions()
            return
        }

        try {
            val uri = Uri.fromParts("tel", number, null)
            val intent = Intent(Intent.ACTION_CALL, uri)
            startActivity(intent)
            Log.d(TAG, "Call initiated to: $number")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate call: ${e.message}", e)
        }
    }
}

@Composable
fun MainScreen(
    hasAllPermissions: Boolean,
    isDefaultDialer: Boolean,
    onRequestPermissions: () -> Unit,
    onRequestDefaultDialer: () -> Unit,
    onContactClick: (String) -> Unit
) {
    if (isDefaultDialer && hasAllPermissions) {
        MainScreenContent(onContactClick = onContactClick)
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
fun MainScreenContent(onContactClick: (String) -> Unit) {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = { BottomNavigationBar(navController) }
    ) {
        NavHost(navController, startDestination = Screen.Contacts.route) {
            composable(Screen.Contacts.route) {
                ContactsScreen(onContactClick = onContactClick)
            }
            composable(Screen.Dialer.route) {
                DialerScreen()
            }
        }
    }
}

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
        // .systemBarsPadding(), // <-- REMOVED FROM HERE
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .systemBarsPadding(), // <-- ADDED HERE
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Title
            Text(
                text = "Callyn",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = ComposeColor.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Your Modern Dialer",
                fontSize = 16.sp,
                color = ComposeColor.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 48.dp)
            )

            // Default Dialer Setup
            if (!isDefaultDialer) {
                SetupCard(
                    title = "Set as Default Dialer",
                    description = "Callyn needs to be your default dialer app to make and receive calls.",
                    buttonText = "Set as Default",
                    onClick = onRequestDefaultDialer
                )
            }

            // Permissions Setup
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