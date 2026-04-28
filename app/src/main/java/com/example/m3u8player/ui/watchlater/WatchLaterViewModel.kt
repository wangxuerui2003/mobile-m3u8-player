package com.example.m3u8player.ui.watchlater

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m3u8player.data.model.WatchLaterVideo
import com.example.m3u8player.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WatchLaterViewModel @Inject constructor(
    private val repository: VideoRepository
) : ViewModel() {
    
    private val _watchLater = MutableStateFlow<List<WatchLaterVideo>>(emptyList())
    val watchLater: StateFlow<List<WatchLaterVideo>> = _watchLater.asStateFlow()
    
    init {
        loadWatchLater()
    }
    
    private fun loadWatchLater() {
        viewModelScope.launch {
            repository.getWatchLaterVideos().collect { list ->
                _watchLater.value = list
            }
        }
    }
    
    fun deleteFromWatchLater(vodId: Int) {
        viewModelScope.launch {
            repository.removeFromWatchLater(vodId)
        }
    }
}
