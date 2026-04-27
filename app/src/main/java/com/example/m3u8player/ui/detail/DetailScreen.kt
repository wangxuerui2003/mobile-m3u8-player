package com.example.m3u8player.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m3u8player.data.model.VideoDetail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    vodId: Int,
    onBack: () -> Unit,
    onPlay: (Int, Int) -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val videoDetail by viewModel.videoDetail.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isWatchLater by viewModel.isWatchLater.collectAsState()
    
    LaunchedEffect(vodId) {
        viewModel.loadVideoDetail(vodId)
        viewModel.checkWatchLater(vodId)
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                DetailContent(
                    detail = videoDetail!!,
                    isWatchLater = isWatchLater,
                    onPlay = { episode -> onPlay(vodId, episode) },
                    onToggleWatchLater = { viewModel.toggleWatchLater(vodId) },
                    onDownload = { viewModel.startDownload(vodId, videoDetail!!) }
                )
            }
        }
    }
}

@Composable
fun DetailContent(
    detail: VideoDetail,
    isWatchLater: Boolean,
    onPlay: (Int) -> Unit,
    onToggleWatchLater: () -> Unit,
    onDownload: () -> Unit
) {
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
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = { },
                label = { Text(detail.typeName) }
            )
            AssistChip(
                onClick = { },
                label = { Text(detail.vodArea) }
            )
            AssistChip(
                onClick = { },
                label = { Text(detail.vodYear) }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "演员: ${detail.vodActor}",
            style = MaterialTheme.typography.bodyMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "导演: ${detail.vodDirector}",
            style = MaterialTheme.typography.bodyMedium
        )
        
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
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onPlay(0) },
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
                onClick = onDownload,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("下载")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Episodes (if TV series)
        val episodes = parseEpisodes(detail.vodPlayUrl)
        if (episodes.size > 1) {
            Text(
                text = "选集",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            episodes.chunked(5).forEach { chunk ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    chunk.forEach { episode ->
                        OutlinedButton(
                            onClick = { onPlay(episode.index) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(episode.name)
                        }
                    }
                    // Fill remaining space
                    repeat(5 - chunk.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
