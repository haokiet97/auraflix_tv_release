@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)

package com.auraflix.mobile.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auraflix.mobile.MobileGraph
import com.auraflix.mobile.ui.components.EmptyState
import com.auraflix.mobile.ui.components.LoadingState
import com.auraflix.mobile.ui.components.MoviePosterCard
import com.auraflix.mobile.ui.theme.AuraColors
import com.auraflix.tv.ui.factoryOf
import com.auraflix.tv.ui.search.SearchViewModel

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenMovie: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: SearchViewModel = viewModel(factory = factoryOf { SearchViewModel(MobileGraph.movies) })
    val state by vm.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val recentSearches = rememberSaveable(
        saver = listSaver(
            save = { it.toList() },
            restore = { it.toMutableStateList() },
        ),
    ) { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }

    fun submit(keyword: String) {
        val clean = keyword.trim()
        if (clean.isBlank()) return
        recentSearches.remove(clean)
        recentSearches.add(0, clean)
        if (recentSearches.size > 8) recentSearches.removeLast()
        vm.searchNow()
        keyboard?.hide()
    }

    Column(
        modifier
            .fillMaxSize()
            .background(AuraColors.Background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Quay lại",
                    tint = AuraColors.TextPrimary,
                )
            }
            TextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text("Tìm tên phim…", fontSize = 14.sp) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = AuraColors.Surface,
                    unfocusedContainerColor = AuraColors.Surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = AuraColors.TextPrimary,
                    unfocusedTextColor = AuraColors.TextPrimary,
                    cursorColor = AuraColors.Accent,
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = AuraColors.TextMuted,
                    )
                },
                trailingIcon = {
                    if (state.query.isNotBlank()) {
                        IconButton(onClick = { vm.onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Xoá",
                                tint = AuraColors.TextMuted,
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit(state.query) }),
            )
        }

        Box(Modifier.weight(1f)) {
            when {
                state.query.isBlank() -> SearchStart(recentSearches) { keyword ->
                    vm.onQueryChange(keyword)
                    submit(keyword)
                }

                state.loading -> LoadingState(message = "Đang tìm…")
                state.results.isEmpty() && state.searched -> EmptyState(
                    "Không tìm thấy phim \"${state.query}\".",
                )

                state.results.isEmpty() -> SearchStart(recentSearches) { keyword ->
                    vm.onQueryChange(keyword)
                    submit(keyword)
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 96.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(state.results, key = { it.slug }) { movie ->
                        MoviePosterCard(movie = movie, onClick = { onOpenMovie(movie.slug) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchStart(
    recentSearches: List<String>,
    onPick: (String) -> Unit,
) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        if (recentSearches.isEmpty()) {
            Text(
                text = "Nhập tên phim để tìm kiếm",
                color = AuraColors.TextMuted,
                fontSize = 14.sp,
            )
            return
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                tint = AuraColors.TextMuted,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Tìm kiếm gần đây",
                color = AuraColors.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            recentSearches.forEach { keyword ->
                AssistChip(
                    onClick = { onPick(keyword) },
                    label = { Text(keyword, fontSize = 12.sp) },
                )
            }
        }
    }
}
