package com.example.m3u8player.ui.detail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m3u8player.data.model.VideoDetail
import com.example.m3u8player.data.model.DownloadEntity
import com.example.m3u8player.data.repository.VideoRepository
import com.example.m3u8player.service.DownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Data
import androidx.work.WorkManager
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
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _videoDetail = MutableStateFlow<VideoDetail?>(null)
    val videoDetail: StateFlow<VideoDetail?> = _videoDetail.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _isWatchLater = MutableStateFlow(false)
    val isWatchLater: StateFlow<Boolean> = _isWatchLater.asStateFlow()
    
    fun loadVideoDetail(vodId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            val detail = repository.getVideoDetail(vodId)
            _videoDetail.value = detail
            _isLoading.value = false
        }
    }
    
    fun checkWatchLater(vodId: Int) {
        viewModelScope.launch {
            _isWatchLater.value = false
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
    
    fun startDownload(vodId: Int, detail: VideoDetail) {
        viewModelScope.launch {
            val episodes = parseEpisodes(detail.vodPlayUrl)
            val episode = episodes.firstOrNull() ?: return@launch
            
            val download = DownloadEntity(
                vodId = vodId,
                vodName = detail.vodName,
                episode = episode.index,
                status = "downloading"
            )
            repository.addDownload(download)
            
            val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(
                    Data.Builder()
                        .putInt("vodId", vodId)
                        .putString("vodName", detail.vodName)
                        .putInt("episode", episode.index)
                        .putString("videoUrl", episode.url)
                        .build()
                )
                .build()
            
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
