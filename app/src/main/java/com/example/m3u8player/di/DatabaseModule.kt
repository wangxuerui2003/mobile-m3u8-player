package com.example.m3u8player.di

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.m3u8player.data.PresetDataLoader
import com.example.m3u8player.data.db.AppDatabase
import com.example.m3u8player.data.db.VideoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE watch_history ADD COLUMN episodeName TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE watch_history ADD COLUMN coverUrl TEXT")
            database.execSQL("ALTER TABLE watch_history ADD COLUMN coverPath TEXT")

            database.execSQL("ALTER TABLE downloads ADD COLUMN episodeName TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE downloads ADD COLUMN videoUrl TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE downloads ADD COLUMN coverUrl TEXT")
            database.execSQL("ALTER TABLE downloads ADD COLUMN coverPath TEXT")
            database.execSQL("ALTER TABLE downloads ADD COLUMN workId TEXT")
            database.execSQL("ALTER TABLE downloads ADD COLUMN downloadDir TEXT")
            database.execSQL("ALTER TABLE downloads ADD COLUMN downloadedSegments INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE downloads ADD COLUMN totalSegments INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE downloads ADD COLUMN speedBytesPerSec INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE downloads ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")

            database.execSQL("UPDATE downloads SET status = 'failed' WHERE status IN ('pending', 'downloading')")
            database.execSQL("UPDATE downloads SET updatedAt = createdAt WHERE updatedAt = 0")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS search_history (
                    query TEXT NOT NULL PRIMARY KEY,
                    searchedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE downloads ADD COLUMN downloadedDurationMs INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE downloads ADD COLUMN totalDurationMs INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS episode_watch_history (
                    vodId INTEGER NOT NULL,
                    vodName TEXT NOT NULL,
                    episode INTEGER NOT NULL,
                    episodeName TEXT NOT NULL,
                    coverUrl TEXT,
                    coverPath TEXT,
                    progressMs INTEGER NOT NULL,
                    durationMs INTEGER NOT NULL,
                    lastWatchedAt INTEGER NOT NULL,
                    PRIMARY KEY(vodId, episode)
                )
                """.trimIndent()
            )

            database.execSQL(
                """
                INSERT OR REPLACE INTO episode_watch_history (
                    vodId, vodName, episode, episodeName, coverUrl, coverPath, progressMs, durationMs, lastWatchedAt
                )
                SELECT
                    vodId, vodName, episode, episodeName, coverUrl, coverPath, progressMs, durationMs, lastWatchedAt
                FROM watch_history
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS recommendation_cache (
                    `key` TEXT NOT NULL PRIMARY KEY,
                    payloadJson TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }
    
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "okzyw_data.db"
        )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
        .build()
    }
    
    @Provides
    fun provideVideoDao(database: AppDatabase): VideoDao {
        return database.videoDao()
    }

    @Provides
    @Singleton
    fun providePresetDataLoader(
        @ApplicationContext context: Context,
        dao: VideoDao,
        database: AppDatabase
    ): PresetDataLoader {
        return PresetDataLoader(context, dao, database)
    }
}
