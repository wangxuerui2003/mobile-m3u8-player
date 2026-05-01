package com.example.m3u8player.data.repository

import com.example.m3u8player.BuildConfig
import com.example.m3u8player.data.api.TmdbApi
import com.example.m3u8player.data.model.TmdbItem
import com.example.m3u8player.data.model.VideoListEntity
import javax.inject.Inject
import javax.inject.Singleton

data class RecommendationSection(
    val title: String,
    val subtitle: String,
    val items: List<RecommendationItem>
)

data class RecommendationItem(
    val title: String,
    val originalTitle: String?,
    val year: String?,
    val overview: String?,
    val posterUrl: String?,
    val rating: Double?,
    val sourceLabel: String,
    val mediaType: RecommendationMediaType,
    val matchedVodId: Int?,
    val matchedTypeName: String?,
    val matchedRemarks: String?,
    val searchQuery: String
)

enum class RecommendationMediaType {
    MOVIE,
    TV,
    OKZYW
}

@Singleton
class RecommendationRepository @Inject constructor(
    private val tmdbApi: TmdbApi,
    private val videoRepository: VideoRepository
) {
    suspend fun getHomeRecommendations(): List<RecommendationSection> {
        val sections = mutableListOf<RecommendationSection>()
        sections += loadTrendingSection()
        sections += loadTmdbSection(
            title = "高分电影",
            subtitle = "按评分筛选，优先匹配本地可播资源",
            type = RecommendationMediaType.MOVIE,
            fetch = { apiKey -> tmdbApi.getTopRatedMovies(apiKey).results }
        )
        sections += loadTmdbSection(
            title = "高分剧集",
            subtitle = "剧集榜单，点击未匹配条目会进入搜索",
            type = RecommendationMediaType.TV,
            fetch = { apiKey -> tmdbApi.getTopRatedTv(apiKey).results }
        )
        sections += loadOkzywTodaySection()
        return sections.filter { it.items.isNotEmpty() }
    }

    private suspend fun loadTrendingSection(): RecommendationSection {
        val apiKey = BuildConfig.TMDB_API_KEY.trim()
        if (apiKey.isBlank()) {
            return RecommendationSection("最近流行", "来自 TMDB 趋势榜，未收录内容会跳到搜索", emptyList())
        }

        val movies = runCatching { tmdbApi.getTrendingMovies(apiKey).results }
            .getOrDefault(emptyList())
            .take(9)
            .mapNotNull { it.toRecommendationItem(RecommendationMediaType.MOVIE) }
        val tv = runCatching { tmdbApi.getTrendingTv(apiKey).results }
            .getOrDefault(emptyList())
            .take(9)
            .mapNotNull { it.toRecommendationItem(RecommendationMediaType.TV) }

        return RecommendationSection(
            title = "最近流行",
            subtitle = "来自 TMDB 电影和剧集趋势榜，未收录内容会跳到搜索",
            items = (movies + tv).sortedByDescending { it.rating ?: 0.0 }
        )
    }

    private suspend fun loadTmdbSection(
        title: String,
        subtitle: String,
        type: RecommendationMediaType,
        fetch: suspend (String) -> List<TmdbItem>
    ): RecommendationSection {
        val apiKey = BuildConfig.TMDB_API_KEY.trim()
        if (apiKey.isBlank()) {
            return RecommendationSection(title, subtitle, emptyList())
        }

        val items = runCatching { fetch(apiKey) }
            .getOrDefault(emptyList())
            .take(18)
            .mapNotNull { tmdbItem ->
                tmdbItem.toRecommendationItem(type)
            }

        return RecommendationSection(title, subtitle, items)
    }

    private suspend fun TmdbItem.toRecommendationItem(type: RecommendationMediaType): RecommendationItem? {
        val displayTitle = title?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: return null
        val original = originalTitle?.takeIf { it.isNotBlank() }
            ?: originalName?.takeIf { it.isNotBlank() }
        val year = releaseDate?.take(4)
            ?: firstAirDate?.take(4)
        val match = findBestMatch(displayTitle, original, year, type)

        return RecommendationItem(
            title = displayTitle,
            originalTitle = original?.takeIf { it != displayTitle },
            year = year,
            overview = overview?.takeIf { it.isNotBlank() },
            posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
            rating = voteAverage.takeIf { it > 0.0 },
            sourceLabel = "TMDB",
            mediaType = type,
            matchedVodId = match?.vodId,
            matchedTypeName = match?.typeName,
            matchedRemarks = match?.vodRemarks,
            searchQuery = displayTitle
        )
    }

    private suspend fun loadOkzywTodaySection(): RecommendationSection {
        val remote = videoRepository.fetchTodayUpdates(limit = 20).getOrNull()
        val items = (remote ?: videoRepository.getLatestVideos(limit = 20))
            .map { video ->
                RecommendationItem(
                    title = video.vodName,
                    originalTitle = null,
                    year = video.vodTime.take(10).takeIf { it.isNotBlank() },
                    overview = null,
                    posterUrl = null,
                    rating = null,
                    sourceLabel = "OK资源网",
                    mediaType = RecommendationMediaType.OKZYW,
                    matchedVodId = video.vodId,
                    matchedTypeName = video.typeName,
                    matchedRemarks = video.vodRemarks,
                    searchQuery = video.vodName
                )
            }

        return RecommendationSection(
            title = "今日更新",
            subtitle = "OK资源网最近 24 小时新增和更新",
            items = items
        )
    }

    private suspend fun findBestMatch(
        title: String,
        originalTitle: String?,
        year: String?,
        type: RecommendationMediaType
    ): VideoListEntity? {
        val candidates = buildList {
            add(title)
            originalTitle?.let { add(it) }
            add(cleanTitle(title))
            originalTitle?.let { add(cleanTitle(it)) }
        }
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()
            .flatMap { query -> videoRepository.searchVideosSnapshot(query, limit = 12) }
            .distinctBy { it.vodId }

        if (candidates.isEmpty()) return null

        val preferredTypeIds = when (type) {
            RecommendationMediaType.MOVIE -> setOf(1, 6, 7, 8, 9, 10, 11, 12, 20, 39, 62)
            RecommendationMediaType.TV -> setOf(2, 13, 14, 15, 16, 17, 18, 19, 23)
            RecommendationMediaType.OKZYW -> emptySet()
        }

        return candidates.maxWithOrNull(
            compareBy<VideoListEntity> { candidate ->
                if (candidate.typeId in preferredTypeIds) 4 else 0
            }.thenBy { candidate ->
                if (!year.isNullOrBlank() && candidate.vodName.contains(year)) 2 else 0
            }.thenBy { candidate ->
                titleMatchScore(candidate.vodName, title)
            }.thenByDescending { candidate ->
                candidate.vodName.length
            }.thenBy { candidate ->
                candidate.vodTime
            }
        )
    }

    private fun cleanTitle(value: String): String {
        return value
            .replace(Regex("[\\s:：·,.，。!！?？()（）\\[\\]【】《》<>-]"), "")
            .replace(Regex("第[一二三四五六七八九十0-9]+季"), "")
            .replace(Regex("\\d{4}"), "")
    }

    private fun titleMatchScore(candidate: String, title: String): Int {
        if (candidate == title) return 10
        if (candidate.contains(title)) return 8
        val cleanedCandidate = cleanTitle(candidate)
        val cleanedTitle = cleanTitle(title)
        if (cleanedCandidate == cleanedTitle) return 7
        if (cleanedCandidate.contains(cleanedTitle)) return 5
        return 0
    }
}
