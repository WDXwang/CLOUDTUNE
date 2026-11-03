package com.cloudtune.player.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cloudtune.player.CloudTuneApp
import com.cloudtune.player.ui.components.LoadingBox
import com.cloudtune.player.ui.components.MiniPlayer
import com.cloudtune.player.ui.components.SideDrawer
import com.cloudtune.player.ui.equalizer.EqualizerScreen
import com.cloudtune.player.ui.favorites.FavoritesScreen
import com.cloudtune.player.ui.history.HistoryScreen
import com.cloudtune.player.ui.local.LocalScreen
import com.cloudtune.player.ui.lyrics.LyricsScreen
import com.cloudtune.player.ui.player.PlayerScreen
import com.cloudtune.player.ui.playlists.PlaylistDetailScreen
import com.cloudtune.player.ui.playlists.PlaylistsScreen
import com.cloudtune.player.ui.queue.QueueScreen
import com.cloudtune.player.ui.settings.SettingsScreen
import com.cloudtune.player.ui.sources.SourcesScreen
import com.cloudtune.player.ui.setup.SetupScreen
import com.cloudtune.player.ui.setup.SetupViewModel
import com.cloudtune.player.ui.setup.TianyiQrLoginScreen
import com.cloudtune.player.ui.setup.TianyiWebLoginScreen
import com.cloudtune.player.ui.storages.StoragesScreen
import com.cloudtune.player.ui.tabs.ExploreScreen
import com.cloudtune.player.ui.tabs.HomeScreen
import com.cloudtune.player.ui.tabs.LibraryScreen
import com.cloudtune.player.ui.tabs.SearchScreen
import kotlinx.coroutines.flow.first

/** 路由表 */
object Routes {
    // 四 Tab 主入口
    const val HOME = "home"
    const val EXPLORE = "explore"
    const val LIBRARY = "library"
    const val SEARCH = "search"

    // 全屏页面
    const val SETUP = "setup"
    const val TIANYI_WEB_LOGIN = "tianyi_web_login"
    const val TIANYI_QR_LOGIN = "tianyi_qr_login"
    const val STORAGES = "storages"
    const val LOCAL = "local"
    const val PLAYLISTS = "playlists"
    const val PLAYLIST_DETAIL = "playlist/{playlistId}?name={name}"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val PLAYER = "player"
    const val QUEUE = "queue"
    const val EQUALIZER = "equalizer"
    const val LYRICS = "lyrics"
    const val HISTORY = "history"
    const val SOURCES = "sources"
}

