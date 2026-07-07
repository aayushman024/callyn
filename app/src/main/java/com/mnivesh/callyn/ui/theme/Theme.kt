package com.mnivesh.callyn.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun CallynTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val appColors = if (darkTheme) DarkAppColors else LightAppColors

    // Smoothly animate theme colors for the crossfade animation
    val animatedBackground = animateColorAsState(appColors.background, label = "bg")
    val animatedSurface = animateColorAsState(appColors.surface, label = "surface")
    val animatedSurfaceVariant = animateColorAsState(appColors.surfaceVariant, label = "surfaceVariant")
    val animatedCardBackground = animateColorAsState(appColors.cardBackground, label = "cardBackground")
    val animatedTextPrimary = animateColorAsState(appColors.textPrimary, label = "textPrimary")
    val animatedTextSecondary = animateColorAsState(appColors.textSecondary, label = "textSecondary")
    val animatedBorder = animateColorAsState(appColors.border, label = "border")

    val colors = remember(
        animatedBackground.value,
        animatedSurface.value,
        animatedSurfaceVariant.value,
        animatedCardBackground.value,
        animatedTextPrimary.value,
        animatedTextSecondary.value,
        animatedBorder.value,
        darkTheme
    ) {
        AppColors(
            background = animatedBackground.value,
            surface = animatedSurface.value,
            surfaceVariant = animatedSurfaceVariant.value,
            cardBackground = animatedCardBackground.value,
            textPrimary = animatedTextPrimary.value,
            textSecondary = animatedTextSecondary.value,
            border = animatedBorder.value,
            isDark = darkTheme
        )
    }

    val colorScheme = if (darkTheme) {
        DarkColorScheme.copy(
            background = colors.background,
            surface = colors.surface,
            surfaceVariant = colors.surfaceVariant,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary
        )
    } else {
        LightColorScheme.copy(
            background = colors.background,
            surface = colors.surface,
            surfaceVariant = colors.surfaceVariant,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                // Ensure status bar and navigation bar are transparent
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                
                val insetsController = WindowCompat.getInsetsController(window, view)
                // If it is light theme, show dark icons on status/navigation bars
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val currentDensity = androidx.compose.ui.platform.LocalDensity.current
    CompositionLocalProvider(
        LocalAppColors provides colors,
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(currentDensity.density, fontScale = 1f)
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}