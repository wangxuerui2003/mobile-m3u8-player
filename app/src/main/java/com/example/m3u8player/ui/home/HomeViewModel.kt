package com.example.m3u8player.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m3u8player.data.model.DownloadEntity
import com.example.m3u8player.data.model.DownloadStatus
import com.example.m3u8player.data.model.EpisodeWatchHistoryEntity
import com.example.m3u8player.data.model.WatchHistoryEntity
import com.example.m3u8player.data.repository.VideoRepository
import com.example.m3u8player.ui.detail.Episode
import com.example.m3u8player.ui.detail.parseEpisodes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecentEpisodeItem(
    val episode: Int,
    val episodeName: String,
    val progressMs: Long,
    val durationMs: Long,
    val coverUrl: String?,
    val coverPath: String?,
    val lastWatchedAt: Long,
    val download: DownloadEntity? = null
)

data class RecentWatchGroup(
    val vodId: Int,
    val vodName: String,
    val latestEpisode: Int,
    val latestEpisodeName: String,
    val coverUrl: String?,
    val coverPath: String?,
    val progressMs: Long,
    val durationMs: Long,
    val watchedEpisodeCount: Int,
    val episodes: List<RecentEpisodeItem>
)

data class HomePlaybackRequest(
    val vodId: Int,
    val vodName: String,
    val episode: Int,
    val episodeName: String,
    val coverUrl: String?,
    val startPositionMs: Long,
    val videoUrl: String? = null,
    val localPlaylistPath: String? = null,
    val isPartialDownload: Boolean = false,
    val downloadedDurationMs: Long = 0L,
    val totalDurationMs: Long = 0L
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: VideoRepository
) : ViewModel() {

    private val episodeCatalogs = MutableStateFlow<Map<Int, List<Episode>>>(emptyMap())
    private val loadingVodIds = MutableStateFlow<Set<Int>>(emptySet())
    private val _expandedVodIds = MutableStateFlow<Set<Int>>(emptySet())
    val expandedVodIds: StateFlow<Set<Int>> = _expandedVodIds.asStateFlow()

    private val _watchHistory = MutableStateFlow<List<RecentWatchGroup>>(emptyList())
    val watchHistory: StateFlow<List<RecentWatchGroup>> = _watchHistory.asStateFlow()

    private val _playbackRequests = MutableSharedFlow<HomePlaybackRequest>(extraBufferCapacity = 1)
    val playbackRequests: SharedFlow<HomePlaybackRequest> = _playbackRequests.asSharedFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.getWatchHistory(),
                repository.getEpisodeWatchHistory(),
                repository.getDownloads(),
                episodeCatalogs
            ) { summaries, episodeHistory, downloads, catalogs ->
                buildRecentWatchGroups(
                    summaries = summaries,
                    episodeHistory = episodeHistory,
                    downloads = downloads,
                    catalogs = catalogs
                )
            }.collect { groups ->
                _watchHistory.value = groups
            }
        }
    }

    fun toggleExpanded(vodId: Int) {
        val expanded = _expandedVodIds.value
        if (vodId in expanded) {
            _expandedVodIds.value = expanded - vodId
            return
        }

        _expandedVodIds.value = expanded + vodId
        if (vodId !in episodeCatalogs.value && vodId !in loadingVodIds.value) {
            loadEpisodeCatalog(vodId)
        }
    }

    fun playRecentWatch(item: RecentWatchGroup) {
        playEpisode(
            vodId = item.vodId,
            vodName = item.vodName,
            episode = item.latestEpisode,
            episodeName = item.latestEpisodeName,
            coverUrl = item.coverUrl,
            startPositionMs = item.progressMs,
            totalDurationMs = item.durationMs
        )
    }

    fun playRecentEpisode(group: RecentWatchGroup, item: RecentEpisodeItem) {
        playEpisode(
            vodId = group.vodId,
            vodName = group.vodName,
            episode = item.episode,
            episodeName = item.episodeName,
            coverUrl = item.coverUrl ?: group.coverUrl,
            startPositionMs = item.progressMs,
            totalDurationMs = item.durationMs
        )
    }

    private fun loadEpisodeCatalog(vodId: Int) {
        viewModelScope.launch {
            loadingVodIds.value = loadingVodIds.value + vodId
            val episodes = repository.getVideoDetail(vodId)
                ?.let { parseEpisodes(it.vodPlayUrl) }
                .orEmpty()
            episodeCatalogs.value = episodeCatalogs.value + (vodId to episodes)
            loadingVodIds.value = loadingVodIds.value - vodId
        }
    }

    private fun playEpisode(
        vodId: Int,
        vodName: String,
        episode: Int,
        episodeName: String,
        coverUrl: String?,
        startPositionMs: Long,
        totalDurationMs: Long
    ) {
        viewModelScope.launch {
            val download = repository.findDownloadByVodIdAndEpisode(vodId, episode)
            if (!download?.localPath.isNullOrBlank()) {
                _playbackRequests.emit(
                    HomePlaybackRequest(
                        vodId = vodId,
                        vodName = vodName,
                        episode = episode,
                        episodeName = episodeName,
                        coverUrl = coverUrl,
                        startPositionMs = startPositionMs,
                        videoUrl = download?.videoUrl,
                        localPlaylistPath = download?.localPath,
                        isPartialDownload = download?.status != DownloadStatus.COMPLETED,
                        downloadedDurationMs = download?.downloadedDurationMs ?: 0L,
                        totalDurationMs = maxOf(download?.totalDurationMs ?: 0L, totalDurationMs)
                    )
                )
                return@launch
            }

            val cachedEpisode = episodeCatalogs.value[vodId]
                ?.firstOrNull { it.index == episode }
            val remoteEpisode = cachedEpisode
                ?: repository.getVideoDetail(vodId)
                    ?.let { parseEpisodes(it.vodPlayUrl) }
                    ?.firstOrNull { it.index == episode }

            if (remoteEpisode?.url.isNullOrBlank()) {
                _messages.emit("该剧集没有可恢复的播放地址")
                return@launch
            }

            _playbackRequests.emit(
                HomePlaybackRequest(
                    vodId = vodId,
                    vodName = vodName,
                    episode = episode,
                    episodeName = remoteEpisode?.name ?: episodeName,
                    coverUrl = coverUrl,
                    startPositionMs = startPositionMs,
                    videoUrl = remoteEpisode?.url,
                    totalDurationMs = totalDurationMs
                )
            )
        }
    }
}

