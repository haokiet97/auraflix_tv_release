@file:OptIn(ExperimentalMaterial3Api::class)

package com.auraflix.mobile.ui.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.auraflix.mobile.MobileGraph
import com.auraflix.mobile.ui.theme.AuraColors
import com.auraflix.tv.core.cast.CastPlaybackState
import com.auraflix.tv.core.cast.RemoteStatus
import com.auraflix.tv.core.util.formatDuration

/**
 * Màn hình điều khiển TV từ điện thoại: phát/tạm dừng, tua ±10s, thanh tua,
 * chuyển tập và tắt phát trên TV. Trạng thái TV được đọc lại mỗi giây từ [TvRemote].
 */
@Composable
fun RemoteScreen(
    onBack: () -> Unit,
    onConnectTv: () -> Unit,
) {
    val remote = MobileGraph.tvRemote
    val state by remote.state.collectAsStateWithLifecycle()
    var lastPlayback by remember { mutableStateOf<CastPlaybackState?>(null) }

    DisposableEffect(remote) {
        remote.startPolling()
        onDispose { remote.stopPolling() }
    }

    // `state.playback` có thể là dữ liệu null cũ từ lần poll cuối ở màn duyệt phim (TV vừa
    // chuyển màn). Chỉ tin trạng thái mới khi poll lại thành công, tránh hiện nhầm
    // "TV chưa phát phim nào" trong vài giây đầu.
    val fresh = if (state.status == RemoteStatus.ONLINE) state.playback else null
    if (fresh != null && fresh != lastPlayback) {
        lastPlayback = fresh
    }
    val playback = lastPlayback

    if (state.tv == null) {
        EmptyRemote(onConnectTv = onConnectTv, onBack = onBack)
        return
    }

    var dragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }
    var showEpisodes by remember { mutableStateOf(false) }

    val progress = if (dragging) sliderValue else playback?.positionMs?.toFloat() ?: 0f
    val duration = playback?.durationMs?.coerceAtLeast(1L) ?: 1L

    Column(
        Modifier
            .fillMaxSize()
            .background(AuraColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        TopBar(
            title = state.tv?.name ?: "Điều khiển TV",
            subtitle = when (state.status) {
                RemoteStatus.ONLINE -> "Đã kết nối"
                RemoteStatus.CONNECTING -> "Đang kết nối…"
                RemoteStatus.OFFLINE -> "Mất kết nối"
                RemoteStatus.NONE -> ""
            },
            online = state.status == RemoteStatus.ONLINE,
            onBack = onBack,
            onSwitchTv = onConnectTv,
        )

        state.message?.let { message ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(AuraColors.Accent.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message,
                    color = AuraColors.AccentSoft,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = remote::clearMessage) {
                    Text("Đóng", color = AuraColors.AccentSoft, fontSize = 12.sp)
                }
            }
        }

        if (playback == null || !playback.active) {
            NothingPlaying(
                status = state.status,
                hadPlayback = playback != null,
                onPick = onBack,
                onRetry = remote::retryConnection,
            )
        } else {
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(10.dp))
                NowPlayingCard(playback = playback)

                Spacer(Modifier.height(20.dp))
                Slider(
                    value = progress.coerceIn(0f, duration.toFloat()),
                    onValueChange = {
                        dragging = true
                        sliderValue = it
                    },
                    onValueChangeFinished = {
                        remote.seekTo(sliderValue.toLong())
                        dragging = false
                    },
                    valueRange = 0f..duration.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = AuraColors.Accent,
                        activeTrackColor = AuraColors.Accent,
                        inactiveTrackColor = AuraColors.SurfaceVariant,
                    ),
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = formatDuration(progress.toLong()),
                        color = AuraColors.TextMuted,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatDuration(duration),
                        color = AuraColors.TextMuted,
                        fontSize = 11.sp,
                    )
                }

                Spacer(Modifier.height(18.dp))
                TransportRow(
                    isPlaying = playback.isPlaying,
                    canPrev = playback.episodeIndex > 0,
                    canNext = playback.hasNext,
                    onPrev = remote::previousEpisode,
                    onBack10 = { remote.seekBy(-10_000) },
                    onToggle = remote::togglePlay,
                    onForward10 = { remote.seekBy(10_000) },
                    onNext = remote::nextEpisode,
                )

                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (playback.episodeCount > 1) {
                        OutlinedButton(
                            onClick = { showEpisodes = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Chọn tập", color = AuraColors.TextPrimary, fontSize = 12.sp)
                        }
                    }
                    OutlinedButton(
                        onClick = remote::stop,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Filled.Stop,
                            contentDescription = null,
                            tint = AuraColors.TextPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Dừng trên TV", color = AuraColors.TextPrimary, fontSize = 12.sp)
                    }
                }

                if (playback.error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "TV báo lỗi: ${playback.error}",
                        color = AuraColors.Accent,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }

    if (showEpisodes && playback != null) {
        EpisodeSheet(
            count = playback.episodeCount,
            current = playback.episodeIndex,
            onSelect = {
                remote.selectEpisode(it)
                showEpisodes = false
            },
            onDismiss = { showEpisodes = false },
        )
    }
}

