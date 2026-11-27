package com.auraflix.tv.core.cast

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.auraflix.tv.core.repo.MovieRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.SecureRandom
import kotlin.concurrent.thread

private val Context.castStore: DataStore<Preferences> by preferencesDataStore(name = "auraflix_cast")

/** Cài đặt kết nối hiển thị trong Cài đặt của TV. */
data class CastSettings(
    val enabled: Boolean = true,
    val name: String = defaultDeviceName(),
) {
    companion object {
        /** Tên mặc định suy ra từ model của box (vd. "N5MAX", "MiTV-AXFR0"). */
        fun defaultDeviceName(): String {
            val model = Build.MODEL?.trim().orEmpty()
            return if (model.isBlank()) "AuraFlix TV" else "AuraFlix TV · $model"
        }
    }
}

enum class CastServerStatus { STOPPED, STARTING, RUNNING, ERROR }

data class CastServerState(
    val status: CastServerStatus = CastServerStatus.STOPPED,
    val port: Int = CastProtocol.PORT,
    /** Địa chỉ IP LAN của TV, vd. 192.168.1.12. */
    val ip: String? = null,
    /** PIN ghép cặp hiện tại (luôn có khi server chạy). */
    val pin: String = "",
    val pairedDeviceCount: Int = 0,
    val error: String? = null,
    /** Số thiết bị đang gửi lệnh trong 10 giây gần nhất. */
    val activeControllers: Int = 0,
) {
    val running: Boolean get() = status == CastServerStatus.RUNNING
}

/**
 * Chạy HTTP server + UDP discovery để điện thoại điều khiển TV trong cùng LAN.
 *
 * Thiết kế:
 * - Một thread "acceptor" chuyên dụng (không dùng coroutine) để `accept()` không
 *   chiếm thread của Dispatchers.IO; nếu lỗi bất ngờ, thread tự mở lại socket.
 * - Mỗi kết nối được xử lý trong thread riêng, đọc/ghi tuần tự, có timeout.
 * - PIN và danh sách token được giữ trong bộ nhớ (nạp từ DataStore khi bật server),
 *   nhờ vậy handler không cần gọi hàm suspend.
 * - Chỉ dùng API JDK nên chạy được từ Android 5.0.
 */
