package com.example.m3u8player.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m3u8player.data.model.VideoListEntity
import com.example.m3u8player.data.repository.VideoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }
    
    fun search() {
        val query = _searchQuery.value.trim()
        if (query.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        
        viewModelScope.launch {
            _isSearching.value = true
            repository.searchVideos(query).collect { results ->
                _searchResults.value = results
                _isSearching.value = false
            }
        }
    }
}
