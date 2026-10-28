package com.cloudtune.player.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudtune.player.CloudTuneApp
import com.cloudtune.player.player.PlayerControllerManager

/** 全屏居中加载圈 */
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * 错误 / 空态提示：居中显示图标与文案，可选一个操作按钮（如“重试”）。
 */
@Composable
fun MessageView(
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!actionText.isNullOrEmpty() && onAction != null) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onAction) { Text(actionText) }
            }
        }
    }
}

/**
 * 全局迷你播放条：固定在底部导航上方。
 *
 * 无播放项时不占用空间（返回空 Box）；有播放项时显示标题、
 * 播放/暂停、上一首/下一首；点击整条进入正在播放页。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MiniPlayer(
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 取应用容器中的播放器管理器
    val container = (LocalContext.current.applicationContext as CloudTuneApp).container
    val player: PlayerControllerManager = container.playerManager
    val nowPlaying by player.nowPlaying.collectAsStateWithLifecycle()

    // 无有效曲目时不渲染
    if (nowPlaying.mediaId.isNullOrBlank()) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 3.dp,
        onClick = onOpenPlayer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            // 标题（单行省略），副标题为艺术家
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nowPlaying.title ?: "未知曲目",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val artist = nowPlaying.artist
                if (!artist.isNullOrBlank()) {
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // 上一首
            IconButton(onClick = { player.previous() }) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "上一首",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            // 播放 / 暂停
            IconButton(onClick = { player.togglePlay() }) {
                Icon(
                    imageVector = if (nowPlaying.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (nowPlaying.isPlaying) "暂停" else "播放",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            // 下一首
            IconButton(onClick = { player.next() }) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "下一首",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
