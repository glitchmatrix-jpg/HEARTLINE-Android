package com.heartline.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heartline.app.HeartlineApplication
import com.heartline.app.data.AppSettings
import com.heartline.app.data.PlayerState
import com.heartline.app.data.PlayerStatus
import com.heartline.app.data.SettingsRepository
import com.heartline.app.data.TrackDao
import com.heartline.app.media.MediaSessionRepository
import com.heartline.app.ui.theme.HeartlineTheme
import kotlinx.coroutines.launch

private enum class Tab { NOW, LIBRARY, SETTINGS }

@Composable
fun HeartlineApp(
    openNotificationAccess: () -> Unit,
    startLyricsService: () -> Unit,
    stopLyricsService: () -> Unit
) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as HeartlineApplication
    val settings by app.settings.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings(
            "Bubblegum", true, 20, true, true, true, 0,
            true, "hide_lyrics_locked", true, false, null
        )
    )
    val state by MediaSessionRepository.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.NOW) }

    HeartlineTheme(settings.theme) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { HeartlineNav(tab) { tab = it } }
        ) { padding ->
            when (tab) {
                Tab.NOW -> PlayerScreen(
                    state = state,
                    modifier = Modifier.padding(padding),
                    catEnabled = settings.catEnabled,
                    reducedMotion = settings.reducedMotion,
                    openNotificationAccess = openNotificationAccess,
                    startLyricsService = startLyricsService
                )
                Tab.LIBRARY -> LibraryScreen(app.database.trackDao(), Modifier.padding(padding))
                Tab.SETTINGS -> SettingsScreen(
                    settings,
                    app.settings,
                    Modifier.padding(padding),
                    openNotificationAccess,
                    startLyricsService,
                    stopLyricsService
                )
            }
        }
    }
}

@Composable
private fun HeartlineNav(selected: Tab, onSelect: (Tab) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 14.dp,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf(
                Triple(Tab.NOW, "♥", "NOW"),
                Triple(Tab.LIBRARY, "▣", "VAULT"),
                Triple(Tab.SETTINGS, "✦", "SETTINGS")
            ).forEach { (tab, icon, label) ->
                val active = selected == tab
                Column(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onSelect(tab) }
                        .padding(horizontal = 22.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(icon, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp)
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun PlayerScreen(
    state: PlayerState,
    modifier: Modifier,
    catEnabled: Boolean,
    reducedMotion: Boolean,
    openNotificationAccess: () -> Unit,
    startLyricsService: () -> Unit
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        BrandHeader(state)

        if (state.status == PlayerStatus.PermissionRequired) {
            PremiumPanel(accented = true) {
                Text("MUSIC ACCESS NEEDED", style = MaterialTheme.typography.titleMedium)
                Text("HEARTLINE reads media metadata only. No microphone. No recordings.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                PixelButton("ENABLE MUSIC ACCESS", Modifier.fillMaxWidth(), openNotificationAccess)
            }
        }

        TrackCard(state)

        PremiumPanel(Modifier.weight(1f)) {
            Box(Modifier.fillMaxSize()) {
                when {
                    state.lyrics.isNotEmpty() -> SyncedLyrics(state)
                    !state.plainLyrics.isNullOrBlank() -> Text(
                        state.plainLyrics,
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
                        lineHeight = 25.sp
                    )
                    else -> EmptyLyricState(state)
                }
                if (catEnabled) {
                    PixelCat(
                        status = state.status,
                        isPlaying = state.track?.isPlaying == true,
                        isFavourite = state.isFavourite,
                        reducedMotion = reducedMotion,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    )
                }
            }
        }

        Timeline(state)
        PlaybackControls(state)
        SyncStrip(state)
        PixelButton("KEEP LYRICS NEARBY", Modifier.fillMaxWidth(), startLyricsService)
    }
}

@Composable
private fun BrandHeader(state: PlayerState) {
    PremiumPanel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) { Text("♥", fontSize = 21.sp, color = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text("HEARTLINE", style = MaterialTheme.typography.titleLarge)
                Text("the words follow the music", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CompactChip(if (state.sourceLocked) "LOCKED" else "AUTO") { MediaSessionRepository.toggleSourceLock() }
        }
    }
}

@Composable
private fun TrackCard(state: PlayerState) {
    PremiumPanel(accented = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) { Text("♫", fontSize = 28.sp, color = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    state.track?.title?.uppercase() ?: "WAITING FOR A SONG",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(state.track?.artist ?: "Play music in another app", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.track != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 7.dp)) {
                        StatusPill(state.track.sourceLabel)
                        StatusPill(if (state.isOfflineReady) "OFFLINE READY" else "ONLINE")
                    }
                }
            }
            CompactChip(if (state.isFavourite) "♥" else "♡") { MediaSessionRepository.toggleFavourite() }
        }
    }
}

