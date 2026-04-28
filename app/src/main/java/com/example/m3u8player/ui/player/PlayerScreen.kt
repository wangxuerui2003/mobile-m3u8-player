package com.example.m3u8player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.m3u8player.M3U8PlayerApplication
import com.example.m3u8player.data.model.WatchHistoryEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.abs

private val playbackSpeeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
private const val PARTIAL_PLAYBACK_SWITCH_THRESHOLD_MS = 1_500L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    vodId: Int,
    vodName: String,
    episode: Int,
    episodeName: String,
    coverUrl: String?,
    playbackSource: PlaybackSource?,
    startPositionMs: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewConfiguration = LocalViewConfiguration.current
    val app = context.applicationContext as M3U8PlayerApplication
    val repository = app.videoRepository
    val artworkCache = app.artworkCache
    val playerSettingsStore = app.playerSettingsStore
    val hasPlayableSource = playbackSource != null
    val coroutineScope = rememberCoroutineScope()
    val remoteUri = playbackSource?.remoteUrl?.takeIf { it.isNotBlank() }
    val partialDownloadBoundaryMs = playbackSource?.downloadedDurationMs?.coerceAtLeast(0L) ?: 0L
    val totalDurationHintMs = playbackSource?.totalDurationMs?.coerceAtLeast(0L) ?: 0L

    val exoPlayer = remember(playbackSource?.uri, playbackSource?.remoteUrl, playbackSource?.localPlaylistPath) {
        ExoPlayer.Builder(context).build()
    }

    var isPlaying by remember { mutableStateOf(exoPlayer.playWhenReady) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var speed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var overlayText by remember { mutableStateOf<String?>(null) }
    var gestureWidth by remember { mutableFloatStateOf(1f) }
    var dragPreviewPosition by remember { mutableLongStateOf(-1L) }
    var dragStartPosition by remember { mutableLongStateOf(0L) }
    var dragAccumulatedX by remember { mutableFloatStateOf(0f) }
    var dragAccumulatedY by remember { mutableFloatStateOf(0f) }
    var isSeekGestureActive by remember { mutableStateOf(false) }
    var ignoreCurrentDragGesture by remember { mutableStateOf(false) }
    var activeSourceUri by remember(playbackSource?.uri, playbackSource?.remoteUrl, playbackSource?.localPlaylistPath) {
        mutableStateOf<String?>(null)
    }
    var isUsingRemoteSource by remember(playbackSource?.uri, playbackSource?.remoteUrl, playbackSource?.localPlaylistPath) {
        mutableStateOf(false)
    }

    fun resolveSourceForPosition(positionMs: Long): Pair<String?, Boolean> {
        if (playbackSource == null) return null to false
        val safePositionMs = positionMs.coerceAtLeast(0L)
        val localUri = playbackSource.uri.takeIf { it.isNotBlank() }
        val shouldUseRemote = when {
            remoteUri.isNullOrBlank() -> false
            !playbackSource.isPartialDownload -> !playbackSource.isLocal
            partialDownloadBoundaryMs <= 0L -> true
            safePositionMs > partialDownloadBoundaryMs -> true
            else -> false
        }
        val targetUri = if (shouldUseRemote) remoteUri else localUri ?: remoteUri
        return targetUri to shouldUseRemote
    }

    fun switchToSource(
        sourceUri: String,
        useRemote: Boolean,
        seekMs: Long,
        playWhenReady: Boolean = exoPlayer.playWhenReady
    ) {
        activeSourceUri = sourceUri
        isUsingRemoteSource = useRemote
        exoPlayer.setMediaItem(MediaItem.fromUri(sourceUri))
        exoPlayer.prepare()
        exoPlayer.seekTo(seekMs.coerceAtLeast(0L))
        exoPlayer.playWhenReady = playWhenReady
        currentPosition = seekMs.coerceAtLeast(0L)
    }

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
                            durationMs = listOf(
                                totalDurationHintMs,
                                duration,
                                exoPlayer.duration.coerceAtLeast(0L)
                            ).maxOrNull() ?: 0L
                        )
                    )
                }
            }
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        speed = playerSettingsStore.playbackSpeed.first()
        exoPlayer.playbackParameters = PlaybackParameters(speed)
    }

    LaunchedEffect(playbackSource, startPositionMs) {
        val (initialUri, useRemote) = resolveSourceForPosition(startPositionMs)
        if (initialUri != null) {
            switchToSource(
                sourceUri = initialUri,
                useRemote = useRemote,
                seekMs = startPositionMs
            )
        } else {
            activeSourceUri = null
            isUsingRemoteSource = false
            currentPosition = 0L
            duration = 0L
        }
    }

    LaunchedEffect(exoPlayer, hasPlayableSource, totalDurationHintMs, partialDownloadBoundaryMs, remoteUri) {
        if (hasPlayableSource) {
            while (true) {
                delay(250)
                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                val playerDuration = exoPlayer.duration.coerceAtLeast(0L)
                duration = maxOf(playerDuration, totalDurationHintMs)

                val shouldSwitchToRemote = playbackSource?.isPartialDownload == true &&
                    !isUsingRemoteSource &&
                    !remoteUri.isNullOrBlank() &&
                    partialDownloadBoundaryMs > 0L &&
                    currentPosition >= (partialDownloadBoundaryMs - PARTIAL_PLAYBACK_SWITCH_THRESHOLD_MS).coerceAtLeast(0L)

                if (shouldSwitchToRemote) {
                    switchToSource(
                        sourceUri = remoteUri ?: return@LaunchedEffect,
                        useRemote = true,
                        seekMs = currentPosition
                    )
                }
            }
        }
    }

    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3000)
            showControls = false
        }
    }

    LaunchedEffect(overlayText) {
        if (overlayText != null) {
            delay(900)
            overlayText = null
        }
    }

    val displayPosition = if (dragPreviewPosition >= 0) dragPreviewPosition else currentPosition

    fun seekTo(positionMs: Long, showDeltaFrom: Long? = null) {
        val safeDuration = duration.takeIf { it > 0 } ?: exoPlayer.duration.coerceAtLeast(0L)
        val target = if (safeDuration > 0) positionMs.coerceIn(0L, safeDuration) else positionMs.coerceAtLeast(0L)
        val (targetUri, shouldUseRemote) = resolveSourceForPosition(target)
        if (!targetUri.isNullOrBlank() && targetUri != activeSourceUri) {
            switchToSource(
                sourceUri = targetUri,
                useRemote = shouldUseRemote,
                seekMs = target
            )
        } else {
            exoPlayer.seekTo(target)
        }
        currentPosition = target
        dragPreviewPosition = -1L
        val delta = showDeltaFrom?.let { target - it }
        overlayText = when {
            delta == null -> formatTime(target)
            delta > 0 -> "+${delta / 1000}s"
            delta < 0 -> "${delta / 1000}s"
            else -> "0s"
        }
    }

    fun seekBy(deltaMs: Long) {
        seekTo(exoPlayer.currentPosition + deltaMs, exoPlayer.currentPosition)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(hasPlayableSource) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        if (!hasPlayableSource) return@detectTapGestures
                        when {
                            offset.x < size.width / 3f -> seekBy(-5000L)
                            offset.x > size.width * 2f / 3f -> seekBy(5000L)
                            else -> exoPlayer.playWhenReady = !exoPlayer.playWhenReady
                        }
                    },
                    onTap = { showControls = !showControls }
                )
            }
            .pointerInput(hasPlayableSource, duration) {
                gestureWidth = size.width.toFloat().coerceAtLeast(1f)
                detectDragGestures(
                    onDragStart = {
                        if (hasPlayableSource) {
                            dragAccumulatedX = 0f
                            dragAccumulatedY = 0f
                            isSeekGestureActive = false
                            ignoreCurrentDragGesture = false
                        }
                    },
                    onDrag = { change, dragAmount ->
                        if (!hasPlayableSource || ignoreCurrentDragGesture) return@detectDragGestures
                        dragAccumulatedX += dragAmount.x
                        dragAccumulatedY += dragAmount.y
                        val absoluteX = abs(dragAccumulatedX)
                        val absoluteY = abs(dragAccumulatedY)
                        val horizontalThreshold = viewConfiguration.touchSlop * 3f
                        if (!isSeekGestureActive) {
                            if (absoluteX < horizontalThreshold) return@detectDragGestures
                            if (absoluteX <= absoluteY * 1.8f) {
                                ignoreCurrentDragGesture = true
                                dragPreviewPosition = -1L
                                return@detectDragGestures
                            }
                            isSeekGestureActive = true
                            dragStartPosition = exoPlayer.currentPosition
                            dragPreviewPosition = exoPlayer.currentPosition
                            showControls = true
                        }
                        change.consume()
                        val safeDuration = (if (duration > 0) duration else exoPlayer.duration).coerceAtLeast(0L)
                        if (safeDuration <= 0L) return@detectDragGestures
                        val candidate = (dragPreviewPosition + (dragAmount.x / gestureWidth * safeDuration).toLong())
                            .coerceIn(0L, safeDuration)
                        dragPreviewPosition = candidate
                        val deltaSeconds = ((candidate - dragStartPosition) / 1000f).toInt()
                        overlayText = buildString {
                            append(if (deltaSeconds >= 0) "+" else "")
                            append("${deltaSeconds}s")
                            append("  ")
                            append(formatTime(candidate))
                        }
                    },
                    onDragEnd = {
                        if (isSeekGestureActive && dragPreviewPosition >= 0L) {
                            seekTo(dragPreviewPosition, dragStartPosition)
                        }
                        dragAccumulatedX = 0f
                        dragAccumulatedY = 0f
                        isSeekGestureActive = false
                        ignoreCurrentDragGesture = false
                    },
                    onDragCancel = {
                        dragPreviewPosition = -1L
                        dragAccumulatedX = 0f
                        dragAccumulatedY = 0f
                        isSeekGestureActive = false
                        ignoreCurrentDragGesture = false
                    }
                )
            }
    ) {
        if (hasPlayableSource) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("当前视频没有可播放的地址", color = Color.White)
            }
        }

        overlayText?.let { text ->
            Card(modifier = Modifier.align(Alignment.Center)) {
                Text(
                    text = text,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        if (showControls) {
            PlayerControls(
                title = vodName.ifBlank { "视频播放" },
                episodeLabel = episodeName.ifBlank { "第${episode + 1}集" },
                isPlayable = hasPlayableSource,
                isPlaying = isPlaying,
                currentPosition = displayPosition,
                duration = duration,
                selectedSpeed = speed,
                isPartialDownload = playbackSource?.isPartialDownload == true,
                onBack = onBack,
                onShowSpeed = { showSpeedSheet = true },
                onPlayPause = { if (hasPlayableSource) exoPlayer.playWhenReady = !exoPlayer.playWhenReady },
                onSeek = { position -> if (hasPlayableSource) seekTo(position) },
                onSeekBack = { seekBy(-5000L) },
                onSeekForward = { seekBy(5000L) }
            )
        }
    }

    if (showSpeedSheet) {
        ModalBottomSheet(onDismissRequest = { showSpeedSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("倍速", style = MaterialTheme.typography.titleLarge)
                playbackSpeeds.forEach { option ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            speed = option
                            exoPlayer.playbackParameters = PlaybackParameters(option)
                            coroutineScope.launch { playerSettingsStore.setPlaybackSpeed(option) }
                            showSpeedSheet = false
                            overlayText = "${option}x"
                        }
                    ) {
                        Text(
                            text = "${option}x",
                            modifier = Modifier.padding(16.dp),
                            color = if (option == speed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerControls(
    title: String,
    episodeLabel: String,
    isPlayable: Boolean,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    selectedSpeed: Float,
    isPartialDownload: Boolean,
    onBack: () -> Unit,
    onShowSpeed: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = title, color = Color.White, maxLines = 1)
                Text(
                    text = if (isPartialDownload) "$episodeLabel · 边下边放" else episodeLabel,
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onShowSpeed) {
                Icon(Icons.Default.Speed, contentDescription = "倍速", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(100.dp))

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = formatTime(currentPosition), color = Color.White)
                Text(text = "${selectedSpeed}x", color = Color.White)
                Text(text = formatTime(duration), color = Color.White)
            }

            Slider(
                value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                onValueChange = { progress -> onSeek((progress * duration).toLong()) },
                enabled = isPlayable && duration > 0,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSeekBack, enabled = isPlayable) {
                    Icon(Icons.Default.FastRewind, contentDescription = "Rewind", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(onClick = onPlayPause, enabled = isPlayable) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(onClick = onSeekForward, enabled = isPlayable) {
                    Icon(Icons.Default.FastForward, contentDescription = "Forward", tint = Color.White)
                }
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
