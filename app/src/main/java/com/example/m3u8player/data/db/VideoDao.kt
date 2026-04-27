package com.example.m3u8player.data.db

import androidx.room.*
import com.example.m3u8player.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    
    @Query("SELECT * FROM video_list ORDER BY vodTime DESC LIMIT :limit OFFSET :offset")
    fun getVideoList(limit: Int = 20, offset: Int = 0): Flow<List<VideoListEntity>>
    
    @Query("SELECT * FROM video_list WHERE vodId = :vodId")
    suspend fun getVideoById(vodId: Int): VideoListEntity?
    
    @Query("SELECT * FROM video_list WHERE vodName LIKE '%' || :query || '%'")
    fun searchVideos(query: String): Flow<List<VideoListEntity>>
    
    @Query("SELECT * FROM video_list WHERE typeId = :typeId ORDER BY vodTime DESC LIMIT :limit OFFSET :offset")
    fun getVideosByType(typeId: Int, limit: Int = 20, offset: Int = 0): Flow<List<VideoListEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<VideoListEntity>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoListEntity)
    
    @Query("DELETE FROM video_list")
    suspend fun deleteAllVideos()
    
    @Query("SELECT COUNT(*) FROM video_list")
    suspend fun getVideoCount(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM video_list WHERE vodId = :vodId)")
    suspend fun videoExists(vodId: Int): Boolean
    
    // Category
    @Query("SELECT * FROM category")
    fun getCategories(): Flow<List<CategoryEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)
    
    @Query("DELETE FROM category")
    suspend fun deleteAllCategories()
    
    // Watch History
    @Query("SELECT * FROM watch_history ORDER BY lastWatchedAt DESC")
    fun getWatchHistory(): Flow<List<WatchHistoryEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchHistory(history: WatchHistoryEntity)
    
    @Query("DELETE FROM watch_history WHERE vodId = :vodId")
    suspend fun deleteWatchHistory(vodId: Int)
    
    @Query("DELETE FROM watch_history")
    suspend fun clearWatchHistory()
    
    // Watch Later
    @Query("SELECT * FROM watch_later ORDER BY addedAt DESC")
    fun getWatchLater(): Flow<List<WatchLaterEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchLater(watchLater: WatchLaterEntity)
    
    @Query("DELETE FROM watch_later WHERE vodId = :vodId")
    suspend fun deleteWatchLater(vodId: Int)
    
    @Query("DELETE FROM watch_later")
    suspend fun clearWatchLater()
    
    // Downloads
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getDownloads(): Flow<List<DownloadEntity>>
    
    @Query("SELECT * FROM downloads WHERE status = :status")
    fun getDownloadsByStatus(status: String): Flow<List<DownloadEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity): Long
    
    @Update
    suspend fun updateDownload(download: DownloadEntity)
    
    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownload(id: Long)
    
    @Query("DELETE FROM downloads WHERE vodId = :vodId")
    suspend fun deleteDownloadByVodId(vodId: Int)
    
    @Query("DELETE FROM downloads")
    suspend fun clearDownloads()
    
    // Metadata
    @Query("SELECT value FROM metadata WHERE key = :key")
    suspend fun getMetadata(key: String): String?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setMetadata(metadata: MetadataEntity)
    
    @Query("DELETE FROM metadata")
    suspend fun clearMetadata()
}
