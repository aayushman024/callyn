package com.mnivesh.callyn.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mnivesh.callyn.R
import com.mnivesh.callyn.api.version
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
import kotlinx.coroutines.delay

@Composable
fun ZohoLoginScreen() {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    var showPermissionsDialog by remember { mutableStateOf(false) }

    // Start entrance anims immediately
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    if (showPermissionsDialog) {
        PermissionsDialog(onDismiss = { showPermissionsDialog = false })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background Layer
        AnimatedGradientBackground()

        // Content Layer
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.sdp())
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(1000)) + slideInVertically(initialOffsetY = { 50 })
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    // 1. mNivesh Logo (Top)
                    Image(
                        painter = painterResource(id = R.drawable.mnivesh),
                        contentDescription = "mNivesh",
                        modifier = Modifier
                            .size(200.sdp())
                            .padding(bottom = 8.sdp()),
                        contentScale = ContentScale.Fit
                    )

                    Spacer(modifier = Modifier.height(44.sdp()))

                    // 2. "Welcome to"
                    Text(
                        text = "Welcome to",
                        fontSize = 20.ssp(),
                        fontWeight = FontWeight.W300,
                        color = Color.White.copy(alpha = 0.9f),
                        letterSpacing = 0.5.sp
                    )

                    // 3. "Callyn" Text + Logo Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(top = 18.sdp())
                    ) {
                        Text(
                            text = "Callyn",
                            fontSize = 40.ssp(),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.ssp()
                        )
                        Spacer(modifier = Modifier.width(12.sdp()))
                        Image(
                            painter = painterResource(id = R.drawable.callyn),
                            contentDescription = "Callyn Logo",
                            modifier = Modifier.size(36.sdp()),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Spacer(modifier = Modifier.height(36.sdp()))

                    Text(
                        text = "Sync your personal and work calls.\nSeamlessly integrated.",
                        fontSize = 16.ssp(),
                        color = Color.White.copy(alpha = 0.6f),
                        lineHeight = 24.ssp(),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(32.sdp()))

                    // -- Feature Carousel --
                    FeatureCarousel()
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // CTA Button Area
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(1000, delayMillis = 300)) + slideInVertically(initialOffsetY = { 100 })
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LoginButton(
                        isLoading = isLoading,
                        onClick = {
                            val ssoUrl = "mniveshstore://sso/request?callback=callyn://auth/callback"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ssoUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }

                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Please install mNivesh Store first", Toast.LENGTH_LONG).show()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(24.sdp()))

                    // --- Footer: Permissions & Version ---
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Interactive Permissions Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.sdp()))
                                .clickable { showPermissionsDialog = true }
                                .padding(vertical = 8.sdp(), horizontal = 12.sdp())
                                .background(Color.White.copy(alpha = 0.05f)) // Subtle background
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = "Permissions Info",
                                tint = Color(0xFFA5B4FC), // Soft Indigo tint
                                modifier = Modifier.size(14.sdp())
                            )
                            Spacer(modifier = Modifier.width(8.sdp()))
                            Text(
                                text = "Permissions",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.ssp(),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Separator Dot
                        Text(
                            text = "•",
                            color = Color.White.copy(alpha = 0.2f),
                            fontSize = 12.ssp(),
                            modifier = Modifier.padding(horizontal = 12.sdp())
                        )

                        // Static Version Text
                        Text(
                            text = "v$version",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 12.ssp(),
                            fontWeight = FontWeight.Normal,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.sdp()))
        }
    }
}

// --- Permissions Dialog ---
@Composable
fun PermissionsDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.sdp()),
            shape = RoundedCornerShape(24.sdp()),
            color = Color(0xFF0F172A), // Dark background matching theme
            border = androidx.compose.foundation.BorderStroke(1.sdp(), Color.White.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.sdp())
                    .fillMaxSize()
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 24.sdp())
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = Color(0xFF818CF8), // Indigo
                        modifier = Modifier.size(28.sdp())
                    )
                    Spacer(modifier = Modifier.width(12.sdp()))
                    Text(
                        text = "App Permissions",
                        fontSize = 22.ssp(),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Text(
                    text = "Callyn requires specific permissions to function as your default dialer and business tool. Only required data is securely synced with internal company servers.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.ssp(),
                    lineHeight = 20.ssp(),
                    modifier = Modifier.padding(bottom = 24.sdp())
                )

                // Scrollable List
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.sdp())
                ) {

                    PermissionItem(
                        icon = Icons.Rounded.Call,
                        title = "Phone & Call Management",
                        desc = "Required to make, receive, and manage calls. Enables Callyn to function as your default dialer and handle incoming and ongoing calls."
                    )

                    PermissionItem(
                        icon = Icons.Rounded.Contacts,
                        title = "Contacts Access",
                        desc = "Allows displaying caller names, managing contacts, and syncing business directory information securely."
                    )

                    PermissionItem(
                        icon = Icons.Rounded.Notifications,
                        title = "Notifications",
                        desc = "Ensures incoming call alerts, missed calls, and important communication notifications are delivered reliably."
                    )

                    PermissionItem(
                        icon = Icons.Rounded.SdStorage,
                        title = "Call History Access",
                        desc = "Allows viewing and managing call logs to provide complete call history, analytics, and business communication tracking."
                    )

                    PermissionItem(
                        icon = Icons.Rounded.Mic,
                        title = "Foreground & Call Services",
                        desc = "Allows the app to run call services reliably in the background, ensuring calls continue even when the app is not open."
                    )

                    PermissionItem(
                        icon = Icons.Rounded.Shield,
                        title = "Device & Call State",
                        desc = "Required to detect call status, SIM state, and ensure proper routing and management of business and personal calls."
                    )

                    PermissionItem(
                        icon = Icons.Rounded.Notifications,
                        title = "Full-Screen Call Interface",
                        desc = "Allows displaying the incoming call screen over the lock screen so you can answer calls immediately."
                    )
                }


                Spacer(modifier = Modifier.height(24.sdp()))

                // Close Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.sdp()),
                    shape = RoundedCornerShape(12.sdp()),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF334155),
                        contentColor = Color.White
                    )
                ) {
                    Text("Understood", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun PermissionItem(icon: ImageVector, title: String, desc: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(40.sdp())
                .background(Color(0xFF1E293B), CircleShape)
                .border(1.sdp(), Color.White.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFA5B4FC), // Indigo-200
                modifier = Modifier.size(20.sdp())
            )
        }
        Spacer(modifier = Modifier.width(16.sdp()))
        Column {
            Text(
                text = title,
                fontSize = 16.ssp(),
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.sdp()))
            Text(
                text = desc,
                fontSize = 13.ssp(),
                color = Color.White.copy(alpha = 0.6f),
                lineHeight = 18.ssp()
            )
        }
    }
}

