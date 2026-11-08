package com.example.m3u8player.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.example.m3u8player.data.db.AppDatabase
import com.example.m3u8player.data.db.VideoDao
import com.example.m3u8player.data.model.CategoryEntity
import com.example.m3u8player.data.model.MetadataEntity
import com.example.m3u8player.data.model.VideoListEntity
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class PresetData(
    @SerializedName("categories")
    val categories: List<PresetCategory>,
    @SerializedName("videos")
    val videos: List<PresetVideo>,
    @SerializedName("total")
    val total: Int,
    @SerializedName("fetched_at")
    val fetchedAt: String
)

data class PresetCategory(
    @SerializedName("type_id")
    val typeId: Int,
    @SerializedName("type_name")
    val typeName: String
)

data class PresetVideo(
    @SerializedName("vod_id")
    val vodId: Int,
    @SerializedName("vod_name")
    val vodName: String,
    @SerializedName("type_id")
    val typeId: Int,
    @SerializedName("type_name")
    val typeName: String,
    @SerializedName("vod_en")
    val vodEn: String,
    @SerializedName("vod_time")
    val vodTime: String,
    @SerializedName("vod_remarks")
    val vodRemarks: String,
    @SerializedName("vod_play_from")
    val vodPlayFrom: String
)

@Singleton
class PresetDataLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: VideoDao,
    private val database: AppDatabase
) {
    companion object {
        private const val TAG = "PresetDataLoader"
        private const val PRESET_FILE = "preset_videos.json"
        private const val META_PRESET_FETCHED_AT = "preset_fetched_at"
        private const val META_PRESET_VIDEO_COUNT = "preset_video_count"
        private const val META_PRESET_CATEGORY_COUNT = "preset_category_count"
        private const val META_PRESET_SOURCE_TOTAL = "preset_source_total"
    }

    suspend fun loadIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = context.assets.open(PRESET_FILE).bufferedReader().use { it.readText() }
            val data = Gson().fromJson(json, PresetData::class.java)

            val categories = data.categories
                .distinctBy { it.typeId }
                .map { CategoryEntity(typeId = it.typeId, typeName = it.typeName) }

            val videos = data.videos
                .asReversed()
                .distinctBy { it.vodId }
                .asReversed()
                .map {
                    VideoListEntity(
                        vodId = it.vodId,
                        vodName = it.vodName,
                        typeId = it.typeId,
                        typeName = it.typeName,
                        vodEn = it.vodEn,
                        vodTime = it.vodTime,
                        vodRemarks = it.vodRemarks,
                        vodPlayFrom = it.vodPlayFrom
                    )
                }

            val expectedVideoCount = videos.size
            val expectedCategoryCount = categories.size

            val existingVideoCount = dao.getVideoCount()
            val storedFetchedAt = dao.getMetadata(META_PRESET_FETCHED_AT)

            val needsReload =
                existingVideoCount == 0 ||
                storedFetchedAt != data.fetchedAt

            if (!needsReload) {
                Log.d(
                    TAG,
                    "Preset data already matches asset: videos=$existingVideoCount fetchedAt=$storedFetchedAt"
                )
                return@withContext false
            }

            Log.d(
                TAG,
                "Reload preset data: existingVideos=$existingVideoCount expectedVideos=$expectedVideoCount expectedCategories=$expectedCategoryCount fetchedAt=${data.fetchedAt}"
            )

            database.withTransaction {
                dao.deleteAllVideos()
                dao.deleteAllCategories()

                dao.insertCategories(categories)
                videos.chunked(500).forEach { batch ->
                    dao.insertVideos(batch)
                }

                val lastUpdate = runCatching {
                    java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                        .parse(data.fetchedAt.substringBefore('.'))?.time
                }.getOrNull() ?: System.currentTimeMillis()
                dao.setMetadata(MetadataEntity("last_update", lastUpdate.toString()))
                dao.setMetadata(MetadataEntity("total_items", expectedVideoCount.toString()))
                dao.setMetadata(MetadataEntity(META_PRESET_FETCHED_AT, data.fetchedAt))
                dao.setMetadata(MetadataEntity(META_PRESET_VIDEO_COUNT, expectedVideoCount.toString()))
                dao.setMetadata(MetadataEntity(META_PRESET_CATEGORY_COUNT, expectedCategoryCount.toString()))
                dao.setMetadata(MetadataEntity(META_PRESET_SOURCE_TOTAL, data.total.toString()))
            }

            Log.d(
                TAG,
                "Preset data loaded: videos=$expectedVideoCount categories=$expectedCategoryCount sourceTotal=${data.total}"
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load preset data", e)
            false
        }
    }
}
