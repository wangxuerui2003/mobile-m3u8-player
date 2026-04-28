package com.example.m3u8player.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.m3u8player.data.model.*

@Database(
    entities = [
        VideoListEntity::class,
        CategoryEntity::class,
        WatchHistoryEntity::class,
        WatchLaterEntity::class,
        SearchHistoryEntity::class,
        DownloadEntity::class,
        MetadataEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
}
