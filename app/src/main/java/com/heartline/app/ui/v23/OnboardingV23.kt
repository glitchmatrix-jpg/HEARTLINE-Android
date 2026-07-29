package com.heartline.app.ui.v23

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.heartline.app.data.ListenerConnection
import com.heartline.app.data.PlayerState

@Composable
fun V23OnboardingDialog(
    state: PlayerState,
    openNotificationAccess: () -> Unit,
    onTestConnection: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val musicAccess = state.listenerConnection != ListenerConnection.PERMISSION_REQUIRED
    val listenerConnected = state.listenerConnection == ListenerConnection.CONNECTED
    val songDetected = state.track != null

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Welcome to HEARTLINE") },
        text = {
            Column {
                Text("Complete the connection checklist once. HEARTLINE will repair normal disconnects automatically afterward.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                OnboardingStep("Notifications allowed", notificationsGranted)
                OnboardingStep("Music access enabled", musicAccess)
                OnboardingStep("Listener connected", listenerConnected)
                OnboardingStep("Test song detected", songDetected)
                Spacer(Modifier.height(12.dp))
                if (!musicAccess) Button(openNotificationAccess, Modifier.fillMaxWidth()) { Text("Enable music access") }
                else OutlinedButton(onTestConnection, Modifier.fillMaxWidth()) { Text("Test connection") }
            }
        },
        confirmButton = {
            Button(onClick = onComplete, enabled = musicAccess && listenerConnected) { Text(if (songDetected) "Done" else "Continue") }
        },
        dismissButton = { TextButton(onClick = onComplete) { Text("Skip") } }
    )
}

@Composable
private fun OnboardingStep(label: String, complete: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (complete) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
