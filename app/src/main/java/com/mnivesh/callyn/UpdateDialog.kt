package com.mnivesh.callyn.ui

import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.BulletSpan
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
        if (isHardUpdate) DialogProperties(false, false)
        else DialogProperties(true, true)

    val latestVersion =
        versionInfo.latestVersion?.takeIf { it.isNotBlank() } ?: "New"

    val changelog =
        versionInfo.changelog?.takeIf { it.isNotBlank() }
            ?: "<i>No details provided.</i>"

    val downloadUrl =
        versionInfo.downloadUrl?.takeIf { it.isNotBlank() } ?: ""

    AlertDialog(
        onDismissRequest = { if (!isHardUpdate) onDismiss() },
        properties = properties,
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isHardUpdate) Icons.Default.Warning else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (isHardUpdate) Color(0xFFF87171) else Color(0xFF3B82F6)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "Update Available",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Version $latestVersion is available.",
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "What's New",
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF60A5FA),
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x14FFFFFF))
                        .padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 18.dp)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth(),
                        factory = { context ->
                            TextView(context).apply {
                                setTextColor(android.graphics.Color.WHITE)
                                textSize = 14f
                                movementMethod = LinkMovementMethod.getInstance()
                            }
                        },
                        update = { textView ->
                            textView.text = htmlWithBulletSpacing(
                                changelog,
                                gapPx = 20
                            )
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = downloadUrl.isNotBlank(),
                onClick = { onUpdate(downloadUrl) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3B82F6)
                )
            ) {
                Text(
                    text =
                        if (downloadUrl.isBlank()) "Unavailable"
                        else if (isHardUpdate) "Update to Continue"
                        else "Update Now"
                )
            }
        },
        dismissButton = {
            if (!isHardUpdate) {
                TextButton(onClick = onDismiss) {
                    Text("Later", color = Color.Gray)
                }
            }
        },
        containerColor = Color(0xFF1E293B),
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}
