package com.heartline.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heartline.app.HeartlineApplication
import com.heartline.app.data.*
import com.heartline.app.media.MediaSessionRepository
import com.heartline.app.ui.theme.HeartlineTheme
import kotlinx.coroutines.launch

private enum class Tab { NOW, LIBRARY, SETTINGS }

@Composable
fun HeartlineApp(openNotificationAccess: () -> Unit, startLyricsService: () -> Unit, stopLyricsService: () -> Unit) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as HeartlineApplication
    val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings("Bubblegum", true, 20, true, true, true, 0, true, "hide_lyrics_locked", true, false, null))
    val state by MediaSessionRepository.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.NOW) }

    HeartlineTheme(settings.theme) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    listOf(Tab.NOW to "NOW", Tab.LIBRARY to "VAULT", Tab.SETTINGS to "SETTINGS").forEach { (t, label) ->
                        NavigationBarItem(selected = tab == t, onClick = { tab = t }, icon = { Text(if (tab == t) "♥" else "◇") }, label = { Text(label, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) })
                    }
                }
            }
        ) { padding ->
            when (tab) {
                Tab.NOW -> PlayerScreen(state, Modifier.padding(padding), openNotificationAccess, startLyricsService)
                Tab.LIBRARY -> LibraryScreen(app.database.trackDao(), Modifier.padding(padding))
                Tab.SETTINGS -> SettingsScreen(settings, app.settings, Modifier.padding(padding), openNotificationAccess, startLyricsService, stopLyricsService)
            }
        }
    }
}

@Composable
private fun PlayerScreen(state: PlayerState, modifier: Modifier, openNotificationAccess: () -> Unit, startLyricsService: () -> Unit) {
    Column(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PixelPanel {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("♡ HEARTLINE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Spacer(Modifier.weight(1f))
                PixelButton(if (state.sourceLocked) "LOCKED" else "AUTO") { MediaSessionRepository.toggleSourceLock() }
            }
        }

        if (state.status == PlayerStatus.PermissionRequired) {
            PixelPanel {
                Text("MUSIC ACCESS NEEDED", fontWeight = FontWeight.Black)
                Text("HEARTLINE reads media metadata. It never records audio or uses your microphone.")
                Spacer(Modifier.height(8.dp))
                PixelButton("ENABLE MUSIC ACCESS", openNotificationAccess)
            }
        }

        PixelPanel {
            Text(state.track?.title?.uppercase() ?: "WAITING FOR A SONG", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 19.sp)
            Text(state.track?.artist ?: "Play music in another app", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.track != null) {
                Spacer(Modifier.height(4.dp))
                Text("${state.track.sourceLabel}  •  ${if (state.isOfflineReady) "OFFLINE READY" else "ONLINE"}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }

        PixelPanel(Modifier.weight(1f)) {
            when {
                state.lyrics.isNotEmpty() -> SyncedLyrics(state)
                !state.plainLyrics.isNullOrBlank() -> Text(state.plainLyrics, modifier = Modifier.verticalScroll(rememberScrollState()), lineHeight = 24.sp)
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (state.isLoadingLyrics) "♪  ·  ·  ·" else "♡", fontSize = 36.sp)
                        Text(state.message ?: "waiting for the beat…", textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace)
                        Text(catFor(state.status), fontSize = 28.sp)
                    }
                }
            }
        }

        Timeline(state)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            PixelButton("|<") { MediaSessionRepository.transportPrevious() }
            PixelButton(if (state.track?.isPlaying == true) "PAUSE" else "START") { MediaSessionRepository.transportPlayPause() }
            PixelButton(">|") { MediaSessionRepository.transportNext() }
            PixelButton(if (state.isFavourite) "♥" else "♡") { MediaSessionRepository.toggleFavourite() }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            PixelButton("−5") { MediaSessionRepository.adjustOffset(-5000) }
            PixelButton("−0.5") { MediaSessionRepository.adjustOffset(-500) }
            Text("SYNC ${formatOffset(state.perTrackOffsetMs)}", Modifier.align(Alignment.CenterVertically), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            PixelButton("+0.5") { MediaSessionRepository.adjustOffset(500) }
            PixelButton("+5") { MediaSessionRepository.adjustOffset(5000) }
        }
        PixelButton("KEEP LYRICS IN NOTIFICATION", startLyricsService, Modifier.fillMaxWidth())
    }
}

