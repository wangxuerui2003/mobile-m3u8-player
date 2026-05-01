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

    @Query("SELECT * FROM video_list WHERE vodName LIKE '%' || :query || '%' ORDER BY vodTime DESC LIMIT :limit")
    suspend fun searchVideosSnapshot(query: String, limit: Int = 20): List<VideoListEntity>

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun getSearchHistory(limit: Int = 20): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchHistory(history: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearchHistory(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()
    
    @Query("SELECT * FROM video_list WHERE typeId = :typeId ORDER BY vodTime DESC LIMIT :limit OFFSET :offset")
    fun getVideosByType(typeId: Int, limit: Int = 20, offset: Int = 0): Flow<List<VideoListEntity>>

    @Query("SELECT * FROM video_list WHERE typeId IN (:typeIds) ORDER BY vodTime DESC LIMIT :limit")
    suspend fun getLatestVideosByTypeIds(typeIds: List<Int>, limit: Int = 20): List<VideoListEntity>

    @Query("SELECT * FROM video_list ORDER BY vodTime DESC LIMIT :limit")
    suspend fun getLatestVideosSnapshot(limit: Int = 20): List<VideoListEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<VideoListEntity>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoListEntity)
    
    @Query("DELETE FROM video_list")
    suspend fun deleteAllVideos()
    
    @Query("SELECT COUNT(*) FROM video_list")
    suspend fun getVideoCount(): Int

    @Query("SELECT COUNT(*) FROM video_list")
    fun observeVideoCount(): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM video_list WHERE vodId = :vodId)")
    suspend fun videoExists(vodId: Int): Boolean
    
    // Category
    @Query("SELECT * FROM category")
    fun getCategories(): Flow<List<CategoryEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)
    
    @Query("DELETE FROM category")
    suspend fun deleteAllCategories()

    @Query("SELECT COUNT(*) FROM category")
    suspend fun getCategoryCount(): Int
    
    // Watch History
    @Query("SELECT * FROM watch_history ORDER BY lastWatchedAt DESC")
    fun getWatchHistory(): Flow<List<WatchHistoryEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchHistory(history: WatchHistoryEntity)

    @Query("SELECT * FROM episode_watch_history ORDER BY lastWatchedAt DESC")
    fun getEpisodeWatchHistory(): Flow<List<EpisodeWatchHistoryEntity>>

    @Query("SELECT * FROM episode_watch_history WHERE vodId = :vodId ORDER BY episode ASC")
    fun getEpisodeWatchHistoryByVodId(vodId: Int): Flow<List<EpisodeWatchHistoryEntity>>

    @Query("SELECT * FROM episode_watch_history WHERE vodId = :vodId AND episode = :episode LIMIT 1")
    suspend fun getEpisodeWatchHistoryByVodIdAndEpisode(vodId: Int, episode: Int): EpisodeWatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodeWatchHistory(history: EpisodeWatchHistoryEntity)
    
    @Query("DELETE FROM watch_history WHERE vodId = :vodId")
    suspend fun deleteWatchHistory(vodId: Int)

    @Query("DELETE FROM episode_watch_history WHERE vodId = :vodId")
    suspend fun deleteEpisodeWatchHistoryByVodId(vodId: Int)
    
    @Query("DELETE FROM watch_history")
    suspend fun clearWatchHistory()

    @Query("DELETE FROM episode_watch_history")
    suspend fun clearEpisodeWatchHistory()
    
    // Watch Later
    @Query("SELECT * FROM watch_later ORDER BY addedAt DESC")
    fun getWatchLater(): Flow<List<WatchLaterEntity>>

    @Query(
        """
        SELECT watch_later.vodId AS vodId,
               watch_later.addedAt AS addedAt,
               video_list.vodName AS vodName,
               video_list.vodRemarks AS vodRemarks,
               video_list.typeName AS typeName
        FROM watch_later
        LEFT JOIN video_list ON watch_later.vodId = video_list.vodId
        ORDER BY watch_later.addedAt DESC
        """
    )
    fun getWatchLaterVideos(): Flow<List<WatchLaterVideo>>

    @Query("SELECT EXISTS(SELECT 1 FROM watch_later WHERE vodId = :vodId)")
    suspend fun isWatchLater(vodId: Int): Boolean
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchLater(watchLater: WatchLaterEntity)
    
    @Query("DELETE FROM watch_later WHERE vodId = :vodId")
    suspend fun deleteWatchLater(vodId: Int)
    
    @Query("DELETE FROM watch_later")
    suspend fun clearWatchLater()
    
    // Downloads
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    suspend fun getDownloadsSnapshot(): List<DownloadEntity>
    
    @Query("SELECT * FROM downloads WHERE status = :status")
    fun getDownloadsByStatus(status: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN (:statuses) ORDER BY createdAt ASC")
    suspend fun getDownloadsByStatuses(statuses: List<String>): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE id IN (:ids)")
    suspend fun getDownloadsByIds(ids: List<Long>): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE vodId = :vodId AND episode = :episode LIMIT 1")
    suspend fun getDownloadByVodIdAndEpisode(vodId: Int, episode: Int): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE vodId = :vodId")
    fun getDownloadsByVodId(vodId: Int): Flow<List<DownloadEntity>>

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
