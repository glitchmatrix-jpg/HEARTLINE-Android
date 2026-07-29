package com.heartline.app.ui.v23

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.heartline.app.R
import com.heartline.app.data.*
import com.heartline.app.media.MediaSessionRepository
import kotlinx.coroutines.delay

@Composable
fun V23NowScreen(
    state: PlayerState,
    settings: AppSettings,
    modifier: Modifier,
    openNotificationAccess: () -> Unit,
    onSource: () -> Unit,
    onSync: () -> Unit,
    onShare: () -> Unit,
    onMore: () -> Unit
) {
    Box(modifier) {
        if (settings.artworkBackdrop && settings.showArtwork && !state.track?.artworkUri.isNullOrBlank()) {
            AsyncImage(
                model = state.track?.artworkUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(0.12f),
                contentScale = ContentScale.Crop
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background.copy(alpha = .66f), MaterialTheme.colorScheme.background))))
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            V23TopBar(onMore)
            if (state.status == PlayerStatus.PermissionRequired) {
                Spacer(Modifier.height(8.dp))
                V23PermissionBanner(openNotificationAccess)
            } else if (state.listenerConnection == ListenerConnection.DISCONNECTED || state.listenerConnection == ListenerConnection.CONNECTING) {
                Spacer(Modifier.height(8.dp))
                V23ConnectionBanner(state)
            }
            Spacer(Modifier.height(8.dp))
            V23TrackHeader(state, settings.showArtwork, onShare)
            Spacer(Modifier.height(8.dp))
            V23ModeSelector(state.playbackMode, onSource)
            Spacer(Modifier.height(8.dp))
            V23LyricsStage(state, settings, Modifier.weight(1f).fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            V23Timeline(state)
            V23Transport(state)
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onSource, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Rounded.QueueMusic, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Choose")
                }
                OutlinedButton(onClick = onSync, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("%+.1fs".format(state.perTrackOffsetMs / 1000.0))
                }
            }
        }
    }
}

@Composable
private fun V23TopBar(onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.heartline_logo), "HEARTLINE", Modifier.size(38.dp), contentScale = ContentScale.Fit)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text("HEARTLINE", style = MaterialTheme.typography.titleLarge)
            Text("the words follow the music", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onMore) { Icon(Icons.Rounded.MoreHoriz, contentDescription = "More options") }
    }
}

@Composable
private fun V23PermissionBanner(openNotificationAccess: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.MusicOff, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Music access needed", style = MaterialTheme.typography.titleMedium)
                Text("Enable notification access so HEARTLINE can read playback metadata.", style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = openNotificationAccess) { Text("Enable") }
        }
    }
}

@Composable
private fun V23ConnectionBanner(state: PlayerState) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(state.message ?: "Reconnecting to music…", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { MediaSessionRepository.reconnect() }) { Text("Reconnect") }
        }
    }
}

@Composable
private fun V23TrackHeader(state: PlayerState, showArtwork: Boolean, onShare: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(58.dp), color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(18.dp)) {
            if (showArtwork && !state.track?.artworkUri.isNullOrBlank()) {
                AsyncImage(
                    model = state.track?.artworkUri,
                    contentDescription = "Album artwork",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(state.track?.title ?: "Waiting for a song", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        IconButton(onClick = { MediaSessionRepository.toggleFavourite() }, enabled = state.track != null) {
            Icon(if (state.isFavourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favourite", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onShare, enabled = state.lyrics.isNotEmpty() || !state.plainLyrics.isNullOrBlank()) {
            Icon(Icons.Rounded.Share, "Share lyrics")
        }
    }
}

@Composable
private fun V23ModeSelector(selectedMode: PlaybackMode, onSearch: () -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        PlaybackMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selectedMode == mode,
                onClick = {
                    MediaSessionRepository.setPlaybackMode(mode)
                    if (mode == PlaybackMode.SEARCH) onSearch()
                },
                shape = SegmentedButtonDefaults.itemShape(index, PlaybackMode.entries.size),
                icon = {}
            ) { Text(mode.name.lowercase().replaceFirstChar(Char::uppercase)) }
        }
    }
}

@Composable
private fun V23LyricsStage(state: PlayerState, settings: AppSettings, modifier: Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surface.copy(alpha = .80f), shape = RoundedCornerShape(28.dp), tonalElevation = 1.dp) {
        Box(Modifier.fillMaxSize()) {
            when {
                state.lyrics.isNotEmpty() -> V23SyncedLyrics(state, settings)
                !state.plainLyrics.isNullOrBlank() -> V23PlainLyrics(state.plainLyrics.orEmpty(), settings)
                else -> V23EmptyState(state, settings.catEnabled)
            }
            Box(Modifier.fillMaxWidth().height(42.dp).align(Alignment.TopCenter).background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, Color.Transparent))))
            Box(Modifier.fillMaxWidth().height(42.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface))))
        }
    }
}

