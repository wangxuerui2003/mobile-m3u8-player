package com.example.m3u8player.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.downloadSettingsDataStore by preferencesDataStore(name = "download_settings")

@Singleton
class DownloadSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val DOWNLOAD_CONCURRENCY = intPreferencesKey("download_concurrency")
        const val MIN_CONCURRENCY = 1
        const val MAX_CONCURRENCY = 3
        const val DEFAULT_CONCURRENCY = 1
    }

    val concurrency: Flow<Int> = context.downloadSettingsDataStore.data.map { preferences ->
        (preferences[DOWNLOAD_CONCURRENCY] ?: DEFAULT_CONCURRENCY).coerceIn(MIN_CONCURRENCY, MAX_CONCURRENCY)
    }

    suspend fun setConcurrency(limit: Int) {
        context.downloadSettingsDataStore.edit { preferences ->
            preferences[DOWNLOAD_CONCURRENCY] = limit.coerceIn(MIN_CONCURRENCY, MAX_CONCURRENCY)
        }
    }
}
