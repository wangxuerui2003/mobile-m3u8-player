package com.example.m3u8player.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m3u8player.data.model.WatchHistoryEntity
import com.example.m3u8player.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecentWatchItem(
    val vodId: Int,
    val vodName: String,
    val episodeName: String,
    val coverUrl: String?,
    val coverPath: String?,
    val progressMs: Long,
    val durationMs: Long
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: VideoRepository
) : ViewModel() {

    private val _watchHistory = MutableStateFlow<List<RecentWatchItem>>(emptyList())
    val watchHistory: StateFlow<List<RecentWatchItem>> = _watchHistory.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getWatchHistory().collect { history ->
                _watchHistory.value = history.map { it.toRecentWatchItem() }
            }
        }
    }
}

private fun WatchHistoryEntity.toRecentWatchItem() = RecentWatchItem(
    vodId = vodId,
    vodName = vodName,
    episodeName = episodeName,
    coverUrl = coverUrl,
    coverPath = coverPath,
    progressMs = progressMs,
    durationMs = durationMs
)