class CastLinkManager(
    private val context: Context,
    /** Dùng để tra chi tiết phim khi điện thoại chọn phim trong lúc TV đang ở màn khác. */
    private val repo: MovieRepository,
    private val scope: CoroutineScope,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val random = SecureRandom()

    val deviceId: String = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }.getOrNull() ?: "auraflix-tv"

    /** 4 ký tự ổn định, giúp phân biệt 2 TV cùng model trên cùng mạng. */
    private val deviceTag: String =
        ((deviceId.hashCode().toLong() and 0x7fffffffL) * 7919L % 1679616L)
            .toString(36).uppercase().padStart(4, '0').takeLast(4)

    private val _server = MutableStateFlow(CastServerState())
    val serverState: StateFlow<CastServerState> = _server.asStateFlow()

    private val _settings = MutableStateFlow(CastSettings())
    val settings: StateFlow<CastSettings> = _settings.asStateFlow()

    private val _pairing = MutableStateFlow<CastPairResult?>(null)
    val pairEvent: StateFlow<CastPairResult?> = _pairing.asStateFlow()

    private val _openRequests = MutableSharedFlow<CastOpenRequest>(extraBufferCapacity = 8)
    val openRequests: SharedFlow<CastOpenRequest> = _openRequests.asSharedFlow()

    // --- Trạng thái bảo vệ bằng lock, dùng chung giữa các thread handler ---
    private val lock = Any()
    private var pin: String = ""
    private var tokens: Set<String> = emptySet()
    private val lastCommandAt = mutableMapOf<String, Long>()

    @Volatile private var running = false
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var discoverySocket: DatagramSocket? = null
    private var acceptor: Thread? = null
    private var discovery: Thread? = null
    private var housekeepingJob: Job? = null

    // ------------------------------------------------------------- Vòng đời

    /** Đọc cài đặt đã lưu rồi bật server nếu được phép. Gọi một lần từ Application. */
    fun start() {
        if (housekeepingJob != null) return
        housekeepingJob = scope.launch {
            while (isActive) {
                delay(5_000)
                pruneControllers()
            }
        }
        scope.launch {
            val saved = readSettings()
            _settings.value = saved
            if (saved.enabled) startServer()
        }
    }

    fun setEnabled(enabled: Boolean) {
        scope.launch {
            context.castStore.edit { it[KEY_ENABLED] = enabled.toString() }
            _settings.update { it.copy(enabled = enabled) }
            if (enabled) startServer() else stopServer()
        }
    }

    fun setDeviceName(name: String) {
        val trimmed = name.trim().take(40).ifBlank { CastSettings.defaultDeviceName() }
        scope.launch {
            context.castStore.edit { it[KEY_NAME] = trimmed }
            _settings.update { it.copy(name = trimmed) }
        }
    }

    /** Đổi PIN và ngắt mọi thiết bị đã ghép (khi nghi ngờ có người lạ). */
    fun regeneratePin() {
        scope.launch {
            val newPin = newPin()
            synchronized(lock) {
                pin = newPin
                tokens = emptySet()
            }
            context.castStore.edit {
                it[KEY_PIN] = newPin
                it[KEY_TOKENS] = emptySet()
            }
            _server.update { it.copy(pin = newPin, pairedDeviceCount = 0) }
            lastCommandAt.clear()
        }
    }

    fun forgetAllDevices() {
        scope.launch {
            synchronized(lock) { tokens = emptySet() }
            context.castStore.edit { it[KEY_TOKENS] = emptySet() }
            _server.update { it.copy(pairedDeviceCount = 0) }
        }
    }

    fun clearPairEvent() {
        _pairing.value = null
    }

    private fun startServer() {
        synchronized(lock) {
            if (running) return
            running = true
        }
        _server.update { it.copy(status = CastServerStatus.STARTING, error = null, port = CastProtocol.PORT) }
        DebugLog.add("startServer: bật cổng ${CastProtocol.PORT}")

        // Nạp PIN/token trước khi mở cổng để request đầu tiên đã có dữ liệu.
        scope.launch(Dispatchers.IO) {
            loadPairingState()
            launchAcceptor()
            launchDiscovery()
        }
    }

    /** Đọc PIN/token đã lưu từ DataStore vào bộ nhớ trước khi mở cổng. */
    private suspend fun loadPairingState() {
        val prefs = runCatching { context.castStore.data.first() }.getOrNull()
        val storedPin = prefs?.get(KEY_PIN)?.takeIf { it.length == 6 } ?: newPin()
        val storedTokens = prefs?.get(KEY_TOKENS).orEmpty()
        if (prefs?.get(KEY_PIN).isNullOrBlank()) {
            context.castStore.edit { it[KEY_PIN] = storedPin }
        }
        synchronized(lock) {
            pin = storedPin
            tokens = storedTokens
        }
    }

    /** Cổng đã bị chiếm bởi app AuraFlix khác (VD bản release cài cạnh bản debug). */
    private fun portTaken(): Boolean {
        if (!running) return false
        val ownerPid = runCatching {
            context.getSharedPreferences(PREFS_PORT_OWNER, Context.MODE_PRIVATE)
                .getInt(KEY_OWNER_PID, -1)
        }.getOrDefault(-1)
        if (ownerPid == Process.myPid()) return false
        return runCatching {
            java.net.Socket().use { probe ->
                probe.connect(InetSocketAddress("127.0.0.1", CastProtocol.PORT), 700)
                true
            }
        }.getOrDefault(false)
    }

    private fun stopServer() {
        synchronized(lock) {
            if (!running) return
            running = false
        }
        DebugLog.add("stopServer")
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptor?.interrupt()
        acceptor = null
        runCatching { discoverySocket?.close() }
        discoverySocket = null
        discovery?.interrupt()
        discovery = null
        lastCommandAt.clear()
        _server.update { it.copy(status = CastServerStatus.STOPPED, ip = null, activeControllers = 0) }
    }

    private fun launchAcceptor() {
        acceptor = thread(name = "auraflix-cast-accept", isDaemon = true) {
            DebugLog.add("acceptor bắt đầu")
            while (running) {
                var socket: ServerSocket? = null
                try {
                    socket = ServerSocket()
                    socket.reuseAddress = true
                    socket.bind(InetSocketAddress(CastProtocol.PORT), 16)
                    serverSocket = socket
                    DebugLog.add("acceptor đã bind ${CastProtocol.PORT}")
                    runCatching {
                        context.getSharedPreferences(PREFS_PORT_OWNER, Context.MODE_PRIVATE)
                            .edit().putInt(KEY_OWNER_PID, Process.myPid()).apply()
                    }
                    _server.update {
                        it.copy(
                            status = CastServerStatus.RUNNING,
                            ip = localIpv4(),
                            pin = synchronized(lock) { pin },
                            pairedDeviceCount = synchronized(lock) { tokens.size },
                            error = null,
                        )
                    }
                    while (running) {
                        val client = try {
                            socket.accept()
                        } catch (_: SocketException) {
                            if (running) DebugLog.add("accept SocketException")
                            break
                        }
                        DebugLog.add("accept từ ${client.inetAddress?.hostAddress}")
                        thread(name = "auraflix-cast-conn", isDaemon = true) {
                            runCatching { client.use { handleConnection(it) } }
                        }
                    }
                } catch (t: Throwable) {
                    val taken = t is java.net.BindException && portTaken()
                    if (running) {
                        DebugLog.add(
                            "acceptor lỗi: ${t.javaClass.simpleName}: ${t.message}" + if (taken) " (app khác giữ cổng)" else "",
                        )
                        _server.update {
                            it.copy(
                                status = CastServerStatus.ERROR,
                                error = if (taken) {
                                    "Cổng ${CastProtocol.PORT} đang do một bản AuraFlix khác trên thiết bị này giữ. " +
                                        "Hãy gỡ/hẹn giờ tắt bản đó rồi bấm Bật lại."
                                } else {
                                    "Cổng ${CastProtocol.PORT}: ${t.message ?: t.javaClass.simpleName}"
                                },
                            )
                        }
                    }
                } finally {
                    runCatching { socket?.close() }
                    if (serverSocket === socket) serverSocket = null
                }
                // Nếu bất ngờ dừng khi vẫn được bật: thử lại sau 2 giây.
                if (running) Thread.sleep(2_000)
            }
            DebugLog.add("acceptor kết thúc")
        }
    }

    // -------------------------------------------------------- UDP discovery

    private fun launchDiscovery() {
        discovery = thread(name = "auraflix-cast-disc", isDaemon = true) {
            while (running) {
                try {
                    val socket = DatagramSocket(null).apply {
                        reuseAddress = true
                        broadcast = true
                        bind(InetSocketAddress(CastProtocol.DISCOVERY_PORT))
                    }
                    discoverySocket = socket
                    val buffer = ByteArray(512)
                    while (running) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        try {
                            socket.receive(packet)
                        } catch (_: SocketException) {
                            break
                        }
                        val message = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                        if (message != CastProtocol.DISCOVERY_QUERY) continue
                        val reply = (CastProtocol.DISCOVERY_REPLY + " " +
                            json.encodeToString(CastDeviceInfo.serializer(), deviceInfo()))
                            .toByteArray(Charsets.UTF_8)
                        runCatching {
                            socket.send(DatagramPacket(reply, reply.size, packet.address, packet.port))
                        }
                    }
                } catch (_: SocketException) {
                    // Socket bị đóng khi tắt server — chỉ chạy lại khi vẫn được bật.
                    if (running) Thread.sleep(2_000)
                } catch (_: Exception) {
                    if (running) Thread.sleep(2_000)
                } finally {
                    runCatching { discoverySocket?.close() }
                    discoverySocket = null
                }
            }
        }
    }

    // -------------------------------------------------------- HTTP transport

    private fun handleConnection(client: Socket) {
        client.soTimeout = SOCKET_TIMEOUT_MS
        client.tcpNoDelay = true
        val input = BufferedInputStream(client.getInputStream())
        val output = BufferedOutputStream(client.getOutputStream())
        val remoteIp = client.inetAddress?.hostAddress

        val head = readHead(input) ?: return
        val parts = head.requestLine.split(' ')
        if (parts.size < 2) {
            writeResponse(output, 400, """{"error":"bad request"}""")
            return
        }
        val method = parts[0].uppercase()
        val path = parts[1].substringBefore('?')
        val body = readBody(input, head.contentLength)

        val (status, payload) = try {
            route(method, path, head, body, remoteIp)
        } catch (t: Throwable) {
            500 to """{"error":${quote(t.message ?: "server error")}}"""
        }
        writeResponse(output, status, payload)
    }

    private fun route(
        method: String,
        path: String,
        head: HttpHead,
        body: String,
        remoteIp: String?,
    ): Pair<Int, String> = when {
        method == "GET" && path == CastProtocol.PATH_INFO ->
            200 to json.encodeToString(CastDeviceInfo.serializer(), deviceInfo())

        method == "POST" && path == CastProtocol.PATH_PAIR -> {
            val request = runCatching { json.decodeFromString(CastPairRequest.serializer(), body) }.getOrNull()
            if (request == null) {
                400 to json.encodeToString(
                    CastPairResponse.serializer(),
                    CastPairResponse(false, message = "Dữ liệu không hợp lệ"),
                )
            } else {
                pair(request)
            }
        }

        method == "GET" && path == CastProtocol.PATH_PING ->
            if (authorized(head)) {
                200 to json.encodeToString(CastPingResponse.serializer(), CastPingResponse())
            } else {
                unauthorized()
            }

        method == "GET" && path == CastProtocol.PATH_STATE ->
            if (!authorized(head)) {
                unauthorized()
            } else {
                touchController(head, remoteIp)
                200 to json.encodeToString(CastPlaybackState.serializer(), PlayerControllerHolder.state.value)
            }

        method == "POST" && path == CastProtocol.PATH_COMMAND ->
            if (!authorized(head)) {
                unauthorized()
            } else {
                val command = runCatching { json.decodeFromString(CastCommand.serializer(), body) }.getOrNull()
                if (command == null) {
                    400 to """{"error":"invalid command"}"""
                } else {
                    touchController(head, remoteIp)
                    dispatch(command)
                    200 to """{"ok":true}"""
                }
            }

        else -> 404 to """{"error":"not found"}"""
    }

    private fun unauthorized() = 401 to """{"error":"unauthorized"}"""

    private fun deviceInfo() = CastDeviceInfo(
        id = deviceId,
        name = _settings.value.name,
        paired = synchronized(lock) { tokens.isNotEmpty() },
        ip = _server.value.ip,
        tag = deviceTag,
    )

    private fun pair(request: CastPairRequest): Pair<Int, String> {
        val currentPin = synchronized(lock) { pin }
        if (request.pin.trim() != currentPin) {
            _pairing.value = CastPairResult(false, request.deviceName, System.currentTimeMillis())
            Thread.sleep(900) // chậm brute-force PIN 6 số
            return 401 to json.encodeToString(
                CastPairResponse.serializer(),
                CastPairResponse(false, message = "Mã PIN không đúng. Kiểm tra lại trên TV."),
            )
        }

        val token = randomToken()
        val updated = synchronized(lock) {
            tokens = tokens + token
            tokens
        }
        runBlocking { context.castStore.edit { it[KEY_TOKENS] = updated } }
        _server.update { it.copy(pairedDeviceCount = updated.size) }
        _pairing.value = CastPairResult(true, request.deviceName, System.currentTimeMillis())
        return 200 to json.encodeToString(CastPairResponse.serializer(), CastPairResponse(true, token = token))
    }

    private fun dispatch(command: CastCommand) {
        if (command.type == CastProtocol.Command.OPEN) {
            val slug = command.slug
            if (slug.isNullOrBlank()) return
            scope.launch {
                val detail = runCatching { repo.detail(slug) }.getOrNull()
                _openRequests.tryEmit(
                    CastOpenRequest(
                        slug = slug,
                        server = command.server ?: 0,
                        episode = command.episode ?: 0,
                        name = command.name ?: detail?.movie?.displayName.orEmpty(),
                    ),
                )
            }
            return
        }
        PlayerControllerHolder.current?.enqueue(command)
    }

    private fun touchController(head: HttpHead, remoteIp: String?) {
        val key = head.token ?: remoteIp ?: return
        val firstTouch = synchronized(lock) {
            val existed = lastCommandAt.containsKey(key)
            lastCommandAt[key] = System.currentTimeMillis()
            !existed
        }
        if (firstTouch) {
            _server.update { it.copy(activeControllers = synchronized(lock) { lastCommandAt.size }) }
            wakeScreen()
        }
    }

    private fun pruneControllers() {
        val threshold = System.currentTimeMillis() - CONTROLLER_TTL_MS
        val removed = synchronized(lock) {
            lastCommandAt.entries.removeAll { it.value < threshold }
        }
        if (removed) _server.update { it.copy(activeControllers = synchronized(lock) { lastCommandAt.size }) }
    }

    private fun wakeScreen() {
        runCatching {
            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wake = power.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "AuraFlix:cast",
            )
            wake.acquire(5_000)
        }
    }

    private fun authorized(head: HttpHead): Boolean {
        val token = head.token ?: return false
        return synchronized(lock) { tokens.contains(token) }
    }

    // ---------------------------------------------------- Cài đặt / token

    private suspend fun readSettings(): CastSettings {
        val prefs = runCatching { context.castStore.data.first() }.getOrNull()
        return CastSettings(
            enabled = prefs?.get(KEY_ENABLED)?.toBooleanStrictOrNull() ?: true,
            name = prefs?.get(KEY_NAME)?.takeIf { it.isNotBlank() } ?: CastSettings.defaultDeviceName(),
        )
    }

    private fun newPin(): String = (100_000 + random.nextInt(900_000)).toString()

    private fun randomToken(): String {
        val bytes = ByteArray(24)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ------------------------------------------------------------- Helpers

    /** IP IPv4 của giao diện có route ra LAN (bỏ qua loopback và interface ảo). */
    fun localIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

    private class HttpHead(
        val requestLine: String,
        val contentLength: Int,
        val token: String?,
    )

    private fun readHead(input: InputStream): HttpHead? {
        val head = StringBuilder()
        var length = 0
        while (true) {
            val b = input.read()
            if (b < 0) return null
            head.append(b.toChar())
            length++
            if (length > MAX_HEAD) return null
            if (head.endsWith("\r\n\r\n")) break
        }
        val lines = head.toString().split("\r\n")
        var contentLength = 0
        var token: String? = null
        for (line in lines.drop(1)) {
            if (line.isBlank()) break
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val name = line.substring(0, colon).trim().lowercase()
            val value = line.substring(colon + 1).trim()
            when (name) {
                "content-length" -> contentLength = value.toIntOrNull() ?: 0
                CastProtocol.HEADER_TOKEN.lowercase() -> token = value
            }
        }
        return HttpHead(lines.firstOrNull().orEmpty(), contentLength, token)
    }

    private fun readBody(input: InputStream, contentLength: Int): String {
        if (contentLength <= 0) return ""
        val bytes = ByteArray(contentLength.coerceAtMost(MAX_BODY))
        var read = 0
        while (read < bytes.size) {
            val n = input.read(bytes, read, bytes.size - read)
            if (n < 0) break
            read += n
        }
        return String(bytes, 0, read, Charsets.UTF_8)
    }

    private fun writeResponse(output: OutputStream, status: Int, body: String) {
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            else -> "Internal Server Error"
        }
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n")
            append("Cache-Control: no-store\r\n")
            append("\r\n")
        }.toByteArray(Charsets.UTF_8)
        output.write(header)
        output.write(bytes)
        output.flush()
    }

    private fun quote(text: String): String = "\"" + text.replace("\"", "'") + "\""

    companion object {
        private const val SOCKET_TIMEOUT_MS = 5_000
        private const val MAX_HEAD = 8 * 1024
        private const val MAX_BODY = 64 * 1024
        private const val CONTROLLER_TTL_MS = 10_000L

        private val KEY_ENABLED = stringPreferencesKey("cast_enabled")
        private val KEY_NAME = stringPreferencesKey("cast_name")
        private val KEY_PIN = stringPreferencesKey("cast_pin")
        private val KEY_TOKENS = stringSetPreferencesKey("cast_tokens")

        /** PID của tiến trình đang giữ cổng — phát hiện bản AuraFlix khác chiếm cổng. */
        private const val PREFS_PORT_OWNER = "auraflix_cast_port"
        private const val KEY_OWNER_PID = "owner_pid"
    }
}

data class CastPairResult(
    val success: Boolean,
    val deviceName: String,
    val at: Long,
)

/** Log vòng tròn 12 dòng cuối của server LAN, hiển thị (tạm thời) trong Cài đặt để gỡ lỗi. */
object DebugLog {
    @Volatile private var sink: ((String) -> Unit)? = null

    private val lines = ArrayDeque<String>(16)

    /** App gắn log vào đây (chỉ bản debug) để xem qua logcat. */
    fun install(sink: (String) -> Unit) {
        this.sink = sink
    }

    fun add(line: String) {
        val text = line.take(200)
        synchronized(lines) {
            if (lines.size >= 12) lines.removeFirst()
            lines.addLast(text)
        }
        sink?.invoke(text)
    }

    fun last(): String = synchronized(lines) { lines.joinToString(" | ") }
}
