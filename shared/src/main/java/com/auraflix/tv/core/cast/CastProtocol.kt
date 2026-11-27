package com.auraflix.tv.core.cast

import kotlinx.serialization.Serializable

/**
 * Giao thức điều khiển TV ↔ Mobile trong cùng mạng LAN.
 *
 * TV mở một HTTP server nhỏ (mặc định cổng 8765) và quảng bá qua UDP broadcast
 * (cổng 8766) để điện thoại tự tìm thấy. Sau khi nhập PIN hiển thị trên TV,
 * điện thoại nhận token và gửi kèm ở header `X-Aura-Token` cho mọi lệnh.
 *
 * Toàn bộ payload là JSON (kotlinx.serialization) để cả hai app dùng chung.
 */
object CastProtocol {
    const val PORT = 8765

    /** Cổng UDP nhận gói tin quảng bá tìm thiết bị. */
    const val DISCOVERY_PORT = 8766

    /** Chuỗi nhận diện gói tin trong mạng — tránh nhặt nhầm gói của app khác. */
    const val DISCOVERY_QUERY = "AURAFLIX_DISCOVER_V1"
    const val DISCOVERY_REPLY = "AURAFLIX_TV_V1"

    const val HEADER_TOKEN = "X-Aura-Token"
    const val HEADER_DEVICE = "X-Aura-Device"
    const val HEADER_VERSION = "X-Aura-Version"

    /** Phiên bản giao thức; client chỉ chấp nhận server cùng phiên bản lớn. */
    const val VERSION = 1

    const val PATH_INFO = "/info"
    const val PATH_PAIR = "/pair"
    const val PATH_PING = "/ping"
    const val PATH_STATE = "/state"
    const val PATH_COMMAND = "/command"

    /** Lệnh điều khiển gửi từ điện thoại tới TV. */
    object Command {
        const val PLAY = "play"
        const val PAUSE = "pause"
        const val TOGGLE = "toggle"

        /** [CastCommand.value] = vị trí đích (ms). */
        const val SEEK = "seek"

        /** [CastCommand.value] = vị trí đích (ms), có thể vượt duration để chuyển tập. */
        const val SEEK_ABS = "seek_abs"

        /** [CastCommand.value] = delta (ms) so với vị trí hiện tại. */
        const val SEEK_BY = "seek_by"

        const val NEXT_EPISODE = "next_episode"
        const val PREV_EPISODE = "prev_episode"

        /** [CastCommand.value] = chỉ số tập. */
        const val SELECT_EPISODE = "select_episode"

        /** Mở một phim/tập mới trên TV. */
        const val OPEN = "open"

        const val STOP = "stop"

        /** Bật/tắt thông tin "đang phát" ở thanh điều hướng TV. */
        const val SET_CONTROLLER = "set_controller"
    }
}

/** Thông tin công khai của TV — trả về trước khi ghép cặp. */
@Serializable
data class CastDeviceInfo(
    val id: String,
    val name: String,
    val version: Int = CastProtocol.VERSION,
    val paired: Boolean = false,
    val port: Int = CastProtocol.PORT,
    val ip: String? = null,
    /** Chữ ký 4 ký tự ngẫu nhiên để phân biệt các TV cùng model trên cùng mạng. */
    val tag: String = "",
)

@Serializable
data class CastPairRequest(
    val pin: String,
    val deviceName: String,
)

@Serializable
data class CastPairResponse(
    val ok: Boolean,
    val token: String? = null,
    val message: String? = null,
)

/** Một lệnh điều khiển. [value] dùng chung cho các lệnh số. */
@Serializable
data class CastCommand(
    val type: String,
    val value: Long? = null,
    val slug: String? = null,
    val server: Int? = null,
    val episode: Int? = null,
    val name: String? = null,
)

/** Snapshot trạng thái phát của TV, điện thoại poll ~1s/lần khi đang điều khiển. */
@Serializable
data class CastPlaybackState(
    /** Có màn hình player đang mở trên TV hay không. */
    val active: Boolean = false,
    val slug: String? = null,
    val movieName: String = "",
    val posterUrl: String? = null,
    val episodeLabel: String = "",
    val episodeIndex: Int = 0,
    val episodeCount: Int = 0,
    val serverIndex: Int = 0,
    val serverName: String = "",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val hasNext: Boolean = false,
    /** true khi vừa phát xong tập và đang ở màn hình "tập tiếp theo". */
    val ended: Boolean = false,
    val error: String? = null,
)

@Serializable
data class CastPingResponse(
    val ok: Boolean = true,
    val version: Int = CastProtocol.VERSION,
)

@Serializable
data class CastOpenRequest(
    val slug: String,
    val server: Int = 0,
    val episode: Int = 0,
    val name: String = "",
)
