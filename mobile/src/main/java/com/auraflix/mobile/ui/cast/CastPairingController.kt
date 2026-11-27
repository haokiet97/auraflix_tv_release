package com.auraflix.mobile.ui.cast

import com.auraflix.tv.core.cast.CastClient
import com.auraflix.tv.core.cast.DiscoveredTv
import com.auraflix.tv.core.cast.PairedTv
import com.auraflix.tv.core.cast.TvRemote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PairingUiState(
    val scanning: Boolean = false,
    val found: List<DiscoveredTv> = emptyList(),
    val manualMode: Boolean = false,
    val manualIp: String = "",
    /** Đang ở bước nhập PIN cho TV này. */
    val pairing: Boolean = false,
    val target: DiscoveredTv? = null,
    val pin: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val tvFound: Boolean = false,
    val paired: PairedTv? = null,
    /** Đang có TV cần hiển thị banner mời kết nối lại (trạng thái offline). */
    val disconnected: Boolean = false,
)

/**
 * Logic cho màn hình kết nối TV. Tách khỏi Compose để dễ test và để vòng đời
 * coroutine tự huỷ khi rời màn hình.
 */
class CastPairingController(
    private val client: CastClient,
    private val remote: TvRemote,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PairingUiState(paired = remote.state.value.tv))
    val state: StateFlow<PairingUiState> = _state.asStateFlow()

    fun dispose() {
        scope.cancel()
    }

    fun scan() {
        if (_state.value.scanning) return
        _state.update { it.copy(scanning = true, error = null) }
        scope.launch {
            val found = runCatching { client.discover() }.getOrDefault(emptyList())
                .sortedBy { it.info.name }
            _state.update {
                it.copy(
                    scanning = false,
                    found = found,
                    tvFound = found.isNotEmpty(),
                    error = if (found.isEmpty()) it.error else null,
                )
            }
        }
    }

    fun openManual() = _state.update { it.copy(manualMode = true, error = null) }

    fun exitManual() = _state.update { it.copy(manualMode = false, error = null) }

    fun setManualIp(value: String) = _state.update { it.copy(manualIp = value) }

    /** Xác nhận IP thủ công: gọi /info để chắc chắn đó là TV AuraFlix. */
    fun resolveManual() {
        val baseUrl = client.baseUrlOf(_state.value.manualIp) ?: return
        val host = _state.value.manualIp.trim()
            .removePrefix("http://").removePrefix("https://")
            .substringBefore(':')
        _state.update { it.copy(busy = true, error = null) }
        scope.launch {
            val info = client.info(baseUrl)
            if (info == null) {
                _state.update {
                    it.copy(
                        busy = false,
                        error = "Không thấy AuraFlix TV ở $host. Kiểm tra IP và việc app đang mở trên TV.",
                    )
                }
                return@launch
            }
            _state.update { it.copy(busy = false) }
            choose(DiscoveredTv(info, host))
        }
    }

    /** Người dùng chọn một TV: chuyển sang bước nhập PIN. */
    fun choose(tv: DiscoveredTv) {
        if (remote.state.value.tv?.id == tv.info.id && tv.info.paired) {
            // Đã ghép cặp trước đó: thử dùng token cũ luôn.
            _state.update { it.copy(target = tv, pairing = true, error = null) }
            return
        }
        _state.update {
            it.copy(
                target = tv,
                pairing = true,
                pin = "",
                error = null,
                manualMode = false,
                busy = false,
            )
        }
    }

    fun setPin(value: String) = _state.update { it.copy(pin = value) }

    fun reset() = _state.update {
        it.copy(pairing = false, target = null, pin = "", error = null, busy = false)
    }

    /**
     * Ghép cặp với [target].
     *
     * - Nếu điện thoại đã từng ghép đúng TV này: thử token cũ trước (ping) —
     *   người dùng không phải nhập PIN khi TV đổi IP.
     * - Nếu chưa hoặc token đã bị TV thu hồi: gửi PIN hiển thị trên TV.
     */
    fun pair(onSuccess: () -> Unit) {
        if (_state.value.busy) return
        val target = _state.value.target ?: return
        val pin = _state.value.pin
        _state.update { it.copy(busy = true, error = null) }
        scope.launch {
            val existing = remote.state.value.tv
            if (existing != null && existing.id == target.info.id &&
                client.ping(target.baseUrl, existing.token)
            ) {
                remote.savePaired(existing.copy(host = target.host, port = target.info.port))
                _state.update { it.copy(busy = false, pairing = false) }
                onSuccess()
                return@launch
            }

            val response = client.pair(target.baseUrl, pin, phoneName()).getOrNull()
            val token = response?.token
            if (response?.ok == true && token != null) {
                remote.savePaired(
                    PairedTv(
                        id = target.info.id,
                        name = target.info.name,
                        host = target.host,
                        port = target.info.port,
                        token = token,
                        tag = target.info.tag,
                        lastSeenAt = System.currentTimeMillis(),
                    ),
                )
                _state.update { it.copy(busy = false, pairing = false, error = null) }
                onSuccess()
            } else {
                _state.update {
                    it.copy(
                        busy = false,
                        error = response?.message
                            ?: "Không kết nối được tới TV. Kiểm tra lại mạng.",
                    )
                }
            }
        }
    }
}
