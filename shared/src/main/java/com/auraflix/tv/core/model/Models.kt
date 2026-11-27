package com.auraflix.tv.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Taxonomy(
    val id: String? = null,
    val name: String = "",
    val slug: String = "",
)

@Serializable
data class LastEpisode(
    @SerialName("server_name") val serverName: String? = null,
    val name: String? = null,
)

@Serializable
data class Movie(
    @SerialName("_id") val id: String? = null,
    val name: String = "",
    val slug: String = "",
    @SerialName("origin_name") val originName: String? = null,
    @SerialName("alternative_names") val alternativeNames: List<String> = emptyList(),
    val type: String? = null,
    @SerialName("thumb_url") val thumbUrl: String? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    val time: String? = null,
    @SerialName("episode_current") val episodeCurrent: String? = null,
    @SerialName("episode_total") val episodeTotal: String? = null,
    val quality: String? = null,
    val lang: String? = null,
    val year: Int? = null,
    val content: String? = null,
    val status: String? = null,
    val view: Long? = null,
    val chieurap: Boolean? = null,
    val category: List<Taxonomy> = emptyList(),
    val country: List<Taxonomy> = emptyList(),
    @SerialName("last_episodes") val lastEpisodes: List<LastEpisode> = emptyList(),
    val actor: List<String> = emptyList(),
    val director: List<String> = emptyList(),
) {
    val isSeries: Boolean get() = type == "series" || type == "hoathinh" || type == "tvshows"
    val displayName: String get() = name.ifBlank { originName.orEmpty() }
}

@Serializable
data class Pagination(
    val totalItems: Int = 0,
    val totalItemsPerPage: Int = 24,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
)

@Serializable
data class PageParams(val pagination: Pagination? = null)

@Serializable
data class ListPayload(
    val items: List<Movie> = emptyList(),
    val params: PageParams? = null,
    @SerialName("APP_DOMAIN_CDN_IMAGE") val cdnImage: String? = null,
)

@Serializable
data class ListEnvelope(
    val status: JsonElement? = null,
    val data: ListPayload? = null,
)

@Serializable
data class TaxonomyPayload(
    val items: List<Taxonomy> = emptyList(),
)

@Serializable
data class TaxonomyEnvelope(
    val status: JsonElement? = null,
    val data: TaxonomyPayload? = null,
)

@Serializable
data class Episode(
    val name: String? = null,
    val slug: String? = null,
    val filename: String? = null,
    @SerialName("link_embed") val linkEmbed: String? = null,
    @SerialName("link_m3u8") val linkM3u8: String? = null,
) {
    val playableUrl: String? get() = linkM3u8?.takeIf { it.isNotBlank() }
}

@Serializable
data class EpisodeServer(
    @SerialName("server_name") val serverName: String? = null,
    @SerialName("is_ai") val isAi: Boolean = false,
    @SerialName("server_data") val episodes: List<Episode> = emptyList(),
)

@Serializable
data class MovieDetail(
    val status: JsonElement? = null,
    val movie: Movie? = null,
    val episodes: List<EpisodeServer> = emptyList(),
) {
    val servers: List<EpisodeServer>
        get() = episodes.filter { it.episodes.isNotEmpty() }
}

/** Một dòng phim trên trang chủ. */
data class HomeSection(
    val title: String,
    val movies: List<Movie>,
)

data class HomeFeed(
    val hero: List<Movie>,
    val sections: List<HomeSection>,
)

/** Kết quả phân trang dùng chung cho mọi màn hình danh sách. */
data class MoviePage(
    val movies: List<Movie>,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val totalItems: Int = 0,
) {
    val hasMore: Boolean get() = currentPage < totalPages
}

/** Loại phim cố định dùng cho bộ lọc. */
data class TypeFilter(val slug: String, val label: String)

val MovieTypes = listOf(
    TypeFilter("phim-moi-cap-nhat", "Mới cập nhật"),
    TypeFilter("phim-le", "Phim lẻ"),
    TypeFilter("phim-bo", "Phim bộ"),
    TypeFilter("hoat-hinh", "Hoạt hình"),
    TypeFilter("tv-shows", "TV Shows"),
    TypeFilter("phim-chieu-rap", "Chiếu rạp"),
)
