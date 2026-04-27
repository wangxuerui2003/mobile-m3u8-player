package com.example.m3u8player.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_list")
data class VideoListEntity(
    @PrimaryKey
    val vodId: Int,
    val vodName: String,
    val typeId: Int,
    val typeName: String,
    val vodEn: String,
    val vodTime: String,
    val vodRemarks: String,
    val vodPlayFrom: String
)

@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey
    val typeId: Int,
    val typeName: String
)

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey
    val vodId: Int,
    val vodName: String,
    val episode: Int = 0,
    val progressMs: Long = 0,
    val durationMs: Long = 0,
    val lastWatchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "watch_later")
data class WatchLaterEntity(
    @PrimaryKey
    val vodId: Int,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val vodId: Int,
    val vodName: String,
    val episode: Int = 0,
    val status: String = "pending", // pending, downloading, completed, failed
    val localPath: String? = null,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val progress: Float = 0f,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

@Entity(tableName = "metadata")
data class MetadataEntity(
    @PrimaryKey
    val key: String,
    val value: String
)
