@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.m3u8player.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m3u8player.data.model.DownloadEntity
import com.example.m3u8player.data.model.DownloadStatus
import com.example.m3u8player.data.model.VideoDetail

@Composable
fun DetailScreen(
    vodId: Int,
    onBack: () -> Unit,
    onPlay: (Episode, VideoDetail) -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val videoDetail by viewModel.videoDetail.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isWatchLater by viewModel.isWatchLater.collectAsState()
    val episodeDownloads by viewModel.episodeDownloads.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDownloadSheet by remember { mutableStateOf(false) }

    LaunchedEffect(vodId) {
        viewModel.loadVideoDetail(vodId)
        viewModel.checkWatchLater(vodId)
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (videoDetail == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("加载失败")
            }
        } else {
            val detail = videoDetail!!
            val episodes = parseEpisodes(detail.vodPlayUrl)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                DetailContent(
                    detail = detail,
                    episodes = episodes,
                    isWatchLater = isWatchLater,
                    episodeDownloads = episodeDownloads,
                    onPlay = { episode -> onPlay(episode, detail) },
                    onToggleWatchLater = { viewModel.toggleWatchLater(vodId) },
                    onDownloadClick = {
                        if (episodes.size <= 1) {
                            val firstEpisode = episodes.firstOrNull() ?: Episode(0, "第1集", detail.vodPlayUrl)
                            viewModel.startDownload(vodId, detail, firstEpisode)
                        } else {
                            showDownloadSheet = true
                        }
                    },
                    onEpisodeDownload = { episode ->
                        viewModel.startDownload(vodId, detail, episode)
                    }
                )
            }

            if (showDownloadSheet) {
                EpisodeDownloadSheet(
                    episodes = episodes,
                    episodeDownloads = episodeDownloads,
                    onDismiss = { showDownloadSheet = false },
                    onDownloadSelected = { selectedEpisodes ->
                        viewModel.startDownloads(vodId, detail, selectedEpisodes)
                        showDownloadSheet = false
                    },
                    onDownloadSingle = { episode ->
                        viewModel.startDownload(vodId, detail, episode)
                    }
                )
            }
        }
    }
}

@Composable
fun DetailContent(
    detail: VideoDetail,
    episodes: List<Episode>,
    isWatchLater: Boolean,
    episodeDownloads: Map<Int, DownloadEntity>,
    onPlay: (Episode) -> Unit,
    onToggleWatchLater: () -> Unit,
    onDownloadClick: () -> Unit,
    onEpisodeDownload: (Episode) -> Unit
) {
    val firstEpisode = episodes.firstOrNull() ?: Episode(0, "第1集", detail.vodPlayUrl)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = detail.vodName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { }, label = { Text(detail.typeName) })
            AssistChip(onClick = { }, label = { Text(detail.vodArea) })
            AssistChip(onClick = { }, label = { Text(detail.vodYear) })
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "演员: ${detail.vodActor}", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "导演: ${detail.vodDirector}", style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "剧情简介",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = detail.vodContent.ifEmpty { "暂无简介" },
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onPlay(firstEpisode) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("播放")
            }

            OutlinedButton(
                onClick = onToggleWatchLater,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    if (isWatchLater) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isWatchLater) "已收藏" else "收藏")
            }

            OutlinedButton(
                onClick = onDownloadClick,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (episodes.size > 1) "选集下载" else "下载")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (episodes.size > 1) {
            Text(
                text = "选集",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            episodes.chunked(3).forEach { chunk ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    chunk.forEach { episode ->
                        val existingDownload = episodeDownloads[episode.index]
                        EpisodeButton(
                            episode = episode,
                            existingDownload = existingDownload,
                            modifier = Modifier.weight(1f),
                            onPlay = { onPlay(episode) },
                            onDownload = { onEpisodeDownload(episode) }
                        )
                    }
                    repeat(3 - chunk.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun EpisodeButton(
    episode: Episode,
    existingDownload: DownloadEntity?,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onPlay
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = episode.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = existingDownload?.let { downloadStateLabel(it.status) } ?: "可下载",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = onDownload,
                    enabled = existingDownload == null
                ) {
                    Icon(
                        imageVector = if (existingDownload == null) Icons.Default.Download else Icons.Default.Check,
                        contentDescription = if (existingDownload == null) "下载 ${episode.name}" else "已在下载列表"
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeDownloadSheet(
    episodes: List<Episode>,
    episodeDownloads: Map<Int, DownloadEntity>,
    onDismiss: () -> Unit,
    onDownloadSelected: (List<Episode>) -> Unit,
    onDownloadSingle: (Episode) -> Unit
) {
    val selectedMap = remember(episodes) {
        mutableStateMapOf<Int, Boolean>().apply {
            episodes.forEach { put(it.index, false) }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "选集下载",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        onDownloadSelected(episodes.filter { episodeDownloads[it.index] == null })
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("下载全部未入队剧集")
                }
                OutlinedButton(
                    onClick = {
                        val selectedEpisodes = episodes.filter { selectedMap[it.index] == true }
                        onDownloadSelected(selectedEpisodes)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("下载所选")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(episodes, key = { it.index }) { episode ->
                    val existingDownload = episodeDownloads[episode.index]
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (existingDownload == null) {
                                selectedMap[episode.index] = !(selectedMap[episode.index] ?: false)
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedMap[episode.index] == true,
                                    onCheckedChange = {
                                        if (existingDownload == null) {
                                            selectedMap[episode.index] = it
                                        }
                                    },
                                    enabled = existingDownload == null
                                )
                                Column {
                                    Text(episode.name, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = existingDownload?.let { downloadStateLabel(it.status) } ?: "未下载",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            TextButton(
                                onClick = { onDownloadSingle(episode) },
                                enabled = existingDownload == null
                            ) {
                                Text(if (existingDownload == null) "下载" else "已存在")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun downloadStateLabel(status: String): String {
    return when (status) {
        DownloadStatus.QUEUED -> "等待中"
        DownloadStatus.DOWNLOADING -> "下载中"
        DownloadStatus.PAUSED -> "已暂停"
        DownloadStatus.COMPLETED -> "已完成"
        DownloadStatus.FAILED -> "下载失败"
        else -> status
    }
}
