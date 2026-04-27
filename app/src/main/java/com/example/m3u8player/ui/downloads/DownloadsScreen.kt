package com.example.m3u8player.ui.downloads

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
import com.example.m3u8player.data.model.DownloadEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onVideoClick: (Int) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val downloads by viewModel.downloads.collectAsState(initial = emptyList())
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("下载") }
            )
        }
    ) { paddingValues ->
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无下载")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(downloads) { download ->
                    DownloadItem(
                        download = download,
                        onClick = { onVideoClick(download.vodId) },
                        onDelete = { viewModel.deleteDownload(download.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadItem(
    download: DownloadEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = download.vodName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "状态: ${getStatusText(download.status)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
            }
            
            if (download.status == "downloading") {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = download.progress / 100f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${download.progress.toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { 
                Column {
                    Text("确定要删除这条下载记录吗？")
                    if (download.status == "completed") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("注意：这也会删除本地下载的文件", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
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

fun getStatusText(status: String): String {
    return when (status) {
        "pending" -> "等待中"
        "downloading" -> "下载中"
        "completed" -> "已完成"
        "failed" -> "失败"
        else -> status
    }
}