// --- Data Model for Features ---
data class AppFeature(
    val title: String,
    val description: String
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeatureCarousel() {
    val features = remember {
        listOf(
            AppFeature("Smart Dual-SIM", "Automatically selects the correct SIM for every work call."),
            AppFeature("Work–Life Separation", "Clearly separates personal and professional contacts."),
            AppFeature("Cloud-Powered Directory", "Syncs company contacts in real time from central servers."),
            AppFeature("Detailed Call History", "Tracks incoming, outgoing, and missed calls with filters."),
            AppFeature("Privacy Management", "Control and reclassify contacts to protect personal data.")
        )
    }

    val pagerState = rememberPagerState(pageCount = { features.size })

    // Auto-scroll logic
    LaunchedEffect(Unit) {
        while (true) {
            delay(3500) // 3.5 seconds per slide
            val nextPage = (pagerState.currentPage + 1) % features.size
            pagerState.animateScrollToPage(nextPage, animationSpec = tween(600))
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Carousel Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.sdp()),
            pageSpacing = 16.sdp()
        ) { page ->
            GlassFeatureCard(feature = features[page])
        }

        Spacer(modifier = Modifier.height(16.sdp()))

        // Indicators
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(features.size) { iteration ->
                val isSelected = pagerState.currentPage == iteration
                // Animate size/color
                val width by animateDpAsState(if (isSelected) 24.sdp() else 8.sdp(), label = "width")
                val color by animateColorAsState(if (isSelected) Color.White else Color.White.copy(alpha = 0.3f), label = "color")

                Box(
                    modifier = Modifier
                        .padding(4.sdp())
                        .height(8.sdp())
                        .width(width)
                        .background(color, CircleShape)
                )
            }
        }
    }
}

@Composable
fun GlassFeatureCard(feature: AppFeature) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.sdp()) // Fixed height to prevent jumping
            .clip(RoundedCornerShape(20.sdp()))
            .background(Color(0xFF1E293B).copy(alpha = 0.4f)) // Slightly darker glass
            .border(1.sdp(), Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.sdp()))
            .padding(20.sdp()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = feature.title,
                fontSize = 16.ssp(),
                fontWeight = FontWeight.Bold,
                color = Color(0xFFA5B4FC) // Indigo-200 tint
            )
            Spacer(modifier = Modifier.height(8.sdp()))
            Text(
                text = feature.description,
                fontSize = 13.ssp(),
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                lineHeight = 18.ssp(),
                maxLines = 2
            )
        }
    }
}

// --- Background Animation ---
@Composable
fun AnimatedGradientBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "bg_anim")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(40000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val rad = Math.toRadians(angle.toDouble())
        val xOffset1 = (screenWidth / 2) * kotlin.math.cos(rad).toFloat()
        val yOffset1 = (screenHeight / 2) * kotlin.math.sin(rad).toFloat()
        val rad2 = rad + Math.PI
        val xOffset2 = (screenWidth / 2) * kotlin.math.cos(rad2).toFloat()
        val yOffset2 = (screenHeight / 2) * kotlin.math.sin(rad2).toFloat()

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = xOffset1, y = yOffset1)
                .size(400.sdp())
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF4F46E5).copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
                .blur(60.sdp())
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = xOffset2, y = yOffset2)
                .size(400.sdp())
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFEC4899).copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    )
                )
                .blur(70.sdp())
        )
    }
}

// --- Button ---
@Composable
fun LoginButton(isLoading: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f, label = "btn_scale")

    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(18.sdp()),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .height(60.sdp())
            .scale(scale)
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.sdp()), color = Color(0xFF0F172A), strokeWidth = 2.5.dp)
        } else {
            Image(painter = painterResource(id = R.drawable.mnivesh_store), contentDescription = null, modifier = Modifier.size(32.sdp()))
            Spacer(modifier = Modifier.width(12.sdp()))
            Text(text = "Login using mNivesh Store", fontSize = 17.ssp(), color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
        }
    }
}