@file:OptIn(ExperimentalLayoutApi::class)

package com.auraflix.mobile.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auraflix.mobile.BuildConfig
import com.auraflix.mobile.MobileGraph
import com.auraflix.mobile.ui.theme.AuraColors
import com.auraflix.tv.core.cast.RemoteStatus
import com.auraflix.tv.core.repo.MovieRepository
import com.auraflix.tv.ui.factoryOf
import com.auraflix.tv.ui.settings.SettingsViewModel
import com.auraflix.tv.ui.settings.UpdateStatus
import com.auraflix.tv.ui.settings.UpdateUiState

private val SERVER_OPTIONS = listOf(
    "" to "Tự động",
    "Vietsub" to "Vietsub",
    "Thuyết Minh" to "Thuyết Minh",
    "Lồng Tiếng" to "Lồng Tiếng",
)

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onOpenTv: () -> Unit = {},
    onConnectTv: () -> Unit = {},
) {
    val vm: SettingsViewModel = viewModel(
        factory = factoryOf {
            SettingsViewModel(
                userData = MobileGraph.userData,
                repo = MobileGraph.movies,
                updates = MobileGraph.updates,
                currentVersionName = BuildConfig.VERSION_NAME,
                appLabel = "AuraFlix",
            )
        },
    )
    val settings by vm.settings.collectAsStateWithLifecycle()
    val update by vm.updateState.collectAsStateWithLifecycle()
    val remoteState by MobileGraph.tvRemote.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.checkForUpdates(silent = true) }

    Column(
        modifier
            .fillMaxSize()
            .background(AuraColors.Background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = "Cài đặt",
            color = AuraColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 18.dp),
        )

        SettingBlock(
            title = "Điều khiển TV",
            description = "Chọn phim trên điện thoại và phát trên TV cùng mạng Wi-Fi.",
        ) {
            TvRemoteSetting(
                tvName = remoteState.tv?.name,
                online = remoteState.status == RemoteStatus.ONLINE,
                onOpenTv = onOpenTv,
                onConnectTv = onConnectTv,
                onForget = { MobileGraph.tvRemote.disconnect() },
            )
        }

        SettingBlock(
            title = "Nguồn phát mặc định",
            description = "Ưu tiên server có tên tương ứng khi mở phim.",
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SERVER_OPTIONS.forEach { (value, label) ->
                    FilterChip(
                        selected = settings.preferredServer == value,
                        onClick = { vm.setPreferredServer(value) },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = settingsChipColors(settings.preferredServer == value),
                    )
                }
            }
        }

        SettingBlock(
            title = "Xem tiếp",
            description = "Tự động đề xuất xem tiếp từ vị trí đã dừng.",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (settings.resumeEnabled) "Đang bật" else "Đang tắt",
                    color = AuraColors.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = settings.resumeEnabled,
                    onCheckedChange = { vm.setResumeEnabled(it) },
                )
            }
        }

        SettingBlock(
            title = "Máy chủ dữ liệu phim",
            description = "Chỉ đổi khi ứng dụng không tải được phim.",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MovieRepository.FALLBACK_BASE_URLS.forEach { url ->
                    val selected = settings.baseUrl.trimEnd('/') == url
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.setBaseUrl(url) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { vm.setBaseUrl(url) },
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = url,
                            color = if (selected) AuraColors.TextPrimary else AuraColors.TextSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        SettingBlock(
            title = "Cập nhật ứng dụng",
            description = "Phiên bản hiện tại: v${vm.currentVersion}",
        ) {
            UpdateSection(
                state = update,
                onCheck = { vm.checkForUpdates() },
                onDownload = vm::downloadUpdate,
                onInstall = vm::installUpdate,
            )
        }

        SettingBlock(title = "Dữ liệu", description = null) {
            OutlinedButton(onClick = vm::clearHistory) {
                Text("Xoá lịch sử xem", fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = "AuraFlix v${BuildConfig.VERSION_NAME}",
            color = AuraColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Ứng dụng chỉ là trình duyệt nội dung, không lưu trữ hay phát tán phim. " +
                "Toàn bộ nội dung và bản quyền thuộc về các nhà cung cấp gốc.",
            color = AuraColors.TextMuted,
            fontSize = 11.sp,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun settingsChipColors(selected: Boolean) = FilterChipDefaults.filterChipColors(
    selectedContainerColor = AuraColors.Accent,
    selectedLabelColor = Color.White,
    containerColor = AuraColors.Surface,
    labelColor = AuraColors.TextSecondary,
)

@Composable
private fun UpdateSection(
    state: UpdateUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        state.message?.let { message ->
            Text(
                text = message,
                color = if (state.status == UpdateStatus.ERROR) AuraColors.Accent else AuraColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }

        val info = state.info
        val showRelease = state.status == UpdateStatus.AVAILABLE ||
            state.status == UpdateStatus.DOWNLOADING ||
            state.status == UpdateStatus.READY
        if (info != null && showRelease) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(AuraColors.Surface, RoundedCornerShape(10.dp))
                    .padding(12.dp),
            ) {
                val size = if (info.sizeBytes > 0) {
                    " · ${"%.2f".format(java.util.Locale.US, info.sizeBytes / 1048576.0)} MB"
                } else {
                    ""
                }
                Text(
                    text = "Bản mới: v${info.versionName}$size",
                    color = AuraColors.AccentSoft,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = info.notes,
                        color = AuraColors.TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        maxLines = 6,
                    )
                }
            }
        }

        if (state.status == UpdateStatus.DOWNLOADING) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = AuraColors.Accent,
                trackColor = AuraColors.SurfaceVariant,
            )
            Text(
                text = "${(state.progress * 100).toInt()}%",
                color = AuraColors.TextMuted,
                fontSize = 11.sp,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onCheck,
                enabled = state.status != UpdateStatus.CHECKING &&
                    state.status != UpdateStatus.DOWNLOADING,
            ) {
                Text(
                    text = if (state.status == UpdateStatus.CHECKING) "Đang kiểm tra…" else "Kiểm tra cập nhật",
                    fontSize = 12.sp,
                )
            }
            when (state.status) {
                UpdateStatus.AVAILABLE -> Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(containerColor = AuraColors.Accent),
                ) {
                    Text("Cập nhật ngay", fontSize = 12.sp)
                }

                UpdateStatus.READY -> Button(
                    onClick = onInstall,
                    colors = ButtonDefaults.buttonColors(containerColor = AuraColors.Accent),
                ) {
                    Text("Cài đặt ngay", fontSize = 12.sp)
                }

                else -> Unit
            }
        }
    }
}

