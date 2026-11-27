package com.auraflix.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auraflix.tv.core.repo.AppSettings
import com.auraflix.tv.core.repo.MovieRepository
import com.auraflix.tv.core.repo.UserDataRepository
import com.auraflix.tv.core.update.AppVersion
import com.auraflix.tv.core.update.InstallResult
import com.auraflix.tv.core.update.UpdateInfo
import com.auraflix.tv.core.update.UpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class UpdateStatus { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, ERROR }

data class UpdateUiState(
    val status: UpdateStatus = UpdateStatus.IDLE,
    val info: UpdateInfo? = null,
    val progress: Float = 0f,
    val message: String? = null,
)

class SettingsViewModel(
    private val userData: UserDataRepository,
    private val repo: MovieRepository,
    private val updates: UpdateManager,
    /** Tên phiên bản của app đang chạy (TV hoặc Mobile). */
    currentVersionName: String,
    /** Tên hiển thị của app, dùng trong thông báo cấp quyền cài đặt. */
    private val appLabel: String = "AuraFlix",
) : ViewModel() {

    val settings: StateFlow<AppSettings> = userData.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _updateState = MutableStateFlow(UpdateUiState())
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    private var pendingApk: File? = null

    val currentVersion: String = currentVersionName

    fun checkForUpdates(silent: Boolean = false) {
        val status = _updateState.value.status
        if (status == UpdateStatus.CHECKING || status == UpdateStatus.DOWNLOADING) return

        viewModelScope.launch {
            _updateState.update { it.copy(status = UpdateStatus.CHECKING, message = null, progress = 0f) }
            runCatching { updates.fetchLatest() }
                .onSuccess { latest ->
                    if (latest == null) {
                        _updateState.value = UpdateUiState(
                            status = if (silent) UpdateStatus.IDLE else UpdateStatus.ERROR,
                            message = if (silent) null else "Chưa có bản phát hành nào trên máy chủ.",
                        )
                        return@onSuccess
                    }

                    pendingApk = null
                    if (!AppVersion.isNewer(latest.versionName, currentVersion)) {
                        _updateState.value = UpdateUiState(
                            status = UpdateStatus.UP_TO_DATE,
                            info = latest,
                            message = "Bạn đang dùng bản mới nhất (v$currentVersion).",
                        )
                    } else {
                        _updateState.value = UpdateUiState(
                            status = UpdateStatus.AVAILABLE,
                            info = latest,
                            message = "Đã có bản cập nhật v${latest.versionName}.",
                        )
                    }
                }
                .onFailure { error ->
                    _updateState.value = if (silent) {
                        UpdateUiState()
                    } else {
                        UpdateUiState(
                            status = UpdateStatus.ERROR,
                            message = "Không kiểm tra được cập nhật: ${error.friendlyMessage()}",
                        )
                    }
                }
        }
    }

    fun downloadUpdate() {
        val info = _updateState.value.info ?: return
        if (_updateState.value.status == UpdateStatus.DOWNLOADING) return

        viewModelScope.launch {
            _updateState.update {
                it.copy(
                    status = UpdateStatus.DOWNLOADING,
                    progress = 0f,
                    message = "Đang tải bản cập nhật v${info.versionName}…",
                )
            }
            runCatching {
                updates.download(info) { progress ->
                    _updateState.update { it.copy(progress = progress) }
                }
            }.onSuccess { file ->
                pendingApk = file
                _updateState.update {
                    it.copy(
                        status = UpdateStatus.READY,
                        progress = 1f,
                        message = "Đã tải xong. Bấm “Cài đặt ngay” để hoàn tất cập nhật.",
                    )
                }
            }.onFailure { error ->
                _updateState.update {
                    it.copy(
                        status = UpdateStatus.ERROR,
                        progress = 0f,
                        message = "Tải bản cập nhật thất bại: ${error.friendlyMessage()}",
                    )
                }
            }
        }
    }

    fun installUpdate() {
        val file = pendingApk
        if (file == null) {
            _updateState.update {
                it.copy(status = UpdateStatus.ERROR, message = "Chưa có tệp cập nhật. Hãy tải lại.")
            }
            return
        }

        when (val result = updates.install(file)) {
            InstallResult.Started -> _updateState.update {
                it.copy(message = "Đang mở trình cài đặt…")
            }
            is InstallResult.NeedPermission -> _updateState.update {
                it.copy(
                    message = "Hãy bật “Cài đặt ứng dụng không rõ nguồn gốc” cho $appLabel, " +
                        "sau đó quay lại và bấm “Cài đặt ngay”.",
                )
            }
            is InstallResult.Failed -> _updateState.update {
                it.copy(status = UpdateStatus.ERROR, message = "Không cài được: ${result.message}")
            }
        }
    }

    fun setBaseUrl(url: String) {
        viewModelScope.launch {
            userData.setBaseUrl(url)
            repo.baseUrl = url
            repo.clearMemoryCache()
        }
    }

    fun setPreferredServer(value: String) {
        viewModelScope.launch { userData.setPreferredServer(value) }
    }

    fun setResumeEnabled(value: Boolean) {
        viewModelScope.launch { userData.setResumeEnabled(value) }
    }

    fun clearHistory() {
        viewModelScope.launch { userData.clearHistory() }
    }

    private fun Throwable.friendlyMessage(): String = when (this) {
        is java.net.UnknownHostException -> "không có kết nối mạng"
        is java.net.SocketTimeoutException -> "máy chủ phản hồi chậm"
        else -> message ?: "lỗi không xác định"
    }
}
