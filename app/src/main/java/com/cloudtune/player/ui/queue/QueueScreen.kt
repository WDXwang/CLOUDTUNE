package com.cloudtune.player.ui.queue

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudtune.player.CloudTuneApp

/**
 * 播放队列界面：展示当前播放列表，支持点击跳转、删除、上下移动排序。
 *
 * @param onBack 返回全屏播放器
 */
@Composable
fun QueueScreen(onBack: () -> Unit) {
    // 手动 DI：直接从 Application 容器取播放器控制器
    val playerManager =
        (LocalContext.current.applicationContext as CloudTuneApp).container.playerManager

    // 订阅队列状态
    val queue by playerManager.queue.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "返回")
            }
            Text(
                text = "播放队列（${queue.size}）",
                style = MaterialTheme.typography.titleLarge,
            )
        }

        HorizontalDivider()

        if (queue.isEmpty()) {
            // 空队列提示
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "队列为空，去浏览音乐并播放吧",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        // 队列列表
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(queue, key = { index, item -> "${item.mediaId}_$index" }) { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 当前播放项高亮背景
                        .background(
                            if (item.isCurrent)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .clip(RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 曲目标题：点击跳转到该曲播放
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                // 点击队列项：跳转播放该项
                                playerManager.playQueueItem(index)
                            }
                            .padding(vertical = 6.dp),
                        color = if (item.isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )

                    // 上移
                    IconButton(
                        onClick = { playerManager.moveQueueItem(index, index - 1) },
                        enabled = index > 0,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移")
                    }
                    // 下移
                    IconButton(
                        onClick = { playerManager.moveQueueItem(index, index + 1) },
                        enabled = index < queue.lastIndex,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移")
                    }
                    // 删除
                    IconButton(
                        onClick = { playerManager.removeQueueItem(index) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "移除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
