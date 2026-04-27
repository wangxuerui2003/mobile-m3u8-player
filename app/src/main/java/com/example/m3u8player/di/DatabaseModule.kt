package com.example.m3u8player.di

import android.content.Context
import androidx.room.Room
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
    
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "okzyw_data.db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }
    
    @Provides
    fun provideVideoDao(database: AppDatabase): VideoDao {
        return database.videoDao()
    }
}
