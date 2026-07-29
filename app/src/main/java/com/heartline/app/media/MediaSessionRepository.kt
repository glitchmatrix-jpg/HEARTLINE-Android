package com.heartline.app.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import com.heartline.app.data.DetectedTrack
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
import kotlinx.coroutines.cancel
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
    private lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var controllers = emptyList<MediaController>()
    private var active: MediaController? = null
    private var sourceLockPackage: String? = null
    private var lookupJob: Job? = null
    private var tickerJob: Job? = null
    private var preferredPackage: String? = null

    fun initialize(
        app: Context,
        lyrics: LyricsRepository,
        trackDao: TrackDao,
        settings: SettingsRepository
    ) {
        context = app.applicationContext
        manager = context.getSystemService(MediaSessionManager::class.java)
        lyricsRepository = lyrics
        dao = trackDao
        settingsRepository = settings

        val component = ComponentName(context, HeartlineNotificationListener::class.java)
        runCatching {
            manager.addOnActiveSessionsChangedListener(
                { list -> updateControllers(list ?: emptyList()) },
                component
            )
        }

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
        runCatching { manager.getActiveSessions(component) }
            .onSuccess(::updateControllers)
            .onFailure { markPermissionMissing() }
    }

    fun markPermissionMissing() {
        active = null
        _state.update {
            it.copy(
                status = PlayerStatus.PermissionRequired,
                message = "Enable music access to detect playback"
            )
        }
    }

    private fun updateControllers(list: List<MediaController>) {
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = list.distinctBy { it.sessionToken }
        controllers.forEach { controller -> runCatching { controller.registerCallback(callback) } }
        chooseActive()
    }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = chooseActive()
        override fun onPlaybackStateChanged(state: PlaybackState?) = chooseActive()
        override fun onSessionDestroyed() = refreshSessions()
    }

    private fun chooseActive() {
        val lockedPackage = sourceLockPackage
        val eligible = if (lockedPackage == null) controllers else controllers.filter { it.packageName == lockedPackage }
        val best = eligible.maxByOrNull { score(it, preferredPackage) }

        if (best == null) {
            active = null
            _state.update {
                it.copy(
                    track = null,
                    lyrics = emptyList(),
                    plainLyrics = null,
                    currentLineIndex = -1,
                    displayPositionMs = 0,
                    status = PlayerStatus.Waiting,
                    message = if (lockedPackage == null) {
                        "Play something and HEARTLINE will find it"
                    } else {
                        "Locked source is not currently available"
                    },
                    sourceLocked = lockedPackage != null
                )
            }
            return
        }

        // Hysteresis: keep the current valid player unless the challenger is meaningfully better.
        val current = active?.takeIf { controllers.any { candidate -> candidate.sessionToken == it.sessionToken } }
        val selected = if (
            lockedPackage == null && current != null && current.sessionToken != best.sessionToken &&
            score(best, preferredPackage) < score(current, preferredPackage) + SWITCH_MARGIN
        ) current else best

        val detected = toDetectedTrack(selected) ?: return
        val oldFingerprint = _state.value.track?.fingerprint
        active = selected
        _state.update {
            it.copy(
                track = detected,
                status = if (oldFingerprint == detected.fingerprint && it.lyrics.isNotEmpty()) it.status else PlayerStatus.Detecting,
                sourceLocked = lockedPackage != null
            )
        }
        if (detected.fingerprint != oldFingerprint) loadLyrics(detected)
    }

    private fun score(controller: MediaController, preference: String?): Int {
        val playback = controller.playbackState
        val metadata = controller.metadata
        var value = 0
        when (playback?.state) {
            PlaybackState.STATE_PLAYING -> value += 100
            PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> value += 70
            PlaybackState.STATE_PAUSED -> value += 25
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
        _state.update {
            it.copy(
                lyrics = emptyList(),
                plainLyrics = null,
                currentLineIndex = -1,
                isLoadingLyrics = true,
                status = PlayerStatus.LoadingLyrics,
                message = "Finding the words…",
                perTrackOffsetMs = 0,
                isFavourite = false,
                isOfflineReady = false
            )
        }
        lookupJob = scope.launch {
            when (val result = lyricsRepository.resolve(track)) {
                is LyricsLookup.Found -> _state.update {
                    it.copy(
                        lyrics = result.lines,
                        plainLyrics = result.entity.plainLyrics,
                        isLoadingLyrics = false,
                        status = PlayerStatus.Ready,
                        message = null,
                        perTrackOffsetMs = result.entity.customOffsetMs,
                        isFavourite = result.entity.isFavourite,
                        isOfflineReady = dao.get(track.fingerprint) != null
                    )
                }
                is LyricsLookup.Plain -> _state.update {
                    it.copy(
                        plainLyrics = result.entity.plainLyrics,
                        isLoadingLyrics = false,
                        status = PlayerStatus.PlainLyrics,
                        message = "Unsynchronized lyrics",
                        perTrackOffsetMs = result.entity.customOffsetMs,
                        isFavourite = result.entity.isFavourite,
                        isOfflineReady = dao.get(track.fingerprint) != null
                    )
                }
                is LyricsLookup.Instrumental -> _state.update {
                    it.copy(
                        isLoadingLyrics = false,
                        status = PlayerStatus.Instrumental,
                        message = "Instrumental — let it breathe",
                        perTrackOffsetMs = result.entity.customOffsetMs,
                        isFavourite = result.entity.isFavourite,
                        isOfflineReady = dao.get(track.fingerprint) != null
                    )
                }
                LyricsLookup.OfflineMiss -> _state.update {
                    it.copy(isLoadingLyrics = false, status = PlayerStatus.Offline, message = "No signal, no saved words")
                }
                LyricsLookup.NoMatch -> _state.update {
                    it.copy(isLoadingLyrics = false, status = PlayerStatus.NoLyrics, message = "Lyrics not found")
                }
                is LyricsLookup.Error -> _state.update {
                    it.copy(isLoadingLyrics = false, status = PlayerStatus.Error, message = result.message)
                }
            }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                val currentState = _state.value
                val track = currentState.track
                if (track != null) {
                    val elapsed = if (track.isPlaying) {
                        (SystemClock.elapsedRealtime() - track.updatedAtElapsedMs).coerceAtLeast(0) * track.playbackSpeed
                    } else 0f
                    val rawPosition = track.positionMs + elapsed.toLong()
                    val durationBound = track.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
                    val displayPosition = (rawPosition + currentState.globalOffsetMs + currentState.perTrackOffsetMs)
                        .coerceIn(0, durationBound)
                    val index = LrcParser.currentIndex(currentState.lyrics, displayPosition)
                    _state.update { it.copy(displayPositionMs = displayPosition, currentLineIndex = index) }
                }
                delay(if (_state.value.track?.isPlaying == true) 250 else 1_000)
            }
        }
    }

    fun transportPlayPause() {
        active?.transportControls?.let { controls ->
            if (_state.value.track?.isPlaying == true) controls.pause() else controls.play()
        }
    }

    fun transportNext() = active?.transportControls?.skipToNext()
    fun transportPrevious() = active?.transportControls?.skipToPrevious()
    fun seekTo(ms: Long) = active?.transportControls?.seekTo(ms.coerceAtLeast(0))

    fun adjustOffset(deltaMs: Long) {
        val track = _state.value.track ?: return
        val newOffset = (_state.value.perTrackOffsetMs + deltaMs).coerceIn(-15_000, 15_000)
        _state.update { it.copy(perTrackOffsetMs = newOffset) }
        scope.launch {
            ensureCurrentEntity(track)
            dao.setOffset(track.fingerprint, newOffset)
        }
    }

    fun toggleFavourite() {
        val track = _state.value.track ?: return
        val value = !_state.value.isFavourite
        _state.update { it.copy(isFavourite = value) }
        scope.launch {
            ensureCurrentEntity(track)
            dao.setFavourite(track.fingerprint, value)
        }
    }

    private suspend fun ensureCurrentEntity(track: DetectedTrack) {
        if (dao.get(track.fingerprint) != null) return
        dao.upsert(
            TrackEntity(
                fingerprint = track.fingerprint,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs,
                sourcePackage = track.sourcePackage,
                sourceLabel = track.sourceLabel,
                artworkUri = track.artworkUri,
                syncedLyrics = null,
                plainLyrics = null,
                providerId = null,
                lastPlayedAt = System.currentTimeMillis()
            )
        )
    }

    fun toggleSourceLock() {
        sourceLockPackage = if (sourceLockPackage == null) active?.packageName else null
        chooseActive()
    }

    private const val SWITCH_MARGIN = 35
}
