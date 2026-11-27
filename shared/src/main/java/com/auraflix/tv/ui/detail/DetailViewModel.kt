package com.auraflix.tv.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auraflix.tv.core.model.Movie
import com.auraflix.tv.core.model.MovieDetail
import com.auraflix.tv.core.repo.HistoryItem
import com.auraflix.tv.core.repo.MovieRepository
import com.auraflix.tv.core.repo.UserDataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val loading: Boolean = true,
    val detail: MovieDetail? = null,
    val related: List<Movie> = emptyList(),
    val favorite: Boolean = false,
    val resume: HistoryItem? = null,
    val preferredServer: String = "",
    val error: String? = null,
)

class DetailViewModel(
    private val repo: MovieRepository,
    private val userData: UserDataRepository,
    private val slug: String,
) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init {
        load()
        observeFavorite()
        observePreferredServer()
    }

    fun load(force: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val detail = repo.detail(slug, force)
            if (detail == null || detail.movie == null) {
                _state.update {
                    it.copy(loading = false, error = "Không tải được thông tin phim. Kiểm tra mạng rồi thử lại.")
                }
                return@launch
            }
            val resume = runCatching { userData.progressOf(slug) }.getOrNull()
            _state.update { it.copy(loading = false, detail = detail, resume = resume, error = null) }

            val related = runCatching { repo.related(detail.movie) }.getOrDefault(emptyList())
            _state.update { it.copy(related = related) }
        }
    }

    private fun observeFavorite() {
        viewModelScope.launch {
            userData.favorites.collect { list ->
                _state.update { it.copy(favorite = list.any { movie -> movie.slug == slug }) }
            }
        }
    }

    private fun observePreferredServer() {
        viewModelScope.launch {
            val settings = userData.settings.first()
            _state.update { it.copy(preferredServer = settings.preferredServer) }
        }
    }

    fun toggleFavorite() {
        val movie = _state.value.detail?.movie ?: return
        viewModelScope.launch {
            userData.toggleFavorite(movie)
        }
    }
}