@Composable
private fun TvRemoteSetting(
    tvName: String?,
    online: Boolean,
    onOpenTv: () -> Unit,
    onConnectTv: () -> Unit,
    onForget: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Tv,
                contentDescription = null,
                tint = if (tvName != null) AuraColors.Accent else AuraColors.TextMuted,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = tvName ?: "Chưa kết nối TV",
                color = AuraColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (tvName != null) {
                Text(
                    text = if (online) "Đang kết nối" else "Chưa thấy TV",
                    color = if (online) AuraColors.AccentSoft else AuraColors.TextMuted,
                    fontSize = 11.sp,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tvName != null) {
                Button(
                    onClick = onOpenTv,
                    colors = ButtonDefaults.buttonColors(containerColor = AuraColors.Accent),
                ) {
                    Text("Mở điều khiển", fontSize = 12.sp)
                }
                OutlinedButton(onClick = onConnectTv) {
                    Text("Đổi TV", fontSize = 12.sp)
                }
                TextButton(onClick = onForget) {
                    Text("Ngắt kết nối", color = AuraColors.TextMuted, fontSize = 12.sp)
                }
            } else {
                Button(
                    onClick = onConnectTv,
                    colors = ButtonDefaults.buttonColors(containerColor = AuraColors.Accent),
                ) {
                    Text("Kết nối TV", fontSize = 12.sp)
                }
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
    Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        Text(
            text = title,
            color = AuraColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        description?.let {
            Spacer(Modifier.height(3.dp))
            Text(text = it, color = AuraColors.TextMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}
