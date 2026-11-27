@file:OptIn(ExperimentalLayoutApi::class)

package com.auraflix.tv.ui.detail

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.auraflix.tv.Graph
import com.auraflix.tv.core.model.MovieDetail
import com.auraflix.tv.core.util.prettyViews
import com.auraflix.tv.ui.components.ErrorScreen
import com.auraflix.tv.ui.components.FilterChip
import com.auraflix.tv.ui.components.LoadingScreen
import com.auraflix.tv.ui.components.MetaChip
import com.auraflix.tv.ui.components.PosterCard
import com.auraflix.tv.ui.components.SectionHeader
import com.auraflix.tv.ui.components.TvButton
import com.auraflix.tv.ui.components.TvFocusable
import com.auraflix.tv.ui.factoryOf
import com.auraflix.tv.ui.theme.AuraColors

@Composable
fun DetailScreen(
    slug: String,
    onBack: () -> Unit,
    onOpenMovie: (String) -> Unit,
    onPlay: (String, Int, Int) -> Unit,
) {
    val vm: DetailViewModel = viewModel(
        key = "detail-$slug",
        factory = factoryOf { DetailViewModel(Graph.movies, Graph.userData, slug) },
    )
    val state by vm.state.collectAsStateWithLifecycle()

    BackHandler { onBack() }

    val detail = state.detail

    Box(Modifier.fillMaxSize().background(AuraColors.Background)) {
        when {
            detail == null && state.loading -> LoadingScreen()
            detail == null -> ErrorScreen(
                message = state.error ?: "Đã có lỗi xảy ra.",
                onRetry = { vm.load(true) },
            )

            else -> DetailContent(
                detail = detail,
                related = state.related,
                favorite = state.favorite,
                preferredServer = state.preferredServer,
                startServer = state.resume?.serverIndex ?: -1,
                startEpisode = state.resume?.episodeIndex ?: 0,
                onBack = onBack,
                onToggleFavorite = vm::toggleFavorite,
                onOpenMovie = onOpenMovie,
                onPlay = { server, episode -> onPlay(slug, server, episode) },
            )
        }
    }
}

