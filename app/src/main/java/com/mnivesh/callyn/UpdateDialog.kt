package com.mnivesh.callyn.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.mnivesh.callyn.api.VersionResponse

@Composable
fun UpdateDialog(
    versionInfo: VersionResponse,
    isHardUpdate: Boolean,
    onDismiss: () -> Unit,
    onUpdate: (String) -> Unit
) {
    // If Hard Update: prevent dismissal on back press or click outside
    val properties = if (isHardUpdate) {
        DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    } else {
        DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    }

    AlertDialog(
        onDismissRequest = { if (!isHardUpdate) onDismiss() },
        properties = properties,
        title = {
            Text(
                text = "Update Available",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column {
                Text("Version ${versionInfo.latestVersion} is available.")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "What's New:",
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF60A5FA)
                )
                Text(
                    text = versionInfo.changelog,
                    fontSize = 14.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onUpdate(versionInfo.downloadUrl) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
            ) {
                Text("Update Now")
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