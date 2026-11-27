package com.auraflix.tv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auraflix.tv.BuildConfig
import com.auraflix.tv.Graph
import com.auraflix.tv.core.repo.MovieRepository
import com.auraflix.tv.ui.components.FilterChip
import com.auraflix.tv.ui.components.TvButton
import com.auraflix.tv.ui.factoryOf
import com.auraflix.tv.ui.theme.AuraColors

private val SERVER_OPTIONS = listOf(
    "" to "Tự động",
    "Vietsub" to "Vietsub",
    "Thuyết Minh" to "Thuyết Minh",
    "Lồng Tiếng" to "Lồng Tiếng",
)

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel(
        factory = factoryOf {
            SettingsViewModel(
                userData = Graph.userData,
                repo = Graph.movies,
                updates = Graph.updates,
                currentVersionName = BuildConfig.VERSION_NAME,
                appLabel = "AuraFlix TV",
            )
        },
    )
    val settings by vm.settings.collectAsStateWithLifecycle()
    val update by vm.updateState.collectAsStateWithLifecycle()

    BackHandler { onBack() }

    // Kiểm tra ngầm mỗi lần mở Cài đặt; lỗi mạng không làm phiền người dùng.
    LaunchedEffect(Unit) { vm.checkForUpdates(silent = true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 56.dp, vertical = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvButton(text = "‹", onClick = onBack)
            Spacer(Modifier.width(16.dp))
            Text(
                text = "Cài đặt",
                color = AuraColors.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(30.dp))

        SettingBlock(
            title = "Nguồn phát mặc định",
            description = "Ưu tiên server có tên tương ứng khi mở phim.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SERVER_OPTIONS.forEach { (value, label) ->
                    FilterChip(
                        label = label,
                        selected = (settings.preferredServer) == value,
                        onClick = { vm.setPreferredServer(value) },
                    )
                }
            }
        }

        SettingBlock(
            title = "Xem tiếp",
            description = "Tự động đề xuất xem tiếp từ vị trí đã dừng.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    label = "Bật",
                    selected = settings.resumeEnabled,
                    onClick = { vm.setResumeEnabled(true) },
                )
                FilterChip(
                    label = "Tắt",
                    selected = !settings.resumeEnabled,
                    onClick = { vm.setResumeEnabled(false) },
                )
            }
        }

        SettingBlock(
            title = "Máy chủ dữ liệu phim",
            description = "Chỉ đổi khi ứng dụng không tải được phim.",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MovieRepository.FALLBACK_BASE_URLS.forEach { url ->
                    FilterChip(
                        label = url,
                        selected = settings.baseUrl.trimEnd('/') == url,
                        onClick = { vm.setBaseUrl(url) },
                    )
                }
            }
        }

        SettingBlock(
            title = "Điều khiển bằng điện thoại",
            description = "Chọn phim và điều khiển TV bằng app AuraFlix trên điện thoại cùng Wi-Fi.",
        ) {
            CastSettingsSection()
        }

        SettingBlock(
            title = "Cập nhật ứng dụng",
            description = "Phiên bản hiện tại: v${vm.currentVersion}",
        ) {
            UpdateContent(
                state = update,
                onCheck = { vm.checkForUpdates() },
                onDownload = vm::downloadUpdate,
                onInstall = vm::installUpdate,
            )
        }

        SettingBlock(title = "Dữ liệu", description = null) {
            TvButton(text = "Xoá lịch sử xem", onClick = vm::clearHistory)
        }

        Spacer(Modifier.height(36.dp))

        val server by Graph.cast.serverState.collectAsStateWithLifecycle()
        Column(Modifier.widthIn(max = 900.dp)) {
            Text(
                text = "AuraFlix TV v${BuildConfig.VERSION_NAME}",
                color = AuraColors.TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Ứng dụng chỉ là trình duyệt nội dung, không lưu trữ hay phát tán phim. " +
                    "Toàn bộ nội dung và bản quyền thuộc về các nhà cung cấp gốc.",
                color = AuraColors.TextMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            if (server.running) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Kết nối điện thoại: PIN ${server.pin.chunked(3).joinToString(" ")} · " +
                        (server.ip?.let { "IP $it" } ?: "chưa có Wi-Fi"),
                    color = AuraColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun UpdateContent(
    state: UpdateUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    Column(
        modifier = Modifier.widthIn(max = 900.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.message?.let { message ->
            Text(
                text = message,
                color = if (state.status == UpdateStatus.ERROR) AuraColors.Accent else AuraColors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }

        val info = state.info
        val showRelease = state.status == UpdateStatus.AVAILABLE ||
            state.status == UpdateStatus.DOWNLOADING ||
            state.status == UpdateStatus.READY
        if (info != null && showRelease) {
            val size = if (info.sizeBytes > 0) {
                " · ${"%.2f".format(java.util.Locale.US, info.sizeBytes / 1048576.0)} MB"
            } else {
                ""
            }
            Text(
                text = "Bản mới: v${info.versionName}$size",
                color = AuraColors.AccentSoft,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (info.notes.isNotBlank()) {
                Text(
                    text = info.notes,
                    color = AuraColors.TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    maxLines = 6,
                )
            }
        }

        if (state.status == UpdateStatus.DOWNLOADING) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(0.55f).height(6.dp),
                color = AuraColors.Accent,
                trackColor = AuraColors.SurfaceVariant,
            )
            Text(
                text = "${(state.progress * 100).toInt()}%",
                color = AuraColors.TextMuted,
                fontSize = 12.sp,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton(
                text = if (state.status == UpdateStatus.CHECKING) "Đang kiểm tra…" else "Kiểm tra cập nhật",
                onClick = onCheck,
                compact = true,
            )
            when (state.status) {
                UpdateStatus.AVAILABLE -> TvButton(
                    text = "Cập nhật ngay",
                    onClick = onDownload,
                    primary = true,
                    compact = true,
                )
                UpdateStatus.DOWNLOADING -> TvButton(
                    text = "Đang tải…",
                    onClick = {},
                    compact = true,
                )
                UpdateStatus.READY -> TvButton(
                    text = "Cài đặt ngay",
                    onClick = onInstall,
                    primary = true,
                    compact = true,
                )
                else -> Unit
            }
        }
    }
}

@Composable
private fun SettingBlock(
    title: String,
    description: String?,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 26.dp)) {
        Text(
            text = title,
            color = AuraColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        description?.let {
            Spacer(Modifier.height(4.dp))
            Text(text = it, color = AuraColors.TextMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}
