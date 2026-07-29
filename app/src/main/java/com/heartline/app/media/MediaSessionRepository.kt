package com.heartline.app.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import com.heartline.app.data.DetectedTrack
import com.heartline.app.data.LyricCandidate
import com.heartline.app.data.PlaybackMode
import com.heartline.app.data.PlayerState
import com.heartline.app.data.PlayerStatus
import com.heartline.app.data.SettingsRepository
import com.heartline.app.data.TrackDao
import com.heartline.app.data.TrackEntity
import com.heartline.app.lyrics.LrcParser
import com.heartline.app.lyrics.LyricsLookup
import com.heartline.app.lyrics.LyricsRepository
import com.heartline.app.lyrics.MetadataNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

object MediaSessionRepository {
    private lateinit var context: Context
    private lateinit var manager: MediaSessionManager
    private lateinit var lyricsRepository: LyricsRepository
    private lateinit var dao: TrackDao
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var controllers = emptyList<MediaController>()
    private var active: MediaController? = null
    private var sourceLockPackage: String? = null
    private var lookupJob: Job? = null
    private var tickerJob: Job? = null
    private var preferredPackage: String? = null
    private var manualBasePositionMs = 0L
    private var manualBaseElapsedMs = 0L

    fun initialize(app: Context, lyrics: LyricsRepository, trackDao: TrackDao, settings: SettingsRepository) {
        context = app.applicationContext
        manager = context.getSystemService(MediaSessionManager::class.java)
        lyricsRepository = lyrics
        dao = trackDao
        val component = ComponentName(context, HeartlineNotificationListener::class.java)
        runCatching { manager.addOnActiveSessionsChangedListener({ updateControllers(it ?: emptyList()) }, component) }
        scope.launch {
            settings.settings.collect { config ->
                preferredPackage = config.preferredSourcePackage
                _state.update { it.copy(globalOffsetMs = config.globalOffsetMs) }
            }
        }
        refreshSessions()
        startTicker()
    }

    fun refreshSessions() {
        if (!::manager.isInitialized) return
        val component = ComponentName(context, HeartlineNotificationListener::class.java)
        runCatching { manager.getActiveSessions(component) }.onSuccess(::updateControllers).onFailure { markPermissionMissing() }
    }

    fun reconnect() {
        active = null
        refreshSessions()
        scope.launch { delay(150); chooseActive(forceRebind = true) }
    }

    fun markPermissionMissing() {
        active = null
        _state.update { it.copy(status = PlayerStatus.PermissionRequired, message = "Enable music access to detect playback") }
    }

