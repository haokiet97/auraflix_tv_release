package com.auraflix.tv.core.repo

import com.auraflix.tv.core.model.HomeFeed
import com.auraflix.tv.core.model.HomeSection
import com.auraflix.tv.core.model.ListEnvelope
import com.auraflix.tv.core.model.Movie
import com.auraflix.tv.core.model.MovieDetail
import com.auraflix.tv.core.model.MoviePage
import com.auraflix.tv.core.model.MovieTypes
import com.auraflix.tv.core.model.Taxonomy
import com.auraflix.tv.core.model.TaxonomyEnvelope
import com.auraflix.tv.core.net.HttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

/**
 * Nguồn dữ liệu chính: API công khai phimapi.com (OPhim v1) — cũng là API mà
 * auraflix.vip đang sử dụng để hiển thị catalog.
 */
class MovieRepository(private val http: HttpClient) {

    @Volatile
    var baseUrl: String = DEFAULT_BASE_URL

    @Volatile
    var cdnImage: String = DEFAULT_CDN

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    // ---------------------------------------------------------------- Trang chủ

    suspend fun home(force: Boolean = false): HomeFeed = coroutineScope {
        val sectionsSpec = listOf(
            "phim-moi-cap-nhat" to "Phim mới cập nhật",
            "phim-le" to "Phim lẻ",
            "phim-bo" to "Phim bộ",
            "hoat-hinh" to "Hoạt hình",
            "tv-shows" to "TV Shows",
            "phim-chieu-rap" to "Phim chiếu rạp",
        )

        val pages = sectionsSpec.map { (slug, title) ->
            async {
                title to (typePage(slug, 1, force).movies)
            }
        }.map { it.await() }

        val hero = pages.firstOrNull { it.first == "Phim mới cập nhật" }?.second.orEmpty()

        HomeFeed(
            hero = hero.take(8),
            sections = pages.mapNotNull { (title, movies) ->
                if (movies.isEmpty()) null else HomeSection(title, movies)
            },
        )
    }

    // ------------------------------------------------------------- Danh sách

    suspend fun typePage(typeSlug: String, page: Int, force: Boolean = false): MoviePage =
        page("type:$typeSlug:$page", "/v1/api/danh-sach/$typeSlug?page=$page", force)

    /**
     * Danh sách có bộ lọc — API hỗ trợ query: category, country, year, sort_field.
     * Dùng chung một endpoint `/danh-sach/{type}` nên có thể kết hợp nhiều điều kiện.
     */
    suspend fun browse(
        typeSlug: String,
        category: String? = null,
        country: String? = null,
        year: Int? = null,
        page: Int = 1,
    ): MoviePage {
        val query = buildString {
            append("/v1/api/danh-sach/$typeSlug?page=$page")
            category?.takeIf { it.isNotBlank() }?.let { append("&category=$it") }
            country?.takeIf { it.isNotBlank() }?.let { append("&country=$it") }
            year?.let { append("&year=$it") }
        }
        return page(
            key = "browse:$typeSlug:$category:$country:$year:$page",
            path = query,
        )
    }

    suspend fun categoryPage(slug: String, page: Int): MoviePage =
        page("cat:$slug:$page", "/v1/api/the-loai/$slug?page=$page")

    suspend fun countryPage(slug: String, page: Int): MoviePage =
        page("country:$slug:$page", "/v1/api/quoc-gia/$slug?page=$page")

    suspend fun search(keyword: String, page: Int = 1): MoviePage =
        page(
            key = "search:${keyword.lowercase()}:$page",
            path = "/v1/api/tim-kiem?keyword=${urlEncode(keyword)}&page=$page",
            ttl = 2 * 60_000L,
        )

    private suspend fun page(key: String, path: String, force: Boolean = false, ttl: Long = LIST_TTL): MoviePage {
        return cached(key, ttl, force) {
            val res = http.getJson("$baseUrl$path", ListEnvelope.serializer())
                ?: return@cached null
            val payload = res.data ?: return@cached null
            payload.cdnImage?.takeIf { it.isNotBlank() }?.let { raw ->
                cdnImage = normalizeCdn(raw)
            }
            val items = payload.items
                .filter { it.slug.isNotBlank() }
                .distinctBy { it.slug }
                .map(::normalize)
            val pag = payload.params?.pagination
            MoviePage(
                movies = items,
                currentPage = pag?.currentPage ?: 1,
                totalPages = pag?.totalPages ?: 1,
                totalItems = pag?.totalItems ?: items.size,
            )
        } ?: MoviePage(emptyList())
    }

