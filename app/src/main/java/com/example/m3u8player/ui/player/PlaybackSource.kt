package com.example.m3u8player.ui.player

data class PlaybackSource(
    val uri: String,
    val isLocal: Boolean,
    val remoteUrl: String? = null,
    val localPlaylistPath: String? = null,
    val isPartialDownload: Boolean = false,
    val downloadedDurationMs: Long = 0L,
    val totalDurationMs: Long = 0L
)
