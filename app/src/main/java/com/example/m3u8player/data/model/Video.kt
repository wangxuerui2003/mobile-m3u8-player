package com.example.m3u8player.data.model

import com.google.gson.annotations.SerializedName

data class VideoListResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("msg")
    val message: String,
    @SerializedName("page")
    val page: Int,
    @SerializedName("pagecount")
    val pageCount: Int,
    @SerializedName("limit")
    val limit: String,
    @SerializedName("total")
    val total: Int,
    @SerializedName("list")
    val list: List<VideoItem>,
    @SerializedName("class")
    val categories: List<Category>
)

data class VideoItem(
    @SerializedName("vod_id")
    val vodId: Int,
    @SerializedName("vod_name")
    val vodName: String,
    @SerializedName("type_id")
    val typeId: Int,
    @SerializedName("type_name")
    val typeName: String,
    @SerializedName("vod_en")
    val vodEn: String,
    @SerializedName("vod_time")
    val vodTime: String,
    @SerializedName("vod_remarks")
    val vodRemarks: String,
    @SerializedName("vod_play_from")
    val vodPlayFrom: String
)

data class VideoDetailResponse(
    @SerializedName("code")
    val code: Int,
    @SerializedName("msg")
    val message: String,
    @SerializedName("list")
    val list: List<VideoDetail>
)

data class VideoDetail(
    @SerializedName("vod_id")
    val vodId: Int,
    @SerializedName("vod_name")
    val vodName: String,
    @SerializedName("vod_pic")
    val vodPic: String,
    @SerializedName("vod_actor")
    val vodActor: String,
    @SerializedName("vod_director")
    val vodDirector: String,
    @SerializedName("vod_area")
    val vodArea: String,
    @SerializedName("vod_year")
    val vodYear: String,
    @SerializedName("vod_time")
    val vodTime: String,
    @SerializedName("vod_play_from")
    val vodPlayFrom: String,
    @SerializedName("vod_play_url")
    val vodPlayUrl: String,
    @SerializedName("type_name")
    val typeName: String,
    @SerializedName("vod_content")
    val vodContent: String,
    @SerializedName("type_id")
    val typeId: Int? = null
)

data class Category(
    @SerializedName("type_id")
    val typeId: Int,
    @SerializedName("type_name")
    val typeName: String
)