    private fun updateControllers(list: List<MediaController>) {
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = list.distinctBy { it.sessionToken }
        controllers.forEach { runCatching { it.registerCallback(callback) } }
        chooseActive()
    }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = chooseActive()
        override fun onPlaybackStateChanged(state: PlaybackState?) = chooseActive()
        override fun onSessionDestroyed() = reconnect()
    }

    private fun chooseActive(forceRebind: Boolean = false) {
        val locked = sourceLockPackage
        val eligible = if (locked == null) controllers else controllers.filter { it.packageName == locked }
        val best = eligible.maxByOrNull { score(it, preferredPackage) }
        if (best == null) {
            active = null
            _state.update { current ->
                current.copy(
                    track = if (current.playbackMode == PlaybackMode.SEARCH) current.track else null,
                    status = PlayerStatus.Waiting,
                    message = if (locked == null) "Play something and HEARTLINE will find it" else "Locked source is unavailable",
                    sourceLocked = locked != null
                )
            }
            return
        }
        val current = active?.takeIf { controller -> controllers.any { it.sessionToken == controller.sessionToken } }
        val selected = if (!forceRebind && locked == null && current != null && current.sessionToken != best.sessionToken && score(best, preferredPackage) < score(current, preferredPackage) + SWITCH_MARGIN) current else best
        val detected = toDetectedTrack(selected) ?: return
        val oldFingerprint = _state.value.track?.fingerprint
        active = selected
        _state.update {
            it.copy(
                track = detected,
                status = if (oldFingerprint == detected.fingerprint && (it.lyrics.isNotEmpty() || !it.plainLyrics.isNullOrBlank())) it.status else PlayerStatus.Detecting,
                sourceLocked = locked != null
            )
        }
        if (detected.fingerprint != oldFingerprint && _state.value.playbackMode != PlaybackMode.SEARCH) loadLyrics(detected)
    }

    private fun score(controller: MediaController, preference: String?): Int {
        val playback = controller.playbackState
        val metadata = controller.metadata
        var value = when (playback?.state) {
            PlaybackState.STATE_PLAYING -> 100
            PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> 70
            PlaybackState.STATE_PAUSED -> 25
            else -> 0
        }
        if (!metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank()) value += 25
        if (!metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).isNullOrBlank()) value += 20
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0
        if (duration > 60_000) value += 15
        if (playback != null && SystemClock.elapsedRealtime() - playback.lastPositionUpdateTime < 20_000) value += 25
        if (controller.packageName == preference) value += 40
        if (duration in 1..15_000) value -= 25
        if (metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty().contains("advertisement", true)) value -= 50
        return value
    }

    private fun toDetectedTrack(controller: MediaController): DetectedTrack? {
        val metadata = controller.metadata ?: return null
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
        if (title.isBlank()) return null
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim().takeUnless { it.isNullOrBlank() }
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)?.trim().orEmpty().ifBlank { "Unknown artist" }
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)?.trim()?.takeIf(String::isNotBlank)
        val duration = max(0, metadata.getLong(MediaMetadata.METADATA_KEY_DURATION))
        val playback = controller.playbackState
        val appLabel = runCatching {
            val info = context.packageManager.getApplicationInfo(controller.packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(controller.packageName)
        return DetectedTrack(
            fingerprint = MetadataNormalizer.fingerprint(title, artist, album, duration),
            title = title,
            artist = artist,
            album = album,
            durationMs = duration,
            positionMs = max(0, playback?.position ?: 0),
            playbackSpeed = playback?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            isPlaying = playback?.state == PlaybackState.STATE_PLAYING,
            sourcePackage = controller.packageName,
            sourceLabel = appLabel,
            artworkUri = null,
            updatedAtElapsedMs = playback?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime()
        )
    }

    private fun loadLyrics(track: DetectedTrack) {
        lookupJob?.cancel()
        _state.update { it.copy(lyrics = emptyList(), plainLyrics = null, currentLineIndex = -1, isLoadingLyrics = true, status = PlayerStatus.LoadingLyrics, message = "Finding the words…", candidates = emptyList(), candidatesVisible = false) }
        lookupJob = scope.launch { applyLookup(lyricsRepository.resolve(track), track) }
    }

    private suspend fun applyLookup(result: LyricsLookup, track: DetectedTrack) {
        when (result) {
            is LyricsLookup.Found -> _state.update { it.copy(lyrics = result.lines, plainLyrics = result.entity.plainLyrics, isLoadingLyrics = false, status = PlayerStatus.Ready, message = null, perTrackOffsetMs = result.entity.customOffsetMs, isFavourite = result.entity.isFavourite, isOfflineReady = dao.get(track.fingerprint) != null, selectedProviderId = result.entity.providerId) }
            is LyricsLookup.Plain -> _state.update { it.copy(lyrics = emptyList(), plainLyrics = result.entity.plainLyrics, isLoadingLyrics = false, status = PlayerStatus.PlainLyrics, message = "Unsynchronized lyrics", perTrackOffsetMs = result.entity.customOffsetMs, isFavourite = result.entity.isFavourite, isOfflineReady = dao.get(track.fingerprint) != null, selectedProviderId = result.entity.providerId) }
            is LyricsLookup.Instrumental -> _state.update { it.copy(lyrics = emptyList(), plainLyrics = null, isLoadingLyrics = false, status = PlayerStatus.Instrumental, message = "Instrumental — let it breathe", selectedProviderId = result.entity.providerId) }
            LyricsLookup.OfflineMiss -> _state.update { it.copy(isLoadingLyrics = false, status = PlayerStatus.Offline, message = "No signal, no saved words") }
            LyricsLookup.NoMatch -> _state.update { it.copy(isLoadingLyrics = false, status = PlayerStatus.NoLyrics, message = "No reliable lyrics match — choose another source") }
            is LyricsLookup.Error -> _state.update { it.copy(isLoadingLyrics = false, status = PlayerStatus.Error, message = result.message) }
        }
    }

    fun requestCandidates(query: String? = null) {
        val track = _state.value.track ?: return
        lookupJob?.cancel()
        _state.update { it.copy(isLoadingLyrics = true, candidatesVisible = true, searchQuery = query.orEmpty(), message = "Searching lyric versions…") }
        lookupJob = scope.launch {
            runCatching { lyricsRepository.searchCandidates(track, query) }
                .onSuccess { list -> _state.update { it.copy(isLoadingLyrics = false, candidates = list, candidatesVisible = true, message = if (list.isEmpty()) "No alternate versions found" else null) } }
                .onFailure { error -> _state.update { it.copy(isLoadingLyrics = false, message = error.message ?: "Could not search lyrics") } }
        }
    }

    fun selectCandidate(candidate: LyricCandidate) {
        val track = _state.value.track ?: return
        lookupJob?.cancel()
        lookupJob = scope.launch {
            val result = lyricsRepository.applyCandidate(track, candidate, manuallyMatched = true)
            applyLookup(result, track)
            _state.update { it.copy(candidatesVisible = false, candidates = emptyList()) }
        }
    }

    fun dismissCandidates() = _state.update { it.copy(candidatesVisible = false) }

    fun setPlaybackMode(mode: PlaybackMode) {
        val current = _state.value
        if (mode == current.playbackMode) return
        if (mode == PlaybackMode.MANUAL) {
            manualBasePositionMs = current.displayPositionMs
            manualBaseElapsedMs = SystemClock.elapsedRealtime()
        }
        if (mode == PlaybackMode.AUTO) reconnect()
        _state.update { it.copy(playbackMode = mode, manualClockPlaying = true, message = when (mode) {
            PlaybackMode.AUTO -> "Following the active player"
            PlaybackMode.MANUAL -> "Manual lyric clock — music keeps playing"
            PlaybackMode.SEARCH -> "Search and open any lyrics"
        }) }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                val current = _state.value
                val track = current.track
                if (track != null) {
                    val durationBound = track.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
                    val base = when (current.playbackMode) {
                        PlaybackMode.AUTO -> {
                            val elapsed = if (track.isPlaying) (SystemClock.elapsedRealtime() - track.updatedAtElapsedMs).coerceAtLeast(0) * track.playbackSpeed else 0f
                            track.positionMs + elapsed.toLong()
                        }
                        PlaybackMode.MANUAL, PlaybackMode.SEARCH -> {
                            if (current.manualClockPlaying) manualBasePositionMs + (SystemClock.elapsedRealtime() - manualBaseElapsedMs).coerceAtLeast(0) else manualBasePositionMs
                        }
                    }
                    val display = (base + current.globalOffsetMs + current.perTrackOffsetMs).coerceIn(0, durationBound)
                    val index = LrcParser.currentIndex(current.lyrics, display)
                    _state.update { it.copy(displayPositionMs = display, currentLineIndex = index) }
                }
                delay(if ((_state.value.track?.isPlaying == true && _state.value.playbackMode == PlaybackMode.AUTO) || _state.value.manualClockPlaying) 250 else 1_000)
            }
        }
    }

    fun transportPlayPause() {
        when (_state.value.playbackMode) {
            PlaybackMode.AUTO -> active?.transportControls?.let { if (_state.value.track?.isPlaying == true) it.pause() else it.play() }
            PlaybackMode.MANUAL, PlaybackMode.SEARCH -> {
                val current = _state.value
                if (current.manualClockPlaying) {
                    manualBasePositionMs = current.displayPositionMs - current.globalOffsetMs - current.perTrackOffsetMs
                } else {
                    manualBaseElapsedMs = SystemClock.elapsedRealtime()
                }
                _state.update { it.copy(manualClockPlaying = !current.manualClockPlaying) }
            }
        }
    }

    fun transportNext() { if (_state.value.playbackMode == PlaybackMode.AUTO) active?.transportControls?.skipToNext() }
    fun transportPrevious() { if (_state.value.playbackMode == PlaybackMode.AUTO) active?.transportControls?.skipToPrevious() }

    fun seekTo(ms: Long) {
        if (_state.value.playbackMode == PlaybackMode.AUTO) active?.transportControls?.seekTo(ms.coerceAtLeast(0))
        else {
            manualBasePositionMs = ms.coerceAtLeast(0) - _state.value.globalOffsetMs - _state.value.perTrackOffsetMs
            manualBaseElapsedMs = SystemClock.elapsedRealtime()
            _state.update { it.copy(displayPositionMs = ms.coerceAtLeast(0)) }
        }
    }

    fun rejoinPlayerPosition() {
        setPlaybackMode(PlaybackMode.AUTO)
        chooseActive(forceRebind = true)
    }

    fun adjustOffset(deltaMs: Long) {
        val track = _state.value.track ?: return
        val newOffset = (_state.value.perTrackOffsetMs + deltaMs).coerceIn(-15_000, 15_000)
        _state.update { it.copy(perTrackOffsetMs = newOffset) }
        scope.launch { ensureCurrentEntity(track); dao.setOffset(track.fingerprint, newOffset) }
    }

    fun toggleFavourite() {
        val track = _state.value.track ?: return
        val value = !_state.value.isFavourite
        _state.update { it.copy(isFavourite = value) }
        scope.launch { ensureCurrentEntity(track); dao.setFavourite(track.fingerprint, value) }
    }

    private suspend fun ensureCurrentEntity(track: DetectedTrack) {
        if (dao.get(track.fingerprint) != null) return
        dao.upsert(TrackEntity(track.fingerprint, track.title, track.artist, track.album, track.durationMs, track.sourcePackage, track.sourceLabel, track.artworkUri, null, null, null, lastPlayedAt = System.currentTimeMillis()))
    }

    fun toggleSourceLock() {
        if (sourceLockPackage == null) {
            sourceLockPackage = active?.packageName
            chooseActive(forceRebind = true)
        } else {
            sourceLockPackage = null
            active = null
            _state.update { it.copy(sourceLocked = false, message = "Reconnecting to the best active player…") }
            reconnect()
        }
    }

    private const val SWITCH_MARGIN = 35
}
