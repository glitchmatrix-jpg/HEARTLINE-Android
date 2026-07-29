package com.heartline.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heartline.app.HeartlineApplication
import com.heartline.app.R
import com.heartline.app.data.AppSettings
import com.heartline.app.data.LyricCandidate
import com.heartline.app.data.PlaybackMode
import com.heartline.app.data.PlayerState
import com.heartline.app.data.PlayerStatus
import com.heartline.app.data.SettingsRepository
import com.heartline.app.data.TrackDao
import com.heartline.app.media.MediaSessionRepository
import com.heartline.app.ui.theme.HeartlineTheme
import kotlinx.coroutines.launch

private enum class V21Tab { NOW, VAULT, SETTINGS }

@Composable
fun HeartlineV21App(
    openNotificationAccess: () -> Unit,
    startLyricsService: () -> Unit,
    stopLyricsService: () -> Unit,
    enterFocusMode: () -> Unit
) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as HeartlineApplication
    val settings by app.settings.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings("Bubblegum", true, 20, true, true, true, 0, true, "hide_lyrics_locked", true, false, null)
    )
    val state by MediaSessionRepository.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(V21Tab.NOW) }

    HeartlineTheme(settings.theme) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { PremiumNav(tab) { tab = it } }
        ) { padding ->
            when (tab) {
                V21Tab.NOW -> NowV21(state, settings.catEnabled, Modifier.padding(padding), openNotificationAccess, startLyricsService, enterFocusMode)
                V21Tab.VAULT -> VaultV21(app.database.trackDao(), Modifier.padding(padding))
                V21Tab.SETTINGS -> SettingsV21(settings, app.settings, Modifier.padding(padding), openNotificationAccess, startLyricsService, stopLyricsService)
            }
        }
        if (state.candidatesVisible) CandidateDialog(state)
    }
}

