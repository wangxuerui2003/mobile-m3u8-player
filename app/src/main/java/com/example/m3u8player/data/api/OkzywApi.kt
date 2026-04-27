package com.example.m3u8player.data.api

import com.example.m3u8player.data.model.VideoDetailResponse
import com.example.m3u8player.data.model.VideoListResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface OkzywApi {
    
    @GET("api.php/provide/vod/")
    suspend fun getVideoList(
        @Query("ac") action: String = "list",
        @Query("pg") page: Int = 1
    ): VideoListResponse
    
    @GET("api.php/provide/vod/")
    suspend fun getVideoDetail(
        @Query("ac") action: String = "detail",
        @Query("ids") vodId: Int
    ): VideoDetailResponse
}
