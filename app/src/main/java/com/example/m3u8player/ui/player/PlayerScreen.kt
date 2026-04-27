package com.example.m3u8player.ui.player

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

@Composable
fun PlayerScreen(
    vodId: Int,
    episode: Int,
    videoUrl: String?,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            videoUrl?.let { 
                setMediaItem(MediaItem.fromUri(it))
            }
            prepare()
            playWhenReady = true
        }
    }
    
    var isPlaying by remember { mutableStateOf(exoPlayer.playWhenReady) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        
        exoPlayer.addListener(listener)
        
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }
    
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        val screenWidth = size.width
                        if (offset.x < screenWidth / 3) {
                            exoPlayer.seekTo(exoPlayer.currentPosition - 5000)
                        } else if (offset.x > screenWidth * 2 / 3) {
                            exoPlayer.seekTo(exoPlayer.currentPosition + 5000)
                        } else {
                            exoPlayer.playWhenReady = !exoPlayer.playWhenReady
                        }
                    },
                    onTap = {
                        showControls = !showControls
                    }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        if (showControls) {
            PlayerControls(
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                onBack = onBack,
                onPlayPause = {
                    exoPlayer.playWhenReady = !exoPlayer.playWhenReady
                },
                onSeek = { position ->
                    exoPlayer.seekTo(position)
                }
            )
        }
    }
}

@Composable
fun PlayerControls(
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
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            
            Text(
                text = "Episode $currentPosition",
                color = Color.White
            )
            
            IconButton(onClick = { }) {
                Icon(Icons.Default.Cast, contentDescription = "Cast", tint = Color.White)
            }
        }
        
        Spacer(modifier = Modifier.height(100.dp))
        
        // Bottom controls
        Column {
            // Progress bar
            Slider(
                value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                onValueChange = { progress ->
                    onSeek((progress * duration).toLong())
                },
                modifier = Modifier.fillMaxWidth()
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(currentPosition),
                    color = Color.White
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.FastRewind, contentDescription = "Rewind", tint = Color.White)
                    }
                    
                    IconButton(onClick = onPlayPause) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White
                        )
                    }
                    
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.FastForward, contentDescription = "Forward", tint = Color.White)
                    }
                }
                
                Text(
                    text = formatTime(duration),
                    color = Color.White
                )
            }
        }
    }
}

fun formatTime(timeMs: Long): String {
    val seconds = (timeMs / 1000) % 60
    val minutes = (timeMs / (1000 * 60)) % 60
    val hours = timeMs / (1000 * 60 * 60)
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
