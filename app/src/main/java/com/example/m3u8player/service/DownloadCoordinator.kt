package com.example.m3u8player.service

import android.content.Context
import com.example.m3u8player.data.ArtworkCache
import com.example.m3u8player.data.DownloadSettingsStore
import com.example.m3u8player.data.model.DownloadEntity
import com.example.m3u8player.data.model.DownloadStatus
import com.example.m3u8player.data.repository.VideoRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

sealed class DownloadEnqueueResult {
    data class Added(val downloadId: Long) : DownloadEnqueueResult()
    data class AlreadyExists(val downloadId: Long) : DownloadEnqueueResult()
}

@Singleton
class DownloadCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: VideoRepository,
    private val settingsStore: DownloadSettingsStore,
    private val artworkCache: ArtworkCache
) {
    private val scheduleMutex = Mutex()

    fun observeConcurrency(): Flow<Int> = settingsStore.concurrency

    suspend fun restoreQueueState() {
        scheduleMutex.withLock {
            val downloads = repository.getDownloadsSnapshot()
            downloads.filter { it.status == DownloadStatus.DOWNLOADING }.forEach { download ->
                repository.markDownloadQueued(download.id, resetWork = true)
            }
        }
        schedulePendingDownloads()
    }

    suspend fun enqueueDownload(
        vodId: Int,
        vodName: String,
        episode: Int,
        episodeName: String,
        videoUrl: String,
        coverUrl: String?
    ): DownloadEnqueueResult {
        repository.findDownloadByVodIdAndEpisode(vodId, episode)?.let { existing ->
            return DownloadEnqueueResult.AlreadyExists(existing.id)
        }

        val coverPath = artworkCache.cacheArtwork("vod_$vodId", coverUrl)
        val download = DownloadEntity(
            vodId = vodId,
            vodName = vodName,
            episode = episode,
            episodeName = episodeName,
            videoUrl = videoUrl,
            coverUrl = coverUrl,
            coverPath = coverPath,
            status = DownloadStatus.QUEUED
        )
        val id = repository.addDownload(download)
        schedulePendingDownloads()
        return DownloadEnqueueResult.Added(id)
    }

    suspend fun pauseDownload(id: Long) {
        repository.getDownload(id)?.let { download ->
            if (download.status in setOf(DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED)) {
                DownloadForegroundService.pause(context, id)
            } else {
                repository.markDownloadPaused(id)
                DownloadNotificationHelper.cancel(context, id)
            }
            schedulePendingDownloads()
        }
    }

    suspend fun resumeDownload(id: Long) {
        repository.markDownloadQueued(id, resetWork = true)
        schedulePendingDownloads()
    }

    suspend fun deleteDownload(id: Long) {
        repository.getDownload(id)?.let {
            DownloadForegroundService.delete(context, id)
            schedulePendingDownloads()
        }
    }

    suspend fun startDownloads(ids: Set<Long>) {
        if (ids.isEmpty()) return
        repository.getDownloadsByIds(ids.toList()).forEach { download ->
            if (download.status in setOf(DownloadStatus.QUEUED, DownloadStatus.PAUSED, DownloadStatus.FAILED)) {
                repository.markDownloadQueued(download.id, resetWork = true)
            }
        }
        schedulePendingDownloads()
    }

    suspend fun pauseDownloads(ids: Set<Long>) {
        if (ids.isEmpty()) return
        repository.getDownloadsByIds(ids.toList()).forEach { download ->
            if (download.status in setOf(DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED)) {
                DownloadForegroundService.pause(context, download.id)
            }
        }
        schedulePendingDownloads()
    }

    suspend fun deleteDownloads(ids: Set<Long>) {
        if (ids.isEmpty()) return
        repository.getDownloadsByIds(ids.toList()).forEach { download ->
            DownloadForegroundService.delete(context, download.id)
        }
        schedulePendingDownloads()
    }

    suspend fun setConcurrency(limit: Int) {
        settingsStore.setConcurrency(limit)
        schedulePendingDownloads()
    }

    suspend fun schedulePendingDownloads() {
        scheduleMutex.withLock {
            val queued = repository.getDownloadsByStatuses(listOf(DownloadStatus.QUEUED))
            val active = repository.getDownloadsByStatuses(listOf(DownloadStatus.DOWNLOADING))
            if (queued.isNotEmpty() || active.isNotEmpty()) {
                DownloadForegroundService.start(context)
            }
        }
    }
}
