package com.auraflix.tv.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.auraflix.tv.core.net.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.CacheControl
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0,
    val digest: String? = null,
)

/** Thông tin một bản phát hành mới lấy từ GitHub Releases. */
data class UpdateInfo(
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val sha256: String?,
    val pageUrl: String,
)

sealed interface InstallResult {
    data object Started : InstallResult
    data class NeedPermission(val settingsIntent: Intent) : InstallResult
    data class Failed(val message: String) : InstallResult
}

/**
 * Kiểm tra và cập nhật APK trực tiếp trong ứng dụng (OTA).
 *
 * Nguồn: GitHub Releases của repo phát hành. APK được tải vào cache riêng,
 * đối chiếu SHA-256 do GitHub công bố rồi mở trình cài đặt hệ thống.
 * Bản mới phải được ký cùng khoá với bản đang cài để Android cho phép nâng cấp
 * và giữ nguyên dữ liệu người dùng.
 */
class UpdateManager(
    private val context: Context,
    private val http: HttpClient,
    /** Endpoint GitHub Releases của kênh phát hành (TV và Mobile dùng repo riêng). */
    private val latestReleaseUrl: String = LATEST_RELEASE_URL,
    /** Tiền tố tên tệp APK tải về, giúp phân biệt bản TV và bản Mobile. */
    private val apkNamePrefix: String = "AuraFlixTV",
) {

    /** Trả về bản phát hành mới nhất, hoặc null nếu repo chưa có release. */
    suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(latestReleaseUrl)
            .header("User-Agent", HttpClient.USER_AGENT)
            .header("Accept", "application/vnd.github+json")
            .cacheControl(CacheControl.Builder().noCache().build())
            .build()

        http.okHttp.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val raw = response.body?.string() ?: error("Phản hồi rỗng")
            val release = http.json.decodeFromString(GithubRelease.serializer(), raw)
            val version = release.tagName.removePrefix("v").removePrefix("V").trim()
            if (version.isBlank()) return@withContext null
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: return@withContext null
            UpdateInfo(
                versionName = version,
                notes = ReleaseNotes.clean(release.body),
                apkUrl = apk.downloadUrl,
                sizeBytes = apk.size,
                sha256 = apk.digest?.substringAfter(':', missingDelimiterValue = "")?.takeIf { it.isNotBlank() },
                pageUrl = release.htmlUrl,
            )
        }
    }

    /**
     * Tải APK và báo tiến độ 0..1. Kiểm tra SHA-256 nếu GitHub cung cấp digest.
     */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, UPDATE_DIR).apply { mkdirs() }
            dir.listFiles()?.forEach { if (it.name.endsWith(".apk", true) || it.name.endsWith(".part")) it.delete() }

            val safeVersion = info.versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val target = File(dir, "$apkNamePrefix-$safeVersion.apk")
            val temp = File(dir, target.name + ".part")

            val request = Request.Builder()
                .url(info.apkUrl)
                .header("User-Agent", HttpClient.USER_AGENT)
                .build()

            try {
                http.okHttp.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body ?: error("Phản hồi rỗng")
                    val total = body.contentLength().takeIf { it > 0 } ?: info.sizeBytes
                    val digest = MessageDigest.getInstance("SHA-256")

                    temp.outputStream().use { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var read = 0L
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                digest.update(buffer, 0, n)
                                read += n
                                if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }

                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!info.sha256.isNullOrBlank() && !actual.equals(info.sha256, ignoreCase = true)) {
                        error("Tệp tải về sai mã SHA-256")
                    }
                    if (temp.length() <= 0) error("Tệp tải về rỗng")
                    if (!temp.renameTo(target)) {
                        temp.copyTo(target, overwrite = true)
                        temp.delete()
                    }
                }
                onProgress(1f)
                target
            } catch (t: Throwable) {
                temp.delete()
                throw t
            }
        }

    /** Mở trình cài đặt hệ thống; xin quyền "cài ứng dụng không rõ nguồn gốc" nếu cần. */
    fun install(file: File): InstallResult {
        if (!file.exists() || file.length() <= 0) {
            return InstallResult.Failed("Không tìm thấy tệp cập nhật")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
            return InstallResult.NeedPermission(intent)
        }

        return try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            InstallResult.Started
        } catch (t: Throwable) {
            InstallResult.Failed(t.message ?: "Không mở được trình cài đặt")
        }
    }

    companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/haokiet97/auraflix_tv_release/releases/latest"

        /** Kênh phát hành riêng cho bản Mobile (repo cần được tạo trên GitHub). */
        const val MOBILE_RELEASE_URL =
            "https://api.github.com/repos/haokiet97/auraflix_mobile_release/releases/latest"

        private const val UPDATE_DIR = "updates"
    }
}
