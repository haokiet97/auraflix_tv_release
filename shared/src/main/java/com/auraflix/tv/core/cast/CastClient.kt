package com.auraflix.tv.core.cast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

/** TV tìm thấy trên mạng, đã biết địa chỉ HTTP để gọi. */
data class DiscoveredTv(
    val info: CastDeviceInfo,
    val host: String,
) {
    val baseUrl: String get() = "http://$host:${info.port}"

    /** Khoá nhận diện ổn định giữa các lần quét. */
    val key: String get() = info.id
}

/**
 * Client dùng trên điện thoại để tìm TV trong LAN và gửi lệnh.
 *
 * - Discovery: gửi UDP broadcast [CastProtocol.DISCOVERY_QUERY] tới cổng 8766,
 *   chờ trong [timeoutMs] và gom các TV trả lời. Một số router chặn broadcast;
 *   khi đó người dùng nhập IP thủ công (xem [baseUrlOf]).
 * - HTTP: OkHttp với timeout ngắn vì yêu cầu đi trong LAN.
 */
class CastClient(mobileHttp: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val http: OkHttpClient = mobileHttp.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** Quét và trả về danh sách TV (đã ghép cặp hoặc chưa) trong [timeoutMs]. */
    suspend fun discover(timeoutMs: Long = 1_800L): List<DiscoveredTv> = withContext(Dispatchers.IO) {
        val found = linkedMapOf<String, DiscoveredTv>()
        val payload = CastProtocol.DISCOVERY_QUERY.toByteArray()
        val socket = DatagramSocket(null)
        try {
            socket.reuseAddress = true
            socket.broadcast = true
            socket.soTimeout = 300
            socket.bind(InetSocketAddress(0))

            val targets = broadcastAddresses()
            val deadline = System.currentTimeMillis() + timeoutMs
            for (address in targets) {
                runCatching {
                    socket.send(DatagramPacket(payload, payload.size, address, CastProtocol.DISCOVERY_PORT))
                }
            }

            val buffer = ByteArray(1_024)
            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: java.net.SocketTimeoutException) {
                    continue
                }
                val message = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                if (!message.startsWith(CastProtocol.DISCOVERY_REPLY)) continue
                val jsonPart = message.substringAfter(CastProtocol.DISCOVERY_REPLY).trim()
                val info = runCatching { json.decodeFromString<CastDeviceInfo>(jsonPart) }.getOrNull()
                    ?: continue
                val host = packet.address.hostAddress ?: continue
                found[info.id] = DiscoveredTv(info, host)
            }
        } finally {
            runCatching { socket.close() }
        }
        found.values.toList()
    }

    /** Địa chỉ broadcast của mọi interface wifi/ethernet (vd. 192.168.1.255). */
    private fun broadcastAddresses(): List<InetAddress> {
        val result = mutableListOf<InetAddress>()
        runCatching { result += InetAddress.getByName("255.255.255.255") }
        runCatching {
            java.net.NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .mapNotNull { it.broadcast }
                .forEach { result += it }
        }
        return result.distinct()
    }

    /** Dựng địa chỉ từ IP người dùng gõ tay; chấp nhận có/không có "http://" và cổng. */
    fun baseUrlOf(ip: String): String? {
        val cleaned = ip.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        if (cleaned.isBlank()) return null
        val withPort = if (cleaned.contains(':')) cleaned else "$cleaned:${CastProtocol.PORT}"
        return "http://$withPort"
    }

    suspend fun info(baseUrl: String): CastDeviceInfo? =
        call(baseUrl, CastProtocol.PATH_INFO, null, null)

    suspend fun pair(baseUrl: String, pin: String, deviceName: String): Result<CastPairResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = json.encodeToString(CastPairRequest.serializer(), CastPairRequest(pin.trim(), deviceName))
                    .toRequestBody(jsonMedia)
                val request = Request.Builder()
                    .url(baseUrl + CastProtocol.PATH_PAIR)
                    .header(CastProtocol.HEADER_DEVICE, deviceName)
                    .post(body)
                    .build()
                http.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    val parsed = runCatching { json.decodeFromString(CastPairResponse.serializer(), text) }
                        .getOrNull()
                    when {
                        // TV trả 401 kèm JSON {ok:false, message:...} khi PIN sai.
                        parsed != null -> parsed
                        else -> error("HTTP ${response.code}")
                    }
                }
            }
        }

    suspend fun ping(baseUrl: String, token: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = authorized(baseUrl, CastProtocol.PATH_PING, token).build()
                http.newCall(request).execute().use { it.isSuccessful }
            }.getOrDefault(false)
        }

    suspend fun state(baseUrl: String, token: String): CastPlaybackState? =
        call(baseUrl, CastProtocol.PATH_STATE, token, CastPlaybackState.serializer())

    suspend fun send(baseUrl: String, token: String, command: CastCommand): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = json.encodeToString(CastCommand.serializer(), command).toRequestBody(jsonMedia)
                val request = authorized(baseUrl, CastProtocol.PATH_COMMAND, token)
                    .post(body)
                    .build()
                http.newCall(request).execute().use { it.isSuccessful }
            }.getOrDefault(false)
        }

    /** Gửi lệnh "chọn phim" tới TV. */
    suspend fun open(
        baseUrl: String,
        token: String,
        slug: String,
        server: Int,
        episode: Int,
        name: String,
    ): Boolean = send(
        baseUrl,
        token,
        CastCommand(
            type = CastProtocol.Command.OPEN,
            slug = slug,
            server = server,
            episode = episode,
            name = name,
        ),
    )

    private fun authorized(baseUrl: String, path: String, token: String) =
        Request.Builder()
            .url(baseUrl + path)
            .header(CastProtocol.HEADER_TOKEN, token)

    private suspend fun <T> call(
        baseUrl: String,
        path: String,
        token: String?,
        serializer: kotlinx.serialization.KSerializer<T>?,
    ): T? = withContext(Dispatchers.IO) {
        if (serializer == null) return@withContext null
        runCatching { execute(authorized(baseUrl, path, token ?: "").get().build(), serializer) }
            .getOrNull()
    }

    private fun <T> execute(
        request: Request,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): T? = http.newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("HTTP ${response.code}")
        val body = response.body?.string() ?: error("empty body")
        json.decodeFromString(serializer, body)
    }
}

/** Tên thiết bị hiển thị trên TV khi ghép cặp. */
fun defaultPhoneName(): String {
    val brand = android.os.Build.BRAND?.replaceFirstChar { it.uppercase() }.orEmpty()
    val model = android.os.Build.MODEL.orEmpty()
    return listOf(brand, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Điện thoại" }
}
