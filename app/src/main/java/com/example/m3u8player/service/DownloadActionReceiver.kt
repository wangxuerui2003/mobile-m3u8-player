package com.example.m3u8player.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DownloadActionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var downloadCoordinator: DownloadCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, 0L)
        if (downloadId <= 0L) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_PAUSE -> downloadCoordinator.pauseDownload(downloadId)
                    ACTION_DELETE -> downloadCoordinator.deleteDownload(downloadId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.example.m3u8player.action.PAUSE_DOWNLOAD"
        const val ACTION_DELETE = "com.example.m3u8player.action.DELETE_DOWNLOAD"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"
    }
}
