package com.example.m3u8player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.m3u8player.M3U8PlayerApplication
import com.example.m3u8player.data.model.WatchHistoryEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

@Composable
fun PlayerScreen(
    vodId: Int,
    vodName: String,
    episode: Int,
    episodeName: String,
    coverUrl: String?,
    videoUrl: String?,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as M3U8PlayerApplication
    val repository = app.videoRepository
    val artworkCache = app.artworkCache
    val hasPlayableSource = !videoUrl.isNullOrBlank()

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            videoUrl?.takeIf { it.isNotBlank() }?.let {
                setMediaItem(MediaItem.fromUri(it))
            }
            if (hasPlayableSource) {
                prepare()
                playWhenReady = true
            }
        }
    }

    var isPlaying by remember { mutableStateOf(exoPlayer.playWhenReady) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }

        exoPlayer.addListener(listener)

        onDispose {
            if (vodId > 0 && vodName.isNotBlank()) {
                runBlocking {
                    val coverPath = artworkCache.cacheArtwork("vod_$vodId", coverUrl)
                    repository.saveWatchHistory(
                        WatchHistoryEntity(
                            vodId = vodId,
                            vodName = vodName,
                            episode = episode,
                            episodeName = episodeName.ifBlank { "第${episode + 1}集" },
                            coverUrl = coverUrl,
                            coverPath = coverPath,
                            progressMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                            durationMs = exoPlayer.duration.coerceAtLeast(0L)
                        )
                    )
                }
            }
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer, hasPlayableSource) {
        if (hasPlayableSource) {
            while (true) {
                delay(1000)
                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                duration = exoPlayer.duration.coerceAtLeast(0L)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(hasPlayableSource) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        if (hasPlayableSource) {
                            val screenWidth = size.width
                            if (offset.x < screenWidth / 3) {
                                exoPlayer.seekTo((exoPlayer.currentPosition - 5000).coerceAtLeast(0L))
                            } else if (offset.x > screenWidth * 2 / 3) {
                                exoPlayer.seekTo(exoPlayer.currentPosition + 5000)
                            } else {
                                exoPlayer.playWhenReady = !exoPlayer.playWhenReady
                            }
                        }
                    },
                    onTap = { showControls = !showControls }
                )
            }
    ) {
        if (hasPlayableSource) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "当前视频没有可播放的地址",
                    color = Color.White
                )
            }
        }

        if (showControls) {
            PlayerControls(
                title = vodName.ifBlank { "视频播放" },
                episodeLabel = episodeName.ifBlank { "第${episode + 1}集" },
                isPlayable = hasPlayableSource,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                onBack = onBack,
                onPlayPause = {
                    if (hasPlayableSource) {
                        exoPlayer.playWhenReady = !exoPlayer.playWhenReady
                    }
                },
                onSeek = { position ->
                    if (hasPlayableSource) {
                        exoPlayer.seekTo(position)
                    }
                }
            )
        }
    }
}

@Composable
fun PlayerControls(
    title: String,
    episodeLabel: String,
    isPlayable: Boolean,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Text(
                text = "$title · $episodeLabel",
                color = Color.White
            )

            IconButton(onClick = { }) {
                Icon(Icons.Default.Cast, contentDescription = "Cast", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(100.dp))

        Column {
            Slider(
                value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                onValueChange = { progress -> onSeek((progress * duration).toLong()) },
                enabled = isPlayable && duration > 0,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = formatTime(currentPosition), color = Color.White)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onSeek((currentPosition - 5000).coerceAtLeast(0L)) }, enabled = isPlayable) {
                        Icon(Icons.Default.FastRewind, contentDescription = "Rewind", tint = Color.White)
                    }

                    IconButton(onClick = onPlayPause, enabled = isPlayable) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White
                        )
                    }

                    IconButton(onClick = { onSeek(currentPosition + 5000) }, enabled = isPlayable) {
                        Icon(Icons.Default.FastForward, contentDescription = "Forward", tint = Color.White)
                    }
                }

                Text(text = formatTime(duration), color = Color.White)
            }
        }
    }
}

fun formatTime(timeMs: Long): String {
    val safeTimeMs = timeMs.coerceAtLeast(0L)
    val seconds = (safeTimeMs / 1000) % 60
    val minutes = (safeTimeMs / (1000 * 60)) % 60
    val hours = safeTimeMs / (1000 * 60 * 60)

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
