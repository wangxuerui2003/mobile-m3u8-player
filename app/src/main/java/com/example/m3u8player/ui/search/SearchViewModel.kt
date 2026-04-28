package com.example.m3u8player.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m3u8player.data.model.SearchHistoryEntity
import com.example.m3u8player.data.model.VideoListEntity
import com.example.m3u8player.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: VideoRepository
) : ViewModel() {
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _searchResults = MutableStateFlow<List<VideoListEntity>>(emptyList())
    val searchResults: StateFlow<List<VideoListEntity>> = _searchResults.asStateFlow()
    
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<SearchHistoryEntity>>(emptyList())
    val searchHistory: StateFlow<List<SearchHistoryEntity>> = _searchHistory.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            repository.getSearchHistory().collect { history ->
                _searchHistory.value = history
            }
        }
    }
    
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            searchJob?.cancel()
            _searchResults.value = emptyList()
            _isSearching.value = false
        }
    }
    
    fun search() {
        val query = _searchQuery.value.trim()
        if (query.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            repository.saveSearchQuery(query)
            _searchResults.value = repository.searchVideos(query).first()
            _isSearching.value = false
        }
    }

    fun searchFromHistory(query: String) {
        _searchQuery.value = query
        search()
    }

    fun deleteSearchHistory(query: String) {
        viewModelScope.launch {
            repository.deleteSearchHistory(query)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            repository.clearSearchHistory()
        }
    }
}
