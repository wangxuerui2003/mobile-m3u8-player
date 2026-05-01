package com.example.m3u8player.data.api

import com.example.m3u8player.data.model.TmdbListResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface TmdbApi {
    @GET("trending/movie/week")
    suspend fun getTrendingMovies(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "zh-CN"
    ): TmdbListResponse

    @GET("trending/tv/week")
    suspend fun getTrendingTv(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "zh-CN"
    ): TmdbListResponse

    @GET("movie/top_rated")
    suspend fun getTopRatedMovies(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "zh-CN",
        @Query("region") region: String = "CN",
        @Query("page") page: Int = 1
    ): TmdbListResponse

    @GET("tv/top_rated")
    suspend fun getTopRatedTv(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "zh-CN",
        @Query("page") page: Int = 1
    ): TmdbListResponse
}
