package com.example.m3u8player.ui.profile

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m3u8player.M3U8PlayerApplication
import com.example.m3u8player.data.repository.VideoRepository
import com.example.m3u8player.service.DataUpdateService
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
    private val repository: VideoRepository,
    private val application: Application
) : ViewModel() {

    private val app get() = application as M3U8PlayerApplication

    private val _videoCount = MutableStateFlow(0)
    val videoCount: StateFlow<Int> = _videoCount.asStateFlow()

    private val _lastUpdateTime = MutableStateFlow("")
    val lastUpdateTime: StateFlow<String> = _lastUpdateTime.asStateFlow()

    val updateState get() = app.updateState

    init {
        loadStats()
        // Reload stats when update finishes
        viewModelScope.launch {
            app.updateState.collect { state ->
                if (!state.isUpdating && state.progress > 0f) {
                    loadStats()
                }
            }
        }
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
        DataUpdateService.start(application)
    }

    fun cancelUpdate() {
        DataUpdateService.stop(application)
    }

    fun clearCache() {
        viewModelScope.launch {
            // TODO: Implement cache clearing
        }
    }
}
