package com.cloudtune.player.ui.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudtune.player.core.model.MusicTrack
import com.cloudtune.player.core.util.MusicUtils

/**
 * 「我的收藏」界面：展示本地收藏的音乐，点击播放并跳转全屏播放器。
 *
 * @param onOpenPlayer 点击播放后跳转全屏播放器
 */
@Composable
fun FavoritesScreen(
    onOpenPlayer: () -> Unit,
    vm: FavoritesViewModel = viewModel(),
) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部标题行 + 「全部播放」
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "我的收藏",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            // 全部播放：从第一首开始播放整个收藏列表
            TextButton(
                onClick = {
                    if (favorites.isNotEmpty()) {
                        vm.playAt(favorites, 0)
                        onOpenPlayer()
                    }
                },
                enabled = favorites.isNotEmpty(),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.size(4.dp))
                Text("全部播放")
            }
        }

        HorizontalDivider()

        if (favorites.isEmpty()) {
            // 空状态
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "还没有收藏\n浏览音乐时点星标即可收藏到这里",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        // 收藏列表
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(favorites, key = { it.file.id }) { track ->
                FavoriteRow(
                    track = track,
                    onClick = {
                        // 点击该首：定位到它在列表中的位置播放
                        val index = favorites.indexOf(track).coerceAtLeast(0)
                        vm.playAt(favorites, index)
                        onOpenPlayer()
                    },
                    onRemove = { vm.toggleFavorite(track) },
                )
                HorizontalDivider()
            }
        }
    }
}

/** 单行收藏曲目 */
@Composable
private fun FavoriteRow(
    track: MusicTrack,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // 标题优先用解析结果，回退为文件名
            Text(
                text = track.title.ifBlank { MusicUtils.baseName(track.file.name) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = track.artist?.takeIf { it.isNotBlank() } ?: track.file.path
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 实心星标：再次点击取消收藏
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = "取消收藏",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
