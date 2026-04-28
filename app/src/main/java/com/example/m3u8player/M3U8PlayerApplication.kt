package com.example.m3u8player

import android.app.Application
import com.example.m3u8player.data.ArtworkCache
import com.example.m3u8player.data.PresetDataLoader
import com.example.m3u8player.data.repository.VideoRepository
import com.example.m3u8player.service.DownloadCoordinator
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UpdateState(val isUpdating: Boolean = false, val progress: Float = 0f)

@HiltAndroidApp
class M3U8PlayerApplication : Application() {

    @Inject
    lateinit var videoRepository: VideoRepository

    @Inject
    lateinit var presetDataLoader: PresetDataLoader

    @Inject
    lateinit var downloadCoordinator: DownloadCoordinator

    @Inject
    lateinit var artworkCache: ArtworkCache

    private val _updateState = MutableStateFlow(UpdateState())
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun setUpdateState(isUpdating: Boolean, progress: Float) {
        _updateState.value = UpdateState(isUpdating, progress)
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            presetDataLoader.loadIfNeeded()
        }
        applicationScope.launch {
            downloadCoordinator.restoreQueueState()
        }
    }
}
