package com.auraflix.tv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auraflix.tv.core.model.EpisodeServer
import com.auraflix.tv.core.repo.HistoryItem
import com.auraflix.tv.core.repo.MovieRepository
import com.auraflix.tv.core.repo.UserDataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val movieName: String = "",
    val posterUrl: String? = null,
    val servers: List<EpisodeServer> = emptyList(),
    val serverIndex: Int = 0,
    val episodeIndex: Int = 0,
    val url: String? = null,
    val episodeLabel: String = "",
    val startPositionMs: Long = 0L,
    val isSeries: Boolean = false,
    val preferredServer: String = "",
    val resumeEnabled: Boolean = true,
) {
    val currentServer: EpisodeServer? get() = servers.getOrNull(serverIndex)
    val episodes get() = currentServer?.episodes.orEmpty()
    val hasNext: Boolean get() = episodeIndex < episodes.lastIndex
    val hasNextServer: Boolean get() = servers.size > 1
}

class PlayerViewModel(
    private val repo: MovieRepository,
    private val userData: UserDataRepository,
    /** Scope sống cùng process để lưu tiến trình không bị huỷ khi rời màn hình. */
    private val appScope: CoroutineScope,
    private val slug: String,
    initialServer: Int,
    initialEpisode: Int,
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val requestedServer = initialServer
    private val requestedEpisode = initialEpisode

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val settings = runCatching { userData.settings.first() }.getOrNull()
            val detail = repo.detail(slug)
            if (detail?.movie == null || detail.servers.isEmpty()) {
                _state.update {
                    it.copy(loading = false, error = "Không tìm thấy nguồn phát cho phim này.")
                }
                return@launch
            }

            val servers = detail.servers
            val preferred = settings?.preferredServer.orEmpty()
            val serverIndex = when {
                requestedServer in servers.indices -> requestedServer
                preferred.isNotBlank() ->
                    servers.indexOfFirst { it.serverName == preferred }.takeIf { it >= 0 } ?: 0
                else -> 0
            }

            val history = runCatching { userData.progressOf(slug) }.getOrNull()
            val episodeIndex = when {
                requestedEpisode in servers[serverIndex].episodes.indices -> requestedEpisode
                history != null && history.serverIndex == serverIndex &&
                    history.episodeIndex in servers[serverIndex].episodes.indices -> history.episodeIndex
                else -> 0
            }

            val resumeMs = history
                ?.takeIf {
                    settings?.resumeEnabled != false &&
                        it.serverIndex == serverIndex &&
                        it.episodeIndex == episodeIndex &&
                        it.positionMs > 30_000 &&
                        it.progress < 0.95
                }
                ?.positionMs ?: 0L

            _state.update {
                it.copy(
                    loading = false,
                    movieName = detail.movie.displayName,
                    posterUrl = detail.movie.posterUrl ?: detail.movie.thumbUrl,
                    servers = servers,
                    serverIndex = serverIndex,
                    episodeIndex = episodeIndex,
                    url = servers[serverIndex].episodes.getOrNull(episodeIndex)?.playableUrl,
                    episodeLabel = servers[serverIndex].episodes.getOrNull(episodeIndex)?.name.orEmpty(),
                    startPositionMs = resumeMs,
                    isSeries = servers[serverIndex].episodes.size > 1,
                    preferredServer = preferred,
                    resumeEnabled = settings?.resumeEnabled ?: true,
                    error = null,
                )
            }
        }
    }

    fun selectEpisode(index: Int) {
        val s = _state.value
        val ep = s.episodes.getOrNull(index) ?: return
        val url = ep.playableUrl
        if (url.isNullOrBlank()) {
            _state.update { it.copy(error = "Tập này chưa có nguồn phát. Hãy chọn nguồn khác.") }
            return
        }
        _state.update {
            it.copy(
                episodeIndex = index,
                url = url,
                episodeLabel = ep.name.orEmpty(),
                startPositionMs = 0,
                error = null,
            )
        }
    }

    fun selectServer(index: Int) {
        val s = _state.value
        val server = s.servers.getOrNull(index) ?: return
        if (server.episodes.isEmpty()) return
        val episode = server.episodes.getOrNull(s.episodeIndex) ?: server.episodes.first()
        _state.update {
            it.copy(
                serverIndex = index,
                episodeIndex = server.episodes.indexOf(episode).coerceAtLeast(0),
                url = episode.playableUrl,
                episodeLabel = episode.name.orEmpty(),
                startPositionMs = 0,
                error = null,
            )
        }
    }

    fun nextServer() {
        val s = _state.value
        if (s.servers.size <= 1) return
        selectServer((s.serverIndex + 1) % s.servers.size)
    }

    fun nextEpisode(): Boolean {
        val s = _state.value
        if (!s.hasNext) return false
        selectEpisode(s.episodeIndex + 1)
        return true
    }

    fun retry() {
        val s = _state.value
        _state.update { it.copy(url = null, error = null, loading = true) }
        // Đặt lại cùng URL để LaunchedEffect trong UI kích hoạt prepare() lại.
        viewModelScope.launch {
            kotlinx.coroutines.delay(80)
            _state.update { it.copy(url = s.url, loading = false) }
        }
    }

    fun onPlayerError(message: String) {
        _state.update { it.copy(error = message) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Lưu tiến trình bằng scope của Application để không bị huỷ khi màn hình player
     * bị pop khỏi back stack.
     */
    fun saveProgress(positionMs: Long, durationMs: Long) {
        val s = _state.value
        if (s.url.isNullOrBlank() || positionMs <= 0) return
        val item = HistoryItem(
            slug = slug,
            name = s.movieName,
            posterUrl = s.posterUrl,
            episodeName = s.episodeLabel,
            serverName = s.currentServer?.serverName.orEmpty(),
            serverIndex = s.serverIndex,
            episodeIndex = s.episodeIndex,
            positionMs = positionMs,
            durationMs = durationMs,
        )
        appScope.launch {
            runCatching { userData.saveProgress(item) }
        }
    }
}
