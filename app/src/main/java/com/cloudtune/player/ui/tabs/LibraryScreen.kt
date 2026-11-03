package com.cloudtune.player.ui.tabs

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudtune.player.CloudTuneApp
import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.core.model.StorageAccount
import com.cloudtune.player.ui.theme.NovaTheme
import kotlinx.coroutines.flow.first

/**
 * Library Tab — 乐库主页。
 *
 * 聚合：网盘入口卡片 + 我的音乐（收藏/歌单/本地/历史）+ 设置入口。
 */
@Composable
fun LibraryScreen(
    onOpenStorages: () -> Unit,
    onOpenLocal: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as CloudTuneApp).container
    val repository = container.repository

    val accounts by produceState<List<StorageAccount>>(initialValue = emptyList()) {
        value = repository.accounts.first()
    }

    val colors = NovaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 120.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = "乐库",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.text,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "收藏 · 歌单 · 本地音乐 · 网盘",
            fontSize = 14.sp,
            color = colors.sub,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        // ── 网盘入口卡片 ──
        Spacer(Modifier.height(24.dp))
        SectionTitle("我的网盘")
        Spacer(Modifier.height(10.dp))
        StorageCardRow(
            accounts = accounts.filter { it.enabled },
            onOpenStorages = onOpenStorages,
        )

        // ── 我的音乐入口 ──
        Spacer(Modifier.height(28.dp))
        SectionTitle("我的音乐")
        Spacer(Modifier.height(10.dp))
        MusicEntryGrid(
            onOpenLocal = onOpenLocal,
            onOpenPlaylists = onOpenPlaylists,
            onOpenFavorites = onOpenFavorites,
            onOpenHistory = onOpenHistory,
        )

        // ── 工具入口 ──
        Spacer(Modifier.height(24.dp))
        SectionTitle("资源与设置")
        Spacer(Modifier.height(10.dp))
        ToolEntryRow(
            onOpenStorages = onOpenStorages,
            onOpenSources = onOpenSources,
            onOpenSettings = onOpenSettings,
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = NovaTheme.colors.sub,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

@Composable
private fun StorageCardRow(
    accounts: List<StorageAccount>,
    onOpenStorages: () -> Unit,
) {
    val colors = NovaTheme.colors
    if (accounts.isEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(20.dp),
            onClick = onOpenStorages,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Cloud,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "还没有网盘",
                        fontWeight = FontWeight.Bold,
                        color = colors.text,
                    )
                    Text(
                        text = "点击添加 WebDAV / 天翼 / 夸克 / 123",
                        fontSize = 12.sp,
                        color = colors.sub,
                    )
                }
                Text("›", fontSize = 20.sp, color = colors.sub)
            }
        }
        return
    }

    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(accounts) { account ->
            Card(
                modifier = Modifier.width(160.dp),
                shape = RoundedCornerShape(18.dp),
                onClick = onOpenStorages,
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(colors.accentSoft, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Cloud,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = account.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text,
                        maxLines = 1,
                    )
                    Text(
                        text = "网盘",
                        fontSize = 11.sp,
                        color = colors.sub,
                        maxLines = 1,
                    )
                }
            }
        }
        // 添加按钮
        item {
            Card(
                modifier = Modifier.width(120.dp),
                shape = RoundedCornerShape(18.dp),
                onClick = onOpenStorages,
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(colors.accentSoft.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "＋ 添加",
                        fontSize = 14.sp,
                        color = colors.accent,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun MusicEntryGrid(
    onOpenLocal: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val colors = NovaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MusicEntryCard(
            icon = Icons.Filled.LibraryMusic,
            label = "本地音乐",
            modifier = Modifier.weight(1f),
            colors = colors,
            onClick = onOpenLocal,
        )
        MusicEntryCard(
            icon = Icons.Filled.QueueMusic,
            label = "我的歌单",
            modifier = Modifier.weight(1f),
            colors = colors,
            onClick = onOpenPlaylists,
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MusicEntryCard(
            icon = Icons.Filled.Favorite,
            label = "我的收藏",
            modifier = Modifier.weight(1f),
            colors = colors,
            onClick = onOpenFavorites,
        )
        MusicEntryCard(
            icon = Icons.Filled.History,
            label = "播放历史",
            modifier = Modifier.weight(1f),
            colors = colors,
            onClick = onOpenHistory,
        )
    }
}

@Composable
private fun MusicEntryCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    colors: com.cloudtune.player.ui.theme.NovaPalette,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(colors.accentSoft, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = label, tint = colors.accent)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
            )
        }
    }
}

@Composable
private fun ToolEntryRow(
    onOpenStorages: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = NovaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ToolEntryCard(
            icon = Icons.Filled.Cloud,
            label = "网盘管理",
            modifier = Modifier.weight(1f),
            colors = colors,
            onClick = onOpenStorages,
        )
        ToolEntryCard(
            icon = Icons.Filled.LibraryMusic,
            label = "音源管理",
            modifier = Modifier.weight(1f),
            colors = colors,
            onClick = onOpenSources,
        )
        ToolEntryCard(
            icon = Icons.Filled.Favorite,
            label = "设置",
            modifier = Modifier.weight(1f),
            colors = colors,
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun ToolEntryCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    colors: com.cloudtune.player.ui.theme.NovaPalette,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(14.dp),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = label, tint = colors.accent)
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.text,
            )
        }
    }
}
