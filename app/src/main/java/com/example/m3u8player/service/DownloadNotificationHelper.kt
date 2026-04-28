package com.example.m3u8player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.example.m3u8player.MainActivity
import com.example.m3u8player.data.model.DownloadEntity
import kotlin.math.roundToInt

object DownloadNotificationHelper {
    const val CHANNEL_ID = "download_progress_channel"
    private const val CHANNEL_NAME = "视频下载"
    private const val CHANNEL_DESCRIPTION = "显示视频下载进度"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = CHANNEL_DESCRIPTION
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun buildForegroundInfo(context: Context, download: DownloadEntity): ForegroundInfo {
        val notification = buildProgressNotification(context, download)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId(download.id), notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId(download.id), notification)
        }
    }

    fun buildProgressNotification(context: Context, download: DownloadEntity): Notification {
        ensureChannel(context)

        val openIntent = PendingIntent.getActivity(
            context,
            download.id.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = PendingIntent.getBroadcast(
            context,
            (download.id * 10 + 1).toInt(),
            Intent(context, DownloadActionReceiver::class.java).apply {
                action = DownloadActionReceiver.ACTION_PAUSE
                putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, download.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deleteIntent = PendingIntent.getBroadcast(
            context,
            (download.id * 10 + 2).toInt(),
            Intent(context, DownloadActionReceiver::class.java).apply {
                action = DownloadActionReceiver.ACTION_DELETE
                putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, download.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = buildString {
            append(download.vodName)
            if (download.episodeName.isNotBlank()) {
                append(" · ")
                append(download.episodeName)
            }
        }

        val progressText = if (download.totalSegments > 0) {
            "${download.downloadedSegments}/${download.totalSegments} 片段 · ${download.progress.roundToInt()}%"
        } else {
            "${download.progress.roundToInt()}%"
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText("$progressText · ${formatSpeed(download.speedBytesPerSec)}")
            .setSubText("正在下载")
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(100, download.progress.roundToInt().coerceIn(0, 100), false)
            .addAction(0, "暂停", pauseIntent)
            .addAction(0, "删除", deleteIntent)
            .build()
    }

    fun cancel(context: Context, downloadId: Long) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(notificationId(downloadId))
    }

    fun notifyProgress(context: Context, download: DownloadEntity) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(notificationId(download.id), buildProgressNotification(context, download))
    }

    fun notificationId(downloadId: Long): Int {
        return 20_000 + (downloadId % 100_000).toInt()
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return "0 KB/s"

        val kb = bytesPerSec / 1024f
        val mb = kb / 1024f
        return if (mb >= 1f) {
            String.format("%.1f MB/s", mb)
        } else {
            String.format("%.0f KB/s", kb)
        }
    }
}
