package com.example.m3u8player.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playerSettingsDataStore by preferencesDataStore(name = "player_settings")

@Singleton
class PlayerSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        const val DEFAULT_SPEED = 1.0f
    }

    val playbackSpeed: Flow<Float> = context.playerSettingsDataStore.data.map { preferences ->
        preferences[PLAYBACK_SPEED] ?: DEFAULT_SPEED
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.playerSettingsDataStore.edit { preferences ->
            preferences[PLAYBACK_SPEED] = speed
        }
    }
}
