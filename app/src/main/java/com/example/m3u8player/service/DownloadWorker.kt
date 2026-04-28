package com.example.m3u8player.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.m3u8player.M3U8PlayerApplication
import com.example.m3u8player.data.model.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val okHttpClient = OkHttpClient()
    private val app by lazy { applicationContext as M3U8PlayerApplication }
    private val repository by lazy { app.videoRepository }
    private val downloadCoordinator by lazy { app.downloadCoordinator }

    override suspend fun doWork(): Result {
        val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, 0L)
        if (downloadId <= 0L) return Result.failure()

        val download = repository.getDownload(downloadId) ?: return Result.failure()
        if (download.videoUrl.isBlank()) {
            repository.markDownloadFailed(downloadId)
            return Result.failure()
        }

        return try {
            val downloadedPlaylist = downloadM3U8(download)
            repository.markDownloadCompleted(
                id = downloadId,
                localPath = downloadedPlaylist.playlistFile.absolutePath,
                totalBytes = downloadedPlaylist.totalBytes,
                downloadedBytes = downloadedPlaylist.totalBytes,
                downloadedSegments = downloadedPlaylist.totalSegments,
                totalSegments = downloadedPlaylist.totalSegments
            )
            DownloadNotificationHelper.cancel(applicationContext, downloadId)
            Result.success()
        } catch (_: CancellationException) {
            val status = repository.getDownload(downloadId)?.status
            if (status == DownloadStatus.DOWNLOADING) {
                repository.markDownloadPaused(downloadId)
            }
            DownloadNotificationHelper.cancel(applicationContext, downloadId)
            Result.failure()
        } catch (_: Exception) {
            repository.markDownloadFailed(downloadId)
            DownloadNotificationHelper.cancel(applicationContext, downloadId)
            Result.failure()
        } finally {
            downloadCoordinator.schedulePendingDownloads()
        }
    }

    private suspend fun downloadM3U8(download: com.example.m3u8player.data.model.DownloadEntity): DownloadedPlaylist =
        withContext(Dispatchers.IO) {
            val segments = resolveSegments(download.videoUrl)
            val safeName = download.vodName.ifBlank { "video_${download.id}" }
                .replace(Regex("[^\\w\\u4e00-\\u9fa5.-]+"), "_")

            val downloadDir = download.downloadDir?.let(::File)
                ?: File(applicationContext.getExternalFilesDir(null), "downloads/$safeName/episode_${download.episode + 1}")
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            repository.markDownloadRunning(
                id = download.id,
                downloadDir = downloadDir.absolutePath,
                totalSegments = segments.size
            )

            updateForeground(download.id)

            var downloadedBytes = 0L
            var downloadedSegments = 0
            val downloadedFiles = mutableListOf<DownloadedSegment>()

            segments.forEachIndexed { index, segment ->
                currentCoroutineContext().ensureActive()
                if (isStopped) throw CancellationException()

                val fileExtension = segment.url.substringAfterLast('.', "ts").substringBefore('?')
                val segmentFile = File(downloadDir, "segment_$index.$fileExtension")
                if (segmentFile.exists() && segmentFile.length() > 0) {
                    downloadedBytes += segmentFile.length()
                    downloadedSegments++
                    downloadedFiles += DownloadedSegment(segmentFile, segment.durationSec)
                    updateDownloadProgress(download.id, downloadedBytes, downloadedSegments, segments.size, 0)
                    return@forEachIndexed
                }

                val tempFile = File(downloadDir, "segment_$index.$fileExtension.part")
                val request = Request.Builder().url(segment.url).build()
                val startedAt = System.currentTimeMillis()
                okHttpClient.newCall(request).execute().use { response ->
                    currentCoroutineContext().ensureActive()
                    require(response.isSuccessful) { "Failed to download segment ${segment.url}" }

                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    } ?: error("Empty response body for ${segment.url}")
                }

                if (!tempFile.renameTo(segmentFile)) {
                    tempFile.delete()
                    error("Failed to finalize segment file ${segmentFile.name}")
                }

                val segmentBytes = segmentFile.length()
                val elapsedMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
                val speedBytesPerSec = segmentBytes * 1000 / elapsedMs

                downloadedBytes += segmentBytes
                downloadedSegments++
                downloadedFiles += DownloadedSegment(segmentFile, segment.durationSec)

                updateDownloadProgress(
                    download.id,
                    downloadedBytes,
                    downloadedSegments,
                    segments.size,
                    speedBytesPerSec
                )
            }

            val localM3U8 = File(downloadDir, "video_${download.episode + 1}.m3u8")
            localM3U8.writeText(createLocalM3U8(downloadedFiles))
            DownloadedPlaylist(
                playlistFile = localM3U8,
                totalBytes = downloadedBytes + localM3U8.length(),
                totalSegments = segments.size
            )
        }

    private suspend fun updateDownloadProgress(
        downloadId: Long,
        downloadedBytes: Long,
        downloadedSegments: Int,
        totalSegments: Int,
        speedBytesPerSec: Long
    ) {
        val progress = if (totalSegments > 0) downloadedSegments * 100f / totalSegments else 0f
        repository.updateDownloadProgress(
            id = downloadId,
            progress = progress,
            downloadedBytes = downloadedBytes,
            totalBytes = downloadedBytes,
            downloadedSegments = downloadedSegments,
            totalSegments = totalSegments,
            speedBytesPerSec = speedBytesPerSec
        )
        updateForeground(downloadId)
    }

    private suspend fun updateForeground(downloadId: Long) {
        val currentDownload = repository.getDownload(downloadId) ?: return
        setForeground(DownloadNotificationHelper.buildForegroundInfo(applicationContext, currentDownload))
    }

    private fun resolveSegments(url: String): List<PlaylistSegment> {
        val playlistContent = fetchText(url)
        val parsedEntries = parsePlaylistEntries(playlistContent, url)
        val nestedPlaylist = parsedEntries.firstOrNull { it.url.endsWith(".m3u8", ignoreCase = true) }

        return if (nestedPlaylist != null) {
            resolveSegments(nestedPlaylist.url)
        } else {
            parsedEntries
        }
    }

    private fun fetchText(url: String): String {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "Failed to fetch playlist $url" }
            return response.body?.string().orEmpty()
        }
    }

    private fun parsePlaylistEntries(content: String, baseUrl: String): List<PlaylistSegment> {
        val baseHttpUrl = baseUrl.toHttpUrlOrNull()
        var pendingDuration: String? = null

        return buildList {
            content.lineSequence().forEach { rawLine ->
                val line = rawLine.trim()
                when {
                    line.startsWith("#EXTINF:") -> {
                        pendingDuration = line.removePrefix("#EXTINF:").substringBefore(',').ifBlank { "10.0" }
                    }

                    line.isBlank() || line.startsWith("#") -> Unit

                    else -> {
                        val resolvedUrl = line.toHttpUrlOrNull()?.toString()
                            ?: baseHttpUrl?.resolve(line)?.toString()
                        if (resolvedUrl != null) {
                            add(PlaylistSegment(url = resolvedUrl, durationSec = pendingDuration ?: "10.0"))
                        }
                        pendingDuration = null
                    }
                }
            }
        }
    }

    private fun createLocalM3U8(files: List<DownloadedSegment>): String {
        return buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:3")
            files.forEach { file ->
                appendLine("#EXTINF:${file.durationSec},")
                appendLine(file.file.toURI().toString())
            }
            appendLine("#EXT-X-ENDLIST")
        }
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "downloadId"
    }
}

private data class PlaylistSegment(
    val url: String,
    val durationSec: String
)

private data class DownloadedSegment(
    val file: File,
    val durationSec: String
)

private data class DownloadedPlaylist(
    val playlistFile: File,
    val totalBytes: Long,
    val totalSegments: Int
)
