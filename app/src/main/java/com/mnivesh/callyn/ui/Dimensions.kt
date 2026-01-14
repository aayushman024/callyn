package com.mnivesh.callyn.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun getScaleFactor(): Float {
    val configuration = LocalConfiguration.current
    // 375 is a standard base screen width (e.g. Pixel, iPhone base models).
    return configuration.screenWidthDp.toFloat() / 375f
}

/**
 * Scalable DP: Multiplies the value by the screen width ratio.
 * Usage: 16.sdp() instead of 16.dp
 */
@Composable
fun Int.sdp(): Dp {
    return (this * getScaleFactor()).dp
}

@Composable
fun Double.sdp(): Dp {
    return (this.toFloat() * getScaleFactor()).dp
}

/**
 * Scalable SP: Multiplies the value by the screen width ratio.
 * Usage: 16.ssp() instead of 16.sp
 */
@Composable
fun Int.ssp(): TextUnit {
    return (this * getScaleFactor()).sp
}