package com.example.m3u8player.ui.watchlater

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m3u8player.data.model.WatchLaterEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchLaterScreen(
    onVideoClick: (Int) -> Unit,
    viewModel: WatchLaterViewModel = hiltViewModel()
) {
    val watchLater by viewModel.watchLater.collectAsState(initial = emptyList())
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("稍后再看") }
            )
        }
    ) { paddingValues ->
        if (watchLater.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无稍后再看的视频")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(watchLater) { item ->
                    WatchLaterItem(
                        item = item,
                        onClick = { onVideoClick(item.vodId) },
                        onDelete = { viewModel.deleteFromWatchLater(item.vodId) }
                    )
                }
            }
        }
    }
}

@Composable
fun WatchLaterItem(
    item: WatchLaterEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "视频 ID: ${item.vodId}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "添加时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(item.addedAt)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(Icons.Default.Delete, contentDescription = "删除")
            }
        }
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除这条记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
