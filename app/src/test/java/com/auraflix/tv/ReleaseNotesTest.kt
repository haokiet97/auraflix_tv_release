package com.auraflix.tv

import com.auraflix.tv.core.update.ReleaseNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesTest {

    private val body = """
        # AuraFlix TV v1.1.0

        Ứng dụng xem phim cho Android TV / TV Box.

        ## Thông tin bản phát hành

        | | |
        |---|---|
        | Phiên bản | 1.1.0 |

        ## Có gì mới

        ### Tìm kiếm

        - Thêm phím **Dấu cách**.
        - Hỗ trợ `bàn phím USB`.

        ### Cập nhật OTA

        - Tải APK có tiến độ.
        - Xác thực SHA-256.

        ## Nâng cấp

        ```bash
        adb install -r AuraFlixTV-1.1.0.apk
        ```
    """.trimIndent()

    @Test
    fun `keeps only what's new section`() {
        val cleaned = ReleaseNotes.clean(body)
        assertTrue(cleaned.contains("Tìm kiếm"))
        assertTrue(cleaned.contains("Dấu cách"))
        assertTrue(cleaned.contains("Tải APK có tiến độ"))
        assertFalse(cleaned.contains("Thông tin bản phát hành"))
        assertFalse(cleaned.contains("adb install"))
        assertFalse(cleaned.contains("|"))
    }

    @Test
    fun `strips markdown markers`() {
        val cleaned = ReleaseNotes.clean(body)
        assertFalse(cleaned.contains("##"))
        assertFalse(cleaned.contains("**"))
        assertFalse(cleaned.contains("`"))
    }

    @Test
    fun `falls back to whole body when heading is absent`() {
        val cleaned = ReleaseNotes.clean("# Bản mới\n\n- Sửa lỗi tìm kiếm.")
        assertEquals("Bản mới\nSửa lỗi tìm kiếm.", cleaned)
    }

    @Test
    fun `blank body returns empty string`() {
        assertEquals("", ReleaseNotes.clean(null))
        assertEquals("", ReleaseNotes.clean("  "))
    }
}
