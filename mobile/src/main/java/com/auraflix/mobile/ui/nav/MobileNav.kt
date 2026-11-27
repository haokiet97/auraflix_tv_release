package com.auraflix.mobile.ui.nav

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.auraflix.mobile.MobileGraph
import com.auraflix.mobile.ui.browse.BrowseScreen
import com.auraflix.mobile.ui.cast.ConnectTvScreen
import com.auraflix.mobile.ui.cast.RemoteScreen
import com.auraflix.mobile.ui.cast.TvMiniBar
import com.auraflix.mobile.ui.detail.DetailScreen
import com.auraflix.mobile.ui.favorites.FavoritesScreen
import com.auraflix.mobile.ui.home.HomeScreen
import com.auraflix.mobile.ui.player.PlayerScreen
import com.auraflix.mobile.ui.search.SearchScreen
import com.auraflix.mobile.ui.settings.SettingsScreen
import com.auraflix.mobile.ui.theme.AuraColors

object MobileRoutes {
    const val HOME = "home"
    const val BROWSE = "browse?type={type}"
    const val SEARCH = "search"
    const val DETAIL = "detail/{slug}"
    const val PLAYER = "player/{slug}/{server}/{episode}"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val CONNECT_TV = "connect-tv"
    const val REMOTE = "remote"

    fun browse(type: String) = "browse?type=$type"
    fun detail(slug: String) = "detail/${Uri.encode(slug)}"
    fun player(slug: String, server: Int, episode: Int) =
        "player/${Uri.encode(slug)}/$server/$episode"
}

private data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val bottomTabs = listOf(
    BottomTab(MobileRoutes.HOME, "Trang chủ", Icons.Filled.Home),
    BottomTab(MobileRoutes.browse("phim-moi-cap-nhat"), "Danh mục", Icons.Filled.GridView),
    BottomTab(MobileRoutes.FAVORITES, "Yêu thích", Icons.Filled.Favorite),
    BottomTab(MobileRoutes.SETTINGS, "Cài đặt", Icons.Filled.Settings),
)

@Composable
fun MobileNavHost() {
    val nav = rememberNavController()
    val remote = MobileGraph.tvRemote
    val remoteState by remote.state.collectAsStateWithLifecycle()
    // Theo dõi TV: cập nhật thanh mini và trạng thái khi đang duyệt phim.
    LaunchedEffect(remote) { remote.startPolling() }

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val isBrowse = currentRoute?.startsWith("browse") == true
    val showBottomBar = currentRoute == MobileRoutes.HOME ||
        isBrowse ||
        currentRoute == MobileRoutes.FAVORITES ||
        currentRoute == MobileRoutes.SETTINGS

    Scaffold(
        containerColor = AuraColors.Background,
        bottomBar = {
            if (showBottomBar) {
                Column {
                    TvMiniBar(
                        playback = remoteState.playback,
                        isPaired = remoteState.tv != null,
                        onOpenRemote = {
                            nav.navigate(MobileRoutes.REMOTE) { launchSingleTop = true }
                        },
                        onTogglePlay = remote::togglePlay,
                    )
                    MobileBottomBar(
                        currentRoute = currentRoute,
                        isBrowse = isBrowse,
                        onSelect = { route -> nav.navigateTopLevel(route) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = MobileRoutes.HOME,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(MobileRoutes.HOME) {
                HomeScreen(
                    modifier = Modifier.padding(padding),
                    onOpenMovie = { nav.navigate(MobileRoutes.detail(it)) },
                    onOpenPlayer = { slug, server, episode ->
                        nav.navigate(MobileRoutes.player(slug, server, episode))
                    },
                    onBrowse = { nav.navigate(MobileRoutes.browse(it)) },
                    onSearch = { nav.navigate(MobileRoutes.SEARCH) },
                    onOpenTv = {
                        nav.navigate(
                            if (remoteState.tv == null) MobileRoutes.CONNECT_TV else MobileRoutes.REMOTE,
                        ) { launchSingleTop = true }
                    },
                )
            }

            composable(
                route = MobileRoutes.BROWSE,
                arguments = listOf(
                    navArgument("type") {
                        type = NavType.StringType
                        defaultValue = "phim-moi-cap-nhat"
                    },
                ),
            ) { entry ->
                BrowseScreen(
                    initialType = entry.arguments?.getString("type") ?: "phim-moi-cap-nhat",
                    modifier = Modifier.padding(padding),
                    onOpenMovie = { nav.navigate(MobileRoutes.detail(it)) },
                )
            }

            composable(MobileRoutes.SEARCH) {
                SearchScreen(
                    onBack = { nav.popBackStack() },
                    onOpenMovie = { nav.navigate(MobileRoutes.detail(it)) },
                )
            }

            composable(
                route = MobileRoutes.DETAIL,
                arguments = listOf(navArgument("slug") { type = NavType.StringType }),
            ) { entry ->
                DetailScreen(
                    slug = entry.arguments?.getString("slug").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpenMovie = { nav.navigate(MobileRoutes.detail(it)) },
                    onPlay = { slug, server, episode ->
                        nav.navigate(MobileRoutes.player(slug, server, episode))
                    },
                    onPlayOnTv = { slug, server, episode ->
                        if (remoteState.tv == null) {
                            nav.navigate(MobileRoutes.CONNECT_TV)
                        } else {
                            nav.navigate(MobileRoutes.REMOTE) { launchSingleTop = true }
                        }
                    },
                )
            }

            composable(
                route = MobileRoutes.PLAYER,
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

            composable(MobileRoutes.FAVORITES) {
                FavoritesScreen(
                    modifier = Modifier.padding(padding),
                    onOpenMovie = { nav.navigate(MobileRoutes.detail(it)) },
                )
            }

            composable(MobileRoutes.SETTINGS) {
                SettingsScreen(
                    modifier = Modifier.padding(padding),
                    onOpenTv = { nav.navigate(MobileRoutes.REMOTE) { launchSingleTop = true } },
                    onConnectTv = { nav.navigate(MobileRoutes.CONNECT_TV) },
                )
            }

            composable(MobileRoutes.CONNECT_TV) {
                ConnectTvScreen(
                    onBack = { nav.popBackStack() },
                    onConnected = {
                        nav.navigate(MobileRoutes.REMOTE) {
                            popUpTo(MobileRoutes.REMOTE) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(MobileRoutes.REMOTE) {
                RemoteScreen(
                    onBack = { nav.popBackStack() },
                    onConnectTv = { nav.navigate(MobileRoutes.CONNECT_TV) },
                )
            }
        }
    }
}

@Composable
private fun MobileBottomBar(
    currentRoute: String?,
    isBrowse: Boolean,
    onSelect: (String) -> Unit,
) {
    NavigationBar(
        containerColor = AuraColors.BackgroundElevated,
        tonalElevation = 0.dp,
    ) {
        bottomTabs.forEach { tab ->
            val selected = when (tab.route) {
                MobileRoutes.HOME -> currentRoute == MobileRoutes.HOME
                MobileRoutes.FAVORITES -> currentRoute == MobileRoutes.FAVORITES
                MobileRoutes.SETTINGS -> currentRoute == MobileRoutes.SETTINGS
                else -> isBrowse
            }
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab.route) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                    )
                },
                label = { Text(tab.label, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AuraColors.Accent,
                    selectedTextColor = AuraColors.Accent,
                    indicatorColor = AuraColors.Accent.copy(alpha = 0.16f),
                    unselectedIconColor = AuraColors.TextMuted,
                    unselectedTextColor = AuraColors.TextMuted,
                ),
            )
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
