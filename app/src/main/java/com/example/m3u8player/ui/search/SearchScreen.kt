@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.m3u8player.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.m3u8player.data.model.SearchHistoryEntity
import com.example.m3u8player.data.model.VideoListEntity

@Composable
fun SearchScreen(
    onVideoClick: (Int) -> Unit,
    initialQuery: String? = null,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()

    LaunchedEffect(initialQuery) {
        viewModel.searchInitialQuery(initialQuery)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("搜索") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.onSearchQueryChange(it) },
                onSearch = { viewModel.search() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            when {
                isSearching -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                searchQuery.isBlank() -> {
                    SearchHistorySection(
                        history = searchHistory,
                        onHistoryClick = viewModel::searchFromHistory,
                        onDeleteHistory = viewModel::deleteSearchHistory,
                        onClearHistory = viewModel::clearSearchHistory
                    )
                }

                searchResults.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("没有找到相关视频")
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchResults) { video ->
                            VideoListItem(
                                video = video,
                                onClick = { onVideoClick(video.vodId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHistorySection(
    history: List<SearchHistoryEntity>,
    onHistoryClick: (String) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    if (history.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("暂无搜索记录")
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "搜索记录",
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = onClearHistory) {
                Text("清空")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(history, key = { it.query }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onHistoryClick(item.query) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.History, contentDescription = null)
                            Text(item.query)
                        }
                        IconButton(onClick = { onDeleteHistory(item.query) }) {
                            Icon(Icons.Default.Close, contentDescription = "删除")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("搜索视频") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "清空")
                }
            } else {
                IconButton(onClick = onSearch) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "开始搜索"
                    )
                }
            }
        },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onSearch = { onSearch() }
        ),
        singleLine = true
    )
}

@Composable
fun VideoListItem(
    video: VideoListEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = video.vodName,
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(onClick = { }, label = { Text(video.typeName) })
                if (video.vodRemarks.isNotBlank()) {
                    AssistChip(onClick = { }, label = { Text(video.vodRemarks) })
                }
            }
        }
    }
}
