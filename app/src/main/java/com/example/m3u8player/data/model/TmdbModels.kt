package com.example.m3u8player.data.model

import com.google.gson.annotations.SerializedName

data class TmdbListResponse(
    @SerializedName("page")
    val page: Int = 1,
    @SerializedName("results")
    val results: List<TmdbItem> = emptyList(),
    @SerializedName("total_pages")
    val totalPages: Int = 0,
    @SerializedName("total_results")
    val totalResults: Int = 0
)

data class TmdbItem(
    @SerializedName("id")
    val id: Int,
    @SerializedName("title")
    val title: String? = null,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("original_title")
    val originalTitle: String? = null,
    @SerializedName("original_name")
    val originalName: String? = null,
    @SerializedName("overview")
    val overview: String? = null,
    @SerializedName("poster_path")
    val posterPath: String? = null,
    @SerializedName("backdrop_path")
    val backdropPath: String? = null,
    @SerializedName("release_date")
    val releaseDate: String? = null,
    @SerializedName("first_air_date")
    val firstAirDate: String? = null,
    @SerializedName("vote_average")
    val voteAverage: Double = 0.0,
    @SerializedName("media_type")
    val mediaType: String? = null
)
