package com.cloudtune.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudtune.player.core.util.MusicUtils
import com.cloudtune.player.ui.theme.GradientOrange
import com.cloudtune.player.ui.theme.GradientPurple
import com.cloudtune.player.ui.theme.GradientRed

/**
 * 全屏播放器界面（Apple Music 风格）。
 *
 * @param onBack      返回上一页
 * @param onOpenQueue 打开播放队列
 */
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit = {},
    vm: PlayerViewModel = viewModel(),
) {
    // 订阅播放器状态
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val positionMs by vm.positionFlow().collectAsStateWithLifecycle(0L)

    // 进度条拖动中的临时值；非拖动时由播放位置驱动
    var dragValue by remember { mutableStateOf<Float?>(null) }

    val durationMs = nowPlaying.durationMs.coerceAtLeast(0L)
    // 计算 Slider 取值（0f~1f）
    val sliderValue = when {
        durationMs <= 0L -> 0f
        dragValue != null -> dragValue!!
        else -> (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        GradientRed.copy(alpha = 0.12f),
                        Color.Transparent,
                        Color.Transparent,
                    )
                )
            )
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 顶部栏：下拉箭头 + 正在播放 + 队列入口（Apple 风）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "收起")
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "正在播放",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenLyrics) {
                Icon(Icons.Filled.Subtitles, contentDescription = "歌词")
            }
            IconButton(onClick = onOpenQueue) {
                Icon(Icons.Filled.QueueMusic, contentDescription = "播放队列")
            }
        }

        Spacer(Modifier.weight(1f))

        // 封面占位：Apple 风渐变 + 音符（大圆角方形）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(GradientPurple, GradientRed, GradientOrange)
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = Color.White.copy(alpha = 0.85f),
            )
        }

        Spacer(Modifier.height(32.dp))

        // 标题与艺术家（居中）
        Text(
            text = nowPlaying.title.ifBlank { "未知曲目" },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = nowPlaying.artist?.ifBlank { null } ?: "未知艺术家",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        // 倍速调节（下拉菜单）
        val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        var speedMenuOpen by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { speedMenuOpen = true }) {
                Text(
                    text = "${trimSpeed(nowPlaying.playbackSpeed)}x",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (nowPlaying.playbackSpeed != 1f) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = speedMenuOpen,
                onDismissRequest = { speedMenuOpen = false },
            ) {
                speedOptions.forEach { s ->
                    DropdownMenuItem(
                        text = { Text("${trimSpeed(s)}x") },
                        onClick = {
                            vm.setPlaybackSpeed(s)
                            speedMenuOpen = false
                        },
                        trailingIcon = {
                            if (s == nowPlaying.playbackSpeed) {
                                Text("•", color = MaterialTheme.colorScheme.primary)
                            }
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 进度条（红色调）
        Slider(
            value = sliderValue,
            onValueChange = { dragValue = it },
            onValueChangeFinished = {
                dragValue?.let { vm.seekTo((it * durationMs).toLong()) }
                dragValue = null
            },
            enabled = durationMs > 0L,
        )
        // 时间行：当前位置 / 总时长
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = MusicUtils.formatDuration(if (dragValue != null) (dragValue!! * durationMs).toLong() else positionMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = MusicUtils.formatDuration(durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))

        // 播放控制行：随机 / 上一首 / 播放暂停 / 下一首 / 循环模式
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 随机播放开关
            IconButton(onClick = { vm.toggleShuffle() }) {
                Icon(
                    imageVector = Icons.Filled.Shuffle,
                    contentDescription = "随机播放",
                    tint = if (nowPlaying.shuffleEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(onClick = { vm.previous() }) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "上一首",
                    modifier = Modifier.size(40.dp),
                )
            }

            // 播放 / 暂停主按钮（Apple 风大圆钮）
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(76.dp),
                onClick = { vm.togglePlay() },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (nowPlaying.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (nowPlaying.isPlaying) "暂停" else "播放",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }

            IconButton(onClick = { vm.next() }) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "下一首",
                    modifier = Modifier.size(40.dp),
                )
            }

            // 循环模式：0 顺序 / 2 列表循环 / 1 单曲循环（与 ExoPlayer repeatMode 对齐）
            IconButton(onClick = { vm.cycleRepeatMode() }) {
                val (icon, desc, active) = when (nowPlaying.repeatMode) {
                    1 -> Triple(Icons.Filled.RepeatOne, "单曲循环", true)
                    2 -> Triple(Icons.Filled.Repeat, "列表循环", true)
                    else -> Triple(Icons.Filled.Repeat, "顺序播放", false)
                }
                Icon(
                    imageVector = icon,
                    contentDescription = desc,
                    tint = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // 音量调节（0~200%，100% 为正常，>100% 为增益放大）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.VolumeUp,
                contentDescription = "音量",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = nowPlaying.volume.coerceIn(0f, 2f),
                onValueChange = { vm.setVolume(it) },
                valueRange = 0f..2f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            Text(
                text = "${(nowPlaying.volume * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = if (nowPlaying.volume > 1f) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(44.dp),
            )
        }

        Spacer(Modifier.weight(1f))
    }
}

/** 把倍速显示成 "1.0"/"1.5" 这样的文本 */
private fun trimSpeed(speed: Float): String {
    val v = (speed * 100).toInt()
    val whole = v / 100
    val frac = (v % 100)
    return if (frac == 0) whole.toString() else "$whole.${(frac / 10)}"
}
