package com.mnivesh.callyn.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val cardBackground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val isDark: Boolean,
    val primary: Color = Color(0xFF3B82F6),
    val tabContainer: Color = if (isDark) Color(0xFF1E293B).copy(alpha = 0.75f) else Color(0xFFE2E8F0).copy(alpha = 0.8f),
    val tabSelectedSurface: Color = if (isDark) Color(0xFF334155) else Color(0xFFFFFFFF),
    val tabIndicator: Color = Color(0xFF3B82F6)
)

val DarkAppColors = AppColors(
    background = Color(0xFF0F172A),      // Deep Slate 900
    surface = Color(0xFF1E293B),         // Slate 800
    surfaceVariant = Color(0xFF334155),  // Slate 700
    cardBackground = Color(0xFF1E293B),  // Slate 800
    textPrimary = Color(0xFFF1F5F9),     // Slate 100
    textSecondary = Color(0xFF94A3B8),   // Slate 400
    border = Color(0xFF334155),          // Slate 700
    isDark = true,
    primary = Color(0xFF3B82F6),
    tabContainer = Color(0xFF1E293B).copy(alpha = 0.85f),
    tabSelectedSurface = Color(0xFF334155),
    tabIndicator = Color(0xFF3B82F6)
)

val LightAppColors = AppColors(
    background = Color(0xFFF8FAFC),     // Slate 50
    surface = Color(0xFFFFFFFF),        // Pure White
    surfaceVariant = Color(0xFFF1F5F9), // Slate 100
    cardBackground = Color(0xFFFFFFFF),  // Pure White
    textPrimary = Color(0xFF0F172A),    // Deep Slate 900
    textSecondary = Color(0xFF64748B),  // Slate 500
    border = Color(0xFFE2E8F0),         // Slate 200
    isDark = false,
    primary = Color(0xFF2563EB),
    tabContainer = Color(0xFFE2E8F0).copy(alpha = 0.7f),
    tabSelectedSurface = Color(0xFFFFFFFF),
    tabIndicator = Color(0xFF2563EB)
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

object AppTheme {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}
