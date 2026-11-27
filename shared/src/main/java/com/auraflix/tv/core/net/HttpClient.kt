package com.auraflix.tv.core.net

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Client HTTP dùng chung cho API phim và cho Coil/ExoPlayer.
 */
class HttpClient(context: Context) {

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http"), 128L * 1024 * 1024))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    /**
     * GET + parse JSON. Tự retry 1 lần khi lỗi mạng/parse.
     * Trả về null thay vì throw để UI hiển thị trạng thái lỗi.
     */
    suspend fun <T> getJson(
        url: String,
        serializer: KSerializer<T>,
        attempts: Int = 2,
    ): T? = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        repeat(attempts) { index ->
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()
                okHttp.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body?.string() ?: error("Empty body")
                    return@withContext json.decodeFromString(serializer, body)
                }
            } catch (t: Throwable) {
                lastError = t
                if (index < attempts - 1) delay(400)
            }
        }
        lastError?.printStackTrace()
        null
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 9; AuraFlixTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
    }
}
