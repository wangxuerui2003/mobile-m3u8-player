package com.example.m3u8player.data.repository

import com.example.m3u8player.data.api.OkzywApi
import com.example.m3u8player.data.db.VideoDao
import com.example.m3u8player.data.model.Category
import com.example.m3u8player.data.model.CategoryEntity
import com.example.m3u8player.data.model.DownloadEntity
import com.example.m3u8player.data.model.DownloadStatus
import com.example.m3u8player.data.model.MetadataEntity
import com.example.m3u8player.data.model.VideoDetail
import com.example.m3u8player.data.model.VideoItem
import com.example.m3u8player.data.model.VideoListEntity
import com.example.m3u8player.data.model.WatchHistoryEntity
import com.example.m3u8player.data.model.WatchLaterEntity
import com.example.m3u8player.data.model.WatchLaterVideo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepository @Inject constructor(
    private val api: OkzywApi,
    private val dao: VideoDao
) {

    fun getVideoList(page: Int = 1, pageSize: Int = 20): Flow<List<VideoItem>> {
        val offset = (page - 1) * pageSize
        return dao.getVideoList(limit = pageSize, offset = offset).map { entities ->
            entities.map { it.toVideoItem() }
        }
    }

    fun searchVideos(query: String): Flow<List<VideoListEntity>> {
        return dao.searchVideos(query)
    }

    fun getVideosByType(typeId: Int, page: Int = 1, pageSize: Int = 20): Flow<List<VideoItem>> {
        val offset = (page - 1) * pageSize
        return dao.getVideosByType(typeId, limit = pageSize, offset = offset).map { entities ->
            entities.map { it.toVideoItem() }
        }
    }

    suspend fun getVideoDetail(vodId: Int): VideoDetail? {
        return try {
            val response = api.getVideoDetail(vodId = vodId)
            if (response.code == 1 && response.list.isNotEmpty()) {
                response.list.first()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getCategories(): Flow<List<Category>> {
        return dao.getCategories().map { entities ->
            entities.map { it.toCategory() }
        }
    }

    suspend fun fetchAllVideoList(
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<Int> {
        return try {
            val firstPage = retry { api.getVideoList(page = 1) }
            val totalPages = firstPage.pageCount

            dao.insertCategories(firstPage.categories.map { it.toEntity() })

            var newCount = insertNewVideos(firstPage.list)
            onProgress(1, totalPages)

            var stoppedEarly = false
            for (page in 2..totalPages) {
                delay(800)
                val pageData = retry { api.getVideoList(page = page) }
                val pageNewCount = insertNewVideos(pageData.list)
                newCount += pageNewCount
                onProgress(page, totalPages)

                if (pageNewCount == 0 && pageData.list.isNotEmpty()) {
                    stoppedEarly = true
                    break
                }
            }

            val totalInDb = dao.getVideoCount()
            if (stoppedEarly) {
                onProgress(totalPages, totalPages)
            }

            dao.setMetadata(MetadataEntity("last_update", System.currentTimeMillis().toString()))
            dao.setMetadata(MetadataEntity("total_items", totalInDb.toString()))

            Result.success(totalInDb)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun insertNewVideos(videos: List<VideoItem>): Int {
        var newCount = 0
        val entities = mutableListOf<VideoListEntity>()
        for (video in videos) {
            if (!dao.videoExists(video.vodId)) {
                newCount++
            }
            entities.add(video.toEntity())
        }
        if (entities.isNotEmpty()) {
            dao.insertVideos(entities)
        }
        return newCount
    }

    private suspend fun <T> retry(
        maxRetries: Int = 3,
        initialDelayMs: Long = 2000,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                delay(initialDelayMs * (attempt + 1))
            }
        }
        throw lastException!!
    }

    suspend fun getVideoCount(): Int = dao.getVideoCount()

    fun observeVideoCount(): Flow<Int> = dao.observeVideoCount()

    fun getWatchHistory(): Flow<List<WatchHistoryEntity>> = dao.getWatchHistory()

    suspend fun saveWatchHistory(history: WatchHistoryEntity) {
        dao.insertWatchHistory(history)
    }

    suspend fun deleteWatchHistory(vodId: Int) {
        dao.deleteWatchHistory(vodId)
    }

    fun getWatchLater(): Flow<List<WatchLaterEntity>> = dao.getWatchLater()

    suspend fun addToWatchLater(vodId: Int) {
        dao.insertWatchLater(WatchLaterEntity(vodId = vodId))
    }

    suspend fun removeFromWatchLater(vodId: Int) {
        dao.deleteWatchLater(vodId)
    }

    suspend fun isWatchLater(vodId: Int): Boolean = dao.isWatchLater(vodId)

    fun getWatchLaterVideos(): Flow<List<WatchLaterVideo>> = dao.getWatchLaterVideos()

    fun getDownloads(): Flow<List<DownloadEntity>> = dao.getDownloads()

    suspend fun getDownloadsSnapshot(): List<DownloadEntity> = dao.getDownloadsSnapshot()

    suspend fun getDownloadsByStatuses(statuses: List<String>): List<DownloadEntity> {
        return dao.getDownloadsByStatuses(statuses)
    }

    suspend fun getDownloadsByIds(ids: List<Long>): List<DownloadEntity> {
        if (ids.isEmpty()) return emptyList()
        return dao.getDownloadsByIds(ids)
    }

    suspend fun addDownload(download: DownloadEntity): Long = dao.insertDownload(download)

    suspend fun updateDownload(download: DownloadEntity) {
        dao.updateDownload(download)
    }

    suspend fun getDownload(id: Long): DownloadEntity? = dao.getDownloadById(id)

    suspend fun findDownloadByVodIdAndEpisode(vodId: Int, episode: Int): DownloadEntity? {
        return dao.getDownloadByVodIdAndEpisode(vodId, episode)
    }

    suspend fun markDownloadEnqueued(id: Long, workId: String) {
        dao.getDownloadById(id)?.let { download ->
            dao.updateDownload(
                download.copy(
                    status = DownloadStatus.DOWNLOADING,
                    workId = workId,
                    speedBytesPerSec = 0,
                    updatedAt = System.currentTimeMillis(),
                    completedAt = null
                )
            )
        }
    }

    suspend fun markDownloadQueued(id: Long, resetWork: Boolean = false) {
        dao.getDownloadById(id)?.let { download ->
            dao.updateDownload(
                download.copy(
                    status = DownloadStatus.QUEUED,
                    workId = if (resetWork) null else download.workId,
                    speedBytesPerSec = 0,
                    updatedAt = System.currentTimeMillis(),
                    completedAt = null
                )
            )
        }
    }

    suspend fun markDownloadRunning(
        id: Long,
        downloadDir: String,
        totalSegments: Int,
        totalBytes: Long = 0
    ) {
        dao.getDownloadById(id)?.let { download ->
            dao.updateDownload(
                download.copy(
                    status = DownloadStatus.DOWNLOADING,
                    downloadDir = downloadDir,
                    totalSegments = totalSegments,
                    totalBytes = totalBytes,
                    speedBytesPerSec = 0,
                    updatedAt = System.currentTimeMillis(),
                    completedAt = null
                )
            )
        }
    }

    suspend fun markDownloadPaused(id: Long) {
        dao.getDownloadById(id)?.let { download ->
            dao.updateDownload(
                download.copy(
                    status = DownloadStatus.PAUSED,
                    workId = null,
                    speedBytesPerSec = 0,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun markDownloadFailed(id: Long) {
        dao.getDownloadById(id)?.let { download ->
            dao.updateDownload(
                download.copy(
                    status = DownloadStatus.FAILED,
                    workId = null,
                    speedBytesPerSec = 0,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun markDownloadCompleted(
        id: Long,
        localPath: String,
        totalBytes: Long,
        downloadedBytes: Long,
        downloadedSegments: Int,
        totalSegments: Int
    ) {
        dao.getDownloadById(id)?.let { download ->
            dao.updateDownload(
                download.copy(
                    status = DownloadStatus.COMPLETED,
                    workId = null,
                    localPath = localPath,
                    totalBytes = totalBytes,
                    downloadedBytes = downloadedBytes,
                    downloadedSegments = downloadedSegments,
                    totalSegments = totalSegments,
                    progress = 100f,
                    speedBytesPerSec = 0,
                    updatedAt = System.currentTimeMillis(),
                    completedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun updateDownloadProgress(
        id: Long,
        progress: Float,
        downloadedBytes: Long,
        totalBytes: Long,
        downloadedSegments: Int,
        totalSegments: Int,
        speedBytesPerSec: Long
    ) {
        dao.getDownloadById(id)?.let { download ->
            dao.updateDownload(
                download.copy(
                    status = DownloadStatus.DOWNLOADING,
                    progress = progress.coerceIn(0f, 100f),
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    downloadedSegments = downloadedSegments,
                    totalSegments = totalSegments,
                    speedBytesPerSec = speedBytesPerSec,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun updateDownloadCoverPath(id: Long, coverPath: String?) {
        dao.getDownloadById(id)?.let { download ->
            dao.updateDownload(download.copy(coverPath = coverPath, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun deleteDownload(id: Long) {
        dao.getDownloadById(id)?.let { download ->
            deleteDownloadedAsset(download)
        }
        dao.deleteDownload(id)
    }

    suspend fun deleteDownloadByVodId(vodId: Int) {
        dao.deleteDownloadByVodId(vodId)
    }

    suspend fun getLastUpdateTime(): String? = dao.getMetadata("last_update")

    suspend fun setLastUpdateTime(time: String) {
        dao.setMetadata(MetadataEntity("last_update", time))
    }

    suspend fun getTotalItems(): Int = dao.getMetadata("total_items")?.toIntOrNull() ?: 0

    suspend fun setTotalItems(count: Int) {
        dao.setMetadata(MetadataEntity("total_items", count.toString()))
    }

    suspend fun getDownloadedCount(): Int = dao.getDownloads().first().size

    suspend fun getWatchDurationMs(): Long = dao.getWatchHistory().first().sumOf { it.progressMs }
}

private fun deleteDownloadedAsset(download: DownloadEntity) {
    val directory = download.downloadDir?.let(::File)
    if (directory?.exists() == true) {
        directory.deleteRecursively()
        return
    }

    download.localPath?.let { localPath ->
        val localFile = File(localPath)
        val targetDir = localFile.parentFile
        if (targetDir?.exists() == true) {
            targetDir.deleteRecursively()
        } else {
            if (localFile.exists()) {
                localFile.delete()
            }
        }
        Unit
    }
}

private fun VideoListEntity.toVideoItem() = VideoItem(
    vodId = vodId,
    vodName = vodName,
    typeId = typeId,
    typeName = typeName,
    vodEn = vodEn,
    vodTime = vodTime,
    vodRemarks = vodRemarks,
    vodPlayFrom = vodPlayFrom
)

private fun VideoItem.toEntity() = VideoListEntity(
    vodId = vodId,
    vodName = vodName,
    typeId = typeId,
    typeName = typeName,
    vodEn = vodEn,
    vodTime = vodTime,
    vodRemarks = vodRemarks,
    vodPlayFrom = vodPlayFrom
)

private fun Category.toEntity() = CategoryEntity(
    typeId = typeId,
    typeName = typeName
)

private fun CategoryEntity.toCategory() = Category(
    typeId = typeId,
    typeName = typeName
)
