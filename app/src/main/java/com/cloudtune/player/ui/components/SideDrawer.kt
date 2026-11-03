package com.cloudtune.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudtune.player.ui.navigation.Routes
import com.cloudtune.player.ui.theme.NovaTheme
import kotlinx.coroutines.launch

/** 侧边栏菜单项 */
private data class DrawerItem(
    val title: String,
    val icon: ImageVector,
    val route: String,
)

/**
 * 左侧可折叠遮罩式侧边栏（自定义实现，支持动画展开收起）。
 * 展开时显示完整菜单，收起时隐藏；点击遮罩或菜单项后自动收起。
 *
 * @param drawerState 由宿主控制的抽屉状态；默认用 rememberDrawerState(DrawerValue.Closed)
 * @param onNavigate 点击菜单项时调用的路由跳转回调
 * @param content 主内容（被抽屉覆盖的页面）
 */
@Composable
fun SideDrawer(
    drawerState: DrawerState = rememberDrawerState(DrawerValue.Closed),
    onNavigate: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val colors = NovaTheme.colors
    val isOpen = drawerState.isOpen

    val mainItems = listOf(
        DrawerItem("我的歌单", Icons.Filled.QueueMusic, Routes.PLAYLISTS),
        DrawerItem("我的收藏", Icons.Filled.Favorite, Routes.FAVORITES),
        DrawerItem("本地音乐", Icons.Filled.LibraryMusic, Routes.LOCAL),
        DrawerItem("播放历史", Icons.Filled.History, Routes.HISTORY),
    )
    val toolItems = listOf(
        DrawerItem("设置", Icons.Filled.Settings, Routes.SETTINGS),
    )

    Box(Modifier.fillMaxSize()) {
        // 主内容
        content()

        // 遮罩（点击关闭）
        androidx.compose.animation.AnimatedVisibility(
            visible = isOpen,
            enter = androidx.compose.animation.fadeIn(animationSpec = tween(200)),
            exit = androidx.compose.animation.fadeOut(animationSpec = tween(200)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { scope.launch { drawerState.close() } },
            )
        }

        // 侧边栏
        AnimatedVisibility(
            visible = isOpen,
            enter = slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(250)),
            exit = slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(250)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(280.dp)
                    .background(colors.bg)
                    .padding(top = 32.dp, start = 24.dp, end = 24.dp),
            ) {
                // 顶部用户卡片
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                Brush.linearGradient(listOf(colors.accent, colors.accent2)),
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "C",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.onAccent,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "CloudTune",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.text,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "多网盘聚合音乐播放器",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.sub,
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
                Text(
                    text = "音乐",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.sub,
                )
                Spacer(Modifier.height(8.dp))
                DrawerItemGroup(items = mainItems, colors = colors) { item ->
                    scope.launch { drawerState.close() }
                    onNavigate(item.route)
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.sub,
                )
                Spacer(Modifier.height(8.dp))
                DrawerItemGroup(items = toolItems, colors = colors) { item ->
                    scope.launch { drawerState.close() }
                    onNavigate(item.route)
                }

                Spacer(Modifier.weight(1f))
                Text(
                    text = "v1.8.7",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.sub,
                )
            }
        }
    }
}

/** 抽屉菜单项分组 */
@Composable
private fun DrawerItemGroup(
    items: List<DrawerItem>,
    colors: com.cloudtune.player.ui.theme.NovaPalette,
    onClick: (DrawerItem) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(item) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(colors.accentSoft, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(item.icon, contentDescription = item.title, tint = colors.accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = colors.text,
                )
            }
        }
    }
}
