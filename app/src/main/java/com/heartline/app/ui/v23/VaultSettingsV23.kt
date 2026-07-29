package com.heartline.app.ui.v23

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heartline.app.BuildConfig
import com.heartline.app.data.*
import com.heartline.app.media.MediaSessionRepository
import com.heartline.app.ui.theme.HeartlineThemeDefinition
import com.heartline.app.ui.theme.HeartlineThemeRegistry
import kotlinx.coroutines.launch

enum class VaultSort { RECENT, TITLE, ARTIST }

@Composable
fun V23VaultScreen(dao: TrackDao, modifier: Modifier) {
    val tracks by dao.observeOffline().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var favouritesOnly by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(VaultSort.RECENT) }
    var openedTrack by remember { mutableStateOf<TrackEntity?>(null) }
    val filtered = remember(tracks, query, favouritesOnly, sort) {
        tracks.filter {
            (!favouritesOnly || it.isFavourite) &&
                (query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) || it.album.orEmpty().contains(query, true))
        }.let { list ->
            when (sort) {
                VaultSort.RECENT -> list.sortedByDescending(TrackEntity::lastPlayedAt)
                VaultSort.TITLE -> list.sortedBy { it.title.lowercase() }
                VaultSort.ARTIST -> list.sortedBy { it.artist.lowercase() }
            }
        }
    }

    Column(modifier.padding(18.dp)) {
        Text("Offline Vault", style = MaterialTheme.typography.headlineMedium)
        Text("${tracks.size} saved songs · ${tracks.count(TrackEntity::isFavourite)} favourites", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            placeholder = { Text("Search songs, artists, albums") }
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FilterChip(favouritesOnly, { favouritesOnly = !favouritesOnly }, label = { Text("Favourites") }, leadingIcon = { Icon(Icons.Rounded.Favorite, null, Modifier.size(17.dp)) })
            VaultSort.entries.forEach { option ->
                FilterChip(sort == option, { sort = option }, label = { Text(option.name.lowercase().replaceFirstChar(Char::uppercase)) })
            }
        }
        Spacer(Modifier.height(10.dp))
        if (filtered.isEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(if (tracks.isEmpty()) "Your vault is quiet" else "No matching songs", style = MaterialTheme.typography.titleMedium)
                    Text("Played lyrics can stay ready without a signal.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(filtered, key = TrackEntity::fingerprint) { track ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { openedTrack = track },
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(18.dp),
                        tonalElevation = 1.dp
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(46.dp), color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary) }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    if (track.syncedLyrics != null) Text("Synced", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    else if (track.plainLyrics != null) Text("Plain", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    if (track.keepOffline) Text("Pinned", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            IconButton(onClick = { scope.launch { dao.setFavourite(track.fingerprint, !track.isFavourite) } }) {
                                Icon(if (track.isFavourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favourite", tint = MaterialTheme.colorScheme.primary)
                            }
                            var menu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Track options") }
                                DropdownMenu(menu, { menu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(if (track.keepOffline) "Unpin" else "Pin offline") },
                                        onClick = { menu = false; scope.launch { dao.setKeepOffline(track.fingerprint, !track.keepOffline) } },
                                        leadingIcon = { Icon(Icons.Rounded.PushPin, null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Remove from Vault") },
                                        onClick = { menu = false; scope.launch { dao.delete(track.fingerprint) } },
                                        leadingIcon = { Icon(Icons.Rounded.Delete, null) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    openedTrack?.let { track ->
        AlertDialog(
            onDismissRequest = { openedTrack = null },
            title = { Text(track.title) },
            text = {
                Column {
                    Text(track.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    val lyricText = track.syncedLyrics?.lineSequence()?.map { it.replace(Regex("^\\[[^]]+]"), "").trim() }?.filter(String::isNotBlank)?.joinToString("\n")
                        ?: track.plainLyrics
                        ?: "No saved lyric text"
                    LazyColumn(Modifier.heightIn(max = 430.dp)) {
                        item { Text(lyricText, lineHeight = 24.sp) }
                    }
                }
            },
            confirmButton = { TextButton({ openedTrack = null }) { Text("Close") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun V23SettingsScreen(
    settings: AppSettings,
    repo: SettingsRepository,
    modifier: Modifier,
    openNotificationAccess: () -> Unit,
    startService: () -> Unit,
    stopService: () -> Unit,
    onDiagnostics: () -> Unit
) {
    val scope = rememberCoroutineScope()
    LazyColumn(modifier.padding(horizontal = 18.dp), contentPadding = PaddingValues(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Text("Make HEARTLINE feel like yours.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SettingsCard("Appearance") {
                SettingSwitch("Follow system theme", "Use separate light and dark HEARTLINE themes", settings.followSystemTheme) { scope.launch { repo.setFollowSystemTheme(it) } }
                Text("Theme", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeartlineThemeRegistry.all.forEach { definition ->
                        ThemePreviewCard(definition, settings.theme == definition.displayName && !settings.followSystemTheme) {
                            scope.launch { repo.setTheme(definition.displayName); repo.setFollowSystemTheme(false) }
                        }
                    }
                }
                if (settings.followSystemTheme) {
                    Spacer(Modifier.height(8.dp))
                    Text("Light theme", style = MaterialTheme.typography.labelLarge)
                    ThemeDropdown(settings.systemLightTheme, HeartlineThemeRegistry.all.filter { !it.palette.dark }.map { it.displayName }) { scope.launch { repo.setSystemLightTheme(it) } }
                    Text("Dark theme", style = MaterialTheme.typography.labelLarge)
                    ThemeDropdown(settings.systemDarkTheme, HeartlineThemeRegistry.all.filter { it.palette.dark }.map { it.displayName }) { scope.launch { repo.setSystemDarkTheme(it) } }
                }
                SettingSwitch("OLED black", "Use true black backgrounds in dark themes", settings.oledBlack) { scope.launch { repo.setOledBlack(it) } }
                SettingSwitch("Show album artwork", "Use artwork supplied by the active player", settings.showArtwork) { scope.launch { repo.setShowArtwork(it) } }
                SettingSwitch("Artwork backdrop", "Softly place artwork behind the lyric stage", settings.artworkBackdrop) { scope.launch { repo.setArtworkBackdrop(it) } }
                SettingSwitch("Pixel companion", "Show the mascot in empty states", settings.catEnabled) { scope.launch { repo.setCatEnabled(it) } }
                SettingSwitch("Reduced motion", "Disable nonessential lyric animations", settings.reducedMotion) { scope.launch { repo.setReducedMotion(it) } }
            }
        }
        item {
            SettingsCard("Lyrics") {
                ChoiceRow("Text size", settings.lyricScale, listOf("small", "standard", "large", "extra_large")) { scope.launch { repo.setLyricScale(it) } }
                ChoiceRow("Alignment", settings.lyricAlignment, listOf("center", "left")) { scope.launch { repo.setLyricAlignment(it) } }
                ChoiceRow("Spacing", settings.lyricSpacing, listOf("comfortable", "compact")) { scope.launch { repo.setLyricSpacing(it) } }
                ChoiceRow("Prominent neighbours", settings.surroundingLines.toString(), listOf("1", "2", "3")) { scope.launch { repo.setSurroundingLines(it.toInt()) } }
                SettingSwitch("Bold active lyric", "Emphasize the current line", settings.boldActiveLyric) { scope.launch { repo.setBoldActiveLyric(it) } }
            }
        }
        item {
            SettingsCard("Share Lyrics") {
                SettingSwitch("HEARTLINE branding", "Include the logo and tagline by default", settings.shareBranding) { scope.launch { repo.setShareBranding(it) } }
                Text("Share cards support Post, Story and Square formats, live previews, eight visual styles, plain lyrics, adaptive text fitting, Save and Share.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SettingsCard("Offline Vault") {
                SettingSwitch("Save recent songs", "Keep up to ${settings.recentLimit} lyric files", settings.saveRecentOffline) { scope.launch { repo.setSaveRecent(it) } }
                Slider(settings.recentLimit.toFloat(), { scope.launch { repo.setRecentLimit((it / 5).toInt() * 5) } }, valueRange = 5f..100f, steps = 18)
                SettingSwitch("Keep favourites", "Protect favourited lyrics from automatic cleanup", settings.keepFavouritesOffline) { scope.launch { repo.setKeepFavouritesOffline(it) } }
                SettingSwitch("Wi-Fi only", "Avoid mobile-data lyric downloads", settings.wifiOnly) { scope.launch { repo.setWifiOnly(it) } }
            }
        }
        item {
            SettingsCard("Notification") {
                SettingSwitch("Live lyric notification", "Keep the current lyric nearby", settings.backgroundLyrics) {
                    scope.launch { repo.setBackgroundLyrics(it) }
                    if (it) startService() else stopService()
                }
                ChoiceRow("Detail", settings.notificationDetail, listOf("current", "current_next", "song_only")) { scope.launch { repo.setNotificationDetail(it) } }
                TextButton(openNotificationAccess) { Icon(Icons.Rounded.AdminPanelSettings, null); Spacer(Modifier.width(5.dp)); Text("Music access settings") }
            }
        }
        item {
            SettingsCard("Privacy") {
                ChoiceRow("Lock screen", settings.privacyMode, listOf("show_lyrics", "song_only", "hide_lyrics_locked")) { scope.launch { repo.setPrivacyMode(it) } }
                Text("No microphone · no trackers · no ads\nHTTPS only · history stays on-device", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SettingsCard("Advanced") {
                ListItem(
                    modifier = Modifier.clickable(onClick = onDiagnostics),
                    leadingContent = { Icon(Icons.Rounded.HealthAndSafety, null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text("Connection diagnostics") },
                    supportingContent = { Text("Inspect sessions, reconnect and copy a report") },
                    trailingContent = { Icon(Icons.Rounded.ChevronRight, null) }
                )
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text("HEARTLINE ${BuildConfig.VERSION_NAME}") },
                    supportingContent = { Text("Make the words yours") }
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp), tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(7.dp))
            content()
        }
    }
}

@Composable
private fun ThemePreviewCard(definition: HeartlineThemeDefinition, selected: Boolean, onClick: () -> Unit) {
    val p = definition.palette
    Surface(
        modifier = Modifier.width(150.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        color = p.background,
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, p.accent) else null
    ) {
        Column(Modifier.padding(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(18.dp).clip(RoundedCornerShape(6.dp)).background(p.accent))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(18.dp).clip(RoundedCornerShape(6.dp)).background(p.raised))
                Spacer(Modifier.weight(1f))
                if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = p.accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(definition.displayName, color = p.ink, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Text("the words follow", color = p.muted, style = MaterialTheme.typography.labelSmall)
            Text(if (p.dark) "Dark" else "Light", color = p.accent, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ThemeDropdown(selected: String, choices: List<String>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected, Modifier.weight(1f), textAlign = TextAlign.Start)
            Icon(Icons.Rounded.ArrowDropDown, null)
        }
        DropdownMenu(open, { open = false }) {
            choices.forEach { choice -> DropdownMenuItem({ Text(choice) }, { open = false; onSelect(choice) }) }
        }
    }
}

@Composable
private fun ChoiceRow(title: String, selected: String, choices: List<String>, onSelect: (String) -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            choices.forEach { choice ->
                FilterChip(
                    selected = selected == choice,
                    onClick = { onSelect(choice) },
                    label = { Text(choice.replace('_', ' ').replaceFirstChar(Char::uppercase)) }
                )
            }
        }
    }
}

@Composable
private fun SettingSwitch(label: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V23DiagnosticsSheet(openNotificationAccess: () -> Unit, onDismiss: () -> Unit) {
    val state by MediaSessionRepository.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val age = if (state.lastMediaEventElapsedMs > 0) ((SystemClock.elapsedRealtime() - state.lastMediaEventElapsedMs).coerceAtLeast(0) / 1000) else -1
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Connection diagnostics", style = MaterialTheme.typography.headlineMedium)
            Text("HEARTLINE can usually repair music detection without changing Android settings.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(13.dp))
            DiagnosticRow("Listener", state.listenerConnection.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase), state.listenerConnection == ListenerConnection.CONNECTED)
            DiagnosticRow("Active sessions", state.activeSessionCount.toString(), state.activeSessionCount > 0)
            DiagnosticRow("Selected source", state.track?.sourceLabel ?: "None", state.track != null)
            DiagnosticRow("Metadata", if (state.track != null) "Available" else "Unavailable", state.track != null)
            DiagnosticRow("Last media event", if (age >= 0) "${age}s ago" else "Never", age in 0..45)
            DiagnosticRow("Reconnect attempts", state.reconnectAttempts.toString(), state.reconnectAttempts == 0)
            Spacer(Modifier.height(12.dp))
            Button({ MediaSessionRepository.reconnect() }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Reconnect now") }
            OutlinedButton(openNotificationAccess, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.AdminPanelSettings, null); Spacer(Modifier.width(6.dp)); Text("Open music access") }
            TextButton(
                onClick = {
                    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    manager.setPrimaryClip(ClipData.newPlainText("HEARTLINE diagnostics", MediaSessionRepository.diagnosticsReport()))
                    Toast.makeText(context, "Diagnostic report copied", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Icon(Icons.Rounded.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("Copy diagnostic report") }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String, healthy: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (healthy) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, null, tint = if (healthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(9.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
