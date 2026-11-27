package com.auraflix.tv.ui.browse

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auraflix.tv.Graph
import com.auraflix.tv.core.model.MovieTypes
import com.auraflix.tv.core.model.Taxonomy
import com.auraflix.tv.ui.components.EmptyScreen
import com.auraflix.tv.ui.components.ErrorScreen
import com.auraflix.tv.ui.components.FilterChip
import com.auraflix.tv.ui.components.LoadingScreen
import com.auraflix.tv.ui.components.PosterCard
import com.auraflix.tv.ui.components.TvButton
import com.auraflix.tv.ui.components.TvFocusable
import com.auraflix.tv.ui.factoryOf
import com.auraflix.tv.ui.theme.AuraColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private enum class PickerKind { CATEGORY, COUNTRY, YEAR }

private val availableYears: List<Int> = run {
    val current = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    (current downTo 1990).toList()
}

@Composable
fun BrowseScreen(
    initialType: String,
    onBack: () -> Unit,
    onOpenMovie: (String) -> Unit,
) {
    val vm: BrowseViewModel = viewModel(
        key = "browse-$initialType",
        factory = factoryOf { BrowseViewModel(Graph.movies, initialType) },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf<PickerKind?>(null) }

    BackHandler(enabled = picker != null) { picker = null }
    BackHandler(enabled = picker == null) { onBack() }

    Box(Modifier.fillMaxSize().background(AuraColors.Background)) {
        if (picker != null) {
            val options: List<Pair<String, String>> = when (picker) {
                PickerKind.CATEGORY -> listOf("Tất cả" to "") + state.categories.map { it.name to it.slug }
                PickerKind.COUNTRY -> listOf("Tất cả" to "") + state.countries.map { it.name to it.slug }
                PickerKind.YEAR -> listOf("Tất cả" to "") + availableYears.map { "$it" to "$it" }
                null -> emptyList()
            }
            PickerOverlay(
                title = when (picker) {
                    PickerKind.CATEGORY -> "Chọn thể loại"
                    PickerKind.COUNTRY -> "Chọn quốc gia"
                    else -> "Chọn năm"
                },
                options = options,
                onDismiss = { picker = null },
                onSelect = { _, slug ->
                    when (picker) {
                        PickerKind.CATEGORY -> vm.selectCategory(slug.takeIf { it.isNotBlank() }?.let { s ->
                            state.categories.firstOrNull { it.slug == s }
                        })
                        PickerKind.COUNTRY -> vm.selectCountry(slug.takeIf { it.isNotBlank() }?.let { s ->
                            state.countries.firstOrNull { it.slug == s }
                        })
                        PickerKind.YEAR -> vm.selectYear(slug.takeIf { it.isNotBlank() }?.toIntOrNull())
                        null -> Unit
                    }
                    picker = null
                },
            )
        } else {
            BrowseContent(
                state = state,
                onBack = onBack,
                onOpenMovie = onOpenMovie,
                onSelectType = vm::selectType,
                onOpenCategory = { picker = PickerKind.CATEGORY },
                onOpenCountry = { picker = PickerKind.COUNTRY },
                onOpenYear = { picker = PickerKind.YEAR },
                onLoadMore = vm::loadMore,
                onRetry = vm::reload,
            )
        }
    }
}

@Composable
private fun BrowseContent(
    state: BrowseUiState,
    onBack: () -> Unit,
    onOpenMovie: (String) -> Unit,
    onSelectType: (String) -> Unit,
    onOpenCategory: () -> Unit,
    onOpenCountry: () -> Unit,
    onOpenYear: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState, state.movies.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .filter { it >= state.movies.size - 8 && state.movies.isNotEmpty() }
            .collect { onLoadMore() }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 56.dp, end = 56.dp, top = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvButton(text = "‹", onClick = onBack)
            Spacer(Modifier.width(20.dp))
            Text(
                text = state.typeLabel,
                color = AuraColors.TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${state.movies.size} phim",
                color = AuraColors.TextMuted,
                fontSize = 13.sp,
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 56.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(MovieTypes) { type ->
                FilterChip(
                    label = type.label,
                    selected = state.type == type.slug,
                    onClick = { onSelectType(type.slug) },
                )
            }
        }

        Row(
            modifier = Modifier.padding(horizontal = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilterChip(
                label = state.category?.let { "Thể loại: ${it.name}" } ?: "Thể loại: Tất cả",
                selected = state.category != null,
                onClick = onOpenCategory,
            )
            FilterChip(
                label = state.country?.let { "Quốc gia: ${it.name}" } ?: "Quốc gia: Tất cả",
                selected = state.country != null,
                onClick = onOpenCountry,
            )
            FilterChip(
                label = state.year?.let { "Năm: $it" } ?: "Năm: Tất cả",
                selected = state.year != null,
                onClick = onOpenYear,
            )
        }

        Spacer(Modifier.height(16.dp))

        Box(Modifier.fillMaxSize()) {
            when {
                state.loading -> LoadingScreen()
                state.error != null && state.movies.isEmpty() ->
                    ErrorScreen(message = state.error!!, onRetry = onRetry)
                state.movies.isEmpty() -> EmptyScreen("Không có phim nào.")
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    state = gridState,
                    contentPadding = PaddingValues(start = 56.dp, end = 56.dp, bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(state.movies, key = { it.slug }) { movie ->
                        PosterCard(movie = movie, onClick = { onOpenMovie(movie.slug) })
                    }
                    if (state.loadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    color = AuraColors.Accent,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.width(28.dp).height(28.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerOverlay(
    title: String,
    options: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSelect: (String, String) -> Unit,
) {
    val firstFocus = remember { FocusRequester() }

    Box(
        Modifier
            .fillMaxSize()
            .background(AuraColors.Background)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Back) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 32.dp)) {
            Text(
                text = title,
                color = AuraColors.TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(20.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 48.dp),
            ) {
                itemsIndexed(options) { index, option ->
                    val isFirst = index == 0
                    TvFocusable(
                        onClick = { onSelect(option.first, option.second) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isFirst) Modifier.focusRequester(firstFocus) else Modifier),
                        shape = RoundedCornerShape(10.dp),
                    ) { focused ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    if (focused) AuraColors.Accent.copy(alpha = 0.9f)
                                    else AuraColors.Surface,
                                    RoundedCornerShape(10.dp),
                                )
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = option.first,
                                color = AuraColors.TextPrimary,
                                fontSize = 14.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { firstFocus.requestFocus() }
    }
}