@Composable
private fun SyncedLyrics(state: PlayerState) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.currentLineIndex) {
        if (state.currentLineIndex >= 0) {
            listState.animateScrollToItem(state.currentLineIndex.coerceAtLeast(0))
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 120.dp, bottom = 135.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        itemsIndexed(state.lyrics) { index, line ->
            val active = index == state.currentLineIndex
            Surface(
                color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().animateContentSize().clickable { MediaSessionRepository.seekTo(line.timestampMs) }
            ) {
                Text(
                    line.text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = if (active) 12.dp else 4.dp),
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (active) FontWeight.Black else FontWeight.Normal,
                    fontSize = if (active) 23.sp else 16.sp,
                    lineHeight = if (active) 29.sp else 22.sp,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyLyricState(state: PlayerState) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (state.isLoadingLyrics) "♪  ·  ·  ·" else "♡", fontSize = 42.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(
                state.message ?: "waiting for the beat…",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun Timeline(state: PlayerState) {
    val duration = state.track?.durationMs ?: 0
    val value = if (duration > 0) (state.displayPositionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(formatTime(state.displayPositionMs), style = MaterialTheme.typography.labelSmall)
        Slider(
            value = value,
            onValueChange = { if (duration > 0) MediaSessionRepository.seekTo((it * duration).toLong()) },
            modifier = Modifier.weight(1f).padding(horizontal = 5.dp)
        )
        Text(formatTime(duration), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PlaybackControls(state: PlayerState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        PixelButton("|<", Modifier.weight(1f)) { MediaSessionRepository.transportPrevious() }
        PixelButton(
            if (state.track?.isPlaying == true) "PAUSE" else "PLAY",
            Modifier.weight(1.55f),
            primary = true
        ) { MediaSessionRepository.transportPlayPause() }
        PixelButton(">|", Modifier.weight(1f)) { MediaSessionRepository.transportNext() }
        PixelButton(if (state.isFavourite) "♥" else "♡", Modifier.weight(1f)) { MediaSessionRepository.toggleFavourite() }
    }
}

@Composable
private fun SyncStrip(state: PlayerState) {
    PremiumPanel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TinyButton("−5") { MediaSessionRepository.adjustOffset(-5000) }
            TinyButton("−0.5") { MediaSessionRepository.adjustOffset(-500) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SYNC", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatOffset(state.perTrackOffsetMs), style = MaterialTheme.typography.titleMedium)
            }
            TinyButton("+0.5") { MediaSessionRepository.adjustOffset(500) }
            TinyButton("+5") { MediaSessionRepository.adjustOffset(5000) }
        }
    }
}

@Composable
private fun PixelCat(
    status: PlayerStatus,
    isPlaying: Boolean,
    isFavourite: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "cat")
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (reducedMotion) 0f else -4f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "catBounce"
    )
    val face = when {
        isFavourite -> "happy"
        status == PlayerStatus.LoadingLyrics || status == PlayerStatus.Detecting -> "search"
        status == PlayerStatus.Offline -> "sad"
        status == PlayerStatus.NoLyrics -> "confused"
        status == PlayerStatus.Instrumental -> "music"
        isPlaying -> "sing"
        else -> "idle"
    }
    Canvas(modifier.size(74.dp).graphicsLayer { translationY = bounce }) {
        val u = size.minDimension / 18f
        fun px(x: Int, y: Int, w: Int, h: Int, color: Color) = drawRect(color, Offset(x * u, y * u), Size(w * u, h * u))
        val ink = Color(0xFF2B1830)
        val fur = Color(0xFFF2A9D7)
        val light = Color(0xFFFFDDF1)
        val accent = Color(0xFF8D4D9F)
        px(3, 3, 3, 3, ink); px(12, 3, 3, 3, ink)
        px(4, 2, 2, 3, fur); px(12, 2, 2, 3, fur)
        px(4, 5, 10, 9, ink); px(5, 5, 8, 8, fur)
        px(6, 6, 6, 5, light)
        px(2, 12, 3, 2, ink); px(13, 12, 3, 2, ink)
        px(5, 13, 8, 2, ink); px(6, 13, 6, 1, fur)
        when (face) {
            "happy" -> { px(7, 8, 1, 1, ink); px(10, 8, 1, 1, ink); px(8, 10, 2, 1, accent) }
            "search" -> { px(7, 8, 1, 2, ink); px(10, 8, 1, 2, ink); px(8, 11, 2, 1, ink); px(15, 4, 1, 3, accent); px(16, 3, 1, 1, accent) }
            "sad" -> { px(7, 9, 1, 1, ink); px(10, 9, 1, 1, ink); px(8, 11, 2, 1, ink); px(12, 10, 1, 2, Color(0xFF77BCE8)) }
            "confused" -> { px(7, 8, 1, 1, ink); px(10, 9, 1, 1, ink); px(8, 11, 2, 1, ink); px(15, 3, 1, 1, accent); px(16, 4, 1, 2, accent) }
            "music", "sing" -> { px(7, 8, 1, 1, ink); px(10, 8, 1, 1, ink); px(8, 10, 2, 2, accent); px(15, 5, 1, 3, accent); px(16, 4, 1, 1, accent) }
            else -> { px(7, 8, 1, 1, ink); px(10, 8, 1, 1, ink); px(8, 10, 2, 1, ink) }
        }
    }
}

@Composable
private fun LibraryScreen(dao: TrackDao, modifier: Modifier) {
    val tracks by dao.observeOffline().collectAsStateWithLifecycle(initialValue = emptyList())
    Column(modifier.fillMaxSize().padding(14.dp)) {
        Text("OFFLINE VAULT", style = MaterialTheme.typography.headlineMedium)
        Text("${tracks.size} saved songs · your lyrics, even off-grid", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        if (tracks.isEmpty()) {
            PremiumPanel(Modifier.fillMaxWidth(), accented = true) {
                Text("YOUR VAULT IS QUIET", style = MaterialTheme.typography.titleMedium)
                Text("Play a few songs and HEARTLINE will keep the newest lyrics close.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(tracks, key = { it.fingerprint }) { track ->
                    PremiumPanel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) { Text("♫", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary) }
                            Spacer(Modifier.size(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.title.uppercase(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 7.dp)) {
                                    StatusPill(if (track.syncedLyrics != null) "SYNCED" else "PLAIN")
                                    StatusPill("OFFLINE")
                                    if (track.keepOffline) StatusPill("PINNED")
                                }
                            }
                            Text(if (track.isFavourite) "♥" else "◇", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                        }
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
    LazyColumn(modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item {
            Text("SETTINGS", style = MaterialTheme.typography.headlineMedium)
            Text("Make HEARTLINE feel exactly like yours.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SettingsSection("APPEARANCE", "Choose a mood, then let the little details sing.") {
                ThemePicker(settings.theme) { scope.launch { repo.setTheme(it) } }
                SettingSwitch("Pixel cat", "Your tiny listening companion", settings.catEnabled) { scope.launch { repo.setCatEnabled(it) } }
                SettingSwitch("Reduced motion", "Calmer transitions and no mascot bounce", settings.reducedMotion) { scope.launch { repo.setReducedMotion(it) } }
            }
        }
        item {
            SettingsSection("OFFLINE VAULT", "Keep recent lyrics ready without a signal.") {
                SettingSwitch("Save recent songs offline", "Stores lyrics and timing data locally", settings.saveRecentOffline) { scope.launch { repo.setSaveRecent(it) } }
                Text("RECENT SONGS RETAINED  ·  ${settings.recentLimit}", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = settings.recentLimit.toFloat(),
                    onValueChange = { scope.launch { repo.setRecentLimit(((it / 5).toInt() * 5).coerceIn(5, 40)) } },
                    valueRange = 5f..40f,
                    steps = 6
                )
                Text("Maximum 40 · favourites, pins and manual corrections are protected", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SettingSwitch("Download on Wi-Fi only", "Prevents mobile-data lyric fetches", settings.wifiOnly) { scope.launch { repo.setWifiOnly(it) } }
            }
        }
        item {
            SettingsSection("LYRICS & SYNC", "Fine-tune timing across every track.") {
                Text("GLOBAL OFFSET  ${formatOffset(settings.globalOffsetMs)}", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelButton("−0.5", Modifier.weight(1f)) { scope.launch { repo.setGlobalOffset(settings.globalOffsetMs - 500) } }
                    PixelButton("RESET", Modifier.weight(1f)) { scope.launch { repo.setGlobalOffset(0) } }
                    PixelButton("+0.5", Modifier.weight(1f)) { scope.launch { repo.setGlobalOffset(settings.globalOffsetMs + 500) } }
                }
            }
        }
        item {
            SettingsSection("BACKGROUND & PRIVACY", "Keep one elegant lyric card in your shade.") {
                SettingSwitch("Lyrics notification", "Shows the current line after leaving the app", settings.backgroundLyrics) {
                    scope.launch { repo.setBackgroundLyrics(it) }
                    if (it) startService() else stopService()
                }
                PixelButton("ENABLE / REPAIR MUSIC ACCESS", Modifier.fillMaxWidth(), openNotificationAccess)
            }
        }
        item {
            SettingsSection("SECURITY", "Private by design.") {
                Text("NO MICROPHONE  ·  NO TRACKERS  ·  NO ADS\nHTTPS ONLY  ·  HISTORY STAYS ON-DEVICE", style = MaterialTheme.typography.labelLarge, lineHeight = 22.sp)
            }
        }
    }
}

@Composable
private fun ThemePicker(selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(listOf("Bubblegum", "Cyber Angel", "Cherry Soda", "Haunted CRT", "Peach Dream")) { name ->
            FilterChip(
                selected = selected == name,
                onClick = { onSelect(name) },
                label = { Text(name, style = MaterialTheme.typography.labelLarge) }
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    PremiumPanel {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun SettingSwitch(label: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PremiumPanel(
    modifier: Modifier = Modifier,
    accented: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        color = if (accented) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 7.dp
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun PixelButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 11.dp)
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun CompactChip(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
    ) { Text(text, Modifier.padding(horizontal = 11.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun StatusPill(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TinyButton(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) { Text(text, Modifier.padding(horizontal = 9.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge) }
}

private fun formatTime(ms: Long): String {
    val sec = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(sec / 60, sec % 60)
}

private fun formatOffset(ms: Long): String = "%+.1fs".format(ms / 1000.0)
