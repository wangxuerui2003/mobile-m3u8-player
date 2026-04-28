package com.example.m3u8player.service

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.m3u8player.data.ArtworkCache
import com.example.m3u8player.data.DownloadSettingsStore
import com.example.m3u8player.data.model.DownloadEntity
import com.example.m3u8player.data.model.DownloadStatus
import com.example.m3u8player.data.repository.VideoRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
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
    private val workManager: WorkManager,
    private val settingsStore: DownloadSettingsStore,
    private val artworkCache: ArtworkCache
) {
    private val scheduleMutex = Mutex()

    fun observeConcurrency(): Flow<Int> = settingsStore.concurrency

    suspend fun restoreQueueState() {
        scheduleMutex.withLock {
            val downloads = repository.getDownloadsSnapshot()
            downloads.filter { it.status == DownloadStatus.DOWNLOADING }.forEach { download ->
                if (!isWorkActive(download.workId)) {
                    repository.markDownloadQueued(download.id, resetWork = true)
                }
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
            repository.markDownloadPaused(id)
            cancelWork(download.workId)
            DownloadNotificationHelper.cancel(context, id)
            schedulePendingDownloads()
        }
    }

    suspend fun resumeDownload(id: Long) {
        repository.markDownloadQueued(id, resetWork = true)
        schedulePendingDownloads()
    }

    suspend fun deleteDownload(id: Long) {
        repository.getDownload(id)?.let { download ->
            cancelWork(download.workId)
            DownloadNotificationHelper.cancel(context, id)
            repository.deleteDownload(id)
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
                repository.markDownloadPaused(download.id)
                cancelWork(download.workId)
                DownloadNotificationHelper.cancel(context, download.id)
            }
        }
        schedulePendingDownloads()
    }

    suspend fun deleteDownloads(ids: Set<Long>) {
        if (ids.isEmpty()) return
        repository.getDownloadsByIds(ids.toList()).forEach { download ->
            cancelWork(download.workId)
            DownloadNotificationHelper.cancel(context, download.id)
            repository.deleteDownload(download.id)
        }
        schedulePendingDownloads()
    }

    suspend fun setConcurrency(limit: Int) {
        settingsStore.setConcurrency(limit)
        schedulePendingDownloads()
    }

    suspend fun schedulePendingDownloads() {
        scheduleMutex.withLock {
            val concurrency = settingsStore.concurrency.first()
            val activeDownloads = repository.getDownloadsByStatuses(listOf(DownloadStatus.DOWNLOADING))
            var activeCount = 0

            for (download in activeDownloads) {
                if (isWorkActive(download.workId)) {
                    activeCount++
                } else {
                    repository.markDownloadQueued(download.id, resetWork = true)
                }
            }

            val remainingSlots = (concurrency - activeCount).coerceAtLeast(0)
            if (remainingSlots == 0) return

            repository.getDownloadsByStatuses(listOf(DownloadStatus.QUEUED))
                .take(remainingSlots)
                .forEach { startDownloadWork(it) }
        }
    }

    private suspend fun startDownloadWork(download: DownloadEntity) {
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .addTag(DOWNLOAD_TAG)
            .addTag(downloadTag(download.id))
            .setInputData(
                Data.Builder()
                    .putLong(DownloadWorker.KEY_DOWNLOAD_ID, download.id)
                    .build()
            )
            .build()

        repository.markDownloadEnqueued(download.id, workRequest.id.toString())
        workManager.enqueueUniqueWork(uniqueWorkName(download.id), ExistingWorkPolicy.REPLACE, workRequest)
    }

    private suspend fun isWorkActive(workId: String?): Boolean = withContext(Dispatchers.IO) {
        if (workId.isNullOrBlank()) return@withContext false

        val info = try {
            workManager.getWorkInfoById(UUID.fromString(workId)).get()
        } catch (_: Exception) {
            null
        }

        info?.state in setOf(WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED)
    }

    private fun cancelWork(workId: String?) {
        if (workId.isNullOrBlank()) return
        try {
            workManager.cancelWorkById(UUID.fromString(workId))
        } catch (_: IllegalArgumentException) {
        }
    }

    companion object {
        const val DOWNLOAD_TAG = "video_download"

        fun uniqueWorkName(downloadId: Long): String = "download_work_$downloadId"
        fun downloadTag(downloadId: Long): String = "download_id_$downloadId"
    }
}
