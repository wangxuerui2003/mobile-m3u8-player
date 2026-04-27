package com.example.m3u8player.data.repository

import com.example.m3u8player.data.api.OkzywApi
import com.example.m3u8player.data.db.VideoDao
import com.example.m3u8player.data.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepository @Inject constructor(
    private val api: OkzywApi,
    private val dao: VideoDao
) {
    
    // Get video list from local database
    fun getVideoList(page: Int = 1, pageSize: Int = 20): Flow<List<VideoItem>> {
        val offset = (page - 1) * pageSize
        return dao.getVideoList(limit = pageSize, offset = offset).map { entities ->
            entities.map { it.toVideoItem() }
        }
    }
    
    // Search videos locally
    fun searchVideos(query: String): Flow<List<VideoListEntity>> {
        return dao.searchVideos(query)
    }
    
    // Get videos by type
    fun getVideosByType(typeId: Int, page: Int = 1, pageSize: Int = 20): Flow<List<VideoItem>> {
        val offset = (page - 1) * pageSize
        return dao.getVideosByType(typeId, limit = pageSize, offset = offset).map { entities ->
            entities.map { it.toVideoItem() }
        }
    }
    
    // Get video detail from API
    suspend fun getVideoDetail(vodId: Int): VideoDetail? {
        return try {
            val response = api.getVideoDetail(vodId = vodId)
            if (response.code == 1 && response.list.isNotEmpty()) {
                response.list.first()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    // Get categories
    fun getCategories(): Flow<List<Category>> {
        return dao.getCategories().map { entities ->
            entities.map { it.toCategory() }
        }
    }
    
    // Incremental update: fetch from page 1, stop when we hit existing data
    suspend fun fetchAllVideoList(
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<Int> {
        return try {
            // Fetch first page to get total pages and categories
            val firstPage = retry { api.getVideoList(page = 1) }
            val totalPages = firstPage.pageCount

            // Always update categories (they may change)
            dao.insertCategories(firstPage.categories.map { it.toEntity() })

            // Process first page: insert new/updated videos
            var newCount = insertNewVideos(firstPage.list)
            onProgress(1, totalPages)

            // Fetch remaining pages, stop early if we find all videos already exist
            var stoppedEarly = false
            for (page in 2..totalPages) {
                delay(800)
                val pageData = retry { api.getVideoList(page = page) }
                val pageNewCount = insertNewVideos(pageData.list)
                newCount += pageNewCount
                onProgress(page, totalPages)

                // If no new videos on this page, all subsequent pages are also old
                if (pageNewCount == 0 && pageData.list.isNotEmpty()) {
                    stoppedEarly = true
                    break
                }
            }

            val totalInDb = dao.getVideoCount()
            if (stoppedEarly) {
                // Fill progress to 100% since we stopped early
                onProgress(totalPages, totalPages)
            }

            dao.setMetadata(MetadataEntity("last_update", System.currentTimeMillis().toString()))
            dao.setMetadata(MetadataEntity("total_items", totalInDb.toString()))

            Result.success(totalInDb)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Insert only new or updated videos, return count of new ones
    private suspend fun insertNewVideos(videos: List<VideoItem>): Int {
        var newCount = 0
        val entities = mutableListOf<VideoListEntity>()
        for (video in videos) {
            val exists = dao.videoExists(video.vodId)
            if (!exists) {
                newCount++
            }
            // Always insert (REPLACE) to update any changed fields
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
                val delayMs = initialDelayMs * (attempt + 1) // exponential-ish backoff
                delay(delayMs)
            }
        }
        throw lastException!!
    }
    
    // Get video count
    suspend fun getVideoCount(): Int {
        return dao.getVideoCount()
    }
    
    // Watch History
    fun getWatchHistory(): Flow<List<WatchHistoryEntity>> {
        return dao.getWatchHistory()
    }
    
    suspend fun saveWatchHistory(history: WatchHistoryEntity) {
        dao.insertWatchHistory(history)
    }
    
    suspend fun deleteWatchHistory(vodId: Int) {
        dao.deleteWatchHistory(vodId)
    }
    
    // Watch Later
    fun getWatchLater(): Flow<List<WatchLaterEntity>> {
        return dao.getWatchLater()
    }
    
    suspend fun addToWatchLater(vodId: Int) {
        dao.insertWatchLater(WatchLaterEntity(vodId = vodId))
    }
    
    suspend fun removeFromWatchLater(vodId: Int) {
        dao.deleteWatchLater(vodId)
    }
    
    suspend fun isWatchLater(vodId: Int): Boolean {
        val watchLater = dao.getWatchLater()
        // This is a simple check, in production you'd want a more efficient query
        return false // TODO: Implement properly
    }
    
    // Downloads
    fun getDownloads(): Flow<List<DownloadEntity>> {
        return dao.getDownloads()
    }
    
    suspend fun addDownload(download: DownloadEntity): Long {
        return dao.insertDownload(download)
    }
    
    suspend fun updateDownload(download: DownloadEntity) {
        dao.updateDownload(download)
    }
    
    suspend fun deleteDownload(id: Long) {
        dao.deleteDownload(id)
    }
    
    suspend fun deleteDownloadByVodId(vodId: Int) {
        dao.deleteDownloadByVodId(vodId)
    }
    
    // Metadata
    suspend fun getLastUpdateTime(): String? {
        return dao.getMetadata("last_update")
    }
    
    suspend fun setLastUpdateTime(time: String) {
        dao.setMetadata(MetadataEntity("last_update", time))
    }
    
    suspend fun getTotalItems(): Int {
        return dao.getMetadata("total_items")?.toIntOrNull() ?: 0
    }
    
    suspend fun setTotalItems(count: Int) {
        dao.setMetadata(MetadataEntity("total_items", count.toString()))
    }
}

// Extension functions
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
