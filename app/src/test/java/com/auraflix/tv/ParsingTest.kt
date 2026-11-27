package com.auraflix.tv

import com.auraflix.tv.core.model.ListEnvelope
import com.auraflix.tv.core.model.MovieDetail
import com.auraflix.tv.core.model.TaxonomyEnvelope
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm tra DTO khớp với JSON thật của phimapi.com (mẫu thu thập ngày 2026-11-21).
 */
class ParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    private val listSample = """
    {
      "status": true,
      "msg": "done",
      "data": {
        "seoOnPage": {"og_type": "website", "titleHead": "Phim Mới"},
        "items": [
          {
            "tmdb": {"id": "203483", "type": "tv", "season": 1, "vote_average": 8, "vote_count": 4},
            "imdb": {"id": null, "vote_average": 0, "vote_count": 0},
            "modified": {"time": "2026-11-21T20:11:56.000Z"},
            "_id": "645991d29a09c8535014e5d1924730c7",
            "name": "Lương Kinh Thập Ngũ Nhật",
            "slug": "luong-kinh-thap-ngu-nhat",
            "origin_name": "River Of No Return",
            "alternative_names": ["River of No Return", "流光十五日"],
            "type": "series",
            "thumb_url": "uploads/movies/20261121/luong-kinh-thap-ngu-nhat-thumb.webp",
            "poster_url": "uploads/movies/20261121/luong-kinh-thap-ngu-nhat-poster.webp",
            "sub_docquyen": false,
            "chieurap": false,
            "time": "45 phút/tập",
            "episode_current": "Tập 6",
            "quality": "FHD",
            "lang": "Vietsub + Thuyết Minh",
            "lang_key": ["vs", "tm"],
            "year": 2026,
            "category": [{"id": "1", "name": "Chính kịch", "slug": "chinh-kich"}],
            "country": [{"id": "2", "name": "Trung Quốc", "slug": "trung-quoc"}],
            "last_episodes": [{"server_name": "Vietsub", "is_ai": false, "name": "Tập 06"}]
          }
        ],
        "params": {
          "type_slug": "danh-sach",
          "pagination": {
            "totalItems": 30645,
            "totalItemsPerPage": 24,
            "currentPage": 1,
            "totalPages": 1277
          }
        },
        "APP_DOMAIN_CDN_IMAGE": "https://phimimg.com"
      }
    }
    """.trimIndent()

    private val detailSample = """
    {
      "status": true,
      "msg": "done",
      "movie": {
        "_id": "abc",
        "name": "Lương Kinh Thập Ngũ Nhật",
        "slug": "luong-kinh-thap-ngu-nhat",
        "origin_name": "River Of No Return",
        "content": "<p>Nội dung phim.</p>",
        "type": "series",
        "status": "ongoing",
        "thumb_url": "https://phimimg.com/a-thumb.webp",
        "poster_url": "https://phimimg.com/a-poster.webp",
        "time": "45 phút/tập",
        "episode_current": "Tập 6",
        "episode_total": "26",
        "quality": "FHD",
        "lang": "Vietsub + Thuyết Minh",
        "year": 2026,
        "view": 12345,
        "actor": ["Diễn viên A"],
        "director": ["Đạo diễn B"],
        "category": [{"id": "1", "name": "Chính kịch", "slug": "chinh-kich"}],
        "country": [{"id": "2", "name": "Trung Quốc", "slug": "trung-quoc"}]
      },
      "episodes": [
        {
          "server_name": "Vietsub",
          "is_ai": false,
          "server_data": [
            {
              "name": "Tập 01",
              "slug": "tap-01",
              "filename": "Tap 01",
              "link_embed": "https://player.phimapi.com/player/?url=https://a.kvp726.com/x/index.m3u8",
              "link_m3u8": "https://a.kvp726.com/x/index.m3u8"
            },
            {
              "name": "Tập 02",
              "slug": "tap-02",
              "filename": "Tap 02",
              "link_embed": "",
              "link_m3u8": ""
            }
          ]
        }
      ]
    }
    """.trimIndent()

    private val taxonomiesSample = """
    {
      "status": true,
      "msg": "done",
      "data": {
        "items": [
          {"_id": "1", "name": "Bí ẩn", "slug": "bi-an"},
          {"_id": "2", "name": "Hành Động", "slug": "hanh-dong"}
        ]
      }
    }
    """.trimIndent()

    @Test
    fun `parses movie list envelope`() {
        val parsed = json.decodeFromString(ListEnvelope.serializer(), listSample)
        val payload = parsed.data
        assertNotNull(payload)
        assertEquals(1, payload!!.items.size)

        val movie = payload.items.first()
        assertEquals("luong-kinh-thap-ngu-nhat", movie.slug)
        assertEquals(2026, movie.year)
        assertEquals("Tập 6", movie.episodeCurrent)
        assertEquals("Chính kịch", movie.category.first().name)
        assertTrue(movie.isSeries)

        assertEquals(1277, payload.params?.pagination?.totalPages)
        assertEquals("https://phimimg.com", payload.cdnImage)
    }

    @Test
    fun `parses movie detail with episodes`() {
        val parsed = json.decodeFromString(MovieDetail.serializer(), detailSample)
        val movie = parsed.movie
        assertNotNull(movie)
        assertEquals(
            // So sánh bằng escape để test không phụ thuộc encoding của file nguồn.
            "L\u01B0\u01A1ng Kinh Th\u1EADp Ng\u0169 Nh\u1EADt",
            movie!!.name,
        )

        val servers = parsed.servers
        assertEquals(1, servers.size)
        assertEquals(2, servers.first().episodes.size)

        val first = servers.first().episodes.first()
        assertEquals("https://a.kvp726.com/x/index.m3u8", first.playableUrl)

        // Tập không có link m3u8 phải trả về null thay vì chuỗi rỗng.
        assertEquals(null, servers.first().episodes[1].playableUrl)
    }

    @Test
    fun `parses taxonomies`() {
        val parsed = json.decodeFromString(TaxonomyEnvelope.serializer(), taxonomiesSample)
        assertEquals(2, parsed.data?.items?.size)
        assertEquals("hanh-dong", parsed.data?.items?.last()?.slug)
    }

    @Test
    fun `tolerates unknown fields`() {
        val withExtras = listSample.replace(
            "\"msg\": \"done\"",
            "\"msg\": \"done\", \"brandNewField\": {\"a\": [1,2,3]}",
        )
        val parsed = json.decodeFromString(ListEnvelope.serializer(), withExtras)
        assertNotNull(parsed.data)
    }

    @Test
    fun `vietnamese literals survive compilation`() {
        // "Lương" dựng từ byte UTF-8, đối chiếu với literal tiếng Việt trong source.
        val fromBytes = String(
            byteArrayOf(
                0x4C,
                0xC6.toByte(), 0xB0.toByte(),
                0xC6.toByte(), 0xA1.toByte(),
                0x6E,
                0x67,
            ),
            Charsets.UTF_8,
        )
        assertEquals(fromBytes, "L\u01B0\u01A1ng")
    }
}
