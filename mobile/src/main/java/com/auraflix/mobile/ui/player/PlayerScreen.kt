@file:OptIn(ExperimentalMaterial3Api::class)

package com.auraflix.mobile.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import com.auraflix.mobile.MobileGraph
import com.auraflix.mobile.ui.theme.AuraColors
import com.auraflix.tv.core.util.formatDuration
import com.auraflix.tv.ui.factoryOf
import com.auraflix.tv.ui.player.PlayerUiState
import com.auraflix.tv.ui.player.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

/** Tốc độ khi nhấn giữ màn hình để tua nhanh. */
private const val HOLD_SPEED = 2f

/** Thời gian giữ tối thiểu (ms) trước khi kích hoạt tua nhanh 2x. */
private const val HOLD_DELAY_MS = 350L

@Composable
fun PlayerScreen(
    slug: String,
    initialServer: Int,
    initialEpisode: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: PlayerViewModel = viewModel(
        key = "player-$slug-$initialServer-$initialEpisode",
        factory = factoryOf {
            PlayerViewModel(
                repo = MobileGraph.movies,
                userData = MobileGraph.userData,
                appScope = MobileGraph.appScope,
                slug = slug,
                initialServer = initialServer,
                initialEpisode = initialEpisode,
            )
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(OkHttpDataSource.Factory(MobileGraph.http.okHttp)),
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
    var dragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    var swipeHud by remember { mutableStateOf<SwipeHud?>(null) }
    // Nhấn giữ để tua nhanh: true khi đang giữ, tạm phát ở 2x.
    var fastForwarding by remember { mutableStateOf(false) }

    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

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

    LaunchedEffect(exoPlayer) {
        while (isActive) {
            if (!dragging) {
                position = exoPlayer.currentPosition
                buffered = exoPlayer.bufferedPosition
                if (exoPlayer.duration > 0) duration = exoPlayer.duration
            }
            delay(500)
        }
    }

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
            vm.saveProgress(exoPlayer.currentPosition, exoPlayer.duration.coerceAtLeast(0L))
            exoPlayer.release()
        }
    }

    // Giữ màn hình luôn sáng khi đang xem phim. Cờ được bật ở cấp Window
    // (FLAG_KEEP_SCREEN_ON) chứ không chỉ trên ComposeView: PlayerView của Media3
    // là view con đè lên và có surface riêng, nên khi video surface nhận focus,
    // keepScreenOn của ComposeView không còn được tôn trọng -> màn hình tự tắt/khoá.
    val activity = remember(context) { context.findActivity() }

    /**
     * Đặt độ sáng riêng cho cửa sổ trình phát (0..1). Độ sáng này chỉ áp dụng cho
     * Activity hiện tại và được khôi phục khi rời trình phát.
     */
    fun applyBrightness(value: Float) {
        val window = activity?.window ?: return
        val params = window.attributes
        params.screenBrightness = value.coerceIn(0.01f, 1f)
        window.attributes = params
    }

    DisposableEffect(view, activity) {
        view.keepScreenOn = true
        val window = activity?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            view.keepScreenOn = false
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // Khôi phục độ sáng do hệ thống quản lý khi rời trình phát.
            val params = window?.attributes ?: return@onDispose
            params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = params
        }
    }

    // Khoá landscape + ẩn thanh hệ thống khi xem phim.
    DisposableEffect(activity) {
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = previousOrientation
                ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, showEpisodes, ended, state.error, dragging) {
        if (controlsVisible && isPlaying && !showEpisodes && !ended &&
            state.error == null && !dragging
        ) {
            delay(4_000)
            controlsVisible = false
        }
    }

    // Tốc độ hiệu lực: khi nhấn giữ thì tạm 2x, nhả ra quay lại tốc độ người dùng đã chọn.
    LaunchedEffect(speedIndex, fastForwarding) {
        exoPlayer.setPlaybackSpeed(if (fastForwarding) HOLD_SPEED else SPEEDS[speedIndex])
    }

    LaunchedEffect(aspectIndex) {
        playerView?.resizeMode = when (aspectIndex) {
            1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(800)
            seekFeedback = null
        }
    }

    // Đếm ngược tự phát tập tiếp theo.
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

    BackHandler {
        when {
            showEpisodes -> showEpisodes = false
            controlsVisible -> controlsVisible = false
            else -> onBack()
        }
    }

    fun seekTo(target: Long) {
        val max = exoPlayer.duration.coerceAtLeast(0L)
        val safe = target.coerceIn(0L, max)
        exoPlayer.seekTo(safe)
        position = safe
        controlsVisible = true
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black),
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

        // Lớp nhận thao tác chạm/vuốt:
        // - Tap: hiện/ẩn điều khiển.
        // - Double-tap: tua ±10 giây.
        // - Nhấn giữ (hold): tạm phát 2x, nhả ra về tốc độ đang chọn.
        // - Vuốt dọc nửa trái: tăng/giảm độ sáng. Vuốt dọc nửa phải: tăng/giảm âm lượng.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(showEpisodes, state.error, controlsVisible, ended) {
                    val touchSlop = viewConfiguration.touchSlop
                    // Kéo hết khoảng 60% chiều cao đổi được toàn dải 0..100%.
                    val fullRangePx = (size.height * 0.6f).coerceAtLeast(1f)
                    val doubleTapSlop = touchSlop * 3f

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Hold/vuốt không hoạt động khi đang mở sheet, lỗi hoặc đã hết phim.
                        val gesturesBlocked = showEpisodes || state.error != null || ended
                        // Tap đổi trạng thái điều khiển chỉ khi thanh điều khiển đang ẩn.
                        val tapBlocked = gesturesBlocked || controlsVisible
                        val startX = down.position.x
                        val startY = down.position.y
                        val isLeft = startX < size.width / 2f

                        val brightnessStart = if (isLeft) readBrightness(activity) else 0f
                        val volumeMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val volumeStart = if (!isLeft) {
                            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        } else {
                            0
                        }

                        var mode = SwipeMode.Pending
                        var maxDx = 0f
                        var maxDy = 0f
                        val downTime = System.currentTimeMillis()
                        var holdTriggered = false

                        while (true) {
                            // Trong lúc chưa xác định cử chỉ (chưa di chuyển đủ xa), đợi sự kiện
                            // tối đa tới mốc HOLD_DELAY_MS để có thể kích hoạt tua nhanh khi giữ.
                            val nextEvent = if (mode == SwipeMode.Pending && !holdTriggered && !gesturesBlocked) {
                                val remaining = HOLD_DELAY_MS - (System.currentTimeMillis() - downTime)
                                val event = if (remaining > 0) {
                                    withTimeoutOrNull(remaining) { awaitPointerEvent() }
                                } else {
                                    awaitPointerEvent()
                                }
                                if (event == null) {
                                    // Hết thời gian giữ mà ngón vẫn đè và chưa di chuyển -> tua nhanh.
                                    holdTriggered = true
                                    fastForwarding = true
                                    continue
                                }
                                event
                            } else {
                                awaitPointerEvent()
                            }

                            val change = nextEvent.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            val dx = change.position.x - startX
                            val dy = change.position.y - startY
                            maxDx = maxOf(maxDx, abs(dx))
                            maxDy = maxOf(maxDy, abs(dy))

                            if (mode == SwipeMode.Pending && maxOf(maxDx, maxDy) > touchSlop) {
                                // Đã di chuyển: đây là vuốt (không phải hold).
                                if (holdTriggered) {
                                    holdTriggered = false
                                    fastForwarding = false
                                }
                                // Ưu tiên dọc: người dùng vuốt sáng/âm lượng thường hơi lệch ngang.
                                mode = if (abs(dy) > abs(dx)) SwipeMode.Vertical else SwipeMode.Other
                            }

                            if (mode == SwipeMode.Vertical) {
                                change.consume()
                                if (isLeft) {
                                    val value = (brightnessStart - dy / fullRangePx).coerceIn(0.01f, 1f)
                                    applyBrightness(value)
                                    swipeHud = SwipeHud(SwipeKind.Brightness, value)
                                } else {
                                    val target = (volumeStart - dy / fullRangePx * volumeMax)
                                        .roundToInt().coerceIn(0, volumeMax)
                                    if (target != audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                                    }
                                    swipeHud = SwipeHud(
                                        kind = SwipeKind.Volume,
                                        fraction = if (volumeMax > 0) target.toFloat() / volumeMax else 0f,
                                    )
                                }
                            }
                        }

                        // Nhả tay: luôn tắt tua nhanh nếu đang bật.
                        val wasHolding = holdTriggered
                        if (holdTriggered) {
                            holdTriggered = false
                            fastForwarding = false
                        }

                        when (mode) {
                            SwipeMode.Vertical -> swipeHud = null
                            SwipeMode.Other -> Unit
                            SwipeMode.Pending -> {
                                // Vừa giữ để tua nhanh, hoặc đã di chuyển, hoặc bị chặn: không xử lý tap.
                                if (wasHolding || maxOf(maxDx, maxDy) > touchSlop || tapBlocked) {
                                    return@awaitEachGesture
                                }
                                // Chưa di chuyển: chờ xem có cú chạm thứ hai (double-tap) hay không.
                                val isDoubleTap = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                                    val secondDown = awaitFirstDown(requireUnconsumed = false)
                                    val near = abs(secondDown.position.x - startX) < doubleTapSlop &&
                                        abs(secondDown.position.y - startY) < doubleTapSlop
                                    if (!near) return@withTimeoutOrNull null
                                    // Đợi nhấc ngón để nuốt trọn cú tap thứ hai.
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == secondDown.id } ?: break
                                        if (!change.pressed) break
                                    }
                                    true
                                } ?: false

                                if (isDoubleTap) {
                                    val delta = when {
                                        startX < size.width / 3f -> -10_000L
                                        startX > size.width * 2f / 3f -> 10_000L
                                        else -> 0L
                                    }
                                    if (delta != 0L) {
                                        seekTo(exoPlayer.currentPosition + delta)
                                        controlsVisible = false
                                        seekFeedback = if (delta < 0) "« 10 giây" else "10 giây »"
                                    }
                                } else {
                                    controlsVisible = !controlsVisible
                                }
                            }
                        }
                    }
                },
        )

        if (state.loading || (buffering && state.error == null && !ended)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AuraColors.Accent, strokeWidth = 3.dp)
            }
        }

        seekFeedback?.let { feedback ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = feedback,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }

        swipeHud?.let { hud ->
            SwipeHudOverlay(hud)
        }

        AnimatedVisibility(visible = fastForwarding, modifier = Modifier.align(Alignment.TopCenter)) {
            Text(
                text = "▶▶  ${HOLD_SPEED.toInt()}x",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(top = 18.dp)
                    .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }

        AnimatedVisibility(visible = controlsVisible && !showEpisodes && state.error == null) {
            PlayerControls(
                state = state,
                position = if (dragging) sliderValue.toLong() else position,
                duration = duration,
                isPlaying = isPlaying,
                sliderValue = if (dragging) sliderValue else position.toFloat(),
                onSliderChange = {
                    dragging = true
                    sliderValue = it
                },
                onSliderFinished = {
                    exoPlayer.seekTo(sliderValue.toLong())
                    position = sliderValue.toLong()
                    dragging = false
                },
                speedIndex = speedIndex,
                aspectIndex = aspectIndex,
                onBack = onBack,
                onTogglePlay = {
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    controlsVisible = true
                },
                onSeekBack = { seekTo(position - 10_000) },
                onSeekForward = { seekTo(position + 10_000) },
                onShowEpisodes = {
                    showEpisodes = true
                    controlsVisible = false
                },
                onNextEpisode = {
                    ended = false
                    vm.nextEpisode()
                },
                onSwitchServer = { vm.nextServer() },
                onCycleSpeed = { speedIndex = (speedIndex + 1) % SPEEDS.size },
                onCycleAspect = { aspectIndex = (aspectIndex + 1) % 3 },
            )
        }

        if (showEpisodes) {
            EpisodeSheet(
                state = state,
                onDismiss = { showEpisodes = false },
                onSelectServer = { vm.selectServer(it) },
                onSelectEpisode = {
                    vm.selectEpisode(it)
                    showEpisodes = false
                },
            )
        }

        state.error?.let { message ->
            ErrorOverlay(
                message = message,
                showSwitchServer = state.hasNextServer,
                onRetry = { vm.retry() },
                onSwitchServer = { vm.nextServer() },
                onBack = onBack,
            )
        }

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

@Composable
private fun PlayerControls(
    state: PlayerUiState,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    sliderValue: Float,
    onSliderChange: (Float) -> Unit,
    onSliderFinished: () -> Unit,
    speedIndex: Int,
    aspectIndex: Int,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onShowEpisodes: () -> Unit,
    onNextEpisode: () -> Unit,
    onSwitchServer: () -> Unit,
    onCycleSpeed: () -> Unit,
    onCycleAspect: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Màn hình quá thấp (split-screen, TV letterbox): ẩn cụm điều khiển giữa
        // và đưa nút Phát/Tạm dừng xuống hàng dưới để tránh chồng lấn.
        val compact = maxHeight < 300.dp

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.72f),
                        0.32f to Color.Transparent,
                        0.66f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.92f),
                    ),
                ),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Thoát",
                    tint = Color.White,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.movieName,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.episodeLabel.isNotBlank()) {
                    Text(
                        text = state.episodeLabel,
                        color = AuraColors.TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
        }

        if (!compact) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(34.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onSeekBack, modifier = Modifier.size(52.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Replay,
                        contentDescription = "Tua lùi 10 giây",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(AuraColors.Accent.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = onTogglePlay, modifier = Modifier.size(64.dp)) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Tạm dừng" else "Phát",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
                IconButton(onClick = onSeekForward, modifier = Modifier.size(52.dp)) {
                    Icon(
                        imageVector = Icons.Filled.FastForward,
                        contentDescription = "Tua tới 10 giây",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        ) {
            Slider(
                value = sliderValue.coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
                onValueChange = onSliderChange,
                onValueChangeFinished = onSliderFinished,
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = AuraColors.Accent,
                    activeTrackColor = AuraColors.Accent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.28f),
                ),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = formatDuration(position),
                    color = AuraColors.TextSecondary,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatDuration(duration),
                    color = AuraColors.TextSecondary,
                    fontSize = 11.sp,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (compact) {
                    ControlTextButton(
                        text = if (isPlaying) "Tạm dừng" else "Phát",
                        icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        onClick = onTogglePlay,
                    )
                }
                if (state.isSeries) {
                    ControlTextButton(
                        text = "Tập phim",
                        icon = Icons.Filled.PlaylistPlay,
                        onClick = onShowEpisodes,
                    )
                    if (state.hasNext) {
                        ControlTextButton(
                            text = "Tập tiếp",
                            icon = Icons.Filled.SkipNext,
                            onClick = onNextEpisode,
                        )
                    }
                }
                if (state.servers.size > 1) {
                    ControlTextButton(
                        text = "Nguồn: ${state.currentServer?.serverName ?: "1"}",
                        onClick = onSwitchServer,
                    )
                }
                ControlTextButton(
                    text = "${SPEEDS[speedIndex]}x",
                    icon = Icons.Filled.Speed,
                    onClick = onCycleSpeed,
                )
                ControlTextButton(
                    text = when (aspectIndex) {
                        1 -> "Zoom"
                        2 -> "Giãn"
                        else -> "Tỉ lệ gốc"
                    },
                    onClick = onCycleAspect,
                )
            }
        }
    }
}