    // --------------------------------------------------------------- Chi tiết

    suspend fun detail(slug: String, force: Boolean = false): MovieDetail? =
        cached("detail:$slug", DETAIL_TTL, force) {
            val res = http.getJson("$baseUrl/phim/$slug", MovieDetail.serializer())
                ?: return@cached null
            val movie = res.movie ?: return@cached null
            res.copy(
                movie = normalize(movie),
                episodes = res.episodes
                    .map { server -> server.copy(episodes = server.episodes.filter { it.name != null }) }
                    .filter { it.episodes.isNotEmpty() },
            )
        }

    /** Phim cùng thể loại (dùng cho khối "Có thể bạn thích"). */
    suspend fun related(movie: Movie, limit: Int = 12): List<Movie> {
        val slug = movie.category.firstOrNull()?.slug ?: movie.country.firstOrNull()?.slug ?: return emptyList()
        val page = if (movie.category.isNotEmpty()) {
            categoryPage(slug, 1)
        } else {
            countryPage(slug, 1)
        }
        return page.movies
            .filter { it.slug != movie.slug }
            .distinctBy { it.slug }
            .take(limit)
    }

    // ------------------------------------------------------------- Danh mục

    suspend fun categories(): List<Taxonomy> =
        cached("taxonomies:categories", TAXONOMY_TTL) {
            http.getJson("$baseUrl/v1/api/the-loai", TaxonomyEnvelope.serializer())
                ?.data?.items?.filter { it.slug.isNotBlank() }
        } ?: emptyList()

    suspend fun countries(): List<Taxonomy> =
        cached("taxonomies:countries", TAXONOMY_TTL) {
            http.getJson("$baseUrl/v1/api/quoc-gia", TaxonomyEnvelope.serializer())
                ?.data?.items?.filter { it.slug.isNotBlank() }
        } ?: emptyList()

    // --------------------------------------------------------------- Helpers

    private fun normalize(movie: Movie): Movie = movie.copy(
        posterUrl = absoluteImage(movie.posterUrl, cdnImage),
        thumbUrl = absoluteImage(movie.thumbUrl, cdnImage),
        name = movie.name.trim(),
        content = movie.content?.let(::stripHtml)?.takeIf { it.isNotBlank() },
    )

    private fun absoluteImage(url: String?, cdn: String): String? {
        if (url.isNullOrBlank()) return null
        if (url.startsWith("http")) return url
        return "${cdn.trimEnd('/')}/${url.trimStart('/')}"
    }

    private fun normalizeCdn(value: String): String {
        val raw = value.trim().trimEnd('/')
        if (raw.isBlank()) return DEFAULT_CDN
        return if (raw.startsWith("http")) raw else "https://$raw"
    }

    private fun stripHtml(html: String): String = html
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    private fun urlEncode(text: String): String =
        java.net.URLEncoder.encode(text, "UTF-8")

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(
        key: String,
        ttl: Long,
        force: Boolean = false,
        loader: suspend () -> T?,
    ): T? {
        if (!force) {
            val hit = cache[key]
            if (hit != null && System.currentTimeMillis() - hit.at < ttl) {
                return hit.value as T?
            }
        }
        val value = loader()
        if (value != null) {
            cache[key] = CacheEntry(value, System.currentTimeMillis())
            if (cache.size > 120) {
                val oldest = cache.entries.minByOrNull { it.value.at }?.key
                if (oldest != null) cache.remove(oldest)
            }
        }
        return value
    }

    fun clearMemoryCache() = cache.clear()

    private class CacheEntry(val value: Any, val at: Long)

    companion object {
        const val DEFAULT_BASE_URL = "https://phimapi.com"
        const val DEFAULT_CDN = "https://phimimg.com"

        private const val LIST_TTL = 5 * 60_000L
        private const val DETAIL_TTL = 15 * 60_000L
        private const val TAXONOMY_TTL = 60 * 60_000L

        /** Các domain dự phòng nếu domain chính đổi. */
        val FALLBACK_BASE_URLS = listOf(
            "https://phimapi.com",
            "https://phimapi.vip",
            "https://ophim1.com",
        )

        /** Nhãn hiển thị cho type slug. */
        fun typeLabel(slug: String): String =
            MovieTypes.firstOrNull { it.slug == slug }?.label ?: slug
    }
}
