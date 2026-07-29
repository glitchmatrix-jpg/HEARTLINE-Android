package com.heartline.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE fingerprint = :fingerprint LIMIT 1")
    suspend fun get(fingerprint: String): TrackEntity?

    @Query("SELECT * FROM tracks ORDER BY lastPlayedAt DESC")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isFavourite = 1 ORDER BY lastPlayedAt DESC")
    fun observeFavourites(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE keepOffline = 1 OR syncedLyrics IS NOT NULL OR plainLyrics IS NOT NULL ORDER BY lastPlayedAt DESC")
    fun observeOffline(): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: TrackEntity)

    @Query("UPDATE tracks SET isFavourite = :value WHERE fingerprint = :fingerprint")
    suspend fun setFavourite(fingerprint: String, value: Boolean)

    @Query("UPDATE tracks SET keepOffline = :value WHERE fingerprint = :fingerprint")
    suspend fun setKeepOffline(fingerprint: String, value: Boolean)

    @Query("UPDATE tracks SET customOffsetMs = :offsetMs WHERE fingerprint = :fingerprint")
    suspend fun setOffset(fingerprint: String, offsetMs: Long)

    @Query("DELETE FROM tracks WHERE fingerprint = :fingerprint")
    suspend fun delete(fingerprint: String)

    @Query("SELECT COUNT(*) FROM tracks WHERE syncedLyrics IS NOT NULL OR plainLyrics IS NOT NULL")
    suspend fun offlineCount(): Int

    @Query("SELECT * FROM tracks WHERE isFavourite = 0 AND keepOffline = 0 AND manuallyMatched = 0 AND customOffsetMs = 0 ORDER BY lastPlayedAt ASC")
    suspend fun evictionCandidates(): List<TrackEntity>
}
