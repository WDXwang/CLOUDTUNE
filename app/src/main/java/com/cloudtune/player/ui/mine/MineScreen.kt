package com.cloudtune.player.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudtune.player.ui.navigation.Routes
import com.cloudtune.player.ui.theme.NovaTheme

/** “我的”页入口。 */
private data class MineEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
)

/**
 * 我的页（汽水音乐风格）：把原先散落在底部导航的功能收进分组列表，
 * 底部只保留 首页 / 乐库 / 我的 三个主入口。
 */
@Composable
fun MineScreen(onOpenRoute: (String) -> Unit) {
    val colors = NovaTheme.colors
    val musicEntries = listOf(
        MineEntry("我的歌单", "按歌手自动归类，创建专属歌单", Icons.Filled.QueueMusic, Routes.PLAYLISTS),
        MineEntry("我的收藏", "收藏过的歌曲都在这里", Icons.Filled.Favorite, Routes.FAVORITES),
        MineEntry("本地音乐", "扫描设备中的音频文件", Icons.Filled.LibraryMusic, Routes.LOCAL),
        MineEntry("播放历史", "最近听过的歌曲", Icons.Filled.History, Routes.HISTORY),
    )
    val toolEntries = listOf(
        MineEntry("网盘管理", "WebDAV / 天翼 / 123 / 夸克 / 光鸭", Icons.Filled.Cloud, Routes.STORAGES),
        MineEntry("音源管理", "导入洛雪自定义源，获取第三方榜单", Icons.Filled.Extension, Routes.SOURCES),
        MineEntry("均衡器", "调节音效与音量增益", Icons.Filled.GraphicEq, Routes.EQUALIZER),
        MineEntry("设置", "外观、播放与账号", Icons.Filled.Settings, Routes.SETTINGS),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(28.dp))
        Text(
            text = "我的",
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.text,
            letterSpacing = (-0.8).sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "CloudTune · 多网盘聚合音乐播放器",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.sub,
        )

        Spacer(Modifier.height(24.dp))

        // 个人信息卡片（柠檬绿点缀，呼应汽水音乐）
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = colors.surface,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
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
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "CloudTune",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.text,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "网盘音乐 · 第三方音源 · 本地播放",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.sub,
                    )
                }
            }
        }

        Spacer(Modifier.height(26.dp))
        SectionTitle("我的音乐")
        Spacer(Modifier.height(10.dp))
        EntryGroup(musicEntries, onOpenRoute)

        Spacer(Modifier.height(24.dp))
        SectionTitle("资源与设置")
        Spacer(Modifier.height(10.dp))
        EntryGroup(toolEntries, onOpenRoute)

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = NovaTheme.colors.sub,
    )
}

@Composable
private fun EntryGroup(entries: List<MineEntry>, onOpenRoute: (String) -> Unit) {
    val colors = NovaTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.surface,
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            entries.forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenRoute(entry.route) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(colors.accentSoft, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(entry.icon, contentDescription = entry.title, tint = colors.accent)
                    }
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = colors.text,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = entry.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.sub,
                        )
                    }
                    Text("›", fontSize = 20.sp, color = colors.sub)
                }
            }
        }
    }
}
