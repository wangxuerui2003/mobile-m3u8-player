package com.example.m3u8player.data.repository

import com.example.m3u8player.BuildConfig
import com.example.m3u8player.data.api.TmdbApi
import com.example.m3u8player.data.db.VideoDao
import com.example.m3u8player.data.model.RecommendationCacheEntity
import com.example.m3u8player.data.model.TmdbItem
import com.example.m3u8player.data.model.TmdbListResponse
import com.example.m3u8player.data.model.VideoListEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class RecommendationSection(
    val title: String,
    val subtitle: String,
    val items: List<RecommendationItem>
)

data class HomeRecommendationResult(
    val sections: List<RecommendationSection>,
    val hasStaleCache: Boolean = false
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
    private val videoRepository: VideoRepository,
    private val dao: VideoDao
) {
    private val gson = Gson()

    suspend fun getHomeRecommendations(forceRefresh: Boolean = false): HomeRecommendationResult =
        coroutineScope {
            val trending = async { loadTrendingSection(forceRefresh) }
            val topMovies = async {
                loadTmdbSection(
                    title = "高分电影",
                    subtitle = "按评分筛选，优先匹配本地可播资源",
                    cacheKey = CACHE_TOP_RATED_MOVIES,
                    ttlMs = TTL_TMDB_TOP_RATED,
                    type = RecommendationMediaType.MOVIE,
                    forceRefresh = forceRefresh,
                    fetch = { apiKey -> tmdbApi.getTopRatedMovies(apiKey) }
                )
            }
            val topTv = async {
                loadTmdbSection(
                    title = "高分剧集",
                    subtitle = "剧集榜单，点击未匹配条目会进入搜索",
                    cacheKey = CACHE_TOP_RATED_TV,
                    ttlMs = TTL_TMDB_TOP_RATED,
                    type = RecommendationMediaType.TV,
                    forceRefresh = forceRefresh,
                    fetch = { apiKey -> tmdbApi.getTopRatedTv(apiKey) }
                )
            }
            val okzywToday = async { loadOkzywTodaySection(forceRefresh) }

            val results = listOf(
                trending.await(),
                topMovies.await(),
                topTv.await(),
                okzywToday.await()
            )
            HomeRecommendationResult(
                sections = results.map { it.section }.filter { it.items.isNotEmpty() },
                hasStaleCache = results.any { it.isStale }
            )
        }

    private suspend fun loadTrendingSection(forceRefresh: Boolean): CachedSection {
        val apiKey = BuildConfig.TMDB_API_KEY.trim()
        if (apiKey.isBlank()) {
            return CachedSection(
                RecommendationSection("最近流行", "来自 TMDB 趋势榜，未收录内容会跳到搜索", emptyList())
            )
        }

        val (moviesResult, tvResult) = coroutineScope {
            val moviesDeferred = async {
                getCachedTmdbResponse(
                    key = CACHE_TRENDING_MOVIES,
                    ttlMs = TTL_TMDB_TRENDING,
                    forceRefresh = forceRefresh,
                    fetch = { tmdbApi.getTrendingMovies(apiKey) }
                )
            }
            val tvDeferred = async {
                getCachedTmdbResponse(
                    key = CACHE_TRENDING_TV,
                    ttlMs = TTL_TMDB_TRENDING,
                    forceRefresh = forceRefresh,
                    fetch = { tmdbApi.getTrendingTv(apiKey) }
                )
            }
            moviesDeferred.await() to tvDeferred.await()
        }

        val movies = moviesResult.value?.results.orEmpty()
            .take(9)
            .mapNotNull { it.toRecommendationItem(RecommendationMediaType.MOVIE) }
        val tv = tvResult.value?.results.orEmpty()
            .take(9)
            .mapNotNull { it.toRecommendationItem(RecommendationMediaType.TV) }

        return CachedSection(
            section = RecommendationSection(
                title = "最近流行",
                subtitle = "来自 TMDB 电影和剧集趋势榜，未收录内容会跳到搜索",
                items = (movies + tv).sortedByDescending { it.rating ?: 0.0 }
            ),
            isStale = moviesResult.isStale || tvResult.isStale
        )
    }

    private suspend fun loadTmdbSection(
        title: String,
        subtitle: String,
        cacheKey: String,
        ttlMs: Long,
        type: RecommendationMediaType,
        forceRefresh: Boolean,
        fetch: suspend (String) -> TmdbListResponse
    ): CachedSection {
        val apiKey = BuildConfig.TMDB_API_KEY.trim()
        if (apiKey.isBlank()) {
            return CachedSection(RecommendationSection(title, subtitle, emptyList()))
        }

        val cached = getCachedTmdbResponse(
            key = cacheKey,
            ttlMs = ttlMs,
            forceRefresh = forceRefresh,
            fetch = { fetch(apiKey) }
        )

        val items = cached.value?.results.orEmpty()
            .take(18)
            .mapNotNull { tmdbItem ->
                tmdbItem.toRecommendationItem(type)
            }

        return CachedSection(RecommendationSection(title, subtitle, items), cached.isStale)
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

    private suspend fun loadOkzywTodaySection(forceRefresh: Boolean): CachedSection {
        val cached = getCachedOkzywToday(forceRefresh)
        val items = cached.value
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

        return CachedSection(
            section = RecommendationSection(
                title = "今日更新",
                subtitle = "OK资源网最近 24 小时新增和更新",
                items = items
            ),
            isStale = cached.isStale
        )
    }

    private suspend fun getCachedTmdbResponse(
        key: String,
        ttlMs: Long,
        forceRefresh: Boolean,
        fetch: suspend () -> TmdbListResponse
    ): CachedValue<TmdbListResponse?> {
        return getCachedValue(
            key = key,
            ttlMs = ttlMs,
            forceRefresh = forceRefresh,
            decode = { json -> gson.fromJson(json, TmdbListResponse::class.java) },
            encode = { value -> gson.toJson(value) },
            fallback = null,
            fetch = fetch
        )
    }

    private suspend fun getCachedOkzywToday(forceRefresh: Boolean): CachedValue<List<VideoListEntity>> {
        val listType = object : TypeToken<List<VideoListEntity>>() {}.type
        return getCachedValue(
            key = CACHE_OKZYW_TODAY,
            ttlMs = TTL_OKZYW_TODAY,
            forceRefresh = forceRefresh,
            decode = { json -> gson.fromJson<List<VideoListEntity>>(json, listType).orEmpty() },
            encode = { value -> gson.toJson(value, listType) },
            fallback = videoRepository.getLatestVideos(limit = 20),
            fetch = {
                videoRepository.fetchTodayUpdates(limit = 20)
                    .getOrThrow()
            }
        )
    }

    private suspend fun <T> getCachedValue(
        key: String,
        ttlMs: Long,
        forceRefresh: Boolean,
        decode: (String) -> T,
        encode: (T) -> String,
        fallback: T,
        fetch: suspend () -> T
    ): CachedValue<T> {
        val now = System.currentTimeMillis()
        val cache = dao.getRecommendationCache(key)
        val cachedValue = cache?.let { runCatching { decode(it.payloadJson) }.getOrNull() }
        val isFresh = cache != null && now - cache.updatedAt <= ttlMs

        if (!forceRefresh && isFresh && cachedValue != null) {
            return CachedValue(cachedValue)
        }

        return runCatching { fetch() }
            .map { fresh ->
                dao.setRecommendationCache(
                    RecommendationCacheEntity(
                        key = key,
                        payloadJson = encode(fresh),
                        updatedAt = now
                    )
                )
                CachedValue(fresh)
            }
            .getOrElse {
                if (cachedValue != null) {
                    CachedValue(cachedValue, isStale = true)
                } else {
                    CachedValue(fallback, isStale = true)
                }
            }
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

    private data class CachedSection(
        val section: RecommendationSection,
        val isStale: Boolean = false
    )

    private data class CachedValue<T>(
        val value: T,
        val isStale: Boolean = false
    )

    companion object {
        private const val CACHE_TRENDING_MOVIES = "tmdb_trending_movies_week_zh_cn"
        private const val CACHE_TRENDING_TV = "tmdb_trending_tv_week_zh_cn"
        private const val CACHE_TOP_RATED_MOVIES = "tmdb_top_rated_movies_zh_cn"
        private const val CACHE_TOP_RATED_TV = "tmdb_top_rated_tv_zh_cn"
        private const val CACHE_OKZYW_TODAY = "okzyw_today_updates"

        private val TTL_TMDB_TRENDING = TimeUnit.HOURS.toMillis(12)
        private val TTL_TMDB_TOP_RATED = TimeUnit.HOURS.toMillis(24)
        private val TTL_OKZYW_TODAY = TimeUnit.HOURS.toMillis(1)
    }
}
