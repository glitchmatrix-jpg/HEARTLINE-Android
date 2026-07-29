package com.heartline.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.heartline.app.data.*
import com.heartline.app.media.MediaSessionRepository
import com.heartline.app.ui.theme.HeartlineTheme
import kotlinx.coroutines.launch

private enum class V22Tab { NOW, VAULT, SETTINGS }
private enum class Sheet { SOURCE, SYNC, MORE }

@OptIn(ExperimentalMaterial3Api::class)
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
    var tab by remember { mutableStateOf(V22Tab.NOW) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }

    HeartlineTheme(settings.theme) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { CompactNav(tab) { tab = it } }
        ) { padding ->
            when (tab) {
                V22Tab.NOW -> NowScreen(
                    state = state,
                    catEnabled = settings.catEnabled,
                    modifier = Modifier.padding(padding),
                    openNotificationAccess = openNotificationAccess,
                    onSource = { sheet = Sheet.SOURCE },
                    onSync = { sheet = Sheet.SYNC },
                    onMore = { sheet = Sheet.MORE }
                )
                V22Tab.VAULT -> VaultScreen(app.database.trackDao(), Modifier.padding(padding))
                V22Tab.SETTINGS -> SettingsScreen(
                    settings, app.settings, Modifier.padding(padding),
                    openNotificationAccess, startLyricsService, stopLyricsService
                )
            }
        }

        when (sheet) {
            Sheet.SOURCE -> SourceSheet(state, onDismiss = { sheet = null })
            Sheet.SYNC -> SyncSheet(state, onDismiss = { sheet = null })
            Sheet.MORE -> MoreSheet(
                state = state,
                onDismiss = { sheet = null },
                onFocus = { sheet = null; enterFocusMode() },
                onLiveNotification = { sheet = null; startLyricsService() },
                onMusicAccess = { sheet = null; openNotificationAccess() }
            )
            null -> Unit
        }
        if (state.candidatesVisible) CandidateDialog(state)
    }
}

@Composable
private fun CompactNav(selected: V22Tab, onSelect: (V22Tab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp).padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                Triple(V22Tab.NOW, "♥", "Now"),
                Triple(V22Tab.VAULT, "▣", "Vault"),
                Triple(V22Tab.SETTINGS, "⚙", "Settings")
            ).forEach { (tab, icon, label) ->
                val active = selected == tab
                Row(
                    Modifier.clip(RoundedCornerShape(22.dp))
                        .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onSelect(tab) }
                        .padding(horizontal = if (active) 18.dp else 13.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(icon, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (active) Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun NowScreen(
    state: PlayerState,
    catEnabled: Boolean,
    modifier: Modifier,
    openNotificationAccess: () -> Unit,
    onSource: () -> Unit,
    onSync: () -> Unit,
    onMore: () -> Unit
) {
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)) {
        TopBar(onMore)
        Spacer(Modifier.height(8.dp))

        if (state.status == PlayerStatus.PermissionRequired) {
            PermissionBanner(openNotificationAccess)
            Spacer(Modifier.height(10.dp))
        }

        TrackHeader(state)
        Spacer(Modifier.height(10.dp))
        ModeSelector(state.playbackMode, onSearch = onSource)
        Spacer(Modifier.height(8.dp))

        LyricsStage(
            state = state,
            catEnabled = catEnabled,
            modifier = Modifier.weight(1f).fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))
        Timeline(state)
        Transport(state)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("Choose lyrics", Modifier.weight(1f), onClick = onSource)
            SoftButton("Adjust sync", Modifier.weight(1f), onClick = onSync)
        }
    }
}

