package com.mnivesh.callyn.screens

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mnivesh.callyn.CallynApplication
import com.mnivesh.callyn.components.AppDrawer
import com.mnivesh.callyn.managers.AuthManager
import com.mnivesh.callyn.managers.DialerManager
import com.mnivesh.callyn.managers.PermissionManager
import com.mnivesh.callyn.ui.EmployeeDirectoryScreen
import com.mnivesh.callyn.ui.SetupScreen
import com.mnivesh.callyn.ui.theme.AppTheme
import com.mnivesh.callyn.ui.theme.sdp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color as ComposeColor

sealed class Screen(val route: String) {
    object Recents : Screen("recents")
    object Contacts : Screen("contacts")
    object Dialer : Screen("dialer")
    object Requests : Screen("requests")
    object UserDetails : Screen("user_details")
    object ShowCallLogs : Screen("show_call_logs")
    object EmployeeDirectory : Screen("employee_directory")
    object EditQuickReplies : Screen("edit_quick_replies")
}

/**
 * Main screen coordinating the dialer, recents, contacts, and setup logic.
 */
@Composable
fun MainScreenWithDialerLogic(
    userName: String,
    incomingDialNumber: String?,
    onConsumeIncomingNumber: () -> Unit,
    onLogout: () -> Unit,
    callManager: DialerManager,
    permissionManager: PermissionManager,
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit
) {
    var hasAllPermissions by remember { mutableStateOf(permissionManager.checkAllPermissions()) }
    var isDefaultDialer by remember { mutableStateOf(callManager.isDefaultDialer()) }
    var missingPermissions by remember { mutableStateOf(permissionManager.getMissingPermissions()) }
    var missedCallCount by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAllPermissions = permissionManager.checkAllPermissions()
                isDefaultDialer = callManager.isDefaultDialer()
                missingPermissions = permissionManager.getMissingPermissions()
                if (hasAllPermissions) {
                    missedCallCount = callManager.getUnreadMissedCallsCount()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    MainScreen(
        userName = userName,
        incomingNumber = incomingDialNumber,
        onConsumeIncomingNumber = onConsumeIncomingNumber,
        hasAllPermissions = hasAllPermissions,
        isDefaultDialer = isDefaultDialer,
        missingPermissions = missingPermissions,
        missedCallCount = missedCallCount,
        onRequestPermissions = { permissionManager.requestPermissions() },
        onRequestDefaultDialer = { callManager.offerDefaultDialer() },
        onSmartDial = { number, isWork, slot -> callManager.dialSmart(number, isWork, slot) },
        onResetMissedCount = {
            missedCallCount = 0
            callManager.markMissedCallsAsRead()
        },
        onLogout = onLogout,
        isDarkTheme = isDarkTheme,
        onThemeToggle = onThemeToggle
    )
}

/**
 * Determines whether to show SetupScreen or MainScreenContent based on permissions and defaults.
 */
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
    onSmartDial: (String, Boolean, Int?) -> Unit,
    onResetMissedCount: () -> Unit,
    onLogout: () -> Unit,
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit
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
            userName = userName,
            onSmartDial = onSmartDial,
            onLogout = onLogout,
            incomingNumber = incomingNumber,
            onConsumeIncomingNumber = onConsumeIncomingNumber,
            missedCallCount = missedCallCount,
            onResetMissedCount = onResetMissedCount,
            isDarkTheme = isDarkTheme,
            onThemeToggle = onThemeToggle
        )
    }
}

