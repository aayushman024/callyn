package com.mnivesh.callyn.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.mnivesh.callyn.R
import com.mnivesh.callyn.api.RetrofitInstance
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ZohoLoginScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    // Start entrance anims immediately
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background Layer
        AnimatedGradientBackground()

        // Content Layer
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
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
                            .size(200.dp) // Adjusted size for standalone top position
                            .padding(bottom = 8.dp),
                        contentScale = ContentScale.Fit
                    )

                    Spacer(modifier = Modifier.height(44.dp))

                    // 2. "Welcome to"
                    Text(
                        text = "Welcome to",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.W300,
                        color = Color.White.copy(alpha = 0.9f),
                        letterSpacing = 0.5.sp
                    )

                    // 3. "Callyn" Text + Logo Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(top = 18.dp)
                    ) {
                        Text(
                            text = "Callyn",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Image(
                            painter = painterResource(id = R.drawable.callyn),
                            contentDescription = "Callyn Logo",
                            modifier = Modifier.size(36.dp),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Spacer(modifier = Modifier.height(36.dp))

                    Text(
                        text = "Sync your personal and work calls.\nSeamlessly integrated.",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        lineHeight = 24.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // -- Feature Carousel --
                    FeatureCarousel()
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // CTA Button
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(1000, delayMillis = 300)) + slideInVertically(initialOffsetY = { 100 })
            ) {
                LoginButton(
                    isLoading = isLoading,
                    onClick = {
                        // Request login data from the mNivesh Store app
                        // passing our own deep link as the callback
                        val ssoUrl = "mniveshstore://sso/request?callback=callyn://auth/callback"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ssoUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // If the Intent fails, mNivesh Store is probably not installed
                            Toast.makeText(context, "Please install mNivesh Store first", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
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
            contentPadding = PaddingValues(horizontal = 24.dp),
            pageSpacing = 16.dp
        ) { page ->
            GlassFeatureCard(feature = features[page])
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Indicators
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(features.size) { iteration ->
                val isSelected = pagerState.currentPage == iteration
                // Animate size/color
                val width by animateDpAsState(if (isSelected) 24.dp else 8.dp, label = "width")
                val color by animateColorAsState(if (isSelected) Color.White else Color.White.copy(alpha = 0.3f), label = "color")

                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .height(8.dp)
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
            .height(110.dp) // Fixed height to prevent jumping
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1E293B).copy(alpha = 0.4f)) // Slightly darker glass
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = feature.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFA5B4FC) // Indigo-200 tint
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = feature.description,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
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
        modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A))
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
                .size(400.dp)
                .background(Brush.radialGradient(colors = listOf(Color(0xFF4F46E5).copy(alpha = 0.3f), Color.Transparent)))
                .blur(60.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = xOffset2, y = yOffset2)
                .size(400.dp)
                .background(Brush.radialGradient(colors = listOf(Color(0xFFEC4899).copy(alpha = 0.25f), Color.Transparent)))
                .blur(70.dp)
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
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth().height(60.dp).scale(scale)
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF0F172A), strokeWidth = 2.5.dp)
        } else {
            Image(painter = painterResource(id = R.drawable.mnivesh_store), contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "Login using mNivesh Store", fontSize = 17.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
        }
    }
}