package com.example.m3u8player.ui.downloads

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.m3u8player.data.DownloadSettingsStore
import com.example.m3u8player.data.model.DownloadEntity
import com.example.m3u8player.data.model.DownloadStatus
import com.example.m3u8player.service.DownloadNotificationHelper
import com.example.m3u8player.ui.player.PlayerActivity
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onVideoClick: (Int) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val downloads by viewModel.downloads.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val concurrency by viewModel.concurrency.collectAsState()
    val isSelectionMode = selectedIds.isNotEmpty()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var pendingDeleteIds by remember { mutableStateOf<Set<Long>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isSelectionMode) "已选择 ${selectedIds.size} 项" else "下载")
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "退出选择")
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = { viewModel.toggleSelectAll() }) {
                            Icon(
                                if (selectedIds.size == downloads.size) {
                                    Icons.Default.CheckBox
                                } else {
                                    Icons.Default.CheckBoxOutlineBlank
                                },
                                contentDescription = "全选"
                            )
                        }
                        IconButton(onClick = { viewModel.startSelectedDownloads() }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "批量开始")
                        }
                        IconButton(onClick = { viewModel.pauseSelectedDownloads() }) {
                            Icon(Icons.Default.Pause, contentDescription = "批量暂停")
                        }
                        IconButton(onClick = { pendingDeleteIds = selectedIds }) {
                            Icon(Icons.Default.Delete, contentDescription = "批量删除")
                        }
                    } else {
                        IconButton(onClick = { showSettingsSheet = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "下载设置")
                        }
                    }
                }
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(downloads, key = { it.id }) { download ->
                    DownloadItem(
                        download = download,
                        selected = download.id in selectedIds,
                        selectionMode = isSelectionMode,
                        onClick = {
                            if (isSelectionMode) {
                                viewModel.toggleSelection(download.id)
                            } else if (!download.localPath.isNullOrBlank() && download.status in setOf(
                                    DownloadStatus.COMPLETED,
                                    DownloadStatus.DOWNLOADING,
                                    DownloadStatus.PAUSED
                                )
                            ) {
                                context.startActivity(
                                    Intent(context, PlayerActivity::class.java).apply {
                                        putExtra("vodId", download.vodId)
                                        putExtra("vodName", download.vodName)
                                        putExtra("episode", download.episode)
                                        putExtra("episodeName", download.episodeName)
                                        putExtra("coverUrl", download.coverUrl)
                                        putExtra("videoUrl", download.videoUrl)
                                        putExtra("localPlaylistPath", download.localPath)
                                        putExtra("startPositionMs", 0L)
                                        putExtra("isPartialDownload", download.status != DownloadStatus.COMPLETED)
                                        putExtra("downloadedDurationMs", download.downloadedDurationMs)
                                        putExtra("totalDurationMs", download.totalDurationMs)
                                    }
                                )
                            } else {
                                onVideoClick(download.vodId)
                            }
                        },
                        onLongClick = {
                            viewModel.enterSelection(download.id)
                        },
                        onToggleSelection = {
                            viewModel.toggleSelection(download.id)
                        },
                        onStart = { viewModel.startDownload(download.id) },
                        onPause = { viewModel.pauseDownload(download.id) },
                        onDelete = { pendingDeleteIds = setOf(download.id) }
                    )
                }
            }
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "下载并发数",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                (DownloadSettingsStore.MIN_CONCURRENCY..DownloadSettingsStore.MAX_CONCURRENCY).forEach { option ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            viewModel.setConcurrency(option)
                            showSettingsSheet = false
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("$option 个并发")
                            if (option == concurrency) {
                                Text("当前", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    pendingDeleteIds?.let { ids ->
        AlertDialog(
            onDismissRequest = { pendingDeleteIds = null },
            title = { Text("确认删除") },
            text = {
                Text(
                    if (ids.size == 1) {
                        "确定要删除这条下载记录吗？如正在下载，会先停止任务。"
                    } else {
                        "确定要删除选中的 ${ids.size} 条下载记录吗？如有正在下载的项目，会先停止任务。"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDownloads(ids)
                        pendingDeleteIds = null
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteIds = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DownloadItem(
    download: DownloadEntity,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelection: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelection() }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            DownloadCover(download = download)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.vodName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (download.episodeName.isNotBlank()) {
                    Text(
                        text = download.episodeName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "状态: ${getStatusText(download.status)}",
                    style = MaterialTheme.typography.bodySmall
                )

                if (download.totalSegments > 0 || download.progress > 0f) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = (download.progress / 100f).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            append("${download.progress.roundToInt()}%")
                            if (download.totalSegments > 0) {
                                append(" · ${download.downloadedSegments}/${download.totalSegments} 片段")
                            }
                            if (download.status == DownloadStatus.DOWNLOADING) {
                                append(" · ${DownloadNotificationHelper.formatSpeed(download.speedBytesPerSec)}")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                when (download.status) {
                    DownloadStatus.QUEUED,
                    DownloadStatus.PAUSED,
                    DownloadStatus.FAILED -> {
                        IconButton(onClick = onStart) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "开始")
                        }
                    }

                    DownloadStatus.DOWNLOADING -> {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Default.Pause, contentDescription = "暂停")
                        }
                    }

                    DownloadStatus.COMPLETED -> Unit
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
            }
        }
    }
}

@Composable
private fun DownloadCover(download: DownloadEntity) {
    val imageModel = when {
        !download.coverPath.isNullOrBlank() -> File(download.coverPath)
        !download.coverUrl.isNullOrBlank() -> download.coverUrl
        else -> null
    }

    if (imageModel != null) {
        AsyncImage(
            model = imageModel,
            contentDescription = null,
            modifier = Modifier
                .size(width = 72.dp, height = 96.dp)
                .clip(MaterialTheme.shapes.medium),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 96.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(Color(0xFFDAE7F2)),
            contentAlignment = Alignment.Center
        ) {
            Text("封面", style = MaterialTheme.typography.bodySmall)
        }
    }
}

fun getStatusText(status: String): String {
    return when (status) {
        DownloadStatus.QUEUED -> "等待中"
        DownloadStatus.DOWNLOADING -> "下载中"
        DownloadStatus.PAUSED -> "已暂停"
        DownloadStatus.COMPLETED -> "已完成"
        DownloadStatus.FAILED -> "失败"
        else -> status
    }
}
