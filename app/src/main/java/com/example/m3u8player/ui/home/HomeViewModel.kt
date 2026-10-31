package com.example.m3u8player.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.m3u8player.data.repository.RecommendationRepository
import com.example.m3u8player.data.repository.RecommendationSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val sections: List<RecommendationSection> = emptyList(),
    val message: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val recommendationRepository: RecommendationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val hasExistingContent = _uiState.value.sections.isNotEmpty()
            _uiState.value = _uiState.value.copy(
                isLoading = !hasExistingContent,
                isRefreshing = hasExistingContent,
                message = null
            )
            val result = runCatching {
                recommendationRepository.getHomeRecommendations(forceRefresh)
            }.getOrElse {
                com.example.m3u8player.data.repository.HomeRecommendationResult(emptyList(), hasStaleCache = false)
            }
            val sections = result.sections

            _uiState.value = HomeUiState(
                isLoading = false,
                isRefreshing = false,
                sections = sections,
                message = when {
                    sections.isEmpty() -> "暂无推荐内容，请稍后重试或先更新数据"
                    result.hasStaleCache -> "部分推荐来自缓存，稍后会自动更新"
                    else -> null
                }
            )
        }
    }
}
