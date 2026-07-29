package com.heartline.app.ui.v23

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heartline.app.HeartlineApplication
import com.heartline.app.data.AppSettings
import com.heartline.app.media.MediaSessionRepository
import com.heartline.app.ui.theme.HeartlineTheme

enum class V23Tab { NOW, VAULT, SETTINGS }
enum class V23Sheet { SOURCE, SYNC, MORE, SHARE, DIAGNOSTICS }

@Composable
fun HeartlineV23App(
    openNotificationAccess: () -> Unit,
    startLyricsService: () -> Unit,
    stopLyricsService: () -> Unit,
    enterFocusMode: () -> Unit
) {
    val app = LocalContext.current.applicationContext as HeartlineApplication
    val settings by app.settings.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings("Bubblegum", true, 20, true, true, true, 0, true, "hide_lyrics_locked", true, false, null)
    )
    val state by MediaSessionRepository.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(V23Tab.NOW) }
    var sheet by remember { mutableStateOf<V23Sheet?>(null) }

    HeartlineTheme(settings) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { V23Navigation(tab) { tab = it } }
        ) { padding ->
            when (tab) {
                V23Tab.NOW -> V23NowScreen(
                    state = state,
                    settings = settings,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    openNotificationAccess = openNotificationAccess,
                    onSource = { sheet = V23Sheet.SOURCE },
                    onSync = { sheet = V23Sheet.SYNC },
                    onShare = { sheet = V23Sheet.SHARE },
                    onMore = { sheet = V23Sheet.MORE }
                )
                V23Tab.VAULT -> V23VaultScreen(app.database.trackDao(), Modifier.fillMaxSize().padding(padding))
                V23Tab.SETTINGS -> V23SettingsScreen(
                    settings = settings,
                    repo = app.settings,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    openNotificationAccess = openNotificationAccess,
                    startService = startLyricsService,
                    stopService = stopLyricsService,
                    onDiagnostics = { sheet = V23Sheet.DIAGNOSTICS }
                )
            }
        }

        when (sheet) {
            V23Sheet.SOURCE -> V23SourceSheet(state) { sheet = null }
            V23Sheet.SYNC -> V23SyncSheet(state) { sheet = null }
            V23Sheet.SHARE -> V23ShareLyricsSheet(state, settings.shareBranding) { sheet = null }
            V23Sheet.DIAGNOSTICS -> V23DiagnosticsSheet(openNotificationAccess) { sheet = null }
            V23Sheet.MORE -> V23MoreSheet(
                state = state,
                onDismiss = { sheet = null },
                onShare = { sheet = V23Sheet.SHARE },
                onFocus = { sheet = null; enterFocusMode() },
                onLiveNotification = { sheet = null; startLyricsService() },
                onMusicAccess = { sheet = null; openNotificationAccess() },
                onDiagnostics = { sheet = V23Sheet.DIAGNOSTICS }
            )
            null -> Unit
        }
        if (state.candidatesVisible) V23CandidateDialog(state)
    }
}

@Composable
private fun V23Navigation(selected: V23Tab, onSelect: (V23Tab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        NavigationBarItem(
            selected = selected == V23Tab.NOW,
            onClick = { onSelect(V23Tab.NOW) },
            icon = { Icon(Icons.Rounded.Favorite, contentDescription = "Now playing") },
            label = { Text("Now") },
            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer)
        )
        NavigationBarItem(
            selected = selected == V23Tab.VAULT,
            onClick = { onSelect(V23Tab.VAULT) },
            icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = "Offline vault") },
            label = { Text("Vault") },
            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer)
        )
        NavigationBarItem(
            selected = selected == V23Tab.SETTINGS,
            onClick = { onSelect(V23Tab.SETTINGS) },
            icon = { Icon(Icons.Rounded.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer)
        )
    }
}
