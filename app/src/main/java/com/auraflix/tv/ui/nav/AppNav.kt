package com.auraflix.tv.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.auraflix.tv.Graph
import com.auraflix.tv.ui.browse.BrowseScreen
import com.auraflix.tv.ui.detail.DetailScreen
import com.auraflix.tv.ui.favorites.FavoritesScreen
import com.auraflix.tv.ui.home.HomeScreen
import com.auraflix.tv.ui.player.PlayerScreen
import com.auraflix.tv.ui.search.SearchScreen
import com.auraflix.tv.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val BROWSE = "browse/{type}"
    const val SEARCH = "search"
    const val DETAIL = "detail/{slug}"
    const val PLAYER = "player/{slug}/{server}/{episode}"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"

    fun browse(type: String) = "browse/$type"
    fun detail(slug: String) = "detail/$slug"
    fun player(slug: String, server: Int, episode: Int) = "player/$slug/$server/$episode"
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()

    // Điện thoại trong LAN chọn phim -> mở thẳng trình phát trên TV.
    LaunchedEffect(nav) {
        Graph.cast.openRequests.collect { request ->
            // Nếu TV đang mở player khác (hoặc chính phim này nhưng tập khác),
            // gỡ player hiện tại trước để ViewModel mới (theo slug) được tạo lại.
            nav.popBackStack(Routes.PLAYER, inclusive = true)
            nav.navigate(Routes.player(request.slug, request.server, request.episode))
        }
    }

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenMovie = { nav.navigate(Routes.detail(it)) },
                onOpenPlayer = { slug, server, episode ->
                    nav.navigate(Routes.player(slug, server, episode))
                },
                onBrowse = { nav.navigate(Routes.browse(it)) },
                onSearch = { nav.navigate(Routes.SEARCH) },
                onFavorites = { nav.navigate(Routes.FAVORITES) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }

        composable(
            route = Routes.BROWSE,
            arguments = listOf(navArgument("type") { type = NavType.StringType }),
        ) { entry ->
            BrowseScreen(
                initialType = entry.arguments?.getString("type") ?: "phim-moi-cap-nhat",
                onBack = { nav.popBackStack() },
                onOpenMovie = { nav.navigate(Routes.detail(it)) },
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { nav.popBackStack() },
                onOpenMovie = { nav.navigate(Routes.detail(it)) },
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("slug") { type = NavType.StringType }),
        ) { entry ->
            DetailScreen(
                slug = entry.arguments?.getString("slug").orEmpty(),
                onBack = { nav.popBackStack() },
                onOpenMovie = { nav.navigate(Routes.detail(it)) },
                onPlay = { slug, server, episode -> nav.navigate(Routes.player(slug, server, episode)) },
            )
        }

        composable(
            route = Routes.PLAYER,
            arguments = listOf(
                navArgument("slug") { type = NavType.StringType },
                navArgument("server") { type = NavType.IntType; defaultValue = 0 },
                navArgument("episode") { type = NavType.IntType; defaultValue = 0 },
            ),
        ) { entry ->
            PlayerScreen(
                slug = entry.arguments?.getString("slug").orEmpty(),
                initialServer = entry.arguments?.getInt("server") ?: 0,
                initialEpisode = entry.arguments?.getInt("episode") ?: 0,
                onBack = { nav.popBackStack() },
            )
        }

        composable(Routes.FAVORITES) {
            FavoritesScreen(
                onBack = { nav.popBackStack() },
                onOpenMovie = { nav.navigate(Routes.detail(it)) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
