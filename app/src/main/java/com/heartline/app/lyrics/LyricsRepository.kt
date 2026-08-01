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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    suspend fun resolve(track: DetectedTrack): LyricsLookup = safelyLookup {
        dao.get(track.fingerprint)?.let { cached ->
            val updated = cached.touch(track)
            dao.upsert(updated)
            return@safelyLookup lookupFromEntity(updated)
        }
        val candidates = searchCandidates(track, null)
        val best = candidates.firstOrNull()
            ?: return@safelyLookup if (isNetworkAllowed(settings.settings.first())) LyricsLookup.NoMatch else LyricsLookup.OfflineMiss
        if (best.score < MIN_AUTOMATIC_SCORE) return@safelyLookup LyricsLookup.NoMatch
        applyCandidateInternal(track, best, manuallyMatched = false)
    }

    suspend fun searchCandidates(track: DetectedTrack, queryOverride: String?): List<LyricCandidate> {
        val config = settings.settings.first()
        if (!isNetworkAllowed(config)) return emptyList()

        val raw = if (!queryOverride.isNullOrBlank()) {
            client.search(queryOverride.trim()).associateByTo(linkedMapOf(), LrclibResult::id)
        } else {
            searchRobust(track)
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
            .sortedWith(
                compareByDescending<LyricCandidate> { it.score }
                    .thenByDescending { it.synced }
                    .thenBy { durationDifference(track, it.durationSeconds) }
            )
            .take(15)
    }

    private suspend fun searchRobust(track: DetectedTrack): LinkedHashMap<Long, LrclibResult> = coroutineScope {
        val titles = MetadataNormalizer.titleVariants(track.title)
        val artists = MetadataNormalizer.artistVariants(track.artist)
        val albums = MetadataNormalizer.albumVariants(track.album)

        // The old implementation performed three requests sequentially with artificial waits.
        // These two bounded searches run together, so broader matching does not make detection slower.
        val precise = async {
            client.searchFields(
                track = titles.first(),
                artist = artists.firstOrNull(),
                album = albums.firstOrNull()
            )
        }
        val tolerant = async {
            client.searchFields(
                track = titles.getOrElse(1) { titles.first() },
                artist = artists.getOrElse(1) { artists.firstOrNull().orEmpty() }.ifBlank { null },
                album = null
            )
        }

        linkedMapOf<Long, LrclibResult>().apply {
            precise.await().forEach { put(it.id, it) }
            tolerant.await().forEach { put(it.id, it) }

            // Only use one extra broad request when the bounded fielded searches found nothing.
            // This preserves normal lookup speed while rescuing unusual metadata on new releases.
            if (isEmpty()) {
                val broad = listOf(
                    titles.lastOrNull().orEmpty(),
                    artists.lastOrNull().orEmpty()
                ).filter(String::isNotBlank).joinToString(" ")
                client.search(broad).forEach { put(it.id, it) }
            }
        }
    }

    suspend fun applyCandidate(
        track: DetectedTrack,
        candidate: LyricCandidate,
        manuallyMatched: Boolean = true
    ): LyricsLookup = safelyLookup {
        applyCandidateInternal(track, candidate, manuallyMatched)
    }

    private suspend fun applyCandidateInternal(
        track: DetectedTrack,
        candidate: LyricCandidate,
        manuallyMatched: Boolean
    ): LyricsLookup {
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

    private suspend fun safelyLookup(block: suspend () -> LyricsLookup): LyricsLookup = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        LyricsLookup.Error(error.toUserMessage())
    }

    private fun Throwable.toUserMessage(): String = when (this) {
        is LyricsRateLimitedException -> message ?: "Lyrics provider rate limit reached"
        is LyricsNetworkException -> message ?: "Could not reach the lyrics provider"
        is java.net.SocketTimeoutException -> "Lyrics lookup timed out — try again"
        is java.net.UnknownHostException -> "No network connection for lyrics"
        is kotlinx.serialization.SerializationException -> "The lyrics provider returned an unreadable response"
        else -> "Lyrics could not be loaded for this song"
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
        val title = MetadataNormalizer.titleVariants(track.title)
            .maxOf { MetadataNormalizer.similarity(it, item.trackName) }
        val artist = MetadataNormalizer.artistVariants(track.artist)
            .maxOf { MetadataNormalizer.similarity(it, item.artistName) }
        val album = if (track.album.isNullOrBlank() || item.albumName.isNullOrBlank()) {
            0.5
        } else {
            MetadataNormalizer.albumVariants(track.album)
                .maxOfOrNull { MetadataNormalizer.similarity(it, item.albumName.orEmpty()) } ?: 0.5
        }
        val duration = if (track.durationMs <= 0 || item.duration == null) {
            0.5
        } else {
            (1.0 - abs(track.durationMs / 1000.0 - item.duration) / 24.0).coerceIn(0.0, 1.0)
        }
        val syncedBonus = if (!item.syncedLyrics.isNullOrBlank()) 0.06 else 0.0
        val plainBonus = if (item.syncedLyrics.isNullOrBlank() && !item.plainLyrics.isNullOrBlank()) 0.025 else 0.0
        val suspiciousDurationPenalty = if (track.durationMs > 0 && item.duration != null && abs(track.durationMs / 1000.0 - item.duration) > 45) 0.12 else 0.0
        return (0.52 * title + 0.27 * artist + 0.10 * duration + 0.06 * album + syncedBonus + plainBonus - suspiciousDurationPenalty)
            .coerceIn(0.0, 1.0)
    }

    private fun durationDifference(track: DetectedTrack, candidateSeconds: Double?): Double =
        if (track.durationMs <= 0 || candidateSeconds == null) Double.MAX_VALUE
        else abs(track.durationMs / 1000.0 - candidateSeconds)

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

    private companion object { const val MIN_AUTOMATIC_SCORE = 0.60 }
}