@Composable
private fun ControlTextButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = modifier) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = Color.White, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun EpisodeSheet(
    state: PlayerUiState,
    onDismiss: () -> Unit,
    onSelectServer: (Int) -> Unit,
    onSelectEpisode: (Int) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AuraColors.BackgroundElevated,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 18.dp),
        ) {
            Text(
                text = "Danh sách tập",
                color = AuraColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )

            if (state.servers.size > 1) {
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.servers.size) { index ->
                        val server = state.servers[index]
                        FilterChip(
                            selected = index == state.serverIndex,
                            onClick = { onSelectServer(index) },
                            label = {
                                Text(
                                    text = server.serverName ?: "Nguồn ${index + 1}",
                                    fontSize = 12.sp,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AuraColors.Accent,
                                selectedLabelColor = Color.White,
                                containerColor = AuraColors.Surface,
                                labelColor = AuraColors.TextSecondary,
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.heightIn(max = 380.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(state.episodes) { index, episode ->
                    FilterChip(
                        selected = index == state.episodeIndex,
                        onClick = { onSelectEpisode(index) },
                        label = {
                            Text(
                                text = episode.name ?: "Tập ${index + 1}",
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
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

@Composable
private fun ErrorOverlay(
    message: String,
    showSwitchServer: Boolean,
    onRetry: () -> Unit,
    onSwitchServer: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .padding(24.dp),
        ) {
            Text(
                text = "Không phát được phim",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = AuraColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = AuraColors.Accent),
                ) {
                    Text("Thử lại", fontSize = 13.sp)
                }
                if (showSwitchServer) {
                    TextButton(onClick = onSwitchServer) {
                        Text("Đổi nguồn phát", color = Color.White, fontSize = 13.sp)
                    }
                }
                TextButton(onClick = onBack) {
                    Text("Quay lại", color = AuraColors.TextSecondary, fontSize = 13.sp)
                }
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
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (hasNext) "Đang phát tập tiếp theo sau ${countdown}s" else "Đã xem hết phim",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasNext) {
                    Button(
                        onClick = onPlayNext,
                        colors = ButtonDefaults.buttonColors(containerColor = AuraColors.Accent),
                    ) {
                        Text("Phát ngay", fontSize = 13.sp)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Huỷ", color = Color.White, fontSize = 13.sp)
                    }
                } else {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = AuraColors.Accent),
                    ) {
                        Text("Xem lại", fontSize = 13.sp)
                    }
                    TextButton(onClick = onBack) {
                        Text("Quay lại", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeHudOverlay(hud: SwipeHud) {
    val icon = when (hud.kind) {
        SwipeKind.Brightness -> Icons.Filled.Brightness6
        SwipeKind.Volume -> Icons.AutoMirrored.Filled.VolumeUp
    }
    val label = when (hud.kind) {
        SwipeKind.Brightness -> "Độ sáng"
        SwipeKind.Volume -> "Âm lượng"
    }
    Box(
        Modifier
            .fillMaxSize()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.width(180.dp)) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { hud.fraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = AuraColors.Accent,
                    trackColor = Color.White.copy(alpha = 0.25f),
                )
            }
            Text(
                text = "${(hud.fraction.coerceIn(0f, 1f) * 100).roundToInt()}%",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private enum class SwipeMode { Pending, Vertical, Other }

private enum class SwipeKind { Brightness, Volume }

private data class SwipeHud(val kind: SwipeKind, val fraction: Float)

/**
 * Độ sáng hiện tại của cửa sổ (0..1). Nếu app chưa ghi đè, đọc mức hệ thống;
 * nếu hệ thống báo không xác định thì trả về 0.5 để lần vuốt đầu không nhảy bất ngờ.
 */
private fun readBrightness(activity: Activity?): Float {
    val window = activity?.window ?: return 0.5f
    val override = window.attributes.screenBrightness
    if (override >= 0f) return override
    val system = runCatching {
        android.provider.Settings.System.getInt(
            activity.contentResolver,
            android.provider.Settings.System.SCREEN_BRIGHTNESS,
        ) / 255f
    }.getOrNull()
    return system?.coerceIn(0.01f, 1f) ?: 0.5f
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
