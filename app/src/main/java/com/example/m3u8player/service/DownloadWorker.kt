package com.example.m3u8player.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    private val okHttpClient = OkHttpClient()
    
    override suspend fun doWork(): Result {
        val vodId = inputData.getInt("vodId", 0)
        val vodName = inputData.getString("vodName") ?: ""
        val episode = inputData.getInt("episode", 0)
        val videoUrl = inputData.getString("videoUrl") ?: ""
        
        return try {
            downloadM3U8(videoUrl, vodName, episode)
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
    
    private suspend fun downloadM3U8(url: String, name: String, episode: Int) = withContext(Dispatchers.IO) {
        // Download m3u8 playlist
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()
        val m3u8Content = response.body?.string() ?: return@withContext
        
        // Parse m3u8 to get ts segments
        val tsUrls = parseM3U8(m3u8Content, url)
        
        // Create download directory
        val downloadDir = File(applicationContext.getExternalFilesDir(null), "downloads/$name")
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
        
        // Download each ts segment
        val downloadedFiles = mutableListOf<File>()
        for ((index, tsUrl) in tsUrls.withIndex()) {
            val tsRequest = Request.Builder().url(tsUrl).build()
            val tsResponse = okHttpClient.newCall(tsRequest).execute()
            val tsFile = File(downloadDir, "segment_$index.ts")
            
            tsResponse.body?.byteStream()?.use { input ->
                FileOutputStream(tsFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            downloadedFiles.add(tsFile)
            
            // Update progress (simplified)
            setProgress(
                androidx.work.Data.Builder()
                    .putInt("progress", ((index + 1) * 100 / tsUrls.size))
                    .build()
            )
        }
        
        // Create local m3u8 file
        val localM3U8 = File(downloadDir, "video_$episode.m3u8")
        localM3U8.writeText(createLocalM3U8(downloadedFiles))
    }
    
    private fun parseM3U8(content: String, baseUrl: String): List<String> {
        val lines = content.lines()
        val tsUrls = mutableListOf<String>()
        var baseUri = baseUrl.substringBeforeLast("/")
        
        for (line in lines) {
            if (line.startsWith("#") || line.isBlank()) continue
            val tsUrl = if (line.startsWith("http")) {
                line
            } else {
                "$baseUri/$line"
            }
            tsUrls.add(tsUrl)
        }
        
        return tsUrls
    }
    
    private fun createLocalM3U8(files: List<File>): String {
        val sb = StringBuilder()
        sb.append("#EXTM3U8\n")
        for (file in files) {
            sb.append("#EXTINF:10.0,\n")
            sb.append("${file.absolutePath}\n")
        }
        return sb.toString()
    }
}
