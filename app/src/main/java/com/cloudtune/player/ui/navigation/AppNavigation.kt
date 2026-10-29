package com.cloudtune.player.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudtune.player.CloudTuneApp
import com.cloudtune.player.ui.browser.BrowserScreen
import com.cloudtune.player.ui.components.LoadingBox
import com.cloudtune.player.ui.components.MiniPlayer
import com.cloudtune.player.ui.equalizer.EqualizerScreen
import com.cloudtune.player.ui.favorites.FavoritesScreen
import com.cloudtune.player.ui.history.HistoryScreen
import com.cloudtune.player.ui.local.LocalScreen
import com.cloudtune.player.ui.lyrics.LyricsScreen
import com.cloudtune.player.ui.setup.SetupViewModel
import com.cloudtune.player.ui.player.PlayerScreen
import com.cloudtune.player.ui.playlists.PlaylistDetailScreen
import com.cloudtune.player.ui.playlists.PlaylistsScreen
import com.cloudtune.player.ui.queue.QueueScreen
import com.cloudtune.player.ui.settings.SettingsScreen
import com.cloudtune.player.ui.setup.SetupScreen
import com.cloudtune.player.ui.setup.TianyiQrLoginScreen
import com.cloudtune.player.ui.setup.TianyiWebLoginScreen
import kotlinx.coroutines.flow.first

/** 路由表 */
object Routes {
    const val SETUP = "setup"
    const val TIANYI_WEB_LOGIN = "tianyi_web_login"
    const val TIANYI_QR_LOGIN = "tianyi_qr_login"
    const val BROWSER = "browser"
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
}

/** 顶部壳层：启动时读取一次已保存配置决定起始页 */
@Composable
fun AppNavigation() {
    val container = (LocalContext.current.applicationContext as CloudTuneApp).container
    val navController = rememberNavController()

    // 读取配置首值：未配置 → setup；已配置 → 主界面(browser)
    val startRoute by produceState<String?>(initialValue = null) {
        value = if (container.repository.config.first() == null) Routes.SETUP else Routes.BROWSER
    }
    if (startRoute == null) {
        LoadingBox()
        return
    }

    // 当前路由，用于决定是否显示底部导航
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in setOf(
        Routes.BROWSER, Routes.LOCAL, Routes.PLAYLISTS, Routes.FAVORITES, Routes.SETTINGS
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Column {
                    // 全局迷你播放条（有播放项时才显示，否则占位为空）
                    MiniPlayer(onOpenPlayer = { navController.navigate(Routes.PLAYER) })
                    ShellBottomBar(navController, currentRoute)
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
            // 首次配置：完成后进入主界面并清掉 setup 返回栈
            composable(Routes.SETUP) {
                SetupScreen(
                    onConfigured = {
                        navController.navigate(Routes.BROWSER) {
                            popUpTo(Routes.SETUP) { inclusive = true }
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
            // 天翼云盘扫码登录（全屏，原生二维码）
            composable(Routes.TIANYI_QR_LOGIN) {
                val previous = navController.previousBackStackEntry
                // 直接取到设置页的 SetupViewModel，登录成功后同步回填 Cookie，
                // 不依赖 SavedStateHandle 的异步事件（更可靠）
                val setupVm: SetupViewModel? =
                    previous?.let { viewModel(viewModelStoreOwner = it) }
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
            // 天翼云盘手机 Web 登录（全屏）
            composable(Routes.TIANYI_WEB_LOGIN) {
                // 登录成功回填 Cookie 后返回设置页
                val previous = navController.previousBackStackEntry
                val setupVm: SetupViewModel? =
                    previous?.let { viewModel(viewModelStoreOwner = it) }
                TianyiWebLoginScreen(
                    onBack = { navController.popBackStack() },
                    onLoggedIn = { cookie ->
                        setupVm?.applyWebLoginCookie(cookie)
                        navController.popBackStack()
                    },
                )
            }
            // 文件
            composable(Routes.BROWSER) {
                BrowserScreen(onOpenPlayer = { navController.navigate(Routes.PLAYER) })
            }
            // 本地音乐
            composable(Routes.LOCAL) {
                LocalScreen(onOpenPlayer = { navController.navigate(Routes.PLAYER) })
            }
            // 歌单
            composable(Routes.PLAYLISTS) {
                PlaylistsScreen(onOpenPlaylist = { id, name ->
                    val encoded = java.net.URLEncoder.encode(name, "UTF-8")
                    navController.navigate("playlist/$id?name=$encoded")
                })
            }
            // 歌单详情（全屏）
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
            // 收藏
            composable(Routes.FAVORITES) {
                FavoritesScreen(onOpenPlayer = { navController.navigate(Routes.PLAYER) })
            }
            // 设置
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenEqualizer = { navController.navigate(Routes.EQUALIZER) },
                    onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    onLoggedOut = {
                        // 退出登录后回到配置页，清空整个返回栈
                        navController.navigate(Routes.SETUP) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    },
                )
            }
            // 均衡器（全屏）
            composable(Routes.EQUALIZER) {
                EqualizerScreen(onBack = { navController.popBackStack() })
            }
            // 正在播放（全屏，无底部导航）
            composable(Routes.PLAYER) {
                PlayerScreen(
                    onBack = { navController.popBackStack() },
                    onOpenQueue = { navController.navigate(Routes.QUEUE) },
                    onOpenLyrics = { navController.navigate(Routes.LYRICS) },
                )
            }
            // 播放队列（全屏）
            composable(Routes.QUEUE) {
                QueueScreen(onBack = { navController.popBackStack() })
            }
            // 歌词（全屏）
            composable(Routes.LYRICS) {
                LyricsScreen(onBack = { navController.popBackStack() })
            }
            // 历史播放（全屏）
            composable(Routes.HISTORY) {
                HistoryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                )
            }
        }
    }
}

/** 底部导航栏：文件 / 收藏 / 设置 */
@Composable
private fun ShellBottomBar(navController: NavHostController, currentRoute: String?) {
    val items = listOf(
        Triple(Routes.BROWSER, "文件", Icons.Filled.Folder),
        Triple(Routes.LOCAL, "本地", Icons.Filled.LibraryMusic),
        Triple(Routes.PLAYLISTS, "歌单", Icons.Filled.QueueMusic),
        Triple(Routes.FAVORITES, "收藏", Icons.Filled.Favorite),
        Triple(Routes.SETTINGS, "设置", Icons.Filled.Settings),
    )
    NavigationBar {
        items.forEach { (route, label, icon) ->
            NavigationBarItem(
                selected = currentRoute == route,
                onClick = {
                    if (currentRoute != route) {
                        navController.navigate(route) {
                            // 避免重复入栈，回到起始目的地并销毁其上的页面
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
            )
        }
    }
}
