package com.mnivesh.callyn.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.compose.*
import com.mnivesh.callyn.CallynApplication
import com.mnivesh.callyn.R
import com.mnivesh.callyn.components.DeviceContact
import com.mnivesh.callyn.db.AppContact
import com.mnivesh.callyn.managers.AuthManager
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
import com.mnivesh.callyn.utils.ContactUtils.findConflicts
import com.mnivesh.callyn.utils.ContactUtils.loadDeviceContacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Displays a loading screen while resolving contacts and syncing details.
 */
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
