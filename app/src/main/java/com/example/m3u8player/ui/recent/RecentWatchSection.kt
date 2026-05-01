@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.m3u8player.ui.recent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.m3u8player.data.model.DownloadStatus
import java.io.File
import kotlin.math.roundToInt

@Composable
fun RecentWatchSection(
    watchHistory: List<RecentWatchGroup>,
    expandedVodIds: Set<Int>,
    onPlay: (RecentWatchGroup) -> Unit,
    onToggleExpanded: (Int) -> Unit,
    onPlayEpisode: (RecentWatchGroup, RecentEpisodeItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            "最近观看",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (watchHistory.isEmpty()) {
            Text(
                text = "暂无观看记录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }

        watchHistory.forEach { history ->
            WatchHistoryGroupCard(
                group = history,
                expanded = history.vodId in expandedVodIds,
                onPlay = { onPlay(history) },
                onToggleExpanded = { onToggleExpanded(history.vodId) },
                onPlayEpisode = { episode -> onPlayEpisode(history, episode) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun WatchHistoryGroupCard(
    group: RecentWatchGroup,
    expanded: Boolean,
    onPlay: () -> Unit,
    onToggleExpanded: () -> Unit,
    onPlayEpisode: (RecentEpisodeItem) -> Unit
) {
    val progressFraction = progressFraction(group.progressMs, group.durationMs)
    val coverModel = when {
        !group.coverPath.isNullOrBlank() -> File(group.coverPath)
        !group.coverUrl.isNullOrBlank() -> group.coverUrl
        else -> null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onPlay
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (coverModel != null) {
                    AsyncImage(
                        model = coverModel,
                        contentDescription = null,
                        modifier = Modifier
                            .size(width = 76.dp, height = 102.dp)
                            .clip(MaterialTheme.shapes.medium),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(width = 76.dp, height = 102.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(Color(0xFFDAE7F2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("封面", color = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.vodName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "看到 ${group.latestEpisodeName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = progressFraction,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "已观看 ${progressFraction.times(100).roundToInt()}% · 共 ${group.watchedEpisodeCount} 集有记录",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${formatDuration(group.progressMs)} / ${formatDuration(group.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onToggleExpanded) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起剧集" else "展开剧集"
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                group.episodes.forEachIndexed { index, episode ->
                    EpisodeProgressRow(
                        item = episode,
                        onClick = { onPlayEpisode(episode) }
                    )
                    if (index != group.episodes.lastIndex) {
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeProgressRow(
    item: RecentEpisodeItem,
    onClick: () -> Unit
) {
    val progressFraction = progressFraction(item.progressMs, item.durationMs)
    val isDownloaded = item.download != null
    val isPartial = item.download?.status != null && item.download.status != DownloadStatus.COMPLETED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.episodeName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = progressFraction,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))

            val statusText = when {
                isDownloaded && isPartial -> "边下边放"
                isDownloaded -> "已下载"
                item.progressMs > 0 -> "已观看"
                else -> "未观看"
            }

            Text(
                text = "$statusText · ${formatDuration(item.progressMs)} / ${formatDuration(item.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Icon(
            imageVector = if (isDownloaded) Icons.Default.DownloadDone else Icons.Default.PlayArrow,
            contentDescription = null,
            tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun progressFraction(progressMs: Long, durationMs: Long): Float {
    return if (durationMs > 0) {
        (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
}

private fun formatDuration(durationMs: Long): String {
    val safe = durationMs.coerceAtLeast(0L)
    val totalSeconds = safe / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
