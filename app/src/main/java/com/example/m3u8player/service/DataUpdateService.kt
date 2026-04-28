package com.example.m3u8player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.m3u8player.MainActivity
import com.example.m3u8player.M3U8PlayerApplication
import com.example.m3u8player.R
import kotlinx.coroutines.*

class DataUpdateService : Service() {

    companion object {
        private const val TAG = "DataUpdateService"
        const val CHANNEL_ID = "data_update_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.example.m3u8player.ACTION_STOP_UPDATE"

        fun start(context: Context) {
            val intent = Intent(context, DataUpdateService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DataUpdateService::class.java))
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var updateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")
        if (intent?.action == ACTION_STOP) {
            stopUpdate()
            return START_NOT_STICKY
        }

        try {
            val notification = buildNotification(0)
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "startForeground succeeded")
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            stopSelf()
            return START_NOT_STICKY
        }

        val app = application as M3U8PlayerApplication
        val repository = app.videoRepository

        app.setUpdateState(isUpdating = true, progress = 0f)

        updateJob = serviceScope.launch {
            try {
                val result = repository.fetchAllVideoList(
                    onProgress = { current, total ->
                        val progress = current.toFloat() / total * 100
                        Log.d(TAG, "Progress: $current/$total = $progress%")
                        app.setUpdateState(isUpdating = true, progress = progress)
                        updateNotification(progress.toInt())
                    }
                )

                if (result.isSuccess) {
                    repository.setLastUpdateTime(System.currentTimeMillis().toString())
                    repository.setTotalItems(result.getOrNull() ?: 0)
                    Log.d(TAG, "Update completed successfully")
                } else {
                    Log.e(TAG, "Update failed", result.exceptionOrNull())
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Update cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Update error", e)
            } finally {
                app.setUpdateState(isUpdating = false, progress = 100f)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        updateJob?.cancel()
        serviceScope.cancel()
        val app = application as M3U8PlayerApplication
        app.setUpdateState(isUpdating = false, progress = 0f)
        super.onDestroy()
    }

    private fun stopUpdate() {
        updateJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "数据更新",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "视频数据更新进度"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(progress: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, DataUpdateService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("正在更新数据")
            .setContentText(if (progress > 0) "进度: $progress%" else "准备中...")
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setContentIntent(pendingIntent)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .addAction(0, "取消", stopIntent)
            .build()
    }

    private fun updateNotification(progress: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(progress))
    }
}
