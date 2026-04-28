package com.example.m3u8player.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtworkCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val cacheDir: File by lazy {
        File(context.filesDir, "posters").apply { mkdirs() }
    }

    suspend fun cacheArtwork(key: String, url: String?): String? = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) return@withContext null

        val safeKey = key.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val extension = url.substringAfterLast('.', "jpg").substringBefore('?').ifBlank { "jpg" }
        val targetFile = File(cacheDir, "$safeKey.$extension")

        if (targetFile.exists() && targetFile.length() > 0) {
            return@withContext targetFile.absolutePath
        }

        val tempFile = File(cacheDir, "$safeKey.$extension.part")
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                tempFile.delete()
                return@withContext null
            }

            response.body?.byteStream()?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null
        }

        if (!tempFile.renameTo(targetFile)) {
            tempFile.delete()
            return@withContext null
        }

        targetFile.absolutePath
    }
}
