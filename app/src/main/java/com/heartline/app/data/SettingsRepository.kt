package com.heartline.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("heartline_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val saveRecent = booleanPreferencesKey("save_recent")
        val recentLimit = intPreferencesKey("recent_limit")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val keepFavourites = booleanPreferencesKey("keep_favourites")
        val backgroundLyrics = booleanPreferencesKey("background_lyrics")
        val globalOffset = longPreferencesKey("global_offset")
        val showArtwork = booleanPreferencesKey("show_artwork")
        val privacyMode = stringPreferencesKey("privacy_mode")
        val catEnabled = booleanPreferencesKey("cat_enabled")
        val reducedMotion = booleanPreferencesKey("reduced_motion")
        val sourcePackage = stringPreferencesKey("preferred_source")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            theme = p[Keys.theme] ?: "Bubblegum",
            saveRecentOffline = p[Keys.saveRecent] ?: true,
            recentLimit = (p[Keys.recentLimit] ?: 20).coerceIn(5, 40),
            wifiOnly = p[Keys.wifiOnly] ?: true,
            keepFavouritesOffline = p[Keys.keepFavourites] ?: true,
            backgroundLyrics = p[Keys.backgroundLyrics] ?: true,
            globalOffsetMs = p[Keys.globalOffset] ?: 0,
            showArtwork = p[Keys.showArtwork] ?: true,
            privacyMode = p[Keys.privacyMode] ?: "hide_lyrics_locked",
            catEnabled = p[Keys.catEnabled] ?: true,
            reducedMotion = p[Keys.reducedMotion] ?: false,
            preferredSourcePackage = p[Keys.sourcePackage]
        )
    }

    suspend fun setTheme(v: String) = edit(Keys.theme, v)
    suspend fun setRecentLimit(v: Int) = edit(Keys.recentLimit, v.coerceIn(5, 40))
    suspend fun setSaveRecent(v: Boolean) = edit(Keys.saveRecent, v)
    suspend fun setWifiOnly(v: Boolean) = edit(Keys.wifiOnly, v)
    suspend fun setBackgroundLyrics(v: Boolean) = edit(Keys.backgroundLyrics, v)
    suspend fun setGlobalOffset(v: Long) = edit(Keys.globalOffset, v.coerceIn(-15_000, 15_000))
    suspend fun setCatEnabled(v: Boolean) = edit(Keys.catEnabled, v)
    suspend fun setReducedMotion(v: Boolean) = edit(Keys.reducedMotion, v)
    suspend fun setPreferredSource(v: String?) {
        context.dataStore.edit { if (v == null) it.remove(Keys.sourcePackage) else it[Keys.sourcePackage] = v }
    }

    private suspend fun <T> edit(key: Preferences.Key<T>, value: T) { context.dataStore.edit { it[key] = value } }
}

data class AppSettings(
    val theme: String,
    val saveRecentOffline: Boolean,
    val recentLimit: Int,
    val wifiOnly: Boolean,
    val keepFavouritesOffline: Boolean,
    val backgroundLyrics: Boolean,
    val globalOffsetMs: Long,
    val showArtwork: Boolean,
    val privacyMode: String,
    val catEnabled: Boolean,
    val reducedMotion: Boolean,
    val preferredSourcePackage: String?
)
