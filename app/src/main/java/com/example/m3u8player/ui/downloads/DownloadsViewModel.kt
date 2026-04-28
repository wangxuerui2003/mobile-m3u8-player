package com.example.m3u8player.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m3u8player.data.model.DownloadEntity
import com.example.m3u8player.data.repository.VideoRepository
import com.example.m3u8player.service.DownloadCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val downloadCoordinator: DownloadCoordinator
) : ViewModel() {

    private val _downloads = MutableStateFlow<List<DownloadEntity>>(emptyList())
    val downloads: StateFlow<List<DownloadEntity>> = _downloads.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    private val _concurrency = MutableStateFlow(1)
    val concurrency: StateFlow<Int> = _concurrency.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getDownloads().collect { list ->
                _downloads.value = list
                _selectedIds.value = _selectedIds.value.intersect(list.map { it.id }.toSet())
            }
        }

        viewModelScope.launch {
            downloadCoordinator.observeConcurrency().collect { limit ->
                _concurrency.value = limit
            }
        }
    }

    fun isSelectionMode(): Boolean = _selectedIds.value.isNotEmpty()

    fun toggleSelection(id: Long) {
        _selectedIds.value = if (id in _selectedIds.value) {
            _selectedIds.value - id
        } else {
            _selectedIds.value + id
        }
    }

    fun enterSelection(id: Long) {
        _selectedIds.value = _selectedIds.value + id
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun toggleSelectAll() {
        _selectedIds.value = if (_selectedIds.value.size == _downloads.value.size) {
            emptySet()
        } else {
            _downloads.value.map { it.id }.toSet()
        }
    }

    fun startDownload(id: Long) {
        viewModelScope.launch {
            downloadCoordinator.resumeDownload(id)
        }
    }

    fun pauseDownload(id: Long) {
        viewModelScope.launch {
            downloadCoordinator.pauseDownload(id)
        }
    }

    fun deleteDownload(id: Long) {
        viewModelScope.launch {
            downloadCoordinator.deleteDownload(id)
        }
    }

    fun deleteDownloads(ids: Set<Long>) {
        viewModelScope.launch {
            downloadCoordinator.deleteDownloads(ids)
            if (_selectedIds.value.intersect(ids).isNotEmpty()) {
                _selectedIds.value = _selectedIds.value - ids
            }
        }
    }

    fun startSelectedDownloads() {
        viewModelScope.launch {
            downloadCoordinator.startDownloads(_selectedIds.value)
            clearSelection()
        }
    }

    fun pauseSelectedDownloads() {
        viewModelScope.launch {
            downloadCoordinator.pauseDownloads(_selectedIds.value)
            clearSelection()
        }
    }

    fun deleteSelectedDownloads() {
        viewModelScope.launch {
            downloadCoordinator.deleteDownloads(_selectedIds.value)
            clearSelection()
        }
    }

    fun setConcurrency(limit: Int) {
        viewModelScope.launch {
            downloadCoordinator.setConcurrency(limit)
        }
    }
}
