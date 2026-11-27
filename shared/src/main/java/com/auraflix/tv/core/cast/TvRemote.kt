package com.auraflix.tv.core.cast

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Một TV đã ghép cặp và đang được nhớ trên điện thoại. */
data class PairedTv(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val token: String,
    val tag: String = "",
    val lastSeenAt: Long = 0,
) {
    val baseUrl: String get() = "http://$host:$port"
}

/**
 * Bộ nhớ ghép cặp phía điện thoại (SharedPreferences rất nhỏ, không cần DataStore).
 */
class CastStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("auraflix_cast", Context.MODE_PRIVATE)

    fun paired(): PairedTv? {
        val id = prefs.getString(KEY_ID, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val host = prefs.getString(KEY_HOST, null) ?: return null
        return PairedTv(
            id = id,
            name = prefs.getString(KEY_NAME, null) ?: "AuraFlix TV",
            host = host,
            port = prefs.getInt(KEY_PORT, CastProtocol.PORT),
            token = token,
            tag = prefs.getString(KEY_TAG, null).orEmpty(),
            lastSeenAt = prefs.getLong(KEY_SEEN, 0),
        )
    }

    fun save(tv: PairedTv) {
        prefs.edit()
            .putString(KEY_ID, tv.id)
            .putString(KEY_NAME, tv.name)
            .putString(KEY_HOST, tv.host)
            .putInt(KEY_PORT, tv.port)
            .putString(KEY_TOKEN, tv.token)
            .putString(KEY_TAG, tv.tag)
            .putLong(KEY_SEEN, tv.lastSeenAt)
            .apply()
    }

    fun touch(host: String, name: String) {
        prefs.edit()
            .putString(KEY_HOST, host)
            .putString(KEY_NAME, name)
            .putLong(KEY_SEEN, System.currentTimeMillis())
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_ID = "tv_id"
        const val KEY_NAME = "tv_name"
        const val KEY_HOST = "tv_host"
        const val KEY_PORT = "tv_port"
        const val KEY_TOKEN = "tv_token"
        const val KEY_TAG = "tv_tag"
        const val KEY_SEEN = "tv_seen"
    }
}

/** Trạng thái kết nối phía điện thoại. */
enum class RemoteStatus { NONE, CONNECTING, ONLINE, OFFLINE }

data class RemoteUiState(
    val tv: PairedTv? = null,
    val status: RemoteStatus = RemoteStatus.NONE,
    val playback: CastPlaybackState? = null,
    val message: String? = null,
)

/**
 * Điều khiển TV từ điện thoại sau khi đã ghép cặp.
 *
 * - [startPolling] được gọi khi người dùng mở màn hình điều khiển: mỗi
 *   [POLL_INTERVAL_MS] đọc trạng thái phát của TV. Thất bại 2 lần liên tiếp thì chuyển
 *   sang OFFLINE (vẫn tiếp tục thử lại chậm) để người dùng biết TV đã tắt/đổi mạng.
 * - Các hàm điều khiển cập nhật lạc quan (optimistic) trạng thái để nút bấm phản hồi ngay,
 *   lần poll kế tiếp sẽ chỉnh lại theo sự thật từ TV.
 */
