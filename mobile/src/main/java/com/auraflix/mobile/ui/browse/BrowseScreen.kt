@file:OptIn(ExperimentalMaterial3Api::class)

package com.auraflix.mobile.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auraflix.mobile.MobileGraph
import com.auraflix.mobile.ui.components.EmptyState
import com.auraflix.mobile.ui.components.ErrorState
import com.auraflix.mobile.ui.components.LoadingState
import com.auraflix.mobile.ui.components.MoviePosterCard
import com.auraflix.mobile.ui.theme.AuraColors
import com.auraflix.tv.core.model.MovieTypes
import com.auraflix.tv.core.model.Taxonomy
import com.auraflix.tv.ui.browse.BrowseUiState
import com.auraflix.tv.ui.browse.BrowseViewModel
import com.auraflix.tv.ui.factoryOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private enum class FilterKind { CATEGORY, COUNTRY, YEAR }

private val availableYears: List<Int> = run {
    val current = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    (current downTo 1990).toList()
}

@Composable
fun BrowseScreen(
    initialType: String,
    onOpenMovie: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: BrowseViewModel = viewModel(
        key = "browse-$initialType",
        factory = factoryOf { BrowseViewModel(MobileGraph.movies, initialType) },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf<FilterKind?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .background(AuraColors.Background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.typeLabel,
                color = AuraColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${state.movies.size} phim",
                color = AuraColors.TextMuted,
                fontSize = 12.sp,
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(MovieTypes, key = { it.slug }) { type ->
                FilterChip(
                    selected = state.type == type.slug,
                    onClick = { vm.selectType(type.slug) },
                    label = { Text(type.label, fontSize = 12.sp) },
                    colors = auroraChipColors(selected = state.type == type.slug),
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = state.category != null,
                    onClick = { filter = FilterKind.CATEGORY },
                    label = {
                        Text(
                            text = state.category?.let { "Thể loại: ${it.name}" } ?: "Thể loại",
                            fontSize = 12.sp,
                        )
                    },
                    colors = auroraChipColors(selected = state.category != null),
                )
            }
            item {
                FilterChip(
                    selected = state.country != null,
                    onClick = { filter = FilterKind.COUNTRY },
                    label = {
                        Text(
                            text = state.country?.let { "Quốc gia: ${it.name}" } ?: "Quốc gia",
                            fontSize = 12.sp,
                        )
                    },
                    colors = auroraChipColors(selected = state.country != null),
                )
            }
            item {
                FilterChip(
                    selected = state.year != null,
                    onClick = { filter = FilterKind.YEAR },
                    label = {
                        Text(
                            text = state.year?.let { "Năm: $it" } ?: "Năm",
                            fontSize = 12.sp,
                        )
                    },
                    colors = auroraChipColors(selected = state.year != null),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        MovieGrid(
            state = state,
            onOpenMovie = onOpenMovie,
            onLoadMore = vm::loadMore,
            onRetry = vm::reload,
        )
    }

    filter?.let { kind ->
        FilterSheet(
            kind = kind,
            state = state,
            onDismiss = { filter = null },
            onSelectCategory = { vm.selectCategory(it); filter = null },
            onSelectCountry = { vm.selectCountry(it); filter = null },
            onSelectYear = { vm.selectYear(it); filter = null },
        )
    }
}

@Composable
private fun auroraChipColors(selected: Boolean) = FilterChipDefaults.filterChipColors(
    selectedContainerColor = AuraColors.Accent,
    selectedLabelColor = androidx.compose.ui.graphics.Color.White,
    containerColor = AuraColors.Surface,
    labelColor = AuraColors.TextSecondary,
)

@Composable
private fun MovieGrid(
    state: BrowseUiState,
    onOpenMovie: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState, state.movies.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .filter { it >= state.movies.size - 9 && state.movies.isNotEmpty() }
            .collect { onLoadMore() }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            state.loading -> LoadingState()
            state.error != null && state.movies.isEmpty() -> ErrorState(
                message = state.error!!,
                onRetry = onRetry,
            )

            state.movies.isEmpty() -> EmptyState("Không có phim nào.")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 96.dp),
                state = gridState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(state.movies, key = { it.slug }) { movie ->
                    MoviePosterCard(movie = movie, onClick = { onOpenMovie(movie.slug) })
                }
                if (state.loadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = AuraColors.Accent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.width(26.dp).height(26.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSheet(
    kind: FilterKind,
    state: BrowseUiState,
    onDismiss: () -> Unit,
    onSelectCategory: (Taxonomy?) -> Unit,
    onSelectCountry: (Taxonomy?) -> Unit,
    onSelectYear: (Int?) -> Unit,
) {
    val title = when (kind) {
        FilterKind.CATEGORY -> "Chọn thể loại"
        FilterKind.COUNTRY -> "Chọn quốc gia"
        FilterKind.YEAR -> "Chọn năm"
    }

    val options: List<Pair<String, String>> = when (kind) {
        FilterKind.CATEGORY -> listOf("Tất cả" to "") + state.categories.map { it.name to it.slug }
        FilterKind.COUNTRY -> listOf("Tất cả" to "") + state.countries.map { it.name to it.slug }
        FilterKind.YEAR -> listOf("Tất cả" to "") + availableYears.map { "$it" to "$it" }
    }

    val selectedSlug: String = when (kind) {
        FilterKind.CATEGORY -> state.category?.slug.orEmpty()
        FilterKind.COUNTRY -> state.country?.slug.orEmpty()
        FilterKind.YEAR -> state.year?.toString().orEmpty()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AuraColors.BackgroundElevated,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = title,
                color = AuraColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.heightIn(max = 420.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(options) { (label, slug) ->
                    FilterChip(
                        selected = slug == selectedSlug,
                        onClick = {
                            when (kind) {
                                FilterKind.CATEGORY -> onSelectCategory(
                                    state.categories.firstOrNull { it.slug == slug },
                                )

                                FilterKind.COUNTRY -> onSelectCountry(
                                    state.countries.firstOrNull { it.slug == slug },
                                )

                                FilterKind.YEAR -> onSelectYear(slug.toIntOrNull())
                            }
                        },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = auroraChipColors(selected = slug == selectedSlug),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
