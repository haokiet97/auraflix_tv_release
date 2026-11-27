@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)

package com.auraflix.tv.ui.player

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.auraflix.tv.Graph
import com.auraflix.tv.core.cast.CastCommand
import com.auraflix.tv.core.cast.CastPlaybackState
import com.auraflix.tv.core.cast.CastProtocol
import com.auraflix.tv.core.cast.PlayerControllerHolder
import com.auraflix.tv.core.util.formatDuration
import com.auraflix.tv.ui.components.EmptyScreen
import com.auraflix.tv.ui.components.FilterChip
import com.auraflix.tv.ui.components.TvButton
import com.auraflix.tv.ui.factoryOf
import com.auraflix.tv.ui.theme.AuraColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

@Composable
fun PlayerScreen(
    slug: String,
    initialServer: Int,
    initialEpisode: Int,
    onBack: () -> Unit,
) {
    val vm: PlayerViewModel = viewModel(
        key = "player-$slug-$initialServer-$initialEpisode",
        factory = factoryOf {
            PlayerViewModel(Graph.movies, Graph.userData, Graph.appScope, slug, initialServer, initialEpisode)
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(OkHttpDataSource.Factory(Graph.http.okHttp)),
            )
            .build()
            .apply { playWhenReady = true }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var ended by remember { mutableStateOf(false) }
    var showEpisodes by remember { mutableStateOf(false) }
    var aspectIndex by remember { mutableIntStateOf(0) }
    var speedIndex by remember { mutableIntStateOf(1) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }

    // Điều khiển từ điện thoại trong LAN (Giữ màn hình sáng cũng áp dụng cho remote).
    val remoteController = remember { RemotePlayerController() }

    val playFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }

    // Tua khi giữ nút ◀/▶: bước tua tăng dần và có OSD phản hồi.
    val scope = rememberCoroutineScope()
    var seekOverlay by remember { mutableStateOf(false) }
    var seekTotal by remember { mutableLongStateOf(0L) }
    var seekDirection by remember { mutableIntStateOf(0) }
    var holdSeeking by remember { mutableStateOf(false) }
    var seekJob by remember { mutableStateOf<Job?>(null) }

    // Sự kiện từ player
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                ended = playbackState == Player.STATE_ENDED
                if (playbackState == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                buffering = false
                controlsVisible = true
                vm.onPlayerError(
                    "Không phát được video (${error.errorCodeName}).\n" +
                        "Có thể nguồn phim đang lỗi, hãy thử lại hoặc đổi nguồn phát.",
                )
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Tải media khi đổi nguồn/tập
    LaunchedEffect(state.url) {
        val url = state.url ?: return@LaunchedEffect
        ended = false
        vm.clearError()
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        if (state.startPositionMs > 0) exoPlayer.seekTo(state.startPositionMs)
        exoPlayer.playWhenReady = true
        controlsVisible = false
    }

    // Đồng bộ tiến trình
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            position = exoPlayer.currentPosition
            buffered = exoPlayer.bufferedPosition
            if (exoPlayer.duration > 0) duration = exoPlayer.duration
            delay(500)
        }
    }

    // Lưu vị trí xem
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            delay(5_000)
            if (exoPlayer.isPlaying) {
                vm.saveProgress(exoPlayer.currentPosition, exoPlayer.duration.coerceAtLeast(0L))
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            seekJob?.cancel()
            vm.saveProgress(exoPlayer.currentPosition, exoPlayer.duration.coerceAtLeast(0L))
            exoPlayer.release()
        }
    }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Tự ẩn thanh điều khiển
    LaunchedEffect(controlsVisible, isPlaying, showEpisodes) {
        if (controlsVisible && isPlaying && !showEpisodes) {
            delay(5_000)
            controlsVisible = false
        }
    }

    LaunchedEffect(controlsVisible, showEpisodes) {
        runCatching {
            if (!showEpisodes) {
                if (controlsVisible) playFocus.requestFocus() else rootFocus.requestFocus()
            }
        }
    }

    LaunchedEffect(speedIndex) {
        exoPlayer.setPlaybackSpeed(SPEEDS[speedIndex])
    }

    LaunchedEffect(aspectIndex) {
        playerView?.resizeMode = when (aspectIndex) {
            1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    // Đếm ngược tập tiếp theo
    var countdown by remember(ended) { mutableIntStateOf(10) }
    LaunchedEffect(ended, state.episodeIndex) {
        if (ended && state.hasNext) {
            countdown = 10
            while (countdown > 0) {
                delay(1_000)
                countdown--
            }
            if (vm.nextEpisode()) {
                ended = false
            }
        }
    }

    fun togglePlay() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
        controlsVisible = true
    }

    fun seekBy(deltaMs: Long): Long {
        val before = position
        val target = (before + deltaMs).coerceIn(0L, exoPlayer.duration.coerceAtLeast(0L))
        exoPlayer.seekTo(target)
        position = target
        return target - before
    }

    fun stopSeek(hideAfterMs: Long = 1_200L) {
        seekJob?.cancel()
        seekJob = null
        holdSeeking = false
        if (hideAfterMs <= 0) {
            seekOverlay = false
        } else {
            scope.launch {
                delay(hideAfterMs)
                if (!holdSeeking) seekOverlay = false
            }
        }
    }

    /**
     * Giữ ◀/▶: bước tua tăng dần 10s → 20s → 30s → 40s → 50s → 60s.
     * OSD hiển thị tổng thời lượng đã tua để người dùng biết đang ở đâu.
     */
    fun startSeek(direction: Int) {
        if (holdSeeking) return
        holdSeeking = true
        seekDirection = direction
        seekTotal = 0L
        seekOverlay = true
        seekJob?.cancel()
        seekJob = scope.launch {
            var step = 10_000L
            seekTotal += seekBy(direction * step)
            delay(400)

            var held = 0L
            while (isActive && holdSeeking && held < 12_000) {
                held += 250
                step = when {
                    held < 750 -> 10_000L
                    held < 1_500 -> 20_000L
                    held < 2_250 -> 30_000L
                    held < 3_000 -> 40_000L
                    held < 4_000 -> 50_000L
                    else -> 60_000L
                }
                seekTotal += seekBy(direction * step)
                delay(250)
            }

            // Phòng khi remote không gửi KeyUp: tự dừng và ẩn OSD.
            if (holdSeeking) {
                holdSeeking = false
                delay(650)
                if (!holdSeeking) seekOverlay = false
            }
        }
    }

    // Mở thanh công cụ thì dừng hold tua (tránh tua tiếp khi đang điều hướng nút).
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) stopSeek(0)
    }

    BackHandler {
        when {
            showEpisodes -> showEpisodes = false
            controlsVisible -> controlsVisible = false
            else -> onBack()
        }
    }

    // ------------------------------------------------- Điều khiển từ điện thoại

    /**
     * Tua theo lệnh remote: cho phép vượt quá duration để chạm mép phim (khi đó TV
     * hiện màn hình hết tập), và vượt quá phần đầu để lùi về 0.
     */
    fun remoteSeek(target: Long) {
        val max = exoPlayer.duration.coerceAtLeast(0L)
        val safe = target.coerceIn(0L, if (max > 0) max else 0L)
        exoPlayer.seekTo(safe)
        position = safe
        if (safe < max - 1_000) ended = false
        controlsVisible = true
    }

    fun handleRemote(command: CastCommand) {
        when (command.type) {
            CastProtocol.Command.PLAY -> exoPlayer.play()
            CastProtocol.Command.PAUSE -> exoPlayer.pause()
            CastProtocol.Command.TOGGLE -> if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()

            CastProtocol.Command.SEEK -> command.value?.let { remoteSeek(it) }
            CastProtocol.Command.SEEK_ABS -> command.value?.let { remoteSeek(it) }
            CastProtocol.Command.SEEK_BY -> command.value?.let { remoteSeek(position + it) }

            CastProtocol.Command.NEXT_EPISODE -> if (state.hasNext) {
                ended = false
                vm.nextEpisode()
            }

            CastProtocol.Command.PREV_EPISODE -> if (state.episodeIndex > 0) {
                ended = false
                vm.selectEpisode(state.episodeIndex - 1)
            }

            CastProtocol.Command.SELECT_EPISODE -> {
                val index = command.value?.toInt() ?: return
                ended = false
                vm.selectEpisode(index)
            }

            CastProtocol.Command.STOP -> onBack()
        }
    }

    DisposableEffect(remoteController) {
        PlayerControllerHolder.attach(remoteController)
        onDispose {
            remoteController.drain()
            PlayerControllerHolder.detach(remoteController)
        }
    }

    // Xử lý lệnh trên main thread sau mỗi lần có lệnh mới.
    LaunchedEffect(remoteController.version) {
        remoteController.drain().forEach { command ->
            runCatching { handleRemote(command) }
        }
    }

    // Chia sẻ trạng thái cho điện thoại: cập nhật vị trí theo nhịp 1s khi đang mở màn hình.
    LaunchedEffect(Unit) {
        while (isActive) {
            remoteController.publish(
                CastPlaybackState(
                    active = true,
                    movieName = state.movieName,
                    posterUrl = state.posterUrl,
                    episodeLabel = state.episodeLabel,
                    episodeIndex = state.episodeIndex,
                    episodeCount = state.episodes.size,
                    serverIndex = state.serverIndex,
                    serverName = state.currentServer?.serverName.orEmpty(),
                    positionMs = exoPlayer.currentPosition,
                    durationMs = exoPlayer.duration.coerceAtLeast(0L),
                    isPlaying = exoPlayer.isPlaying,
                    buffering = buffering,
                    hasNext = state.hasNext,
                    ended = ended,
                    error = state.error,
                ),
            )
            delay(1_000)
        }
    }

    // Các thay đổi tức thời (tập/nguồn/lỗi) được đẩy ngay, không chờ nhịp 1s.
    LaunchedEffect(state.episodeIndex, state.serverIndex, state.error, ended, isPlaying) {
        remoteController.publish(
            remoteController.state.value.copy(
                episodeIndex = state.episodeIndex,
                episodeLabel = state.episodeLabel,
                serverIndex = state.serverIndex,
                serverName = state.currentServer?.serverName.orEmpty(),
                isPlaying = isPlaying,
                ended = ended,
                error = state.error,
            ),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                // Khi đang mở danh sách tập / màn hình lỗi / overlay hết tập,
                // để các nút trong overlay nhận phím thay vì tua phim phía sau.
                if (showEpisodes || state.error != null || ended) return@onPreviewKeyEvent false

                val seekDirectionKey = when (event.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> 0
                }

                if (seekDirectionKey != 0) {
                    // Thanh công cụ đang hiện: ◀/▶ di chuyển focus giữa các nút.
                    if (controlsVisible && !holdSeeking) return@onPreviewKeyEvent false

                    if (event.type == KeyEventType.KeyDown) {
                        // Bỏ qua các sự kiện auto-repeat của hệ thống;
                        // tốc độ tua do coroutine của app quyết định.
                        if (!holdSeeking && event.nativeKeyEvent.repeatCount == 0) {
                            startSeek(seekDirectionKey)
                        }
                    } else if (event.type == KeyEventType.KeyUp) {
                        stopSeek()
                    }
                    return@onPreviewKeyEvent true
                }

                if (event.type != KeyEventType.KeyDown) {
                    if (holdSeeking) stopSeek()
                    return@onPreviewKeyEvent false
                }
                if (holdSeeking) stopSeek()

                when (event.key) {
                    Key.DirectionUp, Key.DirectionDown,
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter ->
                        if (!controlsVisible) {
                            controlsVisible = true
                            true
                        } else false

                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        togglePlay()
                        true
                    }

                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = exoPlayer
                    playerView = this
                }
            },
            update = { it.player = exoPlayer },
            modifier = Modifier.fillMaxSize(),
        )

        if (state.loading || (buffering && state.error == null && !ended)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AuraColors.Accent, strokeWidth = 3.dp)
            }
        }

        if (state.servers.isEmpty() && !state.loading && state.error == null) {
            EmptyScreen("Không có nguồn phát.")
        }

        // OSD khi giữ ◀/▶: tổng thời lượng tua tăng dần.
        if (seekOverlay && !showEpisodes && state.error == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 30.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = if (seekDirection < 0) "« Tua lùi" else "Tua tới »",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (abs(seekTotal) < 60_000) {
                            "${abs(seekTotal) / 1000} giây"
                        } else {
                            formatDuration(abs(seekTotal))
                        },
                        color = AuraColors.AccentSoft,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${formatDuration(position)} / ${formatDuration(duration)}",
                        color = AuraColors.TextSecondary,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        // Thanh điều khiển
        AnimatedVisibility(visible = controlsVisible && !showEpisodes) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.75f),
                                0.35f to Color.Transparent,
                                0.7f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.92f),
                            ),
                        ),
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvButton(text = "‹ Thoát", onClick = onBack)
                    Spacer(Modifier.width(20.dp))
                    Column {
                        Text(
                            text = state.movieName,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (state.episodeLabel.isNotBlank()) {
                            Text(
                                text = state.episodeLabel,
                                color = AuraColors.TextSecondary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 40.dp, end = 40.dp, bottom = 34.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.22f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction(buffered, duration))
                                .fillMaxHeight()
                                .background(Color.White.copy(alpha = 0.30f)),
                        )
                        Box(
                            Modifier
                                .fillMaxWidth(fraction(position, duration))
                                .fillMaxHeight()
                                .background(AuraColors.Accent),
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            text = formatDuration(position),
                            color = AuraColors.TextSecondary,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = formatDuration(duration),
                            color = AuraColors.TextSecondary,
                            fontSize = 12.sp,
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TvButton(
                            text = "−10s",
                            onClick = { seekBy(-10_000) },
                            compact = true,
                            leading = {
                                Icon(
                                    imageVector = Icons.Filled.Replay,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.width(16.dp).height(16.dp),
                                )
                            },
                        )

                        TvButton(
                            text = if (isPlaying) "Tạm dừng" else "Phát",
                            onClick = { togglePlay() },
                            primary = true,
                            compact = true,
                            modifier = Modifier.focusRequester(playFocus),
                            leading = {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.width(18.dp).height(18.dp),
                                )
                            },
                        )

                        TvButton(
                            text = "+10s",
                            onClick = { seekBy(10_000) },
                            compact = true,
                            leading = {
                                Icon(
                                    imageVector = Icons.Filled.FastForward,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.width(16.dp).height(16.dp),
                                )
                            },
                        )

                        if (state.isSeries) {
                            TvButton(
                                text = "Tập phim",
                                onClick = {
                                    showEpisodes = true
                                    controlsVisible = false
                                },
                                compact = true,
                                leading = {
                                    Icon(
                                        imageVector = Icons.Filled.PlaylistPlay,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.width(16.dp).height(16.dp),
                                    )
                                },
                            )
                            if (state.hasNext) {
                                TvButton(
                                    text = "Tập tiếp",
                                    onClick = {
                                        ended = false
                                        vm.nextEpisode()
                                    },
                                    compact = true,
                                    leading = {
                                        Icon(
                                            imageVector = Icons.Filled.SkipNext,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.width(16.dp).height(16.dp),
                                        )
                                    },
                                )
                            }
                        }

                        if (state.servers.size > 1) {
                            TvButton(
                                text = "Nguồn: ${state.currentServer?.serverName ?: "1"}",
                                onClick = { vm.nextServer() },
                                compact = true,
                            )
                        }

                        TvButton(
                            text = "${SPEEDS[speedIndex]}x",
                            onClick = { speedIndex = (speedIndex + 1) % SPEEDS.size },
                            compact = true,
                            leading = {
                                Icon(
                                    imageVector = Icons.Filled.Speed,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.width(16.dp).height(16.dp),
                                )
                            },
                        )

                        TvButton(
                            text = when (aspectIndex) {
                                1 -> "Zoom"
                                2 -> "Giãn"
                                else -> "Tỉ lệ gốc"
                            },
                            onClick = { aspectIndex = (aspectIndex + 1) % 3 },
                            compact = true,
                        )
                    }
                }
            }
        }

        // Danh sách tập
        if (showEpisodes) {
            EpisodePanel(
                state = state,
                onDismiss = { showEpisodes = false },
                onSelectServer = { vm.selectServer(it) },
                onSelectEpisode = {
                    vm.selectEpisode(it)
                    showEpisodes = false
                },
            )
        }

        // Lỗi phát
        state.error?.let { message ->
            ErrorOverlay(
                message = message,
                showSwitchServer = state.hasNextServer,
                onRetry = { vm.retry() },
                onSwitchServer = { vm.nextServer() },
                onBack = onBack,
            )
        }

        // Hết tập -> tập tiếp theo
        if (ended && state.error == null && !showEpisodes) {
            NextEpisodeOverlay(
                hasNext = state.hasNext,
                countdown = countdown,
                onPlayNext = {
                    ended = false
                    vm.nextEpisode()
                },
                onDismiss = { ended = false },
                onBack = onBack,
            )
        }
    }
}

