package com.example.m3u8player.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.example.m3u8player.M3U8PlayerApplication
import com.example.m3u8player.data.model.DownloadEntity
import com.example.m3u8player.data.model.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DownloadForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = mutableMapOf<Long, Job>()
    private val scheduleMutex = Mutex()
    private var foregroundStarted = false
    private var wakeLock: PowerManager.WakeLock? = null

    private val app: M3U8PlayerApplication
        get() = application as M3U8PlayerApplication

    private val repository
        get() = app.videoRepository

    private val engine by lazy {
        DownloadEngine(applicationContext, repository)
    }

    override fun onCreate() {
        super.onCreate()
        DownloadNotificationHelper.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground()

        when (intent?.action) {
            ACTION_PAUSE -> {
                val id = intent.getLongExtra(EXTRA_DOWNLOAD_ID, 0L)
                if (id > 0L) {
                    serviceScope.launch {
                        pauseDownload(id)
                        scheduleDownloads()
                    }
                }
            }

            ACTION_DELETE -> {
                val id = intent.getLongExtra(EXTRA_DOWNLOAD_ID, 0L)
                if (id > 0L) {
                    serviceScope.launch {
                        deleteDownload(id)
                        scheduleDownloads()
                    }
                }
            }

            else -> {
                serviceScope.launch {
                    scheduleDownloads()
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureForeground() {
        if (foregroundStarted) return
        val notification = DownloadNotificationHelper.buildSummaryNotification(this, 0, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                DownloadNotificationHelper.SUMMARY_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(DownloadNotificationHelper.SUMMARY_NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
    }

    private suspend fun scheduleDownloads() {
        scheduleMutex.withLock {
            val concurrency = app.downloadCoordinator.observeConcurrency().first()
            repository.getDownloadsByStatuses(listOf(DownloadStatus.DOWNLOADING))
                .filter { it.id !in activeJobs }
                .forEach { repository.markDownloadQueued(it.id, resetWork = true) }

            val activeCount = activeJobs.size
            val remainingSlots = (concurrency - activeCount).coerceAtLeast(0)

            if (remainingSlots > 0) {
                repository.getDownloadsByStatuses(listOf(DownloadStatus.QUEUED))
                    .take(remainingSlots)
                    .forEach { download ->
                        if (download.id !in activeJobs) {
                            startDownload(download)
                        }
                    }
            }

            val queuedCount = repository.getDownloadsByStatuses(listOf(DownloadStatus.QUEUED)).size
            updateSummaryNotification(queuedCount)
            stopIfIdle(queuedCount)
        }
    }

    private fun startDownload(download: DownloadEntity) {
        acquireWakeLock()
        val job = serviceScope.launch(start = CoroutineStart.LAZY) {
            try {
                repository.markDownloadEnqueued(download.id, SERVICE_WORK_ID)
                repository.getDownload(download.id)?.let {
                    DownloadNotificationHelper.notifyProgress(applicationContext, it)
                }

                val downloadedPlaylist = engine.downloadM3U8(download) { current ->
                    DownloadNotificationHelper.notifyProgress(applicationContext, current)
                    updateSummaryNotification()
                }

                repository.markDownloadCompleted(
                    id = download.id,
                    localPath = downloadedPlaylist.playlistFile.absolutePath,
                    totalBytes = downloadedPlaylist.totalBytes,
                    downloadedBytes = downloadedPlaylist.totalBytes,
                    downloadedSegments = downloadedPlaylist.totalSegments,
                    totalSegments = downloadedPlaylist.totalSegments,
                    totalDurationMs = downloadedPlaylist.totalDurationMs,
                    downloadedDurationMs = downloadedPlaylist.totalDurationMs
                )
                DownloadNotificationHelper.cancel(applicationContext, download.id)
            } catch (_: CancellationException) {
                val status = repository.getDownload(download.id)?.status
                if (status == DownloadStatus.DOWNLOADING) {
                    repository.markDownloadPaused(download.id)
                }
                DownloadNotificationHelper.cancel(applicationContext, download.id)
            } catch (_: Exception) {
                repository.markDownloadFailed(download.id)
                DownloadNotificationHelper.cancel(applicationContext, download.id)
            } finally {
                activeJobs.remove(download.id)
                scheduleDownloads()
            }
        }
        activeJobs[download.id] = job
        job.start()
    }

    private suspend fun pauseDownload(id: Long) {
        activeJobs.remove(id)?.cancel()
        repository.markDownloadPaused(id)
        DownloadNotificationHelper.cancel(applicationContext, id)
    }

    private suspend fun deleteDownload(id: Long) {
        activeJobs.remove(id)?.cancel()
        DownloadNotificationHelper.cancel(applicationContext, id)
        repository.deleteDownload(id)
    }

    private suspend fun updateSummaryNotification(queuedCount: Int? = null) {
        val resolvedQueuedCount = queuedCount ?: repository.getDownloadsByStatuses(listOf(DownloadStatus.QUEUED)).size
        DownloadNotificationHelper.notifySummary(
            context = applicationContext,
            activeCount = activeJobs.size,
            queuedCount = resolvedQueuedCount
        )
    }

    private fun stopIfIdle(queuedCount: Int) {
        if (activeJobs.isNotEmpty() || queuedCount > 0) return
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        val existing = wakeLock
        if (existing?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "M3U8Player:Download").apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        private const val ACTION_START = "com.example.m3u8player.action.START_DOWNLOAD_QUEUE"
        private const val ACTION_PAUSE = "com.example.m3u8player.action.PAUSE_DOWNLOAD_SERVICE"
        private const val ACTION_DELETE = "com.example.m3u8player.action.DELETE_DOWNLOAD_SERVICE"
        private const val EXTRA_DOWNLOAD_ID = "extra_download_id"
        private const val SERVICE_WORK_ID = "foreground_service"
        private const val WAKE_LOCK_TIMEOUT_MS = 6L * 60L * 60L * 1000L

        fun start(context: Context) {
            startCommand(context, Intent(context, DownloadForegroundService::class.java).setAction(ACTION_START))
        }

        fun pause(context: Context, downloadId: Long) {
            startCommand(
                context,
                Intent(context, DownloadForegroundService::class.java)
                    .setAction(ACTION_PAUSE)
                    .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            )
        }

        fun delete(context: Context, downloadId: Long) {
            startCommand(
                context,
                Intent(context, DownloadForegroundService::class.java)
                    .setAction(ACTION_DELETE)
                    .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            )
        }

        private fun startCommand(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
