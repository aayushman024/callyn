package com.mnivesh.callyn.ui.theme

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val BASE_WIDTH = 425f
private const val BASE_HEIGHT = 890f

@Composable
fun Int.sdp() = (this * scaleWidth()).dp

@Composable
fun Int.ssp() = (this * scaleText()).sp

@Composable
fun scaleWidth(): Float {
    val config = LocalConfiguration.current
    val isPortrait = config.orientation != Configuration.ORIENTATION_LANDSCAPE

    // In landscape, screenWidthDp represents the longer side of the device.
    // We scale against BASE_HEIGHT to maintain proportional sizing.
    return if (isPortrait) {
        config.screenWidthDp / BASE_WIDTH
    } else {
        config.screenWidthDp / BASE_HEIGHT
    }
}

@Composable
fun scaleHeight(): Float {
    val config = LocalConfiguration.current
    val isPortrait = config.orientation != Configuration.ORIENTATION_LANDSCAPE

    // In landscape, screenHeightDp is the shorter side.
    // Scale against BASE_WIDTH here.
    return if (isPortrait) {
        config.screenHeightDp / BASE_HEIGHT
    } else {
        config.screenHeightDp / BASE_WIDTH
    }
}

@Composable
fun scaleText(): Float {
    return scaleWidth().coerceAtMost(scaleHeight())
}