/**
 * Main content with Navigation Drawer and Bottom Bar.
 */
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScreenContent(
    userName: String,
    onSmartDial: (String, Boolean, Int?) -> Unit,
    onLogout: () -> Unit,
    incomingNumber: String?,
    onConsumeIncomingNumber: () -> Unit,
    missedCallCount: Int,
    onResetMissedCount: () -> Unit,
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(incomingNumber) {
        if (!incomingNumber.isNullOrEmpty()) {
            navController.navigate(Screen.Dialer.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

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
                },
                onShowEditQuickReplies = {
                    scope.launch { drawerState.close() }
                    navController.navigate(Screen.EditQuickReplies.route)
                },
                isDarkTheme = isDarkTheme,
                onThemeToggle = onThemeToggle
            )
        }
    ) {
        Scaffold(
            containerColor = ComposeColor.Transparent,
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
                            onSmartDial(number, true, slot)
                        }
                    )
                }
                composable(Screen.ShowCallLogs.route) {
                    ShowCallLogsScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.EditQuickReplies.route) {
                    EditQuickRepliesScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

/**
 * Bottom navigation bar for MainScreenContent.
 */
@Composable
fun BottomNavigationBar(navController: NavController, missedCallCount: Int) {
    val items = listOf(Screen.Recents, Screen.Contacts, Screen.Dialer)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isDark = AppTheme.colors.isDark
    
    val bg = AppTheme.colors.background
    val surfaceColor = AppTheme.colors.surface.copy(alpha = if (isDark) 0.95f else 0.98f)
    val activeColor = ComposeColor(0xFF3B82F6)
    
    val borderColor = if (isDark) ComposeColor.White.copy(alpha = 0.06f) else ComposeColor.Black.copy(alpha = 0.06f)
    val shadowColor = if (isDark) ComposeColor.Black.copy(alpha = 0.5f) else ComposeColor.Black.copy(alpha = 0.08f)
    val roundedShape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

    // Gradient background container fading from transparent to solid
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        bg.copy(alpha = 0.0f),
                        bg.copy(alpha = 0.8f),
                        bg
                    )
                )
            )
    ) {
        // Safe Area container with top-rounded border and shadow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .customShadow(
                    color = shadowColor,
                    borderRadius = 32.dp,
                    blurRadius = 40.dp,
                    offsetY = (-10).dp
                )
                .background(color = surfaceColor, shape = roundedShape)
                .drawBehind {
                    val r = 32.dp.toPx()
                    val stroke = 1.2.dp.toPx()
                    val halfStroke = stroke / 2f
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(halfStroke, r)
                        quadraticTo(halfStroke, halfStroke, r, halfStroke)
                        lineTo(size.width - r, halfStroke)
                        quadraticTo(size.width - halfStroke, halfStroke, size.width - halfStroke, r)
                    }
                    drawPath(
                        path = path,
                        color = borderColor,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                    )
                }
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) {
                    // Consume clicks to prevent them from leaking to items underneath
                }
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                items.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

                    BottomNavItem(
                        screen = screen,
                        selected = selected,
                        missedCallCount = missedCallCount,
                        activeColor = activeColor,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun BottomNavItem(
    screen: Screen,
    selected: Boolean,
    missedCallCount: Int,
    activeColor: ComposeColor,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isDark = AppTheme.colors.isDark
    
    val transition = updateTransition(targetState = selected, label = "navItemTransition")
    
    val activePillBg = activeColor.copy(alpha = if (isDark) 0.12f else 0.08f)
    val itemBgColor by transition.animateColor(label = "bgColor") { isSelected ->
        if (isSelected) activePillBg else ComposeColor.Transparent
    }
    
    val inactiveColor = if (isDark) ComposeColor.White.copy(alpha = 0.38f) else ComposeColor.Black.copy(alpha = 0.38f)
    val iconColor by transition.animateColor(label = "iconColor") { isSelected ->
        if (isSelected) activeColor else inactiveColor
    }
    
    val horizontalPadding by animateDpAsState(
        targetValue = if (selected) 22.dp else 14.dp,
        animationSpec = tween(
            durationMillis = 300,
            easing = CubicBezierEasing(0.25f, 1.0f, 0.5f, 1.0f) // easeOutQuart equivalent
        ),
        label = "horizontalPadding"
    )
    
    Box(
        modifier = Modifier
            .zIndex(if (screen == Screen.Recents && missedCallCount > 0) 1f else 0f)
            .background(
                color = itemBgColor,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
            )
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .clickable(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = ripple(
                    bounded = true,
                    color = activeColor
                )
            )
            .padding(horizontal = horizontalPadding, vertical = 14.dp)
            .animateContentSize(
                animationSpec = tween(
                    durationMillis = 300,
                    easing = CubicBezierEasing(0.25f, 1.0f, 0.5f, 1.0f)
                )
            ),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            val icon = when (screen) {
                Screen.Recents -> Icons.Filled.History
                Screen.Contacts -> Icons.Filled.Contacts
                Screen.Dialer -> Icons.Filled.Dialpad
                else -> Icons.Filled.History
            }
            
            Icon(
                imageVector = icon,
                contentDescription = screen.route,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            
            if (selected) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = when (screen) {
                        Screen.Recents -> "Recents"
                        Screen.Contacts -> "Contacts"
                        Screen.Dialer -> "Dialer"
                        else -> screen.route.replaceFirstChar { it.uppercase() }
                    },
                    color = activeColor,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                        letterSpacing = (-0.4).sp
                    ),
                    maxLines = 1
                )
            }

            if (screen == Screen.Recents && missedCallCount > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                        .background(
                            color = ComposeColor(0xFFEF4444),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                        .padding(horizontal = 2.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(
                        text = missedCallCount.toString(),
                        color = ComposeColor.White,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        ),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

fun Modifier.customShadow(
    color: ComposeColor,
    borderRadius: androidx.compose.ui.unit.Dp,
    blurRadius: androidx.compose.ui.unit.Dp,
    offsetY: androidx.compose.ui.unit.Dp = 0.dp,
    offsetX: androidx.compose.ui.unit.Dp = 0.dp,
    spread: androidx.compose.ui.unit.Dp = 0.dp
) = this.drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        val spreadPixel = spread.toPx()
        val left = offsetX.toPx() - spreadPixel
        val top = offsetY.toPx() - spreadPixel
        val right = size.width + offsetX.toPx() + spreadPixel
        val bottom = size.height + offsetY.toPx() + spreadPixel

        if (blurRadius > 0.dp) {
            frameworkPaint.maskFilter = android.graphics.BlurMaskFilter(
                blurRadius.toPx(),
                android.graphics.BlurMaskFilter.Blur.NORMAL
            )
        }
        frameworkPaint.color = color.toArgb()

        val strokeRadius = borderRadius.toPx()
        val path = android.graphics.Path().apply {
            val rect = android.graphics.RectF(left, top, right, bottom)
            val radii = floatArrayOf(
                strokeRadius, strokeRadius,
                strokeRadius, strokeRadius,
                0f, 0f,
                0f, 0f
            )
            addRoundRect(rect, radii, android.graphics.Path.Direction.CW)
        }
        canvas.nativeCanvas.drawPath(path, frameworkPaint)
    }
}
