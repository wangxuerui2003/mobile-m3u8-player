package com.example.m3u8player.ui.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CastManager(private val context: Context) {
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()
    
    private var castContext: CastContext? = null
    
    fun initialize() {
        try {
            castContext = CastContext.getSharedInstance(context)
        } catch (e: Exception) {
            // Cast SDK not available or not configured
        }
    }
    
    fun castVideo(title: String, url: String) {
        try {
            val session = castContext?.sessionManager?.currentCastSession
            if (session != null) {
                val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
                    putString(MediaMetadata.KEY_TITLE, title)
                }
                
                val mediaInfo = MediaInfo.Builder(url)
                    .setContentType("application/x-mpegURL")
                    .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                    .setMetadata(metadata)
                    .build()
                
                val remoteMediaClient = session.remoteMediaClient
                remoteMediaClient?.load(
                    MediaLoadRequestData.Builder()
                        .setMediaInfo(mediaInfo)
                        .setAutoplay(true)
                        .build()
                )
                
                _isCasting.value = true
            }
        } catch (e: Exception) {
            // Handle error
        }
    }
    
    fun stopCast() {
        try {
            castContext?.sessionManager?.endCurrentSession(true)
            _isCasting.value = false
        } catch (e: Exception) {
            // Handle error
        }
    }
    
    fun cleanup() {
        // No-op
    }
}
