package com.example.m3u8player.ui.recent

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentWatchScreen(
    onPlayRequest: (RecentPlaybackRequest) -> Unit,
    viewModel: RecentWatchViewModel = hiltViewModel()
) {
    val watchHistory by viewModel.watchHistory.collectAsState()
    val expandedVodIds by viewModel.expandedVodIds.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.playbackRequests.collect(onPlayRequest)
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("最近观看") })
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                RecentWatchSection(
                    watchHistory = watchHistory,
                    expandedVodIds = expandedVodIds,
                    onPlay = viewModel::playRecentWatch,
                    onToggleExpanded = viewModel::toggleExpanded,
                    onPlayEpisode = viewModel::playRecentEpisode
                )
            }
        }
    }
}
