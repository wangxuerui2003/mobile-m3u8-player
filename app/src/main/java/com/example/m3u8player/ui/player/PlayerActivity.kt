package com.example.m3u8player.ui.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.m3u8player.ui.theme.M3U8PlayerTheme

class PlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val vodId = intent.getIntExtra("vodId", 0)
        val episode = intent.getIntExtra("episode", 0)
        val vodName = intent.getStringExtra("vodName").orEmpty()
        val episodeName = intent.getStringExtra("episodeName").orEmpty()
        val coverUrl = intent.getStringExtra("coverUrl")
        val videoUrl = intent.getStringExtra("videoUrl")
        
        setContent {
            M3U8PlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PlayerScreen(
                        vodId = vodId,
                        vodName = vodName,
                        episode = episode,
                        episodeName = episodeName,
                        coverUrl = coverUrl,
                        videoUrl = videoUrl,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}