@Composable
private fun PremiumNav(selected: V21Tab, onSelect: (V21Tab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 18.dp, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(V21Tab.NOW to "NOW", V21Tab.VAULT to "VAULT", V21Tab.SETTINGS to "SETTINGS").forEach { (tab, label) ->
                val active = selected == tab
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onSelect(tab) }.padding(horizontal = 24.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if (tab == V21Tab.NOW) "♥" else if (tab == V21Tab.VAULT) "▣" else "✦", color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp)
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun NowV21(
    state: PlayerState,
    catEnabled: Boolean,
    modifier: Modifier,
    openNotificationAccess: () -> Unit,
    startLyricsService: () -> Unit,
    enterFocusMode: () -> Unit
) {
    Column(modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BrandBar(state)
        ModeSwitcher(state.playbackMode)

        if (state.status == PlayerStatus.PermissionRequired) {
            PremiumCard(accented = true) {
                Text("MUSIC ACCESS NEEDED", style = MaterialTheme.typography.titleMedium)
                Text("HEARTLINE reads media metadata only — never your microphone.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                ActionButton("ENABLE MUSIC ACCESS", Modifier.fillMaxWidth(), onClick = openNotificationAccess)
            }
        }

        TrackIdentity(state)
        if (state.playbackMode == PlaybackMode.SEARCH) SearchPanel(state)

        PremiumCard(Modifier.weight(1f)) {
            Box(Modifier.fillMaxSize()) {
                when {
                    state.lyrics.isNotEmpty() -> SyncedLyricsV21(state)
                    !state.plainLyrics.isNullOrBlank() -> Text(state.plainLyrics, Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp), lineHeight = 25.sp)
                    else -> EmptyState(state)
                }
                if (catEnabled) {
                    Image(
                        painter = painterResource(R.drawable.cat_mascot),
                        contentDescription = "HEARTLINE cat mascot",
                        modifier = Modifier.align(Alignment.BottomEnd).size(66.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        TimelineV21(state)
        PlaybackControlsV21(state)
        SyncControlsV21(state)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("LYRICS SOURCE", Modifier.weight(1f)) { MediaSessionRepository.requestCandidates() }
            ActionButton("FOCUS", Modifier.weight(0.72f), primary = true, onClick = enterFocusMode)
        }
        ActionButton("KEEP LYRICS NEARBY", Modifier.fillMaxWidth(), primary = true, onClick = startLyricsService)
    }
}

@Composable
private fun BrandBar(state: PlayerState) {
    PremiumCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_launcher_foreground), "HEARTLINE", Modifier.size(42.dp))
            Spacer(Modifier.size(9.dp))
            Column(Modifier.weight(1f)) {
                Text("HEARTLINE", style = MaterialTheme.typography.titleLarge)
                Text("the words follow the music", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MiniChip(if (state.sourceLocked) "SOURCE LOCKED" else "SOURCE AUTO") { MediaSessionRepository.toggleSourceLock() }
        }
    }
}

@Composable
private fun ModeSwitcher(selected: PlaybackMode) {
    PremiumCard {
        Text("PLAYBACK MODE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PlaybackMode.entries.forEach { mode ->
                FilterChip(
                    selected = selected == mode,
                    onClick = { MediaSessionRepository.setPlaybackMode(mode) },
                    label = { Text(mode.name, style = MaterialTheme.typography.labelLarge) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Text(
            when (selected) {
                PlaybackMode.AUTO -> "Controls and slider operate the real music player."
                PlaybackMode.MANUAL -> "Controls move HEARTLINE's lyric clock; the song keeps playing."
                PlaybackMode.SEARCH -> "Search and open a lyric version without automatic syncing."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TrackIdentity(state: PlayerState) {
    PremiumCard(accented = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(58.dp), color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("♫", fontSize = 27.sp, color = MaterialTheme.colorScheme.primary) }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(state.track?.title?.uppercase() ?: "WAITING FOR A SONG", style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(state.track?.artist ?: "Play music or use Search mode", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    state.track?.let { StatusTag(it.sourceLabel) }
                    StatusTag(state.playbackMode.name)
                    if (state.isOfflineReady) StatusTag("OFFLINE")
                }
            }
            MiniChip(if (state.isFavourite) "♥" else "♡") { MediaSessionRepository.toggleFavourite() }
        }
    }
}

@Composable
private fun SearchPanel(state: PlayerState) {
    var query by remember(state.searchQuery) { mutableStateOf(state.searchQuery) }
    PremiumCard(accented = true) {
        Text("SEARCH LYRICS", style = MaterialTheme.typography.titleMedium)
        Text("Search another version, cover, live recording, or remaster.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("title and artist") })
        Spacer(Modifier.height(8.dp))
        ActionButton("SEARCH LRCLIB", Modifier.fillMaxWidth(), primary = true) { MediaSessionRepository.requestCandidates(query) }
    }
}

@Composable
private fun SyncedLyricsV21(state: PlayerState) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.currentLineIndex) { if (state.currentLineIndex >= 0) listState.animateScrollToItem(state.currentLineIndex) }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 105.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        itemsIndexed(state.lyrics) { index, line ->
            val active = index == state.currentLineIndex
            Surface(
                modifier = Modifier.fillMaxWidth().animateContentSize().clickable { MediaSessionRepository.seekTo(line.timestampMs) },
                color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = RoundedCornerShape(15.dp)
            ) {
                Text(line.text, Modifier.padding(horizontal = 13.dp, vertical = if (active) 12.dp else 4.dp), textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace, fontWeight = if (active) FontWeight.Black else FontWeight.Normal,
                    fontSize = if (active) 23.sp else 16.sp, lineHeight = if (active) 29.sp else 22.sp,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyState(state: PlayerState) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (state.isLoadingLyrics) "♪  ·  ·  ·" else "♡", fontSize = 42.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(state.message ?: "waiting for the beat…", textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            if (state.status == PlayerStatus.NoLyrics || state.status == PlayerStatus.Error) {
                Spacer(Modifier.height(10.dp))
                ActionButton("TRY OTHER VERSIONS") { MediaSessionRepository.requestCandidates() }
            }
        }
    }
}

@Composable
private fun TimelineV21(state: PlayerState) {
    val duration = state.track?.durationMs ?: 0L
    val value = if (duration > 0) (state.displayPositionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(formatTimeV21(state.displayPositionMs), style = MaterialTheme.typography.labelSmall)
        Slider(value, { if (duration > 0) MediaSessionRepository.seekTo((it * duration).toLong()) }, Modifier.weight(1f).padding(horizontal = 5.dp))
        Text(formatTimeV21(duration), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PlaybackControlsV21(state: PlayerState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton("|<", Modifier.weight(1f)) { MediaSessionRepository.transportPrevious() }
        val playing = if (state.playbackMode == PlaybackMode.AUTO) state.track?.isPlaying == true else state.manualClockPlaying
        ActionButton(if (playing) "PAUSE" else "PLAY", Modifier.weight(1.55f), primary = true) { MediaSessionRepository.transportPlayPause() }
        ActionButton(">|", Modifier.weight(1f)) { MediaSessionRepository.transportNext() }
        ActionButton(if (state.isFavourite) "♥" else "♡", Modifier.weight(1f)) { MediaSessionRepository.toggleFavourite() }
    }
}

@Composable
private fun SyncControlsV21(state: PlayerState) {
    PremiumCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TinyAction("−5") { MediaSessionRepository.adjustOffset(-5000) }
            TinyAction("−0.5") { MediaSessionRepository.adjustOffset(-500) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (state.playbackMode == PlaybackMode.AUTO) "SYNC" else "LYRIC CLOCK", style = MaterialTheme.typography.labelSmall)
                Text("%+.1fs".format(state.perTrackOffsetMs / 1000.0), style = MaterialTheme.typography.titleMedium)
            }
            TinyAction("+0.5") { MediaSessionRepository.adjustOffset(500) }
            TinyAction("+5") { MediaSessionRepository.adjustOffset(5000) }
        }
        if (state.playbackMode != PlaybackMode.AUTO) {
            Spacer(Modifier.height(7.dp))
            TextButton(onClick = { MediaSessionRepository.rejoinPlayerPosition() }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("REJOIN LIVE PLAYER") }
        }
    }
}

@Composable
private fun CandidateDialog(state: PlayerState) {
    AlertDialog(
        onDismissRequest = { MediaSessionRepository.dismissCandidates() },
        title = { Text("LYRICS SOURCE", style = MaterialTheme.typography.titleLarge) },
        text = {
            if (state.isLoadingLyrics) Text("Searching possible versions…")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(420.dp)) {
                items(state.candidates, key = LyricCandidate::id) { candidate -> CandidateCard(candidate, candidate.id == state.selectedProviderId) }
            }
        },
        confirmButton = { TextButton(onClick = { MediaSessionRepository.dismissCandidates() }) { Text("CLOSE") } }
    )
}

@Composable
private fun CandidateCard(candidate: LyricCandidate, selected: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { MediaSessionRepository.selectCandidate(candidate) },
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(candidate.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(candidate.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (selected) "CURRENT" else "${(candidate.score * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                StatusTag(if (candidate.synced) "SYNCED" else "PLAIN")
                candidate.durationSeconds?.let { StatusTag(formatTimeV21((it * 1000).toLong())) }
                if (candidate.instrumental) StatusTag("INSTRUMENTAL")
            }
            if (candidate.preview.isNotBlank()) Text(candidate.preview, Modifier.padding(top = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun VaultV21(dao: TrackDao, modifier: Modifier) {
    val tracks by dao.observeOffline().collectAsStateWithLifecycle(initialValue = emptyList())
    Column(modifier.fillMaxSize().padding(14.dp)) {
        Text("OFFLINE VAULT", style = MaterialTheme.typography.headlineMedium)
        Text("${tracks.size} saved songs · ready without a signal", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(13.dp))
        if (tracks.isEmpty()) PremiumCard(accented = true) { Text("YOUR VAULT IS QUIET", style = MaterialTheme.typography.titleMedium); Text("Play songs and HEARTLINE will keep your newest lyrics close.") }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(tracks, key = { it.fingerprint }) { track ->
                PremiumCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(48.dp), color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(13.dp)) { Box(contentAlignment = Alignment.Center) { Text("♫", fontSize = 23.sp) } }
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.title.uppercase(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) { StatusTag(if (track.syncedLyrics != null) "SYNCED" else "PLAIN"); StatusTag("OFFLINE"); if (track.manuallyMatched) StatusTag("CHOSEN") }
                        }
                        Text(if (track.isFavourite) "♥" else "◇", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsV21(settings: AppSettings, repo: SettingsRepository, modifier: Modifier, openNotificationAccess: () -> Unit, startService: () -> Unit, stopService: () -> Unit) {
    val scope = rememberCoroutineScope()
    LazyColumn(modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("SETTINGS", style = MaterialTheme.typography.headlineMedium); Text("A calmer control room for HEARTLINE.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { SettingsCard("APPEARANCE", "Keep the personality; lose the clutter.") {
            LazyColumn(modifier = Modifier.height(58.dp), horizontalAlignment = Alignment.Start) { item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Bubblegum", "Cyber Angel", "Cherry Soda", "Haunted CRT", "Peach Dream").forEach { name -> FilterChip(settings.theme == name, { scope.launch { repo.setTheme(name) } }, { Text(name) }) } } } }
            SettingLine("Pixel mascot", "Show the HEARTLINE companion", settings.catEnabled) { scope.launch { repo.setCatEnabled(it) } }
            SettingLine("Reduced motion", "Disable decorative motion", settings.reducedMotion) { scope.launch { repo.setReducedMotion(it) } }
        } }
        item { SettingsCard("OFFLINE VAULT", "Lyrics and timing stay ready.") {
            SettingLine("Save recent songs", "Keep up to ${settings.recentLimit} lyric files", settings.saveRecentOffline) { scope.launch { repo.setSaveRecent(it) } }
            Slider(settings.recentLimit.toFloat(), { scope.launch { repo.setRecentLimit(((it / 5).toInt() * 5).coerceIn(5, 40)) } }, valueRange = 5f..40f, steps = 6)
            SettingLine("Wi-Fi only", "Avoid mobile-data lyric downloads", settings.wifiOnly) { scope.launch { repo.setWifiOnly(it) } }
        } }
        item { SettingsCard("BACKGROUND", "A clean, system-aware lyric notification.") {
            SettingLine("Lyrics notification", "Keep the current line nearby", settings.backgroundLyrics) { scope.launch { repo.setBackgroundLyrics(it) }; if (it) startService() else stopService() }
            ActionButton("ENABLE / REPAIR MUSIC ACCESS", Modifier.fillMaxWidth(), onClick = openNotificationAccess)
        } }
        item { SettingsCard("PRIVACY", "Private by design.") { Text("NO MICROPHONE · NO TRACKERS · NO ADS\nHTTPS ONLY · HISTORY STAYS ON-DEVICE", style = MaterialTheme.typography.labelLarge, lineHeight = 21.sp) } }
    }
}

@Composable
private fun SettingsCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) = PremiumCard {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(10.dp)); content()
}

@Composable
private fun SettingLine(label: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.titleMedium); Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked, onChange)
    }
}

@Composable
private fun PremiumCard(modifier: Modifier = Modifier, accented: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.shadow(7.dp, RoundedCornerShape(17.dp)), color = if (accented) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(17.dp), border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)) { Column(Modifier.padding(13.dp), content = content) }
}

@Composable
private fun ActionButton(text: String, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
    Button(onClick, modifier, shape = RoundedCornerShape(11.dp), border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.buttonColors(containerColor = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun MiniChip(text: String, onClick: () -> Unit) { Surface(Modifier.clickable(onClick = onClick), color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)) { Text(text, Modifier.padding(horizontal = 9.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall) } }
@Composable
private fun StatusTag(text: String) { Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Text(text, Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall) } }
@Composable
private fun TinyAction(text: String, onClick: () -> Unit) { Surface(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick), color = MaterialTheme.colorScheme.surfaceVariant, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) { Text(text, Modifier.padding(horizontal = 8.dp, vertical = 7.dp), style = MaterialTheme.typography.labelLarge) } }
private fun formatTimeV21(ms: Long): String { val seconds = (ms / 1000).coerceAtLeast(0); return "%02d:%02d".format(seconds / 60, seconds % 60) }
