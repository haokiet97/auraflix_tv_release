@file:OptIn(ExperimentalFoundationApi::class)

package com.auraflix.mobile.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import coil3.compose.AsyncImage
import com.auraflix.mobile.ui.theme.AuraColors
import com.auraflix.tv.core.model.Movie
import com.auraflix.tv.core.repo.HistoryItem
import kotlinx.coroutines.delay

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onMore: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AuraColors.Accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            color = AuraColors.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        onMore?.let {
            Text(
                text = "Xem tất cả",
                color = AuraColors.Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = it)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun MoviePosterCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(AuraColors.SurfaceVariant)
                .clickable(onClick = onClick),
        ) {
            AsyncImage(
                model = movie.posterUrl ?: movie.thumbUrl,
                contentDescription = movie.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.88f),
                        ),
                    ),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            ) {
                Text(
                    text = movie.displayName,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                movie.episodeCurrent?.let {
                    Text(
                        text = it,
                        color = AuraColors.AccentSoft,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = listOfNotNull(movie.year?.toString(), movie.quality).joinToString(" · "),
            color = AuraColors.TextMuted,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

@Composable
fun ContinueWatchingCard(
    item: HistoryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AuraColors.Surface)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(AuraColors.SurfaceVariant),
        ) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.9f),
                        ),
                    ),
            )
            if (item.progress > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.25f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(item.progress.coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(AuraColors.Accent),
                    )
                }
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = item.name,
                color = AuraColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    item.episodeName.takeIf { it.isNotBlank() },
                    "${(item.progress * 100).toInt()}%",
                ).joinToString(" · "),
                color = AuraColors.TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun MetaPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(text, color = AuraColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun FeaturedCarousel(
    heroes: List<Movie>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (heroes.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { heroes.size })

    LaunchedEffect(heroes.size) {
        if (heroes.size <= 1) return@LaunchedEffect
        while (true) {
            delay(6_000)
            if (!pagerState.isScrollInProgress) {
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % heroes.size)
            }
        }
    }

    Column(modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
        ) { page ->
            val movie = heroes[page]
            Box(Modifier.fillMaxSize()) {
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
                                0f to Color.Transparent,
                                0.45f to Color.Transparent,
                                1f to AuraColors.Background,
                            ),
                        ),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to AuraColors.Background.copy(alpha = 0.72f),
                                0.6f to Color.Transparent,
                            ),
                        ),
                )

                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
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
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        movie.year?.let { MetaPill(it.toString()) }
                        movie.quality?.let { MetaPill(it) }
                        movie.lang?.let { MetaPill(it) }
                        movie.episodeCurrent?.let { MetaPill(it) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onOpen(movie.slug) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AuraColors.Accent,
                            contentColor = Color.White,
                        ),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Xem phim", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (heroes.size > 1) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(heroes.size) { index ->
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (index == pagerState.currentPage) 16.dp else 6.dp, 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) AuraColors.Accent
                                else Color.White.copy(alpha = 0.30f),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingState(message: String = "Đang tải phim…", modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = AuraColors.Accent, strokeWidth = 3.dp)
            Spacer(Modifier.height(14.dp))
            Text(message, color = AuraColors.TextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(28.dp),
        ) {
            Text("😕", fontSize = 34.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                color = AuraColors.TextSecondary,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = AuraColors.Accent),
            ) {
                Text("Thử lại")
            }
        }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = AuraColors.TextMuted,
            fontSize = 14.sp,
            modifier = Modifier.padding(28.dp),
        )
    }
}
