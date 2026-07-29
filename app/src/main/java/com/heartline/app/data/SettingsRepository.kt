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
        val followSystemTheme = booleanPreferencesKey("follow_system_theme")
        val systemLightTheme = stringPreferencesKey("system_light_theme")
        val systemDarkTheme = stringPreferencesKey("system_dark_theme")
        val oledBlack = booleanPreferencesKey("oled_black")
        val saveRecent = booleanPreferencesKey("save_recent")
        val recentLimit = intPreferencesKey("recent_limit")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val keepFavourites = booleanPreferencesKey("keep_favourites")
        val backgroundLyrics = booleanPreferencesKey("background_lyrics")
        val globalOffset = longPreferencesKey("global_offset")
        val showArtwork = booleanPreferencesKey("show_artwork")
        val artworkBackdrop = booleanPreferencesKey("artwork_backdrop")
        val privacyMode = stringPreferencesKey("privacy_mode")
        val catEnabled = booleanPreferencesKey("cat_enabled")
        val reducedMotion = booleanPreferencesKey("reduced_motion")
        val sourcePackage = stringPreferencesKey("preferred_source")
        val lyricScale = stringPreferencesKey("lyric_scale")
        val lyricAlignment = stringPreferencesKey("lyric_alignment")
        val lyricSpacing = stringPreferencesKey("lyric_spacing")
        val boldActiveLyric = booleanPreferencesKey("bold_active_lyric")
        val surroundingLines = intPreferencesKey("surrounding_lines")
        val shareBranding = booleanPreferencesKey("share_branding")
        val notificationDetail = stringPreferencesKey("notification_detail")
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            theme = p[Keys.theme] ?: "Bubblegum",
            saveRecentOffline = p[Keys.saveRecent] ?: true,
            recentLimit = (p[Keys.recentLimit] ?: 20).coerceIn(5, 100),
            wifiOnly = p[Keys.wifiOnly] ?: true,
            keepFavouritesOffline = p[Keys.keepFavourites] ?: true,
            backgroundLyrics = p[Keys.backgroundLyrics] ?: true,
            globalOffsetMs = p[Keys.globalOffset] ?: 0,
            showArtwork = p[Keys.showArtwork] ?: true,
            privacyMode = p[Keys.privacyMode] ?: "hide_lyrics_locked",
            catEnabled = p[Keys.catEnabled] ?: true,
            reducedMotion = p[Keys.reducedMotion] ?: false,
            preferredSourcePackage = p[Keys.sourcePackage],
            followSystemTheme = p[Keys.followSystemTheme] ?: false,
            systemLightTheme = p[Keys.systemLightTheme] ?: "Bubblegum",
            systemDarkTheme = p[Keys.systemDarkTheme] ?: "Moonlit Lavender",
            oledBlack = p[Keys.oledBlack] ?: false,
            artworkBackdrop = p[Keys.artworkBackdrop] ?: false,
            lyricScale = p[Keys.lyricScale] ?: "standard",
            lyricAlignment = p[Keys.lyricAlignment] ?: "center",
            lyricSpacing = p[Keys.lyricSpacing] ?: "comfortable",
            boldActiveLyric = p[Keys.boldActiveLyric] ?: true,
            surroundingLines = (p[Keys.surroundingLines] ?: 2).coerceIn(1, 3),
            shareBranding = p[Keys.shareBranding] ?: true,
            notificationDetail = p[Keys.notificationDetail] ?: "current_next",
            onboardingComplete = p[Keys.onboardingComplete] ?: false
        )
    }

    suspend fun setTheme(v: String) = edit(Keys.theme, v)
    suspend fun setFollowSystemTheme(v: Boolean) = edit(Keys.followSystemTheme, v)
    suspend fun setSystemLightTheme(v: String) = edit(Keys.systemLightTheme, v)
    suspend fun setSystemDarkTheme(v: String) = edit(Keys.systemDarkTheme, v)
    suspend fun setOledBlack(v: Boolean) = edit(Keys.oledBlack, v)
    suspend fun setRecentLimit(v: Int) = edit(Keys.recentLimit, v.coerceIn(5, 100))
    suspend fun setSaveRecent(v: Boolean) = edit(Keys.saveRecent, v)
    suspend fun setWifiOnly(v: Boolean) = edit(Keys.wifiOnly, v)
    suspend fun setKeepFavouritesOffline(v: Boolean) = edit(Keys.keepFavourites, v)
    suspend fun setBackgroundLyrics(v: Boolean) = edit(Keys.backgroundLyrics, v)
    suspend fun setGlobalOffset(v: Long) = edit(Keys.globalOffset, v.coerceIn(-15_000, 15_000))
    suspend fun setShowArtwork(v: Boolean) = edit(Keys.showArtwork, v)
    suspend fun setArtworkBackdrop(v: Boolean) = edit(Keys.artworkBackdrop, v)
    suspend fun setPrivacyMode(v: String) = edit(Keys.privacyMode, v)
    suspend fun setCatEnabled(v: Boolean) = edit(Keys.catEnabled, v)
    suspend fun setReducedMotion(v: Boolean) = edit(Keys.reducedMotion, v)
    suspend fun setLyricScale(v: String) = edit(Keys.lyricScale, v)
    suspend fun setLyricAlignment(v: String) = edit(Keys.lyricAlignment, v)
    suspend fun setLyricSpacing(v: String) = edit(Keys.lyricSpacing, v)
    suspend fun setBoldActiveLyric(v: Boolean) = edit(Keys.boldActiveLyric, v)
    suspend fun setSurroundingLines(v: Int) = edit(Keys.surroundingLines, v.coerceIn(1, 3))
    suspend fun setShareBranding(v: Boolean) = edit(Keys.shareBranding, v)
    suspend fun setNotificationDetail(v: String) = edit(Keys.notificationDetail, v)
    suspend fun setOnboardingComplete(v: Boolean) = edit(Keys.onboardingComplete, v)

    suspend fun setPreferredSource(v: String?) {
        context.dataStore.edit { if (v == null) it.remove(Keys.sourcePackage) else it[Keys.sourcePackage] = v }
    }

    private suspend fun <T> edit(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
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
    val preferredSourcePackage: String?,
    val followSystemTheme: Boolean = false,
    val systemLightTheme: String = "Bubblegum",
    val systemDarkTheme: String = "Moonlit Lavender",
    val oledBlack: Boolean = false,
    val artworkBackdrop: Boolean = false,
    val lyricScale: String = "standard",
    val lyricAlignment: String = "center",
    val lyricSpacing: String = "comfortable",
    val boldActiveLyric: Boolean = true,
    val surroundingLines: Int = 2,
    val shareBranding: Boolean = true,
    val notificationDetail: String = "current_next",
    val onboardingComplete: Boolean = false
)
