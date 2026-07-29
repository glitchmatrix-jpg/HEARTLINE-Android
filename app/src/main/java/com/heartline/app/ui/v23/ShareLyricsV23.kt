package com.heartline.app.ui.v23

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.heartline.app.data.PlayerState
import com.heartline.app.share.*

enum class ShareLineScope { NEARBY, ALL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V23ShareLyricsSheet(state: PlayerState, defaultBranding: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val allLines = remember(state.lyrics, state.plainLyrics) {
        if (state.lyrics.isNotEmpty()) state.lyrics.map { it.text }
        else state.plainLyrics.orEmpty().lineSequence().map(String::trim).filter(String::isNotBlank).toList()
    }
    val current = state.currentLineIndex.coerceIn(0, (allLines.size - 1).coerceAtLeast(0))
    var scope by remember { mutableStateOf(ShareLineScope.NEARBY) }
    var query by remember { mutableStateOf("") }
    val visibleIndices = remember(allLines, current, scope, query) {
        when (scope) {
            ShareLineScope.NEARBY -> {
                if (allLines.isEmpty()) emptyList()
                else ((current - 4).coerceAtLeast(0)..(current + 4).coerceAtMost(allLines.lastIndex)).toList()
            }
            ShareLineScope.ALL -> allLines.indices.filter { query.isBlank() || allLines[it].contains(query, ignoreCase = true) }
        }
    }
    val initial = remember(allLines, current) {
        if (allLines.isEmpty()) emptySet()
        else ((current - 1).coerceAtLeast(0)..(current + 1).coerceAtMost(allLines.lastIndex)).toSet()
    }
    var selected by remember(allLines) { mutableStateOf(initial) }
    var themeId by remember { mutableStateOf(ShareCardRegistry.all.first().id) }
    var format by remember { mutableStateOf(ShareCardFormat.POST) }
    var showTitle by remember { mutableStateOf(true) }
    var showArtist by remember { mutableStateOf(true) }
    var showBranding by remember { mutableStateOf(defaultBranding) }
    var textScale by remember { mutableFloatStateOf(1f) }
    var intensity by remember { mutableFloatStateOf(1f) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }

    val lines = selected.sorted().mapNotNull(allLines::getOrNull).filter(String::isNotBlank)
    val options = remember(themeId, format, showTitle, showArtist, showBranding, textScale, intensity) {
        ShareCardOptions(
            format = format,
            theme = ShareCardRegistry.byId(themeId),
            showTitle = showTitle,
            showArtist = showArtist,
            showBranding = showBranding,
            textScale = textScale,
            backgroundIntensity = intensity
        )
    }

    LaunchedEffect(state.track?.title, state.track?.artist, lines, options) {
        preview?.recycle()
        preview = LyricShareCard.renderPreview(
            context,
            state.track?.title ?: "HEARTLINE lyrics",
            state.track?.artist ?: "Unknown artist",
            lines.ifEmpty { listOf("Select a lyric to preview") },
            options,
            maxWidth = 360
        )
    }
    DisposableEffect(Unit) { onDispose { preview?.recycle() } }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
            Text("Share Lyrics 2.0", style = MaterialTheme.typography.headlineMedium)
            Text("Select lines, preview the design, then share or save.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            preview?.let {
                Surface(shape = RoundedCornerShape(22.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Image(it.asImageBitmap(), "Lyric card preview", Modifier.fillMaxHeight().padding(8.dp), contentScale = ContentScale.Fit)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            Text("Style", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ShareCardRegistry.all, key = { it.id }) { theme ->
                    FilterChip(
                        selected = themeId == theme.id,
                        onClick = { themeId = theme.id },
                        label = { Text(theme.name) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ShareCardFormat.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = format == option,
                        onClick = { format = option },
                        shape = SegmentedButtonDefaults.itemShape(index, ShareCardFormat.entries.size),
                        icon = {}
                    ) { Text(option.label) }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(showTitle, { showTitle = !showTitle }, label = { Text("Title") })
                FilterChip(showArtist, { showArtist = !showArtist }, label = { Text("Artist") })
                FilterChip(showBranding, { showBranding = !showBranding }, label = { Text("Branding") })
            }
            Text("Text size", style = MaterialTheme.typography.labelLarge)
            Slider(textScale, { textScale = it }, valueRange = .75f..1.3f)
            Text("Background intensity", style = MaterialTheme.typography.labelLarge)
            Slider(intensity, { intensity = it }, valueRange = .55f..1.35f)

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Lyrics", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${selected.size}/5 selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ShareLineScope.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = scope == option,
                        onClick = { scope = option },
                        shape = SegmentedButtonDefaults.itemShape(index, ShareLineScope.entries.size),
                        icon = {}
                    ) { Text(option.name.lowercase().replaceFirstChar(Char::uppercase)) }
                }
            }
            if (scope == ShareLineScope.ALL) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text("Search all lyrics") }
                )
            }
            Spacer(Modifier.height(8.dp))
            if (allLines.isEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp)) {
                    Text("Lyrics are needed before an image can be created.", Modifier.fillMaxWidth().padding(18.dp))
                }
            } else {
                LazyColumn(Modifier.heightIn(max = 270.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(visibleIndices, key = { it }) { index ->
                        val checked = index in selected
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selected = when {
                                    checked -> selected - index
                                    selected.size < 5 -> selected + index
                                    else -> selected
                                }
                            },
                            color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked, onCheckedChange = null)
                                Text(allLines[index], Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val ok = LyricShareCard.save(context, state.track?.title ?: "HEARTLINE lyrics", state.track?.artist ?: "Unknown artist", lines, options)
                        Toast.makeText(context, if (ok) "Saved to Pictures/HEARTLINE" else "Could not save image", Toast.LENGTH_LONG).show()
                    },
                    enabled = lines.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) { Icon(Icons.Rounded.Download, null); Spacer(Modifier.width(5.dp)); Text("Save") }
                Button(
                    onClick = {
                        val ok = LyricShareCard.share(context, state.track?.title ?: "HEARTLINE lyrics", state.track?.artist ?: "Unknown artist", lines, options)
                        if (!ok) Toast.makeText(context, "Could not create the lyric image", Toast.LENGTH_LONG).show() else onDismiss()
                    },
                    enabled = lines.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(5.dp)); Text("Share") }
            }
        }
    }
}
