package com.heartline.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TrackEntity::class], version = 1, exportSchema = true)
abstract class HeartlineDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    companion object {
        fun create(context: Context): HeartlineDatabase = Room.databaseBuilder(
            context.applicationContext,
            HeartlineDatabase::class.java,
            "heartline.db"
        ).fallbackToDestructiveMigrationOnDowngrade().build()
    }
}
