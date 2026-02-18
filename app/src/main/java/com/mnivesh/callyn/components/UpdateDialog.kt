package com.mnivesh.callyn.ui

import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.BulletSpan
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.mnivesh.callyn.ui.theme.sdp
import com.mnivesh.callyn.ui.theme.ssp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.text.HtmlCompat
import com.mnivesh.callyn.api.VersionResponse

fun htmlWithBulletSpacing(html: String, gapPx: Int): Spannable {
    val spanned =
        HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY) as Spannable

    val bullets = spanned.getSpans(0, spanned.length, BulletSpan::class.java)

    bullets.forEach { old ->
        val start = spanned.getSpanStart(old)
        val end = spanned.getSpanEnd(old)
        spanned.removeSpan(old)

        spanned.setSpan(
            BulletSpan(gapPx),
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    return spanned
}

@Composable
fun UpdateDialog(
    versionInfo: VersionResponse,
    isHardUpdate: Boolean,
    onDismiss: () -> Unit,
    onUpdate: (String) -> Unit
) {

    val properties =
        if (isHardUpdate)
            DialogProperties(false, false, usePlatformDefaultWidth = false)
        else
            DialogProperties(true, true, usePlatformDefaultWidth = false)

    val latestVersion =
        versionInfo.latestVersion?.takeIf { it.isNotBlank() } ?: "New"

    val changelog =
        versionInfo.changelog?.takeIf { it.isNotBlank() }
            ?: "<i>No details provided.</i>"

    val downloadUrl =
        versionInfo.downloadUrl?.takeIf { it.isNotBlank() } ?: ""

    Dialog(
        onDismissRequest = {
            if (!isHardUpdate) onDismiss()
        },
        properties = properties
    ) {

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.sdp()),
            shape = RoundedCornerShape(24.sdp()),
            color = Color(0xFF0F172A).copy(alpha = 0.98f),
            border = BorderStroke(
                1.sdp(),
                Color.White.copy(alpha = 0.1f)
            )
        ) {

            Column(
                modifier = Modifier
                    .padding(24.sdp())
                    .fillMaxWidth()
            ) {

                /*
                 Header
                 */

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.sdp())
                ) {

                    Icon(
                        imageVector =
                            if (isHardUpdate)
                                Icons.Rounded.Warning
                            else
                                Icons.Rounded.Info,

                        contentDescription = null,

                        tint =
                            if (isHardUpdate)
                                Color(0xFFF87171)
                            else
                                Color(0xFF818CF8),

                        modifier = Modifier.size(28.sdp())
                    )

                    Spacer(modifier = Modifier.width(12.sdp()))

                    Text(
                        text = "Update Available",
                        fontSize = 22.ssp(),
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                /*
                 Version text
                 */

                Text(
                    text = "Version $latestVersion is available.",
                    fontSize = 15.ssp(),
                    color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(20.sdp()))

                /*
                 What's New label
                 */

                Text(
                    text = "What's New",
                    fontSize = 15.ssp(),
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFA5B4FC)
                )

                Spacer(modifier = Modifier.height(10.sdp()))

                /*
                 Glass changelog card
                 */

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.sdp(), max = 300.sdp())
                        .background(
                            Color(0xFF1E293B).copy(alpha = 0.4f),
                            RoundedCornerShape(16.sdp())
                        )
                        .border(
                            1.sdp(),
                            Color.White.copy(alpha = 0.1f),
                            RoundedCornerShape(16.sdp())
                        )
                        .padding(16.sdp())
                ) {

                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),

                        factory = { context ->
                            TextView(context).apply {

                                setTextColor(
                                    android.graphics.Color.WHITE
                                )

                                textSize = 14f

                                movementMethod =
                                    LinkMovementMethod.getInstance()
                            }
                        },

                        update = { textView ->

                            textView.text =
                                htmlWithBulletSpacing(
                                    changelog,
                                    gapPx = 20
                                )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.sdp()))

                /*
                 Buttons
                 */

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {

                    if (!isHardUpdate) {

                        TextButton(
                            onClick = onDismiss
                        ) {

                            Text(
                                "Later",
                                color =
                                    Color.White.copy(alpha = 0.6f)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.sdp()))
                    }

                    val enabled = downloadUrl.isNotBlank()

                    Button(
                        enabled = enabled,
                        onClick = { onUpdate(downloadUrl) },
                        modifier = Modifier.height(52.sdp()),
                        shape = RoundedCornerShape(14.sdp()),
                        contentPadding = PaddingValues(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent
                        )
                    ) {

                        Box(
                            modifier = Modifier
                                .background(
                                    if (enabled)
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color(0xFF4F46E5), // Indigo
                                                Color(0xFF6366F1), // Primary
                                                Color(0xFF8B5CF6)  // Purple
                                            )
                                        )
                                    else
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color(0xFF334155),
                                                Color(0xFF334155)
                                            )
                                        )
                                )
                                .padding(horizontal = 28.sdp(), vertical = 14.sdp()),
                            contentAlignment = Alignment.Center
                        ) {

                            Text(
                                text = if (enabled) "Download" else "Unavailable",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.ssp()
                            )
                        }
                    }
                }
            }
        }
    }
}
