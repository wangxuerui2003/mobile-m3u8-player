package com.example.m3u8player.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m3u8player.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: VideoRepository
) : ViewModel() {
    
    private val _videoCount = MutableStateFlow(0)
    val videoCount: StateFlow<Int> = _videoCount.asStateFlow()
    
    private val _lastUpdateTime = MutableStateFlow("")
    val lastUpdateTime: StateFlow<String> = _lastUpdateTime.asStateFlow()
    
    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()
    
    private val _updateProgress = MutableStateFlow(0f)
    val updateProgress: StateFlow<Float> = _updateProgress.asStateFlow()
    
    init {
        loadStats()
    }
    
    private fun loadStats() {
        viewModelScope.launch {
            _videoCount.value = repository.getVideoCount()
            val lastUpdate = repository.getLastUpdateTime()
            _lastUpdateTime.value = lastUpdate?.let { 
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it.toLong()))
            } ?: "从未更新"
        }
    }
    
    fun updateData() {
        viewModelScope.launch {
            _isUpdating.value = true
            val result = repository.fetchAllVideoList(
                onProgress = { current, total ->
                    _updateProgress.value = current.toFloat() / total * 100
                }
            )
            
            _isUpdating.value = false
            if (result.isSuccess) {
                repository.setLastUpdateTime(System.currentTimeMillis().toString())
                repository.setTotalItems(result.getOrNull() ?: 0)
                loadStats()
            }
        }
    }
    
    fun clearCache() {
        viewModelScope.launch {
            // TODO: Implement cache clearing
        }
    }
}
