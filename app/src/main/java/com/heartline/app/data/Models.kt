package com.heartline.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val fingerprint: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val sourcePackage: String,
    val sourceLabel: String,
    val artworkUri: String?,
    val syncedLyrics: String?,
    val plainLyrics: String?,
    val providerId: Long?,
    val customOffsetMs: Long = 0,
    val isFavourite: Boolean = false,
    val keepOffline: Boolean = false,
    val manuallyMatched: Boolean = false,
    val lastPlayedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

data class LyricLine(val timestampMs: Long, val text: String)

data class DetectedTrack(
    val fingerprint: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val positionMs: Long,
    val playbackSpeed: Float,
    val isPlaying: Boolean,
    val sourcePackage: String,
    val sourceLabel: String,
    val artworkUri: String? = null,
    val updatedAtElapsedMs: Long
)

data class PlayerState(
    val track: DetectedTrack? = null,
    val lyrics: List<LyricLine> = emptyList(),
    val plainLyrics: String? = null,
    val currentLineIndex: Int = -1,
    val displayPositionMs: Long = 0,
    val isLoadingLyrics: Boolean = false,
    val status: PlayerStatus = PlayerStatus.Waiting,
    val message: String? = null,
    val globalOffsetMs: Long = 0,
    val perTrackOffsetMs: Long = 0,
    val isFavourite: Boolean = false,
    val isOfflineReady: Boolean = false,
    val sourceLocked: Boolean = false
)

enum class PlayerStatus { Waiting, PermissionRequired, Detecting, LoadingLyrics, Ready, PlainLyrics, Instrumental, NoLyrics, Offline, Error }
