package com.example.m3u8player.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    // VideoRepository is provided automatically by Hilt via @Inject constructor
    // No explicit @Provides needed - that would create a dependency cycle
}
