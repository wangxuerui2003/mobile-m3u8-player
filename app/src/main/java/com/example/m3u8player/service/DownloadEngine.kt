package com.example.m3u8player.service

import android.content.Context
import com.example.m3u8player.data.model.DownloadEntity
import com.example.m3u8player.data.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class DownloadEngine(
    private val context: Context,
    private val repository: VideoRepository,
    private val okHttpClient: OkHttpClient = OkHttpClient()
) {
    suspend fun downloadM3U8(
        download: DownloadEntity,
        onProgress: suspend (DownloadEntity) -> Unit
    ): DownloadedPlaylist = withContext(Dispatchers.IO) {
        val segments = resolveSegments(download.videoUrl)
        val totalDurationMs = segments.sumOf { it.durationMs }
        val safeName = download.vodName.ifBlank { "video_${download.id}" }
            .replace(Regex("[^\\w\\u4e00-\\u9fa5.-]+"), "_")

        val downloadDir = download.downloadDir?.let(::File)
            ?: File(context.getExternalFilesDir(null), "downloads/$safeName/episode_${download.episode + 1}")
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }

        val localM3U8 = File(downloadDir, "video_${download.episode + 1}.m3u8")
        if (!localM3U8.exists()) {
            localM3U8.writeText(createLocalM3U8(emptyList(), isComplete = false))
        }

        repository.markDownloadRunning(
            id = download.id,
            downloadDir = downloadDir.absolutePath,
            localPath = localM3U8.absolutePath,
            totalSegments = segments.size,
            totalDurationMs = totalDurationMs
        )
        repository.getDownload(download.id)?.let { onProgress(it) }

        var downloadedBytes = 0L
        var downloadedSegments = 0
        var downloadedDurationMs = 0L
        val downloadedFiles = mutableListOf<DownloadedSegment>()

        segments.forEachIndexed { index, segment ->
            currentCoroutineContext().ensureActive()

            val fileExtension = segment.url.substringAfterLast('.', "ts").substringBefore('?')
            val segmentFile = File(downloadDir, "segment_$index.$fileExtension")
            if (segmentFile.exists() && segmentFile.length() > 0) {
                downloadedBytes += segmentFile.length()
                downloadedSegments++
                downloadedDurationMs += segment.durationMs
                downloadedFiles += DownloadedSegment(segmentFile, segment.durationSec)
                localM3U8.writeText(createLocalM3U8(downloadedFiles, isComplete = false))
                updateDownloadProgress(
                    downloadId = download.id,
                    downloadedBytes = downloadedBytes,
                    downloadedSegments = downloadedSegments,
                    totalSegments = segments.size,
                    downloadedDurationMs = downloadedDurationMs,
                    totalDurationMs = totalDurationMs,
                    speedBytesPerSec = 0,
                    onProgress = onProgress
                )
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
            downloadedDurationMs += segment.durationMs
            downloadedFiles += DownloadedSegment(segmentFile, segment.durationSec)
            localM3U8.writeText(createLocalM3U8(downloadedFiles, isComplete = false))

            updateDownloadProgress(
                downloadId = download.id,
                downloadedBytes = downloadedBytes,
                downloadedSegments = downloadedSegments,
                totalSegments = segments.size,
                downloadedDurationMs = downloadedDurationMs,
                totalDurationMs = totalDurationMs,
                speedBytesPerSec = speedBytesPerSec,
                onProgress = onProgress
            )
        }

        localM3U8.writeText(createLocalM3U8(downloadedFiles, isComplete = true))
        DownloadedPlaylist(
            playlistFile = localM3U8,
            totalBytes = downloadedBytes + localM3U8.length(),
            totalSegments = segments.size,
            totalDurationMs = totalDurationMs
        )
    }

    private suspend fun updateDownloadProgress(
        downloadId: Long,
        downloadedBytes: Long,
        downloadedSegments: Int,
        totalSegments: Int,
        downloadedDurationMs: Long,
        totalDurationMs: Long,
        speedBytesPerSec: Long,
        onProgress: suspend (DownloadEntity) -> Unit
    ) {
        val progress = if (totalSegments > 0) downloadedSegments * 100f / totalSegments else 0f
        repository.updateDownloadProgress(
            id = downloadId,
            progress = progress,
            downloadedBytes = downloadedBytes,
            totalBytes = downloadedBytes,
            downloadedSegments = downloadedSegments,
            totalSegments = totalSegments,
            downloadedDurationMs = downloadedDurationMs,
            totalDurationMs = totalDurationMs,
            speedBytesPerSec = speedBytesPerSec
        )
        repository.getDownload(downloadId)?.let { onProgress(it) }
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
                            val durationSec = pendingDuration ?: "10.0"
                            add(
                                PlaylistSegment(
                                    url = resolvedUrl,
                                    durationSec = durationSec,
                                    durationMs = durationSec.toDoubleOrNull()?.times(1000)?.toLong() ?: 10_000L
                                )
                            )
                        }
                        pendingDuration = null
                    }
                }
            }
        }
    }

    private fun createLocalM3U8(files: List<DownloadedSegment>, isComplete: Boolean): String {
        return buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:3")
            appendLine("#EXT-X-TARGETDURATION:10")
            appendLine("#EXT-X-MEDIA-SEQUENCE:0")
            files.forEach { file ->
                appendLine("#EXTINF:${file.durationSec},")
                appendLine(file.file.toURI().toString())
            }
            if (isComplete) {
                appendLine("#EXT-X-ENDLIST")
            }
        }
    }
}

data class DownloadedPlaylist(
    val playlistFile: File,
    val totalBytes: Long,
    val totalSegments: Int,
    val totalDurationMs: Long
)

private data class PlaylistSegment(
    val url: String,
    val durationSec: String,
    val durationMs: Long
)

private data class DownloadedSegment(
    val file: File,
    val durationSec: String
)
