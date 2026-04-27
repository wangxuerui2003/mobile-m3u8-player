package com.example.m3u8player

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class M3U8PlayerApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
    }
}