/** 顶部壳层：启动时读取一次已保存配置决定起始页 */
@Composable
fun AppNavigation() {
    val container = (LocalContext.current.applicationContext as CloudTuneApp).container
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // 读取存储列表首值：未添加任何网盘 → setup；否则 → Home Tab
    val startRoute by produceState<String?>(initialValue = null) {
        value = if (container.repository.accounts.first().isEmpty()) Routes.SETUP else Routes.HOME
    }
    if (startRoute == null) {
        LoadingBox()
        return
    }

    // 统一的底部导航切换（保持 Tab 状态）
    val navTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // 当前路由，用于决定是否显示底部导航
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tabRoutes = setOf(Routes.HOME, Routes.EXPLORE, Routes.LIBRARY, Routes.SEARCH)
    val showBottomBar = currentRoute in tabRoutes

    SideDrawer(
        drawerState = drawerState,
        onNavigate = { route ->
            navController.navigate(route) { launchSingleTop = true }
        },
    ) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    Column {
                        // 全局迷你播放条（有播放项时才显示）
                        MiniPlayer(onOpenPlayer = { navController.navigate(Routes.PLAYER) })
                        ShellBottomBar(currentRoute, navTo)
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = startRoute!!,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                // ─── 四 Tab 主入口 ───
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                    )
                }
                composable(Routes.EXPLORE) {
                    ExploreScreen(
                        onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                    )
                }
                composable(Routes.LIBRARY) {
                    LibraryScreen(
                        onOpenStorages = { navController.navigate(Routes.STORAGES) },
                        onOpenLocal = { navController.navigate(Routes.LOCAL) },
                        onOpenPlaylists = { navController.navigate(Routes.PLAYLISTS) },
                        onOpenFavorites = { navController.navigate(Routes.FAVORITES) },
                        onOpenHistory = { navController.navigate(Routes.HISTORY) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onOpenSources = { navController.navigate(Routes.SOURCES) },
                        onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                    )
                }
                composable(Routes.SEARCH) {
                    SearchScreen(
                        onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                    )
                }

                // ─── 全屏页面（不显示底部导航） ───
                composable(Routes.SETUP) {
                    SetupScreen(
                        onConfigured = {
                            if (navController.previousBackStackEntry != null) {
                                navController.popBackStack()
                            } else {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.SETUP) { inclusive = true }
                                }
                            }
                        },
                        onOpenTianyiWebLogin = {
                            navController.navigate(Routes.TIANYI_WEB_LOGIN)
                        },
                        onOpenTianyiQrLogin = {
                            navController.navigate(Routes.TIANYI_QR_LOGIN)
                        },
                    )
                }
                composable(Routes.TIANYI_QR_LOGIN) {
                    val previous = navController.previousBackStackEntry
                    val setupVm: SetupViewModel? =
                        previous?.let { androidx.lifecycle.viewmodel.compose.viewModel(viewModelStoreOwner = it) }
                    TianyiQrLoginScreen(
                        onBack = { navController.popBackStack() },
                        onLoggedIn = { result ->
                            result.sessionKey?.let { sk ->
                                setupVm?.applyWebLoginSession(
                                    sessionKey = sk,
                                    sessionSecret = result.sessionSecret.orEmpty(),
                                    accessToken = result.accessToken.orEmpty(),
                                    refreshToken = result.refreshToken.orEmpty(),
                                )
                            } ?: result.cookie?.let { setupVm?.applyWebLoginCookie(it) }
                            navController.popBackStack()
                        },
                    )
                }
                composable(Routes.TIANYI_WEB_LOGIN) {
                    val previous = navController.previousBackStackEntry
                    val setupVm: SetupViewModel? =
                        previous?.let { androidx.lifecycle.viewmodel.compose.viewModel(viewModelStoreOwner = it) }
                    TianyiWebLoginScreen(
                        onBack = { navController.popBackStack() },
                        onLoggedIn = { cookie ->
                            setupVm?.applyWebLoginCookie(cookie)
                            navController.popBackStack()
                        },
                    )
                }
                composable(Routes.SOURCES) {
                    SourcesScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                    )
                }
                composable(Routes.STORAGES) {
                    StoragesScreen(
                        onBack = { navController.popBackStack() },
                        onAdd = { navController.navigate(Routes.SETUP) },
                    )
                }
                composable(Routes.LOCAL) {
                    LocalScreen(onOpenPlayer = { navController.navigate(Routes.PLAYER) })
                }
                composable(Routes.PLAYLISTS) {
                    PlaylistsScreen(onOpenPlaylist = { id, name ->
                        val encoded = java.net.URLEncoder.encode(name, "UTF-8")
                        navController.navigate("playlist/$id?name=$encoded")
                    })
                }
                composable(
                    route = Routes.PLAYLIST_DETAIL,
                    arguments = listOf(
                        navArgument("playlistId") { type = NavType.LongType },
                        navArgument("name") { type = NavType.StringType },
                    ),
                ) { entry ->
                    val id = entry.arguments?.getLong("playlistId") ?: -1L
                    val name = entry.arguments?.getString("name") ?: "歌单"
                    PlaylistDetailScreen(
                        onBack = { navController.popBackStack() },
                        onDeleted = { navController.popBackStack() },
                        playlistName = name,
                    )
                }
                composable(Routes.FAVORITES) {
                    FavoritesScreen(onOpenPlayer = { navController.navigate(Routes.PLAYER) })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenStorages = { navController.navigate(Routes.STORAGES) },
                        onOpenEqualizer = { navController.navigate(Routes.EQUALIZER) },
                        onOpenHistory = { navController.navigate(Routes.HISTORY) },
                        onLoggedOut = {
                            navController.navigate(Routes.SETUP) {
                                popUpTo(navController.graph.id) { inclusive = true }
                            }
                        },
                    )
                }
                composable(Routes.EQUALIZER) {
                    EqualizerScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.PLAYER) {
                    PlayerScreen(
                        onNavigate = navTo,
                        onOpenLyrics = { navController.navigate(Routes.LYRICS) },
                    )
                }
                composable(Routes.QUEUE) {
                    QueueScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.LYRICS) {
                    LyricsScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                    )
                }
            }
        }
    }
}

/** 底部导航栏：Home / Explore / Library / Search 四个主入口 */
@Composable
fun ShellBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    val items = listOf(
        Triple(Routes.HOME, "首页", Icons.Filled.Home),
        Triple(Routes.EXPLORE, "探索", Icons.Filled.Explore),
        Triple(Routes.LIBRARY, "乐库", Icons.Filled.LibraryMusic),
        Triple(Routes.SEARCH, "搜索", Icons.Filled.Search),
    )
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        items.forEach { (route, label, icon) ->
            NavigationBarItem(
                selected = currentRoute == route,
                onClick = {
                    if (currentRoute != route) {
                        onNavigate(route)
                    }
                },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
