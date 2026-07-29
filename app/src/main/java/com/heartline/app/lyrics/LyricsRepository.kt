package com.heartline.app.lyrics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.heartline.app.data.AppSettings
import com.heartline.app.data.DetectedTrack
import com.heartline.app.data.LyricCandidate
import com.heartline.app.data.LyricLine
import com.heartline.app.data.SettingsRepository
import com.heartline.app.data.TrackDao
import com.heartline.app.data.TrackEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.abs

sealed interface LyricsLookup {
    data class Found(val entity: TrackEntity, val lines: List<LyricLine>) : LyricsLookup
    data class Plain(val entity: TrackEntity) : LyricsLookup
    data class Instrumental(val entity: TrackEntity) : LyricsLookup
    data object OfflineMiss : LyricsLookup
    data object NoMatch : LyricsLookup
    data class Error(val message: String) : LyricsLookup
}

class LyricsRepository(
    private val context: Context,
    private val dao: TrackDao,
    private val settings: SettingsRepository,
    private val client: LrclibClient = LrclibClient()
) {
    suspend fun resolve(track: DetectedTrack): LyricsLookup {
        dao.get(track.fingerprint)?.let { cached ->
            val updated = cached.touch(track)
            dao.upsert(updated)
            return lookupFromEntity(updated)
        }
        val candidates = searchCandidates(track, null)
        val best = candidates.firstOrNull() ?: return if (isNetworkAllowed(settings.settings.first())) LyricsLookup.NoMatch else LyricsLookup.OfflineMiss
        if (best.score < MIN_AUTOMATIC_SCORE) return LyricsLookup.NoMatch
        return applyCandidate(track, best, manuallyMatched = false)
    }

    suspend fun searchCandidates(track: DetectedTrack, queryOverride: String?): List<LyricCandidate> {
        val config = settings.settings.first()
        if (!isNetworkAllowed(config)) return emptyList()
        val queries = if (!queryOverride.isNullOrBlank()) {
            listOf(queryOverride.trim())
        } else {
            listOf(
                "${track.title} ${track.artist} ${track.album.orEmpty()}",
                "${track.title} ${track.artist}",
                MetadataNormalizer.clean("${track.title} ${track.artist}")
            ).map(String::trim).filter(String::isNotBlank).distinct()
        }
        val raw = linkedMapOf<Long, LrclibResult>()
        for ((index, query) in queries.withIndex()) {
            if (index > 0) delay(120)
            client.search(query).take(25).forEach { raw[it.id] = it }
            if (raw.size >= 20) break
        }
        return raw.values
            .map { item ->
                LyricCandidate(
                    id = item.id,
                    title = item.trackName,
                    artist = item.artistName,
                    album = item.albumName,
                    durationSeconds = item.duration,
                    synced = !item.syncedLyrics.isNullOrBlank(),
                    instrumental = item.instrumental,
                    score = score(track, item),
                    preview = item.syncedLyrics?.lineSequence()?.firstOrNull { it.isNotBlank() }
                        ?.replace(Regex("\\[[^]]+\\]"), "")?.trim()
                        ?: item.plainLyrics?.lineSequence()?.firstOrNull { it.isNotBlank() }.orEmpty(),
                    syncedLyrics = item.syncedLyrics,
                    plainLyrics = item.plainLyrics
                )
            }
            .sortedByDescending(LyricCandidate::score)
            .take(15)
    }

    suspend fun applyCandidate(track: DetectedTrack, candidate: LyricCandidate, manuallyMatched: Boolean = true): LyricsLookup {
        val config = settings.settings.first()
        val previous = dao.get(track.fingerprint)
        val entity = TrackEntity(
            fingerprint = track.fingerprint,
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationMs = track.durationMs,
            sourcePackage = track.sourcePackage,
            sourceLabel = track.sourceLabel,
            artworkUri = track.artworkUri,
            syncedLyrics = candidate.syncedLyrics,
            plainLyrics = candidate.plainLyrics,
            providerId = candidate.id,
            customOffsetMs = previous?.customOffsetMs ?: 0,
            isFavourite = previous?.isFavourite ?: false,
            keepOffline = previous?.keepOffline ?: false,
            manuallyMatched = manuallyMatched,
            lastPlayedAt = System.currentTimeMillis(),
            createdAt = previous?.createdAt ?: System.currentTimeMillis()
        )
        if (config.saveRecentOffline || manuallyMatched) {
            dao.upsert(entity)
            enforceLimit(config)
        }
        return when {
            candidate.instrumental -> LyricsLookup.Instrumental(entity)
            else -> lookupFromEntity(entity)
        }
    }

    private fun lookupFromEntity(entity: TrackEntity): LyricsLookup {
        val lines = entity.syncedLyrics?.let(LrcParser::parse).orEmpty()
        return when {
            lines.isNotEmpty() -> LyricsLookup.Found(entity, lines)
            !entity.plainLyrics.isNullOrBlank() -> LyricsLookup.Plain(entity)
            else -> LyricsLookup.Instrumental(entity)
        }
    }

    private fun score(track: DetectedTrack, item: LrclibResult): Double {
        val title = MetadataNormalizer.similarity(track.title, item.trackName)
        val artist = MetadataNormalizer.similarity(track.artist, item.artistName)
        val album = if (track.album.isNullOrBlank() || item.albumName.isNullOrBlank()) 0.5 else MetadataNormalizer.similarity(track.album, item.albumName)
        val duration = if (track.durationMs <= 0 || item.duration == null) 0.5 else (1.0 - abs(track.durationMs / 1000.0 - item.duration) / 30.0).coerceIn(0.0, 1.0)
        val syncedBonus = if (!item.syncedLyrics.isNullOrBlank()) 0.05 else 0.0
        return 0.50 * title + 0.28 * artist + 0.10 * duration + 0.07 * album + syncedBonus
    }

    private suspend fun enforceLimit(config: AppSettings) {
        var count = dao.offlineCount()
        val candidates = dao.evictionCandidates().iterator()
        while (count > config.recentLimit && candidates.hasNext()) {
            dao.delete(candidates.next().fingerprint)
            count--
        }
    }

    private fun isNetworkAllowed(config: AppSettings): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return false
        return !config.wifiOnly || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun TrackEntity.touch(track: DetectedTrack) = copy(
        title = track.title,
        artist = track.artist,
        album = track.album,
        durationMs = track.durationMs,
        sourcePackage = track.sourcePackage,
        sourceLabel = track.sourceLabel,
        artworkUri = track.artworkUri ?: artworkUri,
        lastPlayedAt = System.currentTimeMillis()
    )

    private companion object { const val MIN_AUTOMATIC_SCORE = 0.62 }
}
