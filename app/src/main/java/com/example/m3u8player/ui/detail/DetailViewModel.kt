package com.example.m3u8player.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m3u8player.data.model.DownloadEntity
import com.example.m3u8player.data.model.VideoDetail
import com.example.m3u8player.data.repository.VideoRepository
import com.example.m3u8player.service.DownloadCoordinator
import com.example.m3u8player.service.DownloadEnqueueResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Episode(
    val index: Int,
    val name: String,
    val url: String
)

fun parseEpisodes(playUrl: String): List<Episode> {
    if (playUrl.isEmpty()) return emptyList()

    return playUrl.split("#").mapIndexed { index, part ->
        val parts = part.split("$")
        if (parts.size >= 2) {
            Episode(index, parts[0], parts[1])
        } else {
            Episode(index, "第${index + 1}集", part)
        }
    }
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val downloadCoordinator: DownloadCoordinator
) : ViewModel() {

    private val _videoDetail = MutableStateFlow<VideoDetail?>(null)
    val videoDetail: StateFlow<VideoDetail?> = _videoDetail.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isWatchLater = MutableStateFlow(false)
    val isWatchLater: StateFlow<Boolean> = _isWatchLater.asStateFlow()

    private val _episodeDownloads = MutableStateFlow<Map<Int, DownloadEntity>>(emptyMap())
    val episodeDownloads: StateFlow<Map<Int, DownloadEntity>> = _episodeDownloads.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var downloadsJob: Job? = null

    fun loadVideoDetail(vodId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _videoDetail.value = repository.getVideoDetail(vodId)
            _isLoading.value = false
        }
        observeDownloads(vodId)
    }

    fun checkWatchLater(vodId: Int) {
        viewModelScope.launch {
            _isWatchLater.value = repository.isWatchLater(vodId)
        }
    }

    fun toggleWatchLater(vodId: Int) {
        viewModelScope.launch {
            if (_isWatchLater.value) {
                repository.removeFromWatchLater(vodId)
            } else {
                repository.addToWatchLater(vodId)
            }
            _isWatchLater.value = !_isWatchLater.value
        }
    }

    fun startDownload(vodId: Int, detail: VideoDetail, episode: Episode) {
        viewModelScope.launch {
            if (episode.url.isBlank()) {
                _messages.emit("当前视频没有可下载的播放地址")
                return@launch
            }

            when (
                downloadCoordinator.enqueueDownload(
                    vodId = vodId,
                    vodName = detail.vodName,
                    episode = episode.index,
                    episodeName = episode.name,
                    videoUrl = episode.url,
                    coverUrl = detail.vodPic
                )
            ) {
                is DownloadEnqueueResult.Added -> _messages.emit("${episode.name} 已加入下载队列")
                is DownloadEnqueueResult.AlreadyExists -> _messages.emit("${episode.name} 已在下载列表中")
            }
        }
    }

    fun startDownloads(vodId: Int, detail: VideoDetail, episodes: List<Episode>) {
        viewModelScope.launch {
            if (episodes.isEmpty()) {
                _messages.emit("请先选择要下载的剧集")
                return@launch
            }

            var addedCount = 0
            var existingCount = 0
            episodes.forEach { episode ->
                if (episode.url.isBlank()) return@forEach
                when (
                    downloadCoordinator.enqueueDownload(
                        vodId = vodId,
                        vodName = detail.vodName,
                        episode = episode.index,
                        episodeName = episode.name,
                        videoUrl = episode.url,
                        coverUrl = detail.vodPic
                    )
                ) {
                    is DownloadEnqueueResult.Added -> addedCount++
                    is DownloadEnqueueResult.AlreadyExists -> existingCount++
                }
            }

            when {
                addedCount > 0 && existingCount > 0 -> _messages.emit("已加入 $addedCount 集，$existingCount 集已存在")
                addedCount > 0 -> _messages.emit("已加入 $addedCount 集到下载队列")
                existingCount > 0 -> _messages.emit("所选剧集已在下载列表中")
                else -> _messages.emit("没有可下载的剧集")
            }
        }
    }

    private fun observeDownloads(vodId: Int) {
        downloadsJob?.cancel()
        downloadsJob = viewModelScope.launch {
            repository.getDownloadsByVodId(vodId).collect { downloads ->
                _episodeDownloads.value = downloads.associateBy { it.episode }
            }
        }
    }
}
