package com.heartline.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.heartline.app.service.LyricsForegroundService
import com.heartline.app.ui.HeartlineApp

class MainActivity : ComponentActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startLyricsService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HeartlineApp(
                openNotificationAccess = {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                startLyricsService = ::requestNotificationsAndStart,
                stopLyricsService = {
                    stopService(Intent(this, LyricsForegroundService::class.java))
                }
            )
        }
    }

    private fun requestNotificationsAndStart() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startLyricsService()
        }
    }

    private fun startLyricsService() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, LyricsForegroundService::class.java)
                .setAction(LyricsForegroundService.ACTION_START)
        )
    }
}