class TvRemote(
    private val client: CastClient,
    private val store: CastStore,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(RemoteUiState(tv = store.paired()))
    val state: StateFlow<RemoteUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    val hasTv: Boolean get() = _state.value.tv != null

    fun refresh() {
        _state.update { it.copy(tv = store.paired()) }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    /** Lưu kết quả ghép cặp thành công. */
    fun savePaired(tv: PairedTv) {
        store.save(tv)
        _state.value = RemoteUiState(tv = tv, status = RemoteStatus.CONNECTING)
    }

    fun disconnect() {
        stopPolling()
        store.clear()
        _state.value = RemoteUiState()
    }

    fun startPolling() {
        if (_state.value.tv == null) return
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            var failures = 0
            while (isActive) {
                val tv = _state.value.tv ?: break
                val playback = client.state(tv.baseUrl, tv.token)
                if (playback == null) {
                    failures++
                    if (failures >= 2) {
                        _state.update { it.copy(status = RemoteStatus.OFFLINE) }
                        delay(OFFLINE_RETRY_MS)
                    } else {
                        delay(POLL_INTERVAL_MS)
                    }
                } else {
                    failures = 0
                    store.touch(tv.host, tv.name)
                    _state.update { it.copy(status = RemoteStatus.ONLINE, playback = playback) }
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    /** Thử kết nối lại ngay (nút "Kết nối lại" khi TV đang offline). */
    fun retryConnection() {
        stopPolling()
        _state.update { it.copy(status = RemoteStatus.CONNECTING) }
        startPolling()
    }

    // -------------------------------------------------------------- Lệnh

    fun togglePlay() {
        val playing = _state.value.playback?.isPlaying ?: false
        optimistic { it.copy(isPlaying = !playing) }
        send(CastCommand(type = CastProtocol.Command.TOGGLE))
    }

    fun play() {
        optimistic { it.copy(isPlaying = true) }
        send(CastCommand(type = CastProtocol.Command.PLAY))
    }

    fun pause() {
        optimistic { it.copy(isPlaying = false) }
        send(CastCommand(type = CastProtocol.Command.PAUSE))
    }

    fun seekBy(deltaMs: Long) {
        if (_state.value.playback == null) return
        optimistic { it.copy(positionMs = (it.positionMs + deltaMs).coerceIn(0L, it.durationMs)) }
        send(CastCommand(type = CastProtocol.Command.SEEK_BY, value = deltaMs))
    }

    /** Tua tới vị trí tuyệt đối; nếu vượt quá cuối phim thì TV tự chuyển tập. */
    fun seekTo(positionMs: Long) {
        optimistic { it.copy(positionMs = positionMs.coerceAtLeast(0)) }
        send(CastCommand(type = CastProtocol.Command.SEEK_ABS, value = positionMs))
    }

    fun nextEpisode() = send(CastCommand(type = CastProtocol.Command.NEXT_EPISODE))

    fun previousEpisode() = send(CastCommand(type = CastProtocol.Command.PREV_EPISODE))

    fun selectEpisode(index: Int) =
        send(CastCommand(type = CastProtocol.Command.SELECT_EPISODE, value = index.toLong()))

    fun stop() = send(CastCommand(type = CastProtocol.Command.STOP))

    /** Mở phim trên TV. Trả về false nếu chưa ghép cặp hoặc không gửi được. */
    suspend fun openOnTv(slug: String, server: Int, episode: Int, name: String): Boolean {
        val tv = _state.value.tv ?: return false
        val ok = client.open(tv.baseUrl, tv.token, slug, server, episode, name)
        _state.update {
            it.copy(
                message = if (ok) "Đang mở “$name” trên TV…" else MESSAGE_OFFLINE,
            )
        }
        return ok
    }

    private fun optimistic(update: (CastPlaybackState) -> CastPlaybackState) {
        val playback = _state.value.playback ?: return
        _state.update { it.copy(playback = update(playback)) }
    }

    private fun send(command: CastCommand) {
        val tv = _state.value.tv ?: return
        scope.launch {
            val ok = client.send(tv.baseUrl, tv.token, command)
            if (!ok) {
                _state.update { it.copy(status = RemoteStatus.OFFLINE, message = MESSAGE_OFFLINE) }
            } else if (_state.value.status != RemoteStatus.ONLINE) {
                _state.update { it.copy(status = RemoteStatus.ONLINE) }
                startPolling()
            }
        }
    }

    companion object {
        const val MESSAGE_OFFLINE = "Không kết nối được tới TV. Kiểm tra hai thiết bị cùng mạng Wi-Fi."
        private const val POLL_INTERVAL_MS = 1_000L
        private const val OFFLINE_RETRY_MS = 3_000L
    }
}
