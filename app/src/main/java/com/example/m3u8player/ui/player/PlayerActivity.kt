package com.example.m3u8player.ui.player

import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.m3u8player.ui.theme.M3U8PlayerTheme

class PlayerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val vodId = intent.getIntExtra("vodId", 0)
        val episode = intent.getIntExtra("episode", 0)
        val vodName = intent.getStringExtra("vodName").orEmpty()
        val episodeName = intent.getStringExtra("episodeName").orEmpty()
        val coverUrl = intent.getStringExtra("coverUrl")
        val videoUrl = intent.getStringExtra("videoUrl")
        val localPlaylistPath = intent.getStringExtra("localPlaylistPath")
        val startPositionMs = intent.getLongExtra("startPositionMs", 0L)
        val isPartialDownload = intent.getBooleanExtra("isPartialDownload", false)
        val downloadedDurationMs = intent.getLongExtra("downloadedDurationMs", 0L)
        val totalDurationMs = intent.getLongExtra("totalDurationMs", 0L)
        val playbackSource = when {
            !localPlaylistPath.isNullOrBlank() -> PlaybackSource(
                uri = Uri.fromFile(java.io.File(localPlaylistPath)).toString(),
                isLocal = true,
                remoteUrl = videoUrl,
                localPlaylistPath = localPlaylistPath,
                isPartialDownload = isPartialDownload,
                downloadedDurationMs = downloadedDurationMs,
                totalDurationMs = totalDurationMs
            )
            !videoUrl.isNullOrBlank() -> PlaybackSource(
                uri = videoUrl,
                isLocal = false,
                remoteUrl = videoUrl,
                totalDurationMs = totalDurationMs
            )
            else -> null
        }

        setContent {
            M3U8PlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PlayerScreen(
                        vodId = vodId,
                        vodName = vodName,
                        episode = episode,
                        episodeName = episodeName,
                        coverUrl = coverUrl,
                        playbackSource = playbackSource,
                        startPositionMs = startPositionMs,
                        onBack = { finish() }
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }
}
