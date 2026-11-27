package com.auraflix.tv.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auraflix.tv.Graph
import com.auraflix.tv.core.cast.CastServerStatus
import com.auraflix.tv.core.cast.CastServerState
import com.auraflix.tv.ui.components.FilterChip
import com.auraflix.tv.ui.components.TvButton
import com.auraflix.tv.ui.theme.AuraColors
import kotlinx.coroutines.delay

/**
 * Khối "Điều khiển bằng điện thoại" trong Cài đặt của TV.
 *
 * Hiện PIN ghép cặp, địa chỉ IP để nhập tay và số thiết bị đã ghép. Trạng thái đọc
 * trực tiếp từ [com.auraflix.tv.core.cast.CastLinkManager] nên cập nhật khi server
 * vừa bật/tắt hoặc có điện thoại vừa ghép cặp.
 */
@Composable
fun CastSettingsSection(
    modifier: Modifier = Modifier,
) {
    val server by Graph.cast.serverState.collectAsStateWithLifecycle()
    val settings by Graph.cast.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = modifier.widthIn(max = 900.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = when (server.status) {
                CastServerStatus.RUNNING -> "Sẵn sàng — điện thoại cùng Wi-Fi sẽ tìm thấy TV."
                CastServerStatus.STARTING -> "Đang khởi động…"
                CastServerStatus.ERROR -> server.error ?: "Không khởi động được."
                CastServerStatus.STOPPED -> "Đã tắt. Điện thoại sẽ không kết nối được."
            },
            color = if (server.status == CastServerStatus.ERROR) AuraColors.Accent
            else AuraColors.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )

        if (server.running) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                InfoTile(
                    label = "Mã PIN",
                    value = server.pin.chunked(3).joinToString(" "),
                    highlight = true,
                )
                InfoTile(
                    label = "IP của TV",
                    value = server.ip ?: "không có Wi-Fi",
                )
            }

            Text(
                text = if (settings.name.isNotBlank()) "Tên trên điện thoại: ${settings.name}" else "",
                color = AuraColors.TextMuted,
                fontSize = 12.sp,
            )
            if (server.activeControllers > 0) {
                Text(
                    text = "Đang được điều khiển từ điện thoại.",
                    color = AuraColors.AccentSoft,
                    fontSize = 12.sp,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton(
                text = if (settings.enabled) "Bật" else "Tắt",
                onClick = { Graph.cast.setEnabled(!settings.enabled) },
                primary = settings.enabled,
                compact = true,
            )
            if (server.running) {
                TvButton(
                    text = "Đổi PIN",
                    onClick = { Graph.cast.regeneratePin() },
                    compact = true,
                )
                TvButton(
                    text = "Sao chép IP",
                    onClick = { copyToClipboard(context, server.ip) },
                    compact = true,
                )
                TvButton(
                    text = if (server.pairedDeviceCount > 0) {
                        "Ngắt ${server.pairedDeviceCount} thiết bị"
                    } else {
                        "Chưa có thiết bị"
                    },
                    onClick = { Graph.cast.forgetAllDevices() },
                    compact = true,
                )
            }
        }

        if (server.status == CastServerStatus.RUNNING) {
            Text(
                text = "Trên điện thoại: mở AuraFlix → biểu tượng TV → chọn TV này → nhập mã PIN.",
                color = AuraColors.TextMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

/** Toast nhỏ trên TV khi một thiết bị kết nối được (tự ẩn). */
@Composable
fun CastPairToast() {
    val event by Graph.cast.pairEvent.collectAsStateWithLifecycle()
    LaunchedEffect(event?.at) {
        if (event != null) {
            delay(4_000)
            Graph.cast.clearPairEvent()
        }
    }
    val current = event ?: return
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
        Row(
            Modifier
                .padding(24.dp)
                .background(
                    if (current.success) AuraColors.Accent else AuraColors.BackgroundElevated,
                    RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (current.success) {
                    "📱 Đã kết nối: ${current.deviceName}"
                } else {
                    "⚠️ Sai mã PIN từ: ${current.deviceName}"
                },
                color = AuraColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun InfoTile(label: String, value: String, highlight: Boolean = false) {
    Column(
        Modifier
            .background(
                if (highlight) AuraColors.Accent.copy(alpha = 0.18f) else AuraColors.SurfaceVariant,
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(label, color = AuraColors.TextMuted, fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = if (highlight) AuraColors.AccentSoft else AuraColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = if (highlight) 3.sp else 0.sp,
        )
    }
}

private fun copyToClipboard(context: Context, text: String?) {
    if (text.isNullOrBlank()) return
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("AuraFlix TV IP", text))
    }
}
