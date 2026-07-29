package com.heartline.app

import android.app.Application
import com.heartline.app.data.HeartlineDatabase
import com.heartline.app.data.SettingsRepository
import com.heartline.app.lyrics.LyricsRepository
import com.heartline.app.media.MediaSessionRepository

class HeartlineApplication : Application() {
    lateinit var database: HeartlineDatabase
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var lyrics: LyricsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = HeartlineDatabase.create(this)
        settings = SettingsRepository(this)
        lyrics = LyricsRepository(this, database.trackDao(), settings)
        MediaSessionRepository.initialize(this, lyrics, database.trackDao(), settings)
    }
}