@Composable
private fun SyncedLyrics(state: PlayerState) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.currentLineIndex) { if (state.currentLineIndex >= 0) listState.animateScrollToItem(state.currentLineIndex.coerceAtLeast(0)) }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 120.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        itemsIndexed(state.lyrics) { index, line ->
            val active = index == state.currentLineIndex
            Text(
                line.text,
                modifier = Modifier.fillMaxWidth().clickable { MediaSessionRepository.seekTo(line.timestampMs) }.padding(horizontal = 8.dp),
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (active) FontWeight.Black else FontWeight.Normal,
                fontSize = if (active) 23.sp else 16.sp,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Timeline(state: PlayerState) {
    val duration = state.track?.durationMs ?: 0
    val value = if (duration > 0) (state.displayPositionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(formatTime(state.displayPositionMs), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Slider(value = value, onValueChange = { if (duration > 0) MediaSessionRepository.seekTo((it * duration).toLong()) }, modifier = Modifier.weight(1f))
        Text(formatTime(duration), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}

@Composable
private fun LibraryScreen(dao: TrackDao, modifier: Modifier) {
    val tracks by dao.observeOffline().collectAsStateWithLifecycle(initialValue = emptyList())
    Column(modifier.fillMaxSize().padding(12.dp)) {
        Text("OFFLINE VAULT", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 24.sp)
        Text("${tracks.size} saved songs", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tracks, key = { it.fingerprint }) { track ->
                PixelPanel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(track.title.uppercase(), fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                            Text(track.artist)
                            Text(if (track.syncedLyrics != null) "SYNCED • OFFLINE READY" else "PLAIN • OFFLINE READY", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(if (track.isFavourite) "♥" else if (track.keepOffline) "◆" else "◇", fontSize = 24.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(settings: AppSettings, repo: SettingsRepository, modifier: Modifier, openNotificationAccess: () -> Unit, startService: () -> Unit, stopService: () -> Unit) {
    val scope = rememberCoroutineScope()
    LazyColumn(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("SETTINGS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 24.sp) }
        item {
            SettingsSection("APPEARANCE") {
                Text("Theme")
                ThemePicker(settings.theme) { scope.launch { repo.setTheme(it) } }
                SettingSwitch("Pixel cat", settings.catEnabled) { scope.launch { repo.setCatEnabled(it) } }
                SettingSwitch("Reduced motion", settings.reducedMotion) { scope.launch { repo.setReducedMotion(it) } }
            }
        }
        item {
            SettingsSection("OFFLINE VAULT") {
                SettingSwitch("Save recent songs offline", settings.saveRecentOffline) { scope.launch { repo.setSaveRecent(it) } }
                Text("Recent songs retained: ${settings.recentLimit}")
                Slider(value = settings.recentLimit.toFloat(), onValueChange = { scope.launch { repo.setRecentLimit((it / 5).toInt() * 5) } }, valueRange = 5f..40f, steps = 6)
                Text("Maximum 40 • favourites and manual corrections are protected", fontSize = 12.sp)
                SettingSwitch("Download on Wi‑Fi only", settings.wifiOnly) { scope.launch { repo.setWifiOnly(it) } }
            }
        }
        item {
            SettingsSection("LYRICS & SYNC") {
                Text("Global offset: ${formatOffset(settings.globalOffsetMs)}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelButton("−0.5") { scope.launch { repo.setGlobalOffset(settings.globalOffsetMs - 500) } }
                    PixelButton("RESET") { scope.launch { repo.setGlobalOffset(0) } }
                    PixelButton("+0.5") { scope.launch { repo.setGlobalOffset(settings.globalOffsetMs + 500) } }
                }
            }
        }
        item {
            SettingsSection("BACKGROUND & PRIVACY") {
                SettingSwitch("Lyrics notification", settings.backgroundLyrics) { scope.launch { repo.setBackgroundLyrics(it) }; if (it) startService() else stopService() }
                Text("Lock-screen privacy: hide lyric text", fontSize = 13.sp)
                PixelButton("ENABLE / REPAIR MUSIC ACCESS", openNotificationAccess, Modifier.fillMaxWidth())
            }
        }
        item {
            SettingsSection("SECURITY") {
                Text("• No microphone permission\n• No trackers or ads\n• HTTPS-only network traffic\n• Listening data stays on-device\n• Backups exclude lyric history and artwork", lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun ThemePicker(selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(listOf("Bubblegum", "Cyber Angel", "Cherry Soda", "Haunted CRT", "Peach Dream")) { name ->
            FilterChip(selected = selected == name, onClick = { onSelect(name) }, label = { Text(name, fontFamily = FontFamily.Monospace) })
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) = PixelPanel {
    Text(title, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp)); content()
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked, onChange) }
}

@Composable
private fun PixelPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.background(MaterialTheme.colorScheme.outline, RoundedCornerShape(3.dp)).padding(3.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(2.dp)).padding(12.dp), content = content)
}

@Composable
private fun PixelButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(3.dp), border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 9.dp)) {
        Text(text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
    }
}

private fun formatTime(ms: Long): String { val sec = (ms / 1000).coerceAtLeast(0); return "%02d:%02d".format(sec / 60, sec % 60) }
private fun formatOffset(ms: Long): String = "%+.1fs".format(ms / 1000.0)
private fun catFor(status: PlayerStatus): String = when (status) {
    PlayerStatus.LoadingLyrics, PlayerStatus.Detecting -> "=^･ω･^=  ♪"
    PlayerStatus.Offline -> "=；ェ；=  ⌁"
    PlayerStatus.NoLyrics -> "=・ェ・=?"
    PlayerStatus.Instrumental -> "=^‥^=  ♫"
    else -> "=^._.^="
}
