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

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
            val sections = runCatching {
                recommendationRepository.getHomeRecommendations()
            }.getOrElse { emptyList() }

            _uiState.value = HomeUiState(
                isLoading = false,
                sections = sections,
                message = if (sections.isEmpty()) "暂无推荐内容，请稍后重试或先更新数据" else null
            )
        }
    }
}
