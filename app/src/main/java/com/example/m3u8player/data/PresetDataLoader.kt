package com.example.m3u8player.data

import android.content.Context
import android.util.Log
import com.example.m3u8player.data.db.VideoDao
import com.example.m3u8player.data.model.*
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
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
    private val dao: VideoDao
) {
    companion object {
        private const val TAG = "PresetDataLoader"
        private const val PRESET_FILE = "preset_videos.json"
    }

    suspend fun loadIfNeeded() {
        val existingCount = dao.getVideoCount()
        if (existingCount > 0) {
            Log.d(TAG, "Database already has $existingCount videos, skip preset loading")
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val json = context.assets.open(PRESET_FILE).bufferedReader().use { it.readText() }
                val data = Gson().fromJson(json, PresetData::class.java)

                // Insert categories
                dao.insertCategories(data.categories.map {
                    CategoryEntity(typeId = it.typeId, typeName = it.typeName)
                })

                // Insert videos in batches
                val videos = data.videos.map {
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

                // Batch insert (Room has limits)
                videos.chunked(500).forEach { batch ->
                    dao.insertVideos(batch)
                }

                // Set metadata (use current timestamp since preset data is pre-fetched)
                dao.setMetadata(MetadataEntity("last_update", System.currentTimeMillis().toString()))
                dao.setMetadata(MetadataEntity("total_items", data.total.toString()))

                Log.d(TAG, "Preset data loaded: ${videos.size} videos, ${data.categories.size} categories")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load preset data", e)
            }
        }
    }
}
