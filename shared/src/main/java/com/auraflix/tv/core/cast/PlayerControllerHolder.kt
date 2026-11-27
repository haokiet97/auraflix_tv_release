package com.auraflix.tv.core.cast

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Nơi đăng ký [PlayerController] đang hoạt động trên TV.
 *
 * Server HTTP chạy trong service nền, còn trình phát chỉ tồn tại khi màn hình
 * player mở — holder này là điểm nối giữa hai bên. Khi không có phim đang chiếu,
 * state là mặc định ([CastPlaybackState.active] = false).
 */
object PlayerControllerHolder {
    private val _controller = MutableStateFlow<PlayerController?>(null)
    val controller: StateFlow<PlayerController?> = _controller.asStateFlow()

    val current: PlayerController? get() = _controller.value

    val state: StateFlow<CastPlaybackState>
        get() = current?.state ?: IDLE

    /** PlayerScreen gọi khi mở; chỉ ghi đè nếu vẫn là chính nó (tránh đè của màn hình mới). */
    fun attach(controller: PlayerController) {
        _controller.value = controller
    }

    fun detach(controller: PlayerController) {
        if (_controller.value === controller) _controller.value = null
    }

    private val IDLE = MutableStateFlow(CastPlaybackState())
}
