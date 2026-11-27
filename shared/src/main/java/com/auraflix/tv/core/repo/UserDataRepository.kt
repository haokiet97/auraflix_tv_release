package com.auraflix.tv.core.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.auraflix.tv.core.model.Movie
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auraflix")

@Serializable
data class HistoryItem(
    val slug: String,
    val name: String,
    val posterUrl: String? = null,
    val episodeName: String = "",
    val serverName: String = "",
    val serverIndex: Int = 0,
    val episodeIndex: Int = 0,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val updatedAt: Long = 0,
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

@Serializable
data class AppSettings(
    val preferredServer: String = "",
    val decoderMode: String = DecoderMode.AUTO,
    val baseUrl: String = MovieRepository.DEFAULT_BASE_URL,
    val resumeEnabled: Boolean = true,
)

object DecoderMode {
    const val AUTO = "auto"
    const val HARDWARE = "hw"
    const val SOFTWARE = "sw"
}

class UserDataRepository(context: Context) {

    private val store = context.dataStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // --------------------------------------------------------------- Lịch sử

    val history: Flow<List<HistoryItem>> = store.data.map { prefs ->
        decodeHistory(prefs[KEY_HISTORY])
    }

    val continueWatching: Flow<List<HistoryItem>> = history.map { items ->
        items.filter { it.positionMs > 30_000 && it.progress < 0.95 }
            .sortedByDescending { it.updatedAt }
            .take(12)
    }

    suspend fun saveProgress(item: HistoryItem) {
        store.edit { prefs ->
            val current = decodeHistory(prefs[KEY_HISTORY]).toMutableList()
            current.removeAll { it.slug == item.slug }
            current.add(0, item.copy(updatedAt = System.currentTimeMillis()))
            prefs[KEY_HISTORY] = encode(current.take(60))
        }
    }

    suspend fun progressOf(slug: String): HistoryItem? =
        history.firstValue().firstOrNull { it.slug == slug }

    suspend fun removeProgress(slug: String) {
        store.edit { prefs ->
            val current = decodeHistory(prefs[KEY_HISTORY]).filterNot { it.slug == slug }
            prefs[KEY_HISTORY] = encode(current)
        }
    }

    suspend fun clearHistory() {
        store.edit { it[KEY_HISTORY] = encode(emptyList()) }
    }

    // ------------------------------------------------------------- Yêu thích

    val favorites: Flow<List<Movie>> = store.data.map { prefs ->
        decodeMovies(prefs[KEY_FAVORITES])
    }

    suspend fun isFavorite(slug: String): Boolean =
        favorites.firstValue().any { it.slug == slug }

    suspend fun toggleFavorite(movie: Movie): Boolean {
        var added = false
        store.edit { prefs ->
            val current = decodeMovies(prefs[KEY_FAVORITES]).toMutableList()
            if (current.any { it.slug == movie.slug }) {
                current.removeAll { it.slug == movie.slug }
            } else {
                current.add(0, movie)
                added = true
            }
            prefs[KEY_FAVORITES] = encodeMovies(current.take(200))
        }
        return added
    }

    // ---------------------------------------------------------------- Cài đặt

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            preferredServer = prefs[KEY_SERVER] ?: "",
            decoderMode = prefs[KEY_DECODER] ?: DecoderMode.AUTO,
            baseUrl = prefs[KEY_BASE_URL] ?: MovieRepository.DEFAULT_BASE_URL,
            resumeEnabled = prefs[KEY_RESUME]?.toBooleanStrictOrNull() ?: true,
        )
    }

    suspend fun setPreferredServer(value: String) {
        store.edit { it[KEY_SERVER] = value }
    }

    suspend fun setDecoderMode(value: String) {
        store.edit { it[KEY_DECODER] = value }
    }

    suspend fun setBaseUrl(value: String) {
        store.edit { it[KEY_BASE_URL] = value.trim().trimEnd('/') }
    }

    suspend fun setResumeEnabled(value: Boolean) {
        store.edit { it[KEY_RESUME] = value.toString() }
    }

    // ---------------------------------------------------------------- Helpers

    private suspend fun <T> Flow<T>.firstValue(): T = first()

    private fun decodeHistory(raw: String?): List<HistoryItem> = runCatching {
        if (raw.isNullOrBlank()) emptyList()
        else json.decodeFromString(ListSerializer(HistoryItem.serializer()), raw)
    }.getOrDefault(emptyList())

    private fun encode(items: List<HistoryItem>): String =
        json.encodeToString(ListSerializer(HistoryItem.serializer()), items)

    private fun decodeMovies(raw: String?): List<Movie> = runCatching {
        if (raw.isNullOrBlank()) emptyList()
        else json.decodeFromString(ListSerializer(Movie.serializer()), raw)
    }.getOrDefault(emptyList())

    private fun encodeMovies(items: List<Movie>): String =
        json.encodeToString(ListSerializer(Movie.serializer()), items)

    private companion object {
        val KEY_HISTORY = stringPreferencesKey("history")
        val KEY_FAVORITES = stringPreferencesKey("favorites")
        val KEY_SERVER = stringPreferencesKey("preferred_server")
        val KEY_DECODER = stringPreferencesKey("decoder_mode")
        val KEY_BASE_URL = stringPreferencesKey("base_url")
        val KEY_RESUME = stringPreferencesKey("resume_enabled")
    }
}
