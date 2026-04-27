package com.example.m3u8player.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m3u8player.data.model.WatchHistoryEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onVideoClick: (Int) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val watchHistory by viewModel.watchHistory.collectAsState(initial = emptyList())
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("首页") }
            )
        }
    ) { paddingValues ->
        if (watchHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无观看记录")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "最近观看",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                items(watchHistory) { history ->
                    WatchHistoryItem(
                        history = history,
                        onClick = { onVideoClick(history.vodId) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchHistoryItem(
    history: WatchHistoryEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = history.vodName,
                    style = MaterialTheme.typography.titleMedium
                )
                
                if (history.durationMs > 0) {
                    val progress = (history.progressMs.toFloat() / history.durationMs * 100).toInt()
                    LinearProgressIndicator(
                        progress = progress / 100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "已观看 ${progress}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