@Composable
private fun TopBar(
    title: String,
    subtitle: String,
    online: Boolean,
    onBack: () -> Unit,
    onSwitchTv: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Quay lại",
                tint = AuraColors.TextPrimary,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = AuraColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (online) Color(0xFF4CD07D) else AuraColors.Accent),
                )
                Spacer(Modifier.width(6.dp))
                Text(subtitle, color = AuraColors.TextMuted, fontSize = 11.sp)
            }
        }
        IconButton(onClick = onSwitchTv) {
            Icon(Icons.Filled.Tv, contentDescription = "Đổi TV", tint = AuraColors.TextSecondary)
        }
    }
}

@Composable
private fun NowPlayingCard(playback: CastPlaybackState) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(AuraColors.Surface, RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 118.dp, height = 68.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AuraColors.SurfaceVariant),
        ) {
            AsyncImage(
                model = playback.posterUrl,
                contentDescription = playback.movieName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = playback.movieName.ifBlank { "Chưa có phim" },
                color = AuraColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(
                playback.episodeLabel.takeIf { it.isNotBlank() },
                playback.serverName.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (details.isNotBlank()) {
                Text(details, color = AuraColors.TextMuted, fontSize = 11.sp, maxLines = 1)
            }
            Text(
                text = when {
                    playback.ended -> "Đã hết tập"
                    playback.buffering -> "Đang tải…"
                    playback.isPlaying -> "Đang phát trên TV"
                    else -> "Tạm dừng"
                },
                color = AuraColors.AccentSoft,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun TransportRow(
    isPlaying: Boolean,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onBack10: () -> Unit,
    onToggle: () -> Unit,
    onForward10: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteIconButton(
            icon = Icons.Filled.SkipPrevious,
            description = "Tập trước",
            enabled = canPrev,
            onClick = onPrev,
        )
        RemoteIconButton(
            icon = Icons.Filled.Replay,
            description = "Tua lùi 10 giây",
            onClick = onBack10,
        )
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(AuraColors.Accent),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onToggle, modifier = Modifier.size(72.dp)) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Tạm dừng" else "Phát",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp),
                )
            }
        }
        RemoteIconButton(
            icon = Icons.Filled.FastForward,
            description = "Tua tới 10 giây",
            onClick = onForward10,
        )
        RemoteIconButton(
            icon = Icons.Filled.SkipNext,
            description = "Tập tiếp",
            enabled = canNext,
            onClick = onNext,
        )
    }
}

@Composable
private fun RemoteIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) Color.White else InactiveTint,
            modifier = Modifier.size(30.dp),
        )
    }
}

private val InactiveTint = Color.White.copy(alpha = 0.28f)

@Composable
private fun NothingPlaying(
    status: RemoteStatus,
    hadPlayback: Boolean,
    onPick: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
            Icon(
                Icons.Filled.Tv,
                contentDescription = null,
                tint = AuraColors.TextMuted,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = when {
                    status == RemoteStatus.OFFLINE -> "Không kết nối được với TV."
                    status == RemoteStatus.CONNECTING -> "Đang kết nối với TV…"
                    hadPlayback -> "Đã dừng trên TV."
                    else -> "TV chưa phát phim nào.\nChọn phim trong app rồi bấm “Phát trên TV”."
                },
                color = AuraColors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (status == RemoteStatus.OFFLINE) {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = AuraColors.Accent),
                    ) {
                        Text("Kết nối lại", fontSize = 13.sp)
                    }
                } else {
                    Button(
                        onClick = onPick,
                        colors = ButtonDefaults.buttonColors(containerColor = AuraColors.Accent),
                    ) {
                        Text("Chọn phim", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyRemote(onConnectTv: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(AuraColors.Background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
            Text(
                text = "Chưa kết nối TV",
                color = AuraColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Kết nối với TV để chọn phim trên điện thoại và phát trên TV.",
                color = AuraColors.TextMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onConnectTv,
                colors = ButtonDefaults.buttonColors(containerColor = AuraColors.Accent),
            ) {
                Text("Kết nối TV", fontSize = 13.sp)
            }
            TextButton(onClick = onBack) {
                Text("Quay lại", color = AuraColors.TextSecondary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun EpisodeSheet(
    count: Int,
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AuraColors.BackgroundElevated,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 18.dp)) {
            Text(
                text = "Chọn tập",
                color = AuraColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.heightIn(max = 360.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed((0 until count).toList()) { _, index ->
                    FilterChip(
                        selected = index == current,
                        onClick = { onSelect(index) },
                        label = { Text("Tập ${index + 1}", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AuraColors.Accent,
                            selectedLabelColor = Color.White,
                            containerColor = AuraColors.Surface,
                            labelColor = AuraColors.TextSecondary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
