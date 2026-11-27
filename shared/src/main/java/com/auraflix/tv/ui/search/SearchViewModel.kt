package com.auraflix.tv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auraflix.tv.core.model.Movie
import com.auraflix.tv.core.repo.MovieRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<Movie> = emptyList(),
    val loading: Boolean = false,
    val searched: Boolean = false,
    val error: String? = null,
)

@OptIn(FlowPreview::class)
class SearchViewModel(private val repo: MovieRepository) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            queryFlow
                .debounce(400)
                .distinctUntilChanged()
                .collectLatest { keyword ->
                    val clean = keyword.trim()
                    if (clean.isBlank()) {
                        _state.update { it.copy(results = emptyList(), loading = false, searched = false) }
                    } else {
                        execute(clean)
                    }
                }
        }
    }

    fun onQueryChange(value: String) {
        val clean = value.take(40)
        _state.update { it.copy(query = clean) }
        queryFlow.value = clean
    }    fun searchNow() {
        val keyword = _state.value.query.trim()
        if (keyword.isNotBlank()) {
            viewModelScope.launch { execute(keyword) }
        }
    }

    private suspend fun execute(keyword: String) {
        _state.update { it.copy(loading = true, error = null) }
        val page = repo.search(keyword, 1)
        _state.update {
            it.copy(
                loading = false,
                results = page.movies,
                searched = true,
                error = if (page.movies.isEmpty()) null else null,
            )
        }
    }
}