private fun buildRecentWatchGroups(
    summaries: List<WatchHistoryEntity>,
    episodeHistory: List<EpisodeWatchHistoryEntity>,
    downloads: List<DownloadEntity>,
    catalogs: Map<Int, List<Episode>>
): List<RecentWatchGroup> {
    val episodeHistoryByVodId = episodeHistory.groupBy { it.vodId }
    val downloadsByVodId = downloads.groupBy { it.vodId }

    return summaries.map { summary ->
        val seriesHistory = episodeHistoryByVodId[summary.vodId].orEmpty()
        val seriesDownloads = downloadsByVodId[summary.vodId].orEmpty()
        val episodes = buildRecentEpisodeItems(
            summary = summary,
            episodeHistory = seriesHistory,
            downloads = seriesDownloads,
            catalog = catalogs[summary.vodId].orEmpty()
        )

        RecentWatchGroup(
            vodId = summary.vodId,
            vodName = summary.vodName,
            latestEpisode = summary.episode,
            latestEpisodeName = summary.episodeName.ifBlank { "第${summary.episode + 1}集" },
            coverUrl = summary.coverUrl,
            coverPath = summary.coverPath,
            progressMs = summary.progressMs,
            durationMs = summary.durationMs,
            watchedEpisodeCount = seriesHistory.size.coerceAtLeast(if (episodes.isNotEmpty()) 1 else 0),
            episodes = episodes
        )
    }
}

private fun buildRecentEpisodeItems(
    summary: WatchHistoryEntity,
    episodeHistory: List<EpisodeWatchHistoryEntity>,
    downloads: List<DownloadEntity>,
    catalog: List<Episode>
): List<RecentEpisodeItem> {
    val historyByEpisode = episodeHistory.associateBy { it.episode }
    val downloadByEpisode = downloads.associateBy { it.episode }

    val baseEpisodes = if (catalog.isNotEmpty()) {
        catalog.map { episode ->
            RecentEpisodeItem(
                episode = episode.index,
                episodeName = episode.name.ifBlank { "第${episode.index + 1}集" },
                progressMs = historyByEpisode[episode.index]?.progressMs ?: 0L,
                durationMs = historyByEpisode[episode.index]?.durationMs
                    ?: downloadByEpisode[episode.index]?.totalDurationMs
                    ?: 0L,
                coverUrl = historyByEpisode[episode.index]?.coverUrl ?: summary.coverUrl,
                coverPath = historyByEpisode[episode.index]?.coverPath ?: summary.coverPath,
                lastWatchedAt = historyByEpisode[episode.index]?.lastWatchedAt ?: 0L,
                download = downloadByEpisode[episode.index]
            )
        }
    } else {
        (historyByEpisode.keys + downloadByEpisode.keys + setOf(summary.episode))
            .toSortedSet()
            .map { episode ->
                val history = historyByEpisode[episode]
                val download = downloadByEpisode[episode]
                RecentEpisodeItem(
                    episode = episode,
                    episodeName = history?.episodeName
                        ?: download?.episodeName
                        ?: if (episode == summary.episode) {
                            summary.episodeName.ifBlank { "第${episode + 1}集" }
                        } else {
                            "第${episode + 1}集"
                        },
                    progressMs = history?.progressMs ?: if (episode == summary.episode) summary.progressMs else 0L,
                    durationMs = history?.durationMs
                        ?: download?.totalDurationMs
                        ?: if (episode == summary.episode) summary.durationMs else 0L,
                    coverUrl = history?.coverUrl ?: summary.coverUrl,
                    coverPath = history?.coverPath ?: summary.coverPath,
                    lastWatchedAt = history?.lastWatchedAt ?: if (episode == summary.episode) summary.lastWatchedAt else 0L,
                    download = download
                )
            }
    }

    return baseEpisodes.sortedWith(
        compareByDescending<RecentEpisodeItem> { it.episode == summary.episode }
            .thenBy { it.episode }
    )
}
