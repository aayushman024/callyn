package com.example.callyn.ui

import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.callyn.R
import com.example.callyn.RetrofitInstance
import com.example.callyn.ui.theme.CallynTheme
import kotlinx.coroutines.launch

// --- REMOVED Local Retrofit/ApiService definitions ---

@Composable
fun ZohoLoginScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E293B), // Dark blue
                        Color(0xFF0F172A)  // Darker blue
                    )
                )
            )
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // 1. App Name
        Text(
            text = "Callyn",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(64.dp))

        // 2. Zoho Logo (from local drawable)
        Image(
            painter = painterResource(id = R.drawable.zoho_logo),
            contentDescription = "Zoho Logo",
            modifier = Modifier.size(120.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 3. Login Button
        Button(
            onClick = {
                coroutineScope.launch {
                    try {
                        // --- USE THE SHARED INSTANCE ---
                        val response = RetrofitInstance.api.getZohoAuthUrl()
                        if (response.isSuccessful && response.body() != null) {
                            val authUrl = response.body()!!.authUrl
                            val intent = CustomTabsIntent.Builder().build()
                            intent.launchUrl(context, authUrl.toUri())
                        } else {
                            Log.e("ZohoLogin", "Failed to get auth URL: ${response.code()} ${response.message()}")
                        }
                    } catch (e: Exception) {
                        Log.e("ZohoLogin", "Network Exception: ${e.message}")
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = "Login with Zoho",
                color = Color(0xFF0F172A),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
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