@Composable
private fun V23SyncedLyrics(state: PlayerState, settings: AppSettings) {
    val listState = rememberLazyListState()
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var browsing by remember { mutableStateOf(false) }

    LaunchedEffect(dragged) {
        if (dragged) browsing = true
        else if (browsing) {
            delay(6_000)
            browsing = false
        }
    }
    LaunchedEffect(state.currentLineIndex, browsing, settings.reducedMotion) {
        if (!browsing && state.currentLineIndex >= 0) {
            val target = (state.currentLineIndex - 1).coerceAtLeast(0)
            if (settings.reducedMotion) listState.scrollToItem(target) else listState.animateScrollToItem(target)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 82.dp),
            verticalArrangement = Arrangement.spacedBy(if (settings.lyricSpacing == "compact") 11.dp else 19.dp)
        ) {
            itemsIndexed(state.lyrics, key = { index, line -> "${line.timestampMs}-$index" }) { index, line ->
                val distance = kotlin.math.abs(index - state.currentLineIndex)
                val active = distance == 0
                val visibleNeighbour = distance <= settings.surroundingLines
                val activeSize = when (settings.lyricScale) {
                    "small" -> 24.sp
                    "large" -> 32.sp
                    "extra_large" -> 36.sp
                    else -> 28.sp
                }
                val inactiveSize = when (settings.lyricScale) {
                    "small" -> 17.sp
                    "large" -> 21.sp
                    "extra_large" -> 23.sp
                    else -> 19.sp
                }
                Text(
                    text = line.text,
                    modifier = Modifier.fillMaxWidth()
                        .then(if (settings.reducedMotion) Modifier else Modifier.animateContentSize())
                        .clickable { MediaSessionRepository.seekTo(line.timestampMs) }
                        .semantics {
                            role = Role.Button
                            contentDescription = "Seek to lyric: ${line.text}"
                            selected = active
                        }
                        .padding(vertical = if (active) 7.dp else 2.dp)
                        .alpha(if (active) 1f else if (visibleNeighbour) .68f else .34f),
                    textAlign = if (settings.lyricAlignment == "left") TextAlign.Left else TextAlign.Center,
                    fontWeight = if (active && settings.boldActiveLyric) FontWeight.Bold else FontWeight.Medium,
                    fontSize = if (active) activeSize else inactiveSize,
                    lineHeight = if (active) activeSize * 1.25f else inactiveSize * 1.35f,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        if (browsing && state.currentLineIndex >= 0) {
            ExtendedFloatingActionButton(
                onClick = {
                    browsing = false
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                icon = { Icon(Icons.Rounded.MyLocation, null) },
                text = { Text("Current lyric") },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        }
    }
}

@Composable
private fun V23PlainLyrics(text: String, settings: AppSettings) {
    val size = when (settings.lyricScale) { "small" -> 15.sp; "large" -> 20.sp; "extra_large" -> 23.sp; else -> 18.sp }
    Text(
        text,
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        style = MaterialTheme.typography.bodyLarge,
        fontSize = size,
        lineHeight = size * if (settings.lyricSpacing == "compact") 1.25f else 1.55f,
        textAlign = if (settings.lyricAlignment == "left") TextAlign.Left else TextAlign.Center
    )
}

@Composable
private fun V23EmptyState(state: PlayerState, catEnabled: Boolean) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (catEnabled) {
            Image(painterResource(R.drawable.cat_mascot), "HEARTLINE mascot", Modifier.size(82.dp), contentScale = ContentScale.Fit)
            Spacer(Modifier.height(14.dp))
        } else Icon(Icons.Rounded.FavoriteBorder, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
        Text(state.message ?: "Waiting for the beat…", textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        if (state.status == PlayerStatus.NoLyrics || state.status == PlayerStatus.Error) {
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { MediaSessionRepository.requestCandidates() }) { Text("Try other versions") }
        }
        if (state.listenerConnection == ListenerConnection.DISCONNECTED) {
            TextButton(onClick = { MediaSessionRepository.reconnect() }) { Text("Reconnect music") }
        }
    }
}

@Composable
private fun V23Timeline(state: PlayerState) {
    val duration = state.track?.durationMs ?: 0L
    val value = if (duration > 0) (state.displayPositionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(formatTime(state.displayPositionMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = value, onValueChange = { if (duration > 0) MediaSessionRepository.seekTo((it * duration).toLong()) }, modifier = Modifier.weight(1f).padding(horizontal = 4.dp), enabled = duration > 0)
        Text(formatTime(duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun V23Transport(state: PlayerState) {
    val playing = if (state.playbackMode == PlaybackMode.AUTO) state.track?.isPlaying == true else state.manualClockPlaying
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { MediaSessionRepository.transportPrevious() }, modifier = Modifier.size(48.dp), enabled = state.track != null) {
            Icon(Icons.Rounded.SkipPrevious, "Previous")
        }
        FilledIconButton(
            onClick = { MediaSessionRepository.transportPlayPause() },
            modifier = Modifier.size(58.dp),
            enabled = state.track != null,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", Modifier.size(30.dp)) }
        IconButton(onClick = { MediaSessionRepository.transportNext() }, modifier = Modifier.size(48.dp), enabled = state.track != null) {
            Icon(Icons.Rounded.SkipNext, "Next")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V23SourceSheet(state: PlayerState, onDismiss: () -> Unit) {
    var query by remember(state.searchQuery) { mutableStateOf(state.searchQuery) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Choose lyrics", style = MaterialTheme.typography.headlineMedium)
            Text("Search another version or keep the best automatic match.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Title and artist") }, leadingIcon = { Icon(Icons.Rounded.Search, null) })
            Spacer(Modifier.height(9.dp))
            Button({ MediaSessionRepository.requestCandidates(query) }, Modifier.fillMaxWidth()) { Text("Search LRCLIB") }
            Spacer(Modifier.height(9.dp))
            ListItem(
                headlineContent = { Text("Automatic source") },
                supportingContent = { Text("Follow the best active music app") },
                trailingContent = { Switch(!state.sourceLocked, { MediaSessionRepository.toggleSourceLock() }) }
            )
            TextButton({ MediaSessionRepository.requestCandidates() }, Modifier.align(Alignment.End)) { Text("Show recommended versions") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V23SyncSheet(state: PlayerState, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Adjust sync", style = MaterialTheme.typography.headlineMedium)
            Text("%+.1fs".format(state.perTrackOffsetMs / 1000.0), fontSize = 38.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Negative moves lyrics earlier · positive moves them later", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(-5000L to "−5", -500L to "−0.5", 500L to "+0.5", 5000L to "+5").forEach { (delta, label) ->
                    OutlinedButton({ MediaSessionRepository.adjustOffset(delta) }, Modifier.weight(1f)) { Text(label) }
                }
            }
            TextButton({ MediaSessionRepository.adjustOffset(-state.perTrackOffsetMs) }) { Text("Reset offset") }
            if (state.playbackMode != PlaybackMode.AUTO) Button({ MediaSessionRepository.rejoinPlayerPosition(); onDismiss() }, Modifier.fillMaxWidth()) { Text("Sync to current song position") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V23MoreSheet(
    state: PlayerState,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onFocus: () -> Unit,
    onLiveNotification: () -> Unit,
    onMusicAccess: () -> Unit,
    onDiagnostics: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("More", style = MaterialTheme.typography.headlineMedium)
            V23MenuAction(Icons.Rounded.Share, "Share lyrics", "Create a polished image", onShare)
            V23MenuAction(Icons.Rounded.Fullscreen, "Focus mode", "Hide system bars", onFocus)
            V23MenuAction(Icons.Rounded.NotificationsActive, "Live notification", "Keep the current lyric nearby", onLiveNotification)
            V23MenuAction(Icons.Rounded.Lock, if (state.sourceLocked) "Unlock music source" else "Lock music source", "Control which player HEARTLINE follows") { MediaSessionRepository.toggleSourceLock() }
            V23MenuAction(Icons.Rounded.HealthAndSafety, "Connection diagnostics", "Inspect and repair music detection", onDiagnostics)
            V23MenuAction(Icons.Rounded.AdminPanelSettings, "Music access", "Open Android notification access", onMusicAccess)
        }
    }
}

@Composable
private fun V23MenuAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Icon(Icons.Rounded.ChevronRight, null) }
    )
}

@Composable
fun V23CandidateDialog(state: PlayerState) {
    AlertDialog(
        onDismissRequest = { MediaSessionRepository.dismissCandidates() },
        title = { Text("Lyrics versions") },
        text = {
            if (state.isLoadingLyrics) Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 460.dp)) {
                items(state.candidates, key = LyricCandidate::id) { candidate ->
                    Surface(
                        Modifier.fillMaxWidth().clickable { MediaSessionRepository.selectCandidate(candidate) },
                        color = if (candidate.id == state.selectedProviderId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(13.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(candidate.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(candidate.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                AssistChip(onClick = {}, label = { Text(if (candidate.synced) "Synced" else "Plain") })
                            }
                            if (candidate.preview.isNotBlank()) Text(candidate.preview, Modifier.padding(top = 7.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ MediaSessionRepository.dismissCandidates() }) { Text("Close") } }
    )
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
