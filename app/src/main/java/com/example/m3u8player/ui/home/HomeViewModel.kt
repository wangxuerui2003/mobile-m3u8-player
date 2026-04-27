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

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: VideoRepository
) : ViewModel() {
    
    private val _watchHistory = MutableStateFlow<List<WatchHistoryEntity>>(emptyList())
    val watchHistory: StateFlow<List<WatchHistoryEntity>> = _watchHistory.asStateFlow()
    
    init {
        loadWatchHistory()
    }
    
    private fun loadWatchHistory() {
        viewModelScope.launch {
            repository.getWatchHistory().collect { history ->
                _watchHistory.value = history
            }
        }
    }
}
