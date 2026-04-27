package com.example.m3u8player

import android.app.Application
import com.example.m3u8player.data.repository.VideoRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class UpdateState(val isUpdating: Boolean = false, val progress: Float = 0f)

@HiltAndroidApp
class M3U8PlayerApplication : Application() {

    @Inject
    lateinit var videoRepository: VideoRepository

    private val _updateState = MutableStateFlow(UpdateState())
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    fun setUpdateState(isUpdating: Boolean, progress: Float) {
        _updateState.value = UpdateState(isUpdating, progress)
    }

    override fun onCreate() {
        super.onCreate()
    }
}