@Composable
private fun TopBar(onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.heartline_logo),
            contentDescription = "HEARTLINE",
            modifier = Modifier.size(38.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text("HEARTLINE", style = MaterialTheme.typography.titleLarge)
            Text("the words follow the music", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onMore) { Text("•••", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun PermissionBanner(openNotificationAccess: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Music access needed", style = MaterialTheme.typography.titleMedium)
                Text("Reads playback metadata only — never your microphone.", style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = openNotificationAccess) { Text("Enable") }
        }
    }
}

@Composable
private fun TrackHeader(state: PlayerState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(54.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(17.dp)
        ) { Box(contentAlignment = Alignment.Center) { Text("♫", fontSize = 25.sp, color = MaterialTheme.colorScheme.primary) } }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                state.track?.title ?: "Waiting for a song",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append(state.track?.artist ?: "Play music in another app")
                    state.track?.let { append(" · ${friendlySource(it.sourceLabel, it.sourcePackage)}") }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { MediaSessionRepository.toggleFavourite() }) {
            Text(if (state.isFavourite) "♥" else "♡", fontSize = 25.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ModeSelector(selected: PlaybackMode, onSearch: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(15.dp)) {
        Row(Modifier.fillMaxWidth().padding(4.dp)) {
            PlaybackMode.entries.forEach { mode ->
                val active = mode == selected
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable {
                            MediaSessionRepository.setPlaybackMode(mode)
                            if (mode == PlaybackMode.SEARCH) onSearch()
                        }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(mode.name.lowercase().replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelLarge,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LyricsStage(state: PlayerState, catEnabled: Boolean, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 1.dp
    ) {
        Box(Modifier.fillMaxSize()) {
            when {
                state.lyrics.isNotEmpty() -> SyncedLyrics(state)
                !state.plainLyrics.isNullOrBlank() -> Text(
                    state.plainLyrics,
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 28.sp
                )
                else -> EmptyState(state, catEnabled)
            }
            Box(
                Modifier.fillMaxWidth().height(42.dp).align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, Color.Transparent)))
            )
            Box(
                Modifier.fillMaxWidth().height(42.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface)))
            )
        }
    }
}

@Composable
private fun SyncedLyrics(state: PlayerState) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.currentLineIndex) {
        if (state.currentLineIndex >= 0) {
            listState.animateScrollToItem((state.currentLineIndex - 1).coerceAtLeast(0))
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 82.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        itemsIndexed(state.lyrics) { index, line ->
            val distance = kotlin.math.abs(index - state.currentLineIndex)
            val active = distance == 0
            Text(
                text = line.text,
                modifier = Modifier.fillMaxWidth().animateContentSize()
                    .clickable { MediaSessionRepository.seekTo(line.timestampMs) }
                    .padding(vertical = if (active) 7.dp else 2.dp)
                    .alpha(if (active) 1f else if (distance == 1) 0.68f else 0.38f),
                textAlign = TextAlign.Center,
                fontFamily = if (active) FontFamily.Default else FontFamily.Default,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                fontSize = if (active) 28.sp else 19.sp,
                lineHeight = if (active) 35.sp else 26.sp,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun EmptyState(state: PlayerState, catEnabled: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (catEnabled) {
            Image(painterResource(R.drawable.cat_mascot), "HEARTLINE mascot", Modifier.size(82.dp), contentScale = ContentScale.Fit)
            Spacer(Modifier.height(14.dp))
        } else Text(if (state.isLoadingLyrics) "♪ · · ·" else "♡", fontSize = 40.sp, color = MaterialTheme.colorScheme.primary)
        Text(state.message ?: "Waiting for the beat…", textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        if (state.status == PlayerStatus.NoLyrics || state.status == PlayerStatus.Error) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { MediaSessionRepository.requestCandidates() }) { Text("Try other versions") }
        }
    }
}

@Composable
private fun Timeline(state: PlayerState) {
    val duration = state.track?.durationMs ?: 0L
    val value = if (duration > 0) (state.displayPositionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(formatTime(state.displayPositionMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = value,
            onValueChange = { if (duration > 0) MediaSessionRepository.seekTo((it * duration).toLong()) },
            modifier = Modifier.weight(1f).padding(horizontal = 5.dp)
        )
        Text(formatTime(duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Transport(state: PlayerState) {
    val playing = if (state.playbackMode == PlaybackMode.AUTO) state.track?.isPlaying == true else state.manualClockPlaying
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        RoundControl("|‹") { MediaSessionRepository.transportPrevious() }
        FilledIconButton(
            onClick = { MediaSessionRepository.transportPlayPause() },
            modifier = Modifier.size(58.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) { Text(if (playing) "Ⅱ" else "▶", fontSize = 22.sp, color = MaterialTheme.colorScheme.onPrimary) }
        RoundControl("›|") { MediaSessionRepository.transportNext() }
    }
}

@Composable
private fun RoundControl(text: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) { Text(text, fontSize = 22.sp) }
}

@Composable
private fun SoftButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) { Text(text) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceSheet(state: PlayerState, onDismiss: () -> Unit) {
    var query by remember(state.searchQuery) { mutableStateOf(state.searchQuery) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Choose lyrics", style = MaterialTheme.typography.headlineMedium)
            Text("Find another version or keep this match for the song.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Title and artist") }
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = { MediaSessionRepository.requestCandidates(query) }, modifier = Modifier.fillMaxWidth()) { Text("Search LRCLIB") }
            Spacer(Modifier.height(12.dp))
            SettingRow("Automatic source", "Follow the active music app", !state.sourceLocked) {
                MediaSessionRepository.toggleSourceLock()
            }
            TextButton(onClick = { MediaSessionRepository.requestCandidates() }, modifier = Modifier.align(Alignment.End)) { Text("Show recommended versions") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncSheet(state: PlayerState, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Adjust sync", style = MaterialTheme.typography.headlineMedium)
            Text("%+.1fs".format(state.perTrackOffsetMs / 1000.0), fontSize = 38.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Negative moves lyrics earlier · positive moves them later", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(-5000L to "−5", -500L to "−0.5", 500L to "+0.5", 5000L to "+5").forEach { (delta, label) ->
                    OutlinedButton(onClick = { MediaSessionRepository.adjustOffset(delta) }, modifier = Modifier.weight(1f)) { Text(label) }
                }
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { MediaSessionRepository.adjustOffset(-state.perTrackOffsetMs) }) { Text("Reset offset") }
            if (state.playbackMode != PlaybackMode.AUTO) {
                Button(onClick = { MediaSessionRepository.rejoinPlayerPosition(); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Sync to current song position") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreSheet(
    state: PlayerState,
    onDismiss: () -> Unit,
    onFocus: () -> Unit,
    onLiveNotification: () -> Unit,
    onMusicAccess: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("More", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            MenuAction("Focus mode", "Hide system bars for an immersive lyric view", onFocus)
            MenuAction("Live notification", "Keep the current lyric nearby", onLiveNotification)
            MenuAction(if (state.sourceLocked) "Unlock music source" else "Lock music source", "Control which player HEARTLINE follows") { MediaSessionRepository.toggleSourceLock() }
            MenuAction("Music access", "Open Android notification access settings", onMusicAccess)
        }
    }
}

@Composable
private fun MenuAction(title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CandidateDialog(state: PlayerState) {
    AlertDialog(
        onDismissRequest = { MediaSessionRepository.dismissCandidates() },
        title = { Text("Lyrics versions") },
        text = {
            if (state.isLoadingLyrics) Text("Searching possible versions…")
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 460.dp)) {
                items(state.candidates, key = LyricCandidate::id) { candidate ->
                    CandidateCard(candidate, candidate.id == state.selectedProviderId)
                }
            }
        },
        confirmButton = { TextButton(onClick = { MediaSessionRepository.dismissCandidates() }) { Text("Close") } }
    )
}

@Composable
private fun CandidateCard(candidate: LyricCandidate, selected: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { MediaSessionRepository.selectCandidate(candidate) },
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(candidate.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(candidate.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (selected) "Current" else "${(candidate.score * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusTag(if (candidate.synced) "Synced" else "Plain")
                candidate.durationSeconds?.let { StatusTag(formatTime((it * 1000).toLong())) }
            }
            if (candidate.preview.isNotBlank()) Text(candidate.preview, Modifier.padding(top = 7.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun VaultScreen(dao: TrackDao, modifier: Modifier) {
    val tracks by dao.observeOffline().collectAsStateWithLifecycle(initialValue = emptyList())
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text("Offline vault", style = MaterialTheme.typography.headlineMedium)
        Text("${tracks.size} saved songs", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        if (tracks.isEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("♡", fontSize = 36.sp, color = MaterialTheme.colorScheme.primary)
                    Text("Your vault is quiet", style = MaterialTheme.typography.titleMedium)
                    Text("Played lyrics can stay ready without a signal.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(tracks, key = { it.fingerprint }) { track ->
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(44.dp), color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text("♫") }
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (track.isFavourite) Text("♥", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    settings: AppSettings,
    repo: SettingsRepository,
    modifier: Modifier,
    openNotificationAccess: () -> Unit,
    startService: () -> Unit,
    stopService: () -> Unit
) {
    val scope = rememberCoroutineScope()
    LazyColumn(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Text("Make HEARTLINE feel like yours.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SettingsGroup("Appearance") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("Bubblegum", "Cyber Angel", "Cherry Soda", "Haunted CRT", "Peach Dream")) { name ->
                        FilterChip(selected = settings.theme == name, onClick = { scope.launch { repo.setTheme(name) } }, label = { Text(name) })
                    }
                }
                SettingRow("Pixel companion", "Show the mascot in empty states", settings.catEnabled) { scope.launch { repo.setCatEnabled(it) } }
                SettingRow("Reduced motion", "Use calmer transitions", settings.reducedMotion) { scope.launch { repo.setReducedMotion(it) } }
            }
        }
        item {
            SettingsGroup("Offline vault") {
                SettingRow("Save recent songs", "Keep up to ${settings.recentLimit} lyric files", settings.saveRecentOffline) { scope.launch { repo.setSaveRecent(it) } }
                Slider(value = settings.recentLimit.toFloat(), onValueChange = { scope.launch { repo.setRecentLimit(((it / 5).toInt() * 5).coerceIn(5, 40)) } }, valueRange = 5f..40f, steps = 6)
                SettingRow("Wi-Fi only", "Avoid mobile-data lyric downloads", settings.wifiOnly) { scope.launch { repo.setWifiOnly(it) } }
            }
        }
        item {
            SettingsGroup("Background") {
                SettingRow("Live lyric notification", "Keep the current line nearby", settings.backgroundLyrics) {
                    scope.launch { repo.setBackgroundLyrics(it) }
                    if (it) startService() else stopService()
                }
                TextButton(onClick = openNotificationAccess) { Text("Music access settings") }
            }
        }
        item {
            SettingsGroup("Privacy") {
                Text("No microphone · no trackers · no ads\nHTTPS only · history stays on-device", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(17.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SettingRow(label: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StatusTag(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(20.dp)) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
    }
}

private fun friendlySource(label: String, packageName: String): String {
    val raw = "$label $packageName".lowercase()
    return when {
        "symfonium" in raw -> "Symfonium"
        "spotify" in raw -> "Spotify"
        "youtube" in raw -> "YouTube Music"
        "poweramp" in raw -> "Poweramp"
        "musicolet" in raw -> "Musicolet"
        "apple" in raw -> "Apple Music"
        "amazon" in raw -> "Amazon Music"
        label.isNotBlank() && !label.contains('.') -> label.take(22)
        else -> packageName.substringAfterLast('.').replaceFirstChar(Char::uppercase).take(22)
    }
}

private fun formatTime(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
