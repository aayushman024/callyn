package com.mnivesh.callyn.ui

import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.mnivesh.callyn.R
import com.mnivesh.callyn.api.RetrofitInstance
import com.mnivesh.callyn.ui.theme.CallynTheme
import kotlinx.coroutines.launch

@Composable
fun ZohoLoginScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // UI State for loading feedback
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E293B), // Slate 800
                        Color(0xFF0F172A)  // Slate 900
                    )
                )
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 1. Logo Section
        Image(
            painter = painterResource(id = R.drawable.zoho_logo),
            contentDescription = "App Logo",
            modifier = Modifier
                .size(100.dp)
                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                .padding(16.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(40.dp))

        // 2. Text Section
        Text(
            text = "Welcome to Callyn",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Manage your personal and work calls efficiently",
            fontSize = 16.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(60.dp))

        // 3. Action Section
        Button(
            onClick = {
                if (!isLoading) {
                    isLoading = true
                    coroutineScope.launch {
                        try {
                            val response = RetrofitInstance.api.getZohoAuthUrl()
                            if (response.isSuccessful && response.body() != null) {
                                val authUrl = response.body()!!.authUrl
                                val intent = CustomTabsIntent.Builder().build()
                                intent.launchUrl(context, authUrl.toUri())
                            } else {
                                Log.e("ZohoLogin", "Error: ${response.code()}")
                            }
                        } catch (e: Exception) {
                            Log.e("ZohoLogin", "Exception: ${e.message}")
                        } finally {
                            isLoading = false
                        }
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp) // Standard clickable height
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color(0xFF0F172A),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Login,
                    contentDescription = null,
                    tint = Color(0xFF0F172A),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Login with Zoho",
                    color = Color(0xFF0F172A),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ZohoLoginScreenPreview() {
    CallynTheme {
        ZohoLoginScreen()
    }
}