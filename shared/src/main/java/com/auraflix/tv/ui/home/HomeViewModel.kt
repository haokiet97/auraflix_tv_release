package com.auraflix.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auraflix.tv.core.model.HomeFeed
import com.auraflix.tv.core.repo.HistoryItem
import com.auraflix.tv.core.repo.MovieRepository
import com.auraflix.tv.core.repo.UserDataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val feed: HomeFeed? = null,
    val error: String? = null,
)

class HomeViewModel(
    private val repo: MovieRepository,
    userData: UserDataRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    val continueWatching: StateFlow<List<HistoryItem>> = userData.continueWatching
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        load()
    }

    fun load(force: Boolean = false) {
        if (_state.value.loading && _state.value.feed != null) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val feed = repo.home(force)
            val hasData = feed.sections.isNotEmpty()
            _state.update {
                it.copy(
                    loading = false,
                    feed = if (hasData) feed else it.feed,
                    error = if (hasData) null else ERROR_MESSAGE,
                )
            }
        }
    }

    private companion object {
        const val ERROR_MESSAGE =
            "Không kết nối được tới máy chủ phim.\nKiểm tra mạng rồi thử lại."
    }
}
