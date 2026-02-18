package com.mnivesh.callyn.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.TextUnit
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
    return config.screenWidthDp / BASE_WIDTH
}

@Composable
fun scaleHeight(): Float {
    val config = LocalConfiguration.current
    return config.screenHeightDp / BASE_HEIGHT
}

@Composable
fun scaleText(): Float {
    return scaleWidth().coerceAtMost(scaleHeight())
}
