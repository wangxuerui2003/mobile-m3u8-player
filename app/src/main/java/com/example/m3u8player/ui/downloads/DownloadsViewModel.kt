package com.example.m3u8player.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m3u8player.data.model.DownloadEntity
import com.example.m3u8player.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: VideoRepository
) : ViewModel() {
    
    private val _downloads = MutableStateFlow<List<DownloadEntity>>(emptyList())
    val downloads: StateFlow<List<DownloadEntity>> = _downloads.asStateFlow()
    
    init {
        loadDownloads()
    }
    
    private fun loadDownloads() {
        viewModelScope.launch {
            repository.getDownloads().collect { list ->
                _downloads.value = list
            }
        }
    }
    
    fun deleteDownload(id: Long) {
        viewModelScope.launch {
            repository.deleteDownload(id)
        }
    }
}
