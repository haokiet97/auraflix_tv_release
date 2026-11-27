package com.auraflix.tv

import com.auraflix.tv.core.cast.CastCommand
import com.auraflix.tv.core.cast.CastDeviceInfo
import com.auraflix.tv.core.cast.CastPairRequest
import com.auraflix.tv.core.cast.CastPairResponse
import com.auraflix.tv.core.cast.CastPlaybackState
import com.auraflix.tv.core.cast.CastProtocol
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm tra định dạng JSON giao thức TV ↔ Mobile.
 *
 * Hai app được phát hành APK riêng và có thể nâng cấp lệch phiên bản, nên tên
 * trường và các giá trị mặc định (mà bên nhận phải hiểu được) là hợp đồng cần
 * được khoá bằng test.
 */
class CastProtocolTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun deviceInfoRoundTrip() {
        val info = CastDeviceInfo(
            id = "abc123",
            name = "AuraFlix TV · N5MAX",
            paired = true,
            ip = "192.168.1.20",
            tag = "K3LQ",
        )
        val text = json.encodeToString(CastDeviceInfo.serializer(), info)
        assertTrue(text.contains("\"id\":\"abc123\""))
        assertTrue(text.contains("\"name\":\"AuraFlix TV · N5MAX\""))
        assertEquals(info, json.decodeFromString(CastDeviceInfo.serializer(), text))
    }

    @Test
    fun playbackStateDefaultsAreSafeForOlderClients() {
        // Một payload tối thiểu: mọi trường khác phải có mặc định (không crash khi
        // điện thoại cũ đọc payload mới hoặc ngược lại).
        val minimal = """{"active":true,"movieName":"Dune","positionMs":1200}"""
        val state = json.decodeFromString(CastPlaybackState.serializer(), minimal)
        assertTrue(state.active)
        assertEquals("Dune", state.movieName)
        assertEquals(1200L, state.positionMs)
        assertEquals(0L, state.durationMs)
        assertFalse(state.isPlaying)
        assertFalse(state.hasNext)
        assertNull(state.error)
    }

    @Test
    fun unknownServerFieldsAreIgnored() {
        // TV mới thêm trường mới -> điện thoại cũ vẫn đọc được.
        val future = """{"active":true,"volume":80,"futureField":{"a":1}}"""
        val state = json.decodeFromString(CastPlaybackState.serializer(), future)
        assertTrue(state.active)
    }

    @Test
    fun commandsCarryOptionalTarget() {
        val open = CastCommand(
            type = CastProtocol.Command.OPEN,
            slug = "dune-2021",
            server = 1,
            episode = 3,
            name = "Dune",
        )
        val text = json.encodeToString(CastCommand.serializer(), open)
        val back = json.decodeFromString(CastCommand.serializer(), text)
        assertEquals("open", back.type)
        assertEquals("dune-2021", back.slug)
        assertEquals(1, back.server)
        assertEquals(3, back.episode)
        assertNull(back.value)

        val seek = CastCommand(type = CastProtocol.Command.SEEK_BY, value = -10_000)
        val seekText = json.encodeToString(CastCommand.serializer(), seek)
        assertEquals(-10_000L, json.decodeFromString(CastCommand.serializer(), seekText).value)
    }

    @Test
    fun pairPayloads() {
        val request = CastPairRequest(pin = "123456", deviceName = "Pixel 7")
        val requestText = json.encodeToString(CastPairRequest.serializer(), request)
        assertEquals(request, json.decodeFromString(CastPairRequest.serializer(), requestText))

        val ok = CastPairResponse(ok = true, token = "abc")
        assertEquals(ok, json.decodeFromString(CastPairResponse.serializer(), json.encodeToString(CastPairResponse.serializer(), ok)))

        val failed = json.decodeFromString(
            CastPairResponse.serializer(),
            """{"ok":false,"message":"Mã PIN không đúng."}""",
        )
        assertFalse(failed.ok)
        assertNull(failed.token)
    }

    @Test
    fun discoveryStringsAreStable() {
        // Đổi các hằng này sẽ làm điện thoại và TV cài đặt ở 2 thời điểm không gặp nhau.
        assertEquals("AURAFLIX_DISCOVER_V1", CastProtocol.DISCOVERY_QUERY)
        assertEquals("AURAFLIX_TV_V1", CastProtocol.DISCOVERY_REPLY)
        assertEquals(8765, CastProtocol.PORT)
        assertEquals(8766, CastProtocol.DISCOVERY_PORT)
    }
}
