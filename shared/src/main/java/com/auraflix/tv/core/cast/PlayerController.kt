package com.auraflix.tv.core.cast

import kotlinx.coroutines.flow.StateFlow

/**
 * Giao diện điều khiển trình phát trên TV, dùng bởi [CastLinkManager].
 *
 * Được cài đặt bởi màn hình Player của TV: state lấy từ ExoPlayer thật,
 * còn các lệnh (phát/tạm dừng/tua/chuyển tập) được đưa vào một hàng đợi
 * mà PlayerScreen xử lý để luôn chạy trên main thread.
 */
interface PlayerController {
    val state: StateFlow<CastPlaybackState>

    /** Nhận lệnh từ điện thoại. */
    fun enqueue(command: CastCommand)
}
