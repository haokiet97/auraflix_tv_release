package com.auraflix.mobile.ui.home

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auraflix.mobile.MobileGraph
import com.auraflix.mobile.ui.components.ContinueWatchingCard
import com.auraflix.mobile.ui.components.ErrorState
import com.auraflix.mobile.ui.components.FeaturedCarousel
import com.auraflix.mobile.ui.components.LoadingState
import com.auraflix.mobile.ui.components.MoviePosterCard
import com.auraflix.mobile.ui.components.SectionHeader
import com.auraflix.mobile.ui.theme.AuraColors
import com.auraflix.tv.core.model.HomeSection
import com.auraflix.tv.core.model.MovieTypes
import com.auraflix.tv.ui.factoryOf
import com.auraflix.tv.ui.home.HomeViewModel

@Composable
fun HomeScreen(
    onOpenMovie: (String) -> Unit,
    onOpenPlayer: (String, Int, Int) -> Unit,
    onBrowse: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenTv: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: HomeViewModel = viewModel(
        factory = factoryOf { HomeViewModel(MobileGraph.movies, MobileGraph.userData) },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val continueWatching by vm.continueWatching.collectAsStateWithLifecycle()
    val remoteState by MobileGraph.tvRemote.state.collectAsStateWithLifecycle()

    Column(
        modifier
            .fillMaxSize()
            .background(AuraColors.Background)
            .statusBarsPadding(),
    ) {
        HomeTopBar(
            onSearch = onSearch,
            onOpenTv = onOpenTv,
            tvConnected = remoteState.tv != null,
            tvPlaying = remoteState.playback?.active == true,
        )

        val feed = state.feed
        when {
            feed == null && state.loading -> LoadingState()
            feed == null -> ErrorState(
                message = state.error ?: "Đã có lỗi xảy ra.",
                onRetry = { vm.load(true) },
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                item("hero") {
                    FeaturedCarousel(heroes = feed.hero, onOpen = onOpenMovie)
                }

                item("quick-types") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(MovieTypes, key = { it.slug }) { type ->
                            AssistChip(
                                onClick = { onBrowse(type.slug) },
                                label = { Text(type.label, fontSize = 12.sp) },
                            )
                        }
                    }
                }

                if (continueWatching.isNotEmpty()) {
                    item("continue-header") {
                        SectionHeader(
                            title = "Xem tiếp",
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                        )
                    }
                    item("continue-row") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(continueWatching, key = { it.slug }) { item ->
                                ContinueWatchingCard(
                                    item = item,
                                    onClick = {
                                        onOpenPlayer(item.slug, item.serverIndex, item.episodeIndex)
                                    },
                                )
                            }
                        }
                    }
                }

                items(feed.sections, key = { it.title }) { section ->
                    MovieSection(
                        section = section,
                        onOpenMovie = onOpenMovie,
                        onMore = { onBrowse(sectionToType(section.title)) },
                    )
                }
            }
        }
    }
}

private fun sectionToType(title: String): String = when (title) {
    "Phim mới cập nhật" -> "phim-moi-cap-nhat"
    "Phim lẻ" -> "phim-le"
    "Phim bộ" -> "phim-bo"
    "Hoạt hình" -> "hoat-hinh"
    "TV Shows" -> "tv-shows"
    "Phim chiếu rạp" -> "phim-chieu-rap"
    else -> "phim-moi-cap-nhat"
}

@Composable
private fun HomeTopBar(
    onSearch: () -> Unit,
    onOpenTv: () -> Unit,
    tvConnected: Boolean,
    tvPlaying: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "AURA",
            color = AuraColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp,
        )
        Text(
            text = "FLIX",
            color = AuraColors.Accent,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp,
        )
        Spacer(Modifier.weight(1f))
        // Biểu tượng TV: mở điều khiển (đã ghép cặp) hoặc kết nối TV (chưa ghép).
        Box {
            IconButton(onClick = onOpenTv) {
                Icon(
                    imageVector = Icons.Filled.Tv,
                    contentDescription = if (tvConnected) "Điều khiển TV" else "Kết nối TV",
                    tint = if (tvConnected) AuraColors.Accent else AuraColors.TextMuted,
                )
            }
            if (tvPlaying) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 10.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(AuraColors.Accent),
                )
            }
        }
        IconButton(onClick = onSearch) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Tìm kiếm",
                tint = AuraColors.TextPrimary,
            )
        }
    }
}

@Composable
private fun MovieSection(
    section: HomeSection,
    onOpenMovie: (String) -> Unit,
    onMore: () -> Unit,
) {
    Column(Modifier.padding(top = 18.dp)) {
        SectionHeader(
            title = section.title,
            modifier = Modifier.padding(horizontal = 16.dp),
            onMore = onMore,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(section.movies, key = { it.slug }) { movie ->
                MoviePosterCard(
                    movie = movie,
                    onClick = { onOpenMovie(movie.slug) },
                    modifier = Modifier.width(118.dp),
                )
            }
        }
    }
}
