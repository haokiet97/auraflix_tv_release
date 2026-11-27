@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.auraflix.mobile.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.auraflix.mobile.MobileGraph
import com.auraflix.mobile.ui.components.ErrorState
import com.auraflix.mobile.ui.components.LoadingState
import com.auraflix.mobile.ui.components.MetaPill
import com.auraflix.mobile.ui.components.MoviePosterCard
import com.auraflix.mobile.ui.components.SectionHeader
import com.auraflix.mobile.ui.theme.AuraColors
import com.auraflix.tv.core.model.EpisodeServer
import com.auraflix.tv.core.model.Movie
import com.auraflix.tv.core.model.MovieDetail
import com.auraflix.tv.core.repo.HistoryItem
import com.auraflix.tv.core.util.formatDuration
import com.auraflix.tv.core.util.prettyViews
import com.auraflix.tv.ui.detail.DetailViewModel
import com.auraflix.tv.ui.factoryOf
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    slug: String,
    onBack: () -> Unit,
    onOpenMovie: (String) -> Unit,
    onPlay: (String, Int, Int) -> Unit,
    /** Điện thoại đã ghép TV: gửi phim đang chọn sang TV rồi mở màn hình điều khiển. */
    onPlayOnTv: (String, Int, Int) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    val vm: DetailViewModel = viewModel(
        key = "detail-$slug",
        factory = factoryOf { DetailViewModel(MobileGraph.movies, MobileGraph.userData, slug) },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val remote = MobileGraph.tvRemote
    val remoteState by remote.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Box(
        modifier
            .fillMaxSize()
            .background(AuraColors.Background),
    ) {
        val detail = state.detail
        when {
            detail == null && state.loading -> LoadingState()
            detail == null -> ErrorState(
                message = state.error ?: "Đã có lỗi xảy ra.",
                onRetry = { vm.load(true) },
            )

            else -> DetailContent(
                detail = detail,
                related = state.related,
                favorite = state.favorite,
                preferredServer = state.preferredServer,
                resume = state.resume,
                onBack = onBack,
                onToggleFavorite = vm::toggleFavorite,
                onOpenMovie = onOpenMovie,
                onPlay = { server, episode -> onPlay(slug, server, episode) },
                tvPaired = remoteState.tv != null,
                onPlayOnTv = { server, episode ->
                    scope.launch {
                        val name = state.detail?.movie?.displayName.orEmpty()
                        if (remote.openOnTv(slug, server, episode, name)) {
                            onPlayOnTv(slug, server, episode)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun DetailContent(
    detail: MovieDetail,
    related: List<Movie>,
    favorite: Boolean,
    preferredServer: String,
    resume: HistoryItem?,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenMovie: (String) -> Unit,
    onPlay: (Int, Int) -> Unit,
    tvPaired: Boolean,
    onPlayOnTv: (Int, Int) -> Unit,
) {
    val movie = detail.movie ?: return
    val servers = detail.servers

    var serverIndex by remember(detail) {
        mutableIntStateOf(
            when {
                resume != null && resume.serverIndex in servers.indices -> resume.serverIndex
                preferredServer.isNotBlank() ->
                    servers.indexOfFirst { it.serverName == preferredServer }.takeIf { it >= 0 } ?: 0
                else -> 0
            },
        )
    }
    var episodeIndex by remember(detail) {
        mutableIntStateOf(
            resume?.episodeIndex?.takeIf { it >= 0 } ?: 0,
        )
    }
    var showEpisodes by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    val currentServer = servers.getOrNull(serverIndex)
    val episodes = currentServer?.episodes.orEmpty()
    val safeEpisode = episodeIndex.coerceIn(0, (episodes.size - 1).coerceAtLeast(0))
    val currentEpisode = episodes.getOrNull(safeEpisode)

    val resumeLabel = resume
        ?.takeIf { it.positionMs > 30_000 && it.progress < 0.95 && it.serverIndex == serverIndex }
        ?.let { formatDuration(it.positionMs) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item("header") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(320.dp),
            ) {
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
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.45f),
                                0.45f to Color.Transparent,
                                1f to AuraColors.Background,
                            ),
                        ),
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50)),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Quay lại",
                        tint = Color.White,
                    )
                }

                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = movie.displayName,
                        color = Color.White,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 28.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    movie.originName?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            color = AuraColors.TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        movie.year?.let { MetaPill(it.toString()) }
                        movie.quality?.let { MetaPill(it) }
                        movie.lang?.let { MetaPill(it) }
                        movie.episodeCurrent?.let { MetaPill(it) }
                        movie.time?.let { MetaPill(it) }
                        prettyViews(movie.view)?.let { MetaPill(it) }
                        movie.category.take(3).forEach { MetaPill(it.name) }
                    }
                }
            }
        }

        item("actions") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { onPlay(serverIndex, safeEpisode) },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuraColors.Accent,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when {
                            resumeLabel != null -> "Xem tiếp · $resumeLabel"
                            movie.isSeries && episodes.size > 1 -> "Phát tập ${safeEpisode + 1}"
                            else -> "Xem phim"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                OutlinedButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.height(46.dp),
                ) {
                    Icon(
                        imageVector = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = if (favorite) AuraColors.Accent else AuraColors.TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (favorite) "Đã thích" else "Yêu thích",
                        color = if (favorite) AuraColors.Accent else AuraColors.TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        // Phát trên TV: chỉ hiện khi điện thoại đã ghép cặp với TV trong LAN.
        if (tvPaired) {
            item("actions-tv") {
                OutlinedButton(
                    onClick = { onPlayOnTv(serverIndex, safeEpisode) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(42.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tv,
                        contentDescription = null,
                        tint = AuraColors.Accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Phát trên TV",
                        color = AuraColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        if (servers.isNotEmpty()) {
            item("episodes") {
                Column(Modifier.padding(top = 10.dp)) {
                    if (servers.size > 1) {
                        SectionHeader(
                            title = "Nguồn phát",
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(servers.size) { index ->
                                val server = servers[index]
                                FilterChip(
                                    selected = index == serverIndex,
                                    onClick = {
                                        serverIndex = index
                                        episodeIndex = 0
                                    },
                                    label = {
                                        Text(
                                            text = server.serverName ?: "Nguồn ${index + 1}",
                                            fontSize = 12.sp,
                                        )
                                    },
                                    colors = detailChipColors(index == serverIndex),
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = if (episodes.size > 1) "Danh sách tập (${episodes.size})" else "Tập phim",
                                color = AuraColors.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (currentEpisode != null) {
                                Text(
                                    text = currentEpisode.name ?: "Tập ${safeEpisode + 1}",
                                    color = AuraColors.TextMuted,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (episodes.size > 1) {
                            OutlinedButton(onClick = { showEpisodes = true }) {
                                Text("Chọn tập", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        item("content") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = "Nội dung",
                    color = AuraColors.TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = movie.content?.takeIf { it.isNotBlank() } ?: "Chưa có mô tả cho phim này.",
                    color = AuraColors.TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 6,
                    overflow = TextOverflow.Ellipsis,
                )
                if ((movie.content?.length ?: 0) > 220) {
                    Text(
                        text = if (expanded) "Thu gọn" else "Xem thêm",
                        color = AuraColors.Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { expanded = !expanded }
                            .padding(vertical = 4.dp),
                    )
                }
                val actors = movie.actor.filter { it.isNotBlank() }
                if (actors.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Diễn viên: ${actors.take(8).joinToString(", ")}",
                        color = AuraColors.TextMuted,
                        fontSize = 12.sp,
                    )
                }
                val directors = movie.director.filter { it.isNotBlank() }
                if (directors.isNotEmpty()) {
                    Text(
                        text = "Đạo diễn: ${directors.joinToString(", ")}",
                        color = AuraColors.TextMuted,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        if (related.isNotEmpty()) {
            item("related") {
                Column(Modifier.padding(top = 14.dp)) {
                    SectionHeader(
                        title = "Có thể bạn thích",
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(related, key = { it.slug }) { item ->
                            MoviePosterCard(
                                movie = item,
                                onClick = { onOpenMovie(item.slug) },
                                modifier = Modifier.width(112.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEpisodes) {
        EpisodeSheet(
            servers = servers,
            serverIndex = serverIndex,
            episodeIndex = safeEpisode,
            onSelectServer = {
                serverIndex = it
                episodeIndex = 0
            },
            onSelectEpisode = {
                episodeIndex = it
                showEpisodes = false
                onPlay(serverIndex, it)
            },
            onDismiss = { showEpisodes = false },
        )
    }
}

@Composable
private fun detailChipColors(selected: Boolean) = FilterChipDefaults.filterChipColors(
    selectedContainerColor = AuraColors.Accent,
    selectedLabelColor = Color.White,
    containerColor = AuraColors.Surface,
    labelColor = AuraColors.TextSecondary,
)

@Composable
private fun EpisodeSheet(
    servers: List<EpisodeServer>,
    serverIndex: Int,
    episodeIndex: Int,
    onSelectServer: (Int) -> Unit,
    onSelectEpisode: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val episodes = servers.getOrNull(serverIndex)?.episodes.orEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AuraColors.BackgroundElevated,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
        ) {
            Text(
                text = "Chọn tập phim",
                color = AuraColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )

            if (servers.size > 1) {
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(servers.size) { index ->
                        FilterChip(
                            selected = index == serverIndex,
                            onClick = { onSelectServer(index) },
                            label = {
                                Text(
                                    text = servers[index].serverName ?: "Nguồn ${index + 1}",
                                    fontSize = 12.sp,
                                )
                            },
                            colors = detailChipColors(index == serverIndex),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.heightIn(max = 460.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(episodes) { index, episode ->
                    FilterChip(
                        selected = index == episodeIndex,
                        onClick = { onSelectEpisode(index) },
                        label = {
                            Text(
                                text = episode.name ?: "Tập ${index + 1}",
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        colors = detailChipColors(index == episodeIndex),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