private fun fraction(value: Long, total: Long): Float {
    if (total <= 0) return 0f
    return (value.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

@Composable
private fun EpisodePanel(
    state: PlayerUiState,
    onDismiss: () -> Unit,
    onSelectServer: (Int) -> Unit,
    onSelectEpisode: (Int) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f))) {
        val panelFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { panelFocus.requestFocus() } }
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(520.dp)
                .background(AuraColors.BackgroundElevated)
                .padding(28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Danh sách tập",
                    color = AuraColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TvButton(
                    text = "Đóng",
                    onClick = onDismiss,
                    modifier = Modifier.focusRequester(panelFocus),
                )
            }

            if (state.servers.size > 1) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.servers.forEachIndexed { index, server ->
                        FilterChip(
                            label = server.serverName ?: "Nguồn ${index + 1}",
                            selected = index == state.serverIndex,
                            onClick = { onSelectServer(index) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(state.episodes) { index, episode ->
                    TvButton(
                        text = episode.name ?: "Tập ${index + 1}",
                        onClick = { onSelectEpisode(index) },
                        primary = index == state.episodeIndex,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorOverlay(
    message: String,
    showSwitchServer: Boolean,
    onRetry: () -> Unit,
    onSwitchServer: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        val retryFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { retryFocus.requestFocus() } }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 720.dp).padding(32.dp),
        ) {
            Text(
                text = "Không phát được phim",
                color = AuraColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                color = AuraColors.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    text = "Thử lại",
                    onClick = onRetry,
                    primary = true,
                    modifier = Modifier.focusRequester(retryFocus),
                )
                if (showSwitchServer) {
                    TvButton(text = "Đổi nguồn phát", onClick = onSwitchServer)
                }
                TvButton(text = "Quay lại", onClick = onBack)
            }
        }
    }
}

@Composable
private fun NextEpisodeOverlay(
    hasNext: Boolean,
    countdown: Int,
    onPlayNext: () -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        val actionFocus = remember { FocusRequester() }
        LaunchedEffect(hasNext) { runCatching { actionFocus.requestFocus() } }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (hasNext) "Đang phát tập tiếp theo sau ${countdown}s" else "Đã xem hết phim",
                color = AuraColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (hasNext) {
                    TvButton(
                        text = "Phát ngay",
                        onClick = onPlayNext,
                        primary = true,
                        modifier = Modifier.focusRequester(actionFocus),
                    )
                    TvButton(text = "Huỷ", onClick = onDismiss)
                } else {
                    TvButton(
                        text = "Xem lại",
                        onClick = onDismiss,
                        primary = true,
                        modifier = Modifier.focusRequester(actionFocus),
                    )
                    TvButton(text = "Quay lại", onClick = onBack)
                }
            }
        }
    }
}
