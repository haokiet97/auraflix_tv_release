package com.auraflix.tv.ui.home

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.auraflix.tv.core.model.HomeSection
import com.auraflix.tv.core.model.Movie
import com.auraflix.tv.core.repo.HistoryItem
import com.auraflix.tv.core.util.shorten
import com.auraflix.tv.ui.components.ContinueCard
import com.auraflix.tv.ui.components.ErrorScreen
import com.auraflix.tv.ui.components.LoadingScreen
import com.auraflix.tv.ui.components.MetaChip
import com.auraflix.tv.ui.components.PosterCard
import com.auraflix.tv.ui.components.SectionHeader
import com.auraflix.tv.ui.components.TvButton
import com.auraflix.tv.ui.components.TvFocusable
import com.auraflix.tv.ui.factoryOf
import com.auraflix.tv.ui.theme.AuraColors
import com.auraflix.tv.Graph
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onOpenMovie: (String) -> Unit,
    onOpenPlayer: (String, Int, Int) -> Unit,
    onBrowse: (String) -> Unit,
    onSearch: () -> Unit,
    onFavorites: () -> Unit,
    onSettings: () -> Unit,
) {
    val vm: HomeViewModel = viewModel(
        factory = factoryOf { HomeViewModel(Graph.movies, Graph.userData) },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val continueWatching by vm.continueWatching.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(AuraColors.Background)) {
        val feed = state.feed
        when {
            feed == null && state.loading -> LoadingScreen()
            feed == null -> ErrorScreen(
                message = state.error ?: "Đã có lỗi xảy ra.",
                onRetry = { vm.load(true) },
            )

            else -> Column(Modifier.fillMaxSize()) {
                TopBar(
                    onSearch = onSearch,
                    onFavorites = onFavorites,
                    onSettings = onSettings,
                    onBrowse = onBrowse,
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 56.dp),
                ) {
                    item("hero") {
                        HeroSection(heroes = feed.hero, onOpen = onOpenMovie)
                    }

                    if (continueWatching.isNotEmpty()) {
                        item("continue-header") {
                            Box(Modifier.padding(start = 56.dp, end = 56.dp, top = 8.dp)) {
                                SectionHeader(title = "Xem tiếp")
                            }
                        }
                        item("continue-row") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 56.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                items(continueWatching, key = { it.slug }) { item ->
                                    ContinueCard(
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
private fun TopBar(
    onSearch: () -> Unit,
    onFavorites: () -> Unit,
    onSettings: () -> Unit,
    onBrowse: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "AURA",
            color = AuraColors.TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
        )
        Text(
            text = "FLIX",
            color = AuraColors.Accent,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.width(24.dp))
        TvButton(text = "Phim lẻ", onClick = { onBrowse("phim-le") })
        Spacer(Modifier.width(12.dp))
        TvButton(text = "Phim bộ", onClick = { onBrowse("phim-bo") })
        Spacer(Modifier.width(12.dp))
        TvButton(text = "Hoạt hình", onClick = { onBrowse("hoat-hinh") })
        Spacer(Modifier.weight(1f))
        TvButton(text = "Tìm kiếm", onClick = onSearch)
        Spacer(Modifier.width(12.dp))
        TvButton(text = "Yêu thích", onClick = onFavorites)
        Spacer(Modifier.width(12.dp))
        TvButton(text = "Cài đặt", onClick = onSettings)
    }
}

@Composable
private fun HeroSection(heroes: List<Movie>, onOpen: (String) -> Unit) {
    if (heroes.isEmpty()) return
    var index by remember(heroes) { mutableIntStateOf(0) }
    val heroFocus = remember { FocusRequester() }

    LaunchedEffect(heroes) {
        delay(250)
        runCatching { heroFocus.requestFocus() }
        while (heroes.size > 1) {
            delay(9_000)
            index = (index + 1) % heroes.size
        }
    }

    val movie = heroes[index % heroes.size]

    Box(Modifier.fillMaxWidth().height(420.dp)) {
        AsyncImage(
            model = movie.thumbUrl ?: movie.posterUrl,
            contentDescription = movie.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to AuraColors.Background,
                        0.45f to AuraColors.Background.copy(alpha = 0.75f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to AuraColors.Background,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 56.dp, end = 56.dp, bottom = 28.dp)
                .widthIn(max = 720.dp),
        ) {
            Text(
                text = movie.displayName,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 38.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            movie.originName?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    color = AuraColors.TextSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                movie.year?.let { MetaChip(it.toString()) }
                movie.quality?.let { MetaChip(it) }
                movie.lang?.let { MetaChip(it) }
                movie.episodeCurrent?.let { MetaChip(it) }
            }
            movie.content?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = it.shorten(240),
                    color = AuraColors.TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    text = "Xem phim",
                    onClick = { onOpen(movie.slug) },
                    primary = true,
                    modifier = Modifier.focusRequester(heroFocus),
                    leading = {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.width(20.dp).height(20.dp),
                        )
                    },
                )
                TvButton(
                    text = "‹",
                    onClick = {
                        index = if (index == 0) heroes.size - 1 else index - 1
                    },
                )
                TvButton(
                    text = "›",
                    onClick = {
                        index = (index + 1) % heroes.size
                    },
                )
            }
        }
    }
}

@Composable
private fun MovieSection(
    section: HomeSection,
    onOpenMovie: (String) -> Unit,
    onMore: () -> Unit,
) {
    Column(Modifier.padding(top = 28.dp)) {
        Box(Modifier.padding(horizontal = 56.dp)) {
            SectionHeader(title = section.title, onMore = onMore)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 56.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(section.movies, key = { it.slug }) { movie ->
                PosterCard(
                    movie = movie,
                    onClick = { onOpenMovie(movie.slug) },
                    modifier = Modifier.width(156.dp),
                )
            }
        }
    }
}
