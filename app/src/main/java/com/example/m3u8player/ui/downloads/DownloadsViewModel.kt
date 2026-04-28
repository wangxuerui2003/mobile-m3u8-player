package com.example.m3u8player.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m3u8player.data.model.DownloadEntity
import com.example.m3u8player.data.model.DownloadStatus
import com.example.m3u8player.data.repository.VideoRepository
import com.example.m3u8player.service.DownloadCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadGroup(
    val vodId: Int,
    val vodName: String,
    val coverUrl: String?,
    val coverPath: String?,
    val episodes: List<DownloadEntity>,
    val episodeCount: Int,
    val completedCount: Int,
    val downloadingCount: Int,
    val pausedCount: Int,
    val failedCount: Int,
    val aggregateProgress: Float,
    val aggregateSpeedBytesPerSec: Long
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val downloadCoordinator: DownloadCoordinator
) : ViewModel() {

    private val rawDownloads = MutableStateFlow<List<DownloadEntity>>(emptyList())

    private val _downloadGroups = MutableStateFlow<List<DownloadGroup>>(emptyList())
    val downloadGroups: StateFlow<List<DownloadGroup>> = _downloadGroups.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    private val _expandedVodIds = MutableStateFlow<Set<Int>>(emptySet())
    val expandedVodIds: StateFlow<Set<Int>> = _expandedVodIds.asStateFlow()

    private val _concurrency = MutableStateFlow(1)
    val concurrency: StateFlow<Int> = _concurrency.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getDownloads().collect { list ->
                rawDownloads.value = list
                _downloadGroups.value = buildDownloadGroups(list)
                _selectedIds.value = _selectedIds.value.intersect(list.map { it.id }.toSet())
                _expandedVodIds.value = _expandedVodIds.value.intersect(list.map { it.vodId }.toSet())
            }
        }

        viewModelScope.launch {
            downloadCoordinator.observeConcurrency().collect { limit ->
                _concurrency.value = limit
            }
        }
    }

    fun toggleExpanded(vodId: Int) {
        _expandedVodIds.value = if (vodId in _expandedVodIds.value) {
            _expandedVodIds.value - vodId
        } else {
            _expandedVodIds.value + vodId
        }
    }

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
        val allIds = rawDownloads.value.map { it.id }.toSet()
        _selectedIds.value = if (_selectedIds.value.size == allIds.size) emptySet() else allIds
    }

    fun toggleSelectGroup(vodId: Int) {
        val groupIds = rawDownloads.value.filter { it.vodId == vodId }.map { it.id }.toSet()
        if (groupIds.isEmpty()) return
        _selectedIds.value = if (groupIds.all { it in _selectedIds.value }) {
            _selectedIds.value - groupIds
        } else {
            _selectedIds.value + groupIds
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
            _selectedIds.value = _selectedIds.value - ids
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

    fun startGroupDownloads(vodId: Int) {
        viewModelScope.launch {
            downloadCoordinator.startDownloads(groupIds(vodId))
        }
    }

    fun pauseGroupDownloads(vodId: Int) {
        viewModelScope.launch {
            downloadCoordinator.pauseDownloads(groupIds(vodId))
        }
    }

    fun deleteGroupDownloads(vodId: Int) {
        viewModelScope.launch {
            val ids = groupIds(vodId)
            downloadCoordinator.deleteDownloads(ids)
            _selectedIds.value = _selectedIds.value - ids
        }
    }

    fun setConcurrency(limit: Int) {
        viewModelScope.launch {
            downloadCoordinator.setConcurrency(limit)
        }
    }

    private fun groupIds(vodId: Int): Set<Long> {
        return rawDownloads.value.filter { it.vodId == vodId }.map { it.id }.toSet()
    }
}

private fun buildDownloadGroups(downloads: List<DownloadEntity>): List<DownloadGroup> {
    return downloads
        .groupBy { it.vodId }
        .map { (_, groupDownloads) ->
            val sortedEpisodes = groupDownloads.sortedBy { it.episode }
            val lead = sortedEpisodes.first()
            DownloadGroup(
                vodId = lead.vodId,
                vodName = lead.vodName,
                coverUrl = lead.coverUrl,
                coverPath = lead.coverPath,
                episodes = sortedEpisodes,
                episodeCount = sortedEpisodes.size,
                completedCount = sortedEpisodes.count { it.status == DownloadStatus.COMPLETED },
                downloadingCount = sortedEpisodes.count { it.status == DownloadStatus.DOWNLOADING },
                pausedCount = sortedEpisodes.count { it.status == DownloadStatus.PAUSED },
                failedCount = sortedEpisodes.count { it.status == DownloadStatus.FAILED },
                aggregateProgress = sortedEpisodes.map { it.progress }.average().toFloat(),
                aggregateSpeedBytesPerSec = sortedEpisodes.sumOf { it.speedBytesPerSec }
            )
        }
        .sortedByDescending { group -> group.episodes.maxOfOrNull { it.updatedAt } ?: 0L }
}
