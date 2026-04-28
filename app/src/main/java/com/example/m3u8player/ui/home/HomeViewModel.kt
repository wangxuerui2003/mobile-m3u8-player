package com.example.m3u8player.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m3u8player.data.model.WatchHistoryEntity
import com.example.m3u8player.data.repository.VideoRepository
import com.example.m3u8player.ui.detail.parseEpisodes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecentWatchItem(
    val vodId: Int,
    val vodName: String,
    val episode: Int,
    val episodeName: String,
    val coverUrl: String?,
    val coverPath: String?,
    val progressMs: Long,
    val durationMs: Long
)

data class HomePlaybackRequest(
    val vodId: Int,
    val vodName: String,
    val episode: Int,
    val episodeName: String,
    val coverUrl: String?,
    val startPositionMs: Long,
    val videoUrl: String? = null,
    val localPlaylistPath: String? = null,
    val isPartialDownload: Boolean = false,
    val downloadedDurationMs: Long = 0L,
    val totalDurationMs: Long = 0L
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: VideoRepository
) : ViewModel() {

    private val _watchHistory = MutableStateFlow<List<RecentWatchItem>>(emptyList())
    val watchHistory: StateFlow<List<RecentWatchItem>> = _watchHistory.asStateFlow()

    private val _playbackRequests = MutableSharedFlow<HomePlaybackRequest>(extraBufferCapacity = 1)
    val playbackRequests: SharedFlow<HomePlaybackRequest> = _playbackRequests.asSharedFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.getWatchHistory().collect { history ->
                _watchHistory.value = history.map { it.toRecentWatchItem() }
            }
        }
    }

    fun playRecentWatch(item: RecentWatchItem) {
        viewModelScope.launch {
            val download = repository.findDownloadByVodIdAndEpisode(item.vodId, item.episode)
            if (!download?.localPath.isNullOrBlank()) {
                _playbackRequests.emit(
                    HomePlaybackRequest(
                        vodId = item.vodId,
                        vodName = item.vodName,
                        episode = item.episode,
                        episodeName = item.episodeName,
                        coverUrl = item.coverUrl,
                        startPositionMs = item.progressMs,
                        videoUrl = download?.videoUrl,
                        localPlaylistPath = download?.localPath,
                        isPartialDownload = download?.status != com.example.m3u8player.data.model.DownloadStatus.COMPLETED,
                        downloadedDurationMs = download?.downloadedDurationMs ?: 0L,
                        totalDurationMs = download?.totalDurationMs ?: item.durationMs
                    )
                )
                return@launch
            }

            val detail = repository.getVideoDetail(item.vodId)
            val remoteEpisode = detail
                ?.let { parseEpisodes(it.vodPlayUrl) }
                ?.firstOrNull { episode -> episode.index == item.episode }

            if (remoteEpisode?.url.isNullOrBlank()) {
                _messages.emit("该观看记录没有可恢复的播放地址")
                return@launch
            }

            _playbackRequests.emit(
                HomePlaybackRequest(
                    vodId = item.vodId,
                    vodName = item.vodName,
                    episode = item.episode,
                    episodeName = item.episodeName,
                    coverUrl = item.coverUrl,
                    startPositionMs = item.progressMs,
                    videoUrl = remoteEpisode?.url,
                    totalDurationMs = item.durationMs
                )
            )
        }
    }
}

private fun WatchHistoryEntity.toRecentWatchItem() = RecentWatchItem(
    vodId = vodId,
    vodName = vodName,
    episode = episode,
    episodeName = episodeName,
    coverUrl = coverUrl,
    coverPath = coverPath,
    progressMs = progressMs,
    durationMs = durationMs
)
