package com.auraflix.tv.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.auraflix.tv.core.cast.CastCommand
import com.auraflix.tv.core.cast.CastPlaybackState
import com.auraflix.tv.core.cast.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Điều khiển trình phát TV từ điện thoại.
 *
 * - [enqueue] được gọi từ thread của HTTP server: lệnh được thêm vào danh sách và
 *   tăng [version] để Compose thức dậy.
 * - PlayerScreen đọc hàng đợi trong LaunchedEffect([version]) — luôn trên main thread —
 *   rồi thực thi lệnh lên ExoPlayer/ViewModel.
 * - [publish] được PlayerScreen gọi mỗi khi trạng thái thay đổi; chỉ ghi khi dữ liệu
 *   khác lần trước.
 */
class RemotePlayerController : PlayerController {

    private val _state = MutableStateFlow(CastPlaybackState())
    override val state: StateFlow<CastPlaybackState> = _state.asStateFlow()

    private val queue = ArrayDeque<CastCommand>()

    /** Tăng mỗi khi có lệnh mới; dùng làm key cho LaunchedEffect. */
    var version by mutableIntStateOf(0)
        private set

    override fun enqueue(command: CastCommand) {
        synchronized(queue) {
            if (queue.size >= MAX_QUEUE) queue.removeFirst()
            queue.addLast(command)
        }
        version++
    }

    /** Lấy toàn bộ lệnh đang chờ (chỉ gọi từ main thread). */
    fun drain(): List<CastCommand> = synchronized(queue) {
        val items = queue.toList()
        queue.clear()
        items
    }

    fun publish(next: CastPlaybackState) {
        if (next != _state.value) _state.value = next
    }

    private companion object {
        const val MAX_QUEUE = 16
    }
}

/** Có phải lệnh phát/tạm dừng/tua không (không đổi tập). */
val CastCommand.isTransport: Boolean
    get() = type == "play" || type == "pause" || type == "toggle" ||
        type == "seek" || type == "seek_abs" || type == "seek_by"
