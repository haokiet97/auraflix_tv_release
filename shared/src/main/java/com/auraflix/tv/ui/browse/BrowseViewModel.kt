package com.auraflix.tv.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auraflix.tv.core.model.Movie
import com.auraflix.tv.core.model.MovieTypes
import com.auraflix.tv.core.model.Taxonomy
import com.auraflix.tv.core.repo.MovieRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowseUiState(
    val type: String = "phim-moi-cap-nhat",
    val category: Taxonomy? = null,
    val country: Taxonomy? = null,
    val year: Int? = null,
    val movies: List<Movie> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 1,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val categories: List<Taxonomy> = emptyList(),
    val countries: List<Taxonomy> = emptyList(),
) {
    val typeLabel: String
        get() = MovieTypes.firstOrNull { it.slug == type }?.label ?: "Danh sách phim"
}

class BrowseViewModel(
    private val repo: MovieRepository,
    initialType: String,
) : ViewModel() {

    private val _state = MutableStateFlow(BrowseUiState(type = initialType))
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        loadFilters()
        reload()
    }

    private fun loadFilters() {
        viewModelScope.launch {
            val cats = repo.categories()
            val ctrs = repo.countries()
            _state.update { it.copy(categories = cats, countries = ctrs) }
        }
    }

    fun selectType(type: String) {
        if (_state.value.type == type) return
        _state.update { it.copy(type = type) }
        reload()
    }

    fun selectCategory(value: Taxonomy?) {
        _state.update { it.copy(category = value) }
        reload()
    }

    fun selectCountry(value: Taxonomy?) {
        _state.update { it.copy(country = value) }
        reload()
    }

    fun selectYear(value: Int?) {
        _state.update { it.copy(year = value) }
        reload()
    }

    fun reload() {
        job?.cancel()
        val s = _state.value
        _state.update { it.copy(loading = true, error = null, movies = emptyList(), page = 0) }
        job = viewModelScope.launch {
            val result = repo.browse(s.type, s.category?.slug, s.country?.slug, s.year, page = 1)
            if (result.movies.isEmpty()) {
                _state.update {
                    it.copy(loading = false, error = "Không có phim phù hợp hoặc mất kết nối mạng.")
                }
            } else {
                _state.update {
                    it.copy(
                        loading = false,
                        movies = result.movies,
                        page = result.currentPage,
                        totalPages = result.totalPages,
                        error = null,
                    )
                }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.page >= s.totalPages) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val next = s.page + 1
            val result = repo.browse(s.type, s.category?.slug, s.country?.slug, s.year, page = next)
            _state.update {
                it.copy(
                    loadingMore = false,
                    movies = (it.movies + result.movies).distinctBy { m -> m.slug },
                    page = if (result.movies.isEmpty()) it.totalPages else result.currentPage,
                    totalPages = result.totalPages,
                )
            }
        }
    }
}