@Composable
private fun DetailContent(
    detail: MovieDetail,
    related: List<com.auraflix.tv.core.model.Movie>,
    favorite: Boolean,
    preferredServer: String,
    startServer: Int,
    startEpisode: Int,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenMovie: (String) -> Unit,
    onPlay: (Int, Int) -> Unit,
) {
    val movie = detail.movie ?: return
    val servers = detail.servers

    var serverIndex by remember(detail) {
        mutableIntStateOf(
            when {
                startServer in servers.indices -> startServer
                preferredServer.isNotBlank() ->
                    servers.indexOfFirst { it.serverName == preferredServer }.takeIf { it >= 0 } ?: 0
                else -> 0
            },
        )
    }
    var episodeIndex by remember(detail) { mutableIntStateOf(startEpisode) }

    val currentServer = servers.getOrNull(serverIndex)
    val episodes = currentServer?.episodes.orEmpty()
    val safeEpisode = episodeIndex.coerceIn(0, (episodes.size - 1).coerceAtLeast(0))

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 56.dp),
    ) {
        item("header") {
            Box(Modifier.fillMaxWidth()) {
                Column {
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
                                        0.55f to AuraColors.Background.copy(alpha = 0.6f),
                                        1f to Color.Transparent,
                                    ),
                                ),
                        )
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.5f to Color.Transparent,
                                        1f to AuraColors.Background,
                                    ),
                                ),
                        )

                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 56.dp, top = 26.dp),
                        ) {
                            TvButton(text = "‹ Quay lại", onClick = onBack)
                        }

                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 56.dp, end = 56.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            TvFocusable(
                                onClick = { onPlay(serverIndex, safeEpisode) },
                                modifier = Modifier.width(150.dp).height(225.dp),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                focusedScale = 1.04f,
                            ) {
                                AsyncImage(
                                    model = movie.posterUrl ?: movie.thumbUrl,
                                    contentDescription = movie.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Spacer(Modifier.width(28.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = movie.displayName,
                                    color = Color.White,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    lineHeight = 34.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                movie.originName?.takeIf { it.isNotBlank() }?.let {
                                    Text(
                                        text = it,
                                        color = AuraColors.TextSecondary,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    maxItemsInEachRow = 6,
                                ) {
                                    movie.year?.let { MetaChip(it.toString()) }
                                    movie.quality?.let { MetaChip(it) }
                                    movie.lang?.let { MetaChip(it) }
                                    movie.episodeCurrent?.let { MetaChip(it) }
                                    movie.time?.let { MetaChip(it) }
                                    prettyViews(movie.view)?.let { MetaChip(it) }
                                    movie.category.take(3).forEach { MetaChip(it.name) }
                                }
                                Spacer(Modifier.height(18.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    TvButton(
                                        text = if (movie.isSeries) "Phát tập ${safeEpisode + 1}" else "Xem phim",
                                        onClick = { onPlay(serverIndex, safeEpisode) },
                                        primary = true,
                                        leading = {
                                            Icon(
                                                imageVector = Icons.Filled.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.width(20.dp).height(20.dp),
                                            )
                                        },
                                    )
                                    TvButton(
                                        text = if (favorite) "Bỏ yêu thích" else "Yêu thích",
                                        onClick = onToggleFavorite,
                                        leading = {
                                            Icon(
                                                imageVector = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                                contentDescription = null,
                                                tint = if (favorite) AuraColors.Accent else Color.White,
                                                modifier = Modifier.width(18.dp).height(18.dp),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (servers.isNotEmpty()) {
            item("servers") {
                Column(Modifier.padding(horizontal = 56.dp, vertical = 8.dp)) {
                    if (servers.size > 1) {
                        Text(
                            text = "Nguồn phát",
                            color = AuraColors.TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            servers.forEachIndexed { index, server ->
                                FilterChip(
                                    label = server.serverName ?: "Nguồn ${index + 1}",
                                    selected = index == serverIndex,
                                    onClick = {
                                        serverIndex = index
                                        episodeIndex = 0
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }

                    Text(
                        text = if (episodes.size > 1) "Danh sách tập (${episodes.size})" else "Tập phim",
                        color = AuraColors.TextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        maxItemsInEachRow = 10,
                    ) {
                        episodes.forEachIndexed { index, ep ->
                            FilterChip(
                                label = ep.name ?: "Tập ${index + 1}",
                                selected = index == safeEpisode,
                                onClick = {
                                    episodeIndex = index
                                    onPlay(serverIndex, index)
                                },
                            )
                        }
                    }
                }
            }
        }

        item("content") {
            Column(Modifier.padding(horizontal = 56.dp, vertical = 20.dp).widthIn(max = 1200.dp)) {
                Text(
                    text = "Nội dung",
                    color = AuraColors.TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = movie.content?.takeIf { it.isNotBlank() } ?: "Chưa có mô tả cho phim này.",
                    color = AuraColors.TextSecondary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
                val actors = movie.actor.filter { it.isNotBlank() }
                if (actors.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Diễn viên: ${actors.take(8).joinToString(", ")}",
                        color = AuraColors.TextMuted,
                        fontSize = 13.sp,
                    )
                }
                val directors = movie.director.filter { it.isNotBlank() }
                if (directors.isNotEmpty()) {
                    Text(
                        text = "Đạo diễn: ${directors.joinToString(", ")}",
                        color = AuraColors.TextMuted,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        if (related.isNotEmpty()) {
            item("related") {
                Column(Modifier.padding(top = 10.dp)) {
                    Box(Modifier.padding(horizontal = 56.dp)) {
                        SectionHeader(title = "Có thể bạn thích")
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 56.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(related, key = { it.slug }) { item ->
                            PosterCard(
                                movie = item,
                                onClick = { onOpenMovie(item.slug) },
                                modifier = Modifier.width(150.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
