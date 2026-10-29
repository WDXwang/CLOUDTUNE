package com.cloudtune.player.ui.player

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudtune.player.core.util.MusicUtils
import com.cloudtune.player.ui.theme.GradientOrange
import com.cloudtune.player.ui.theme.GradientPurple
import com.cloudtune.player.ui.theme.GradientRed

// 播放页强制深色（参考图风格）
private val PlayerBg = Color(0xFF0E0E12)
private val CardBg = Color(0xFF1B1B21)
private val MainText = Color(0xFFF4F4F6)
private val SubText = Color(0xFF8E8E98)
private val Accent = Color(0xFFFA586A)

/**
 * 全屏播放器界面（深色、内嵌播放列表风格）。
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
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    val positionMs by vm.positionFlow().collectAsStateWithLifecycle(0L)

    var dragValue by remember { mutableStateOf<Float?>(null) }
    val durationMs = nowPlaying.durationMs.coerceAtLeast(0L)
    val sliderValue = when {
        durationMs <= 0L -> 0f
        dragValue != null -> dragValue!!
        else -> (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    // 当前歌词行
    val lyricIdx = if (lyrics.isEmpty()) -1
    else lyrics.indexOfLast { it.timeMs <= positionMs }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PlayerBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        // ===== 顶部栏：收起 + 标题 + 更多 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "收起", tint = MainText)
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = nowPlaying.title.ifBlank { "正在播放" },
                style = MaterialTheme.typography.titleMedium,
                color = MainText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { /* 更多菜单预留 */ }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多", tint = MainText)
            }
        }

        Spacer(Modifier.height(16.dp))

        // ===== 横向封面卡片 =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(GradientPurple, GradientRed, GradientOrange))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color.White.copy(alpha = 0.9f),
            )
        }

        Spacer(Modifier.height(18.dp))

        // ===== 标题 / 艺术家 =====
        Text(
            text = nowPlaying.title.ifBlank { "未知曲目" },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MainText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = nowPlaying.artist.ifBlank { "未知艺术家" },
            style = MaterialTheme.typography.bodyLarge,
            color = SubText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(14.dp))

        // ===== 歌词预览（当前行高亮 + 后两行） =====
        if (lyricIdx >= 0) {
            Text(
                text = lyrics[lyricIdx].text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MainText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            for (k in 1..2) {
                val idx = lyricIdx + k
                if (idx <= lyrics.lastIndex) {
                    Text(
                        text = lyrics[idx].text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SubText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onOpenLyrics, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("完整歌词", color = Accent, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(8.dp))

        // ===== 控制行：循环 / 快退10 / 播放暂停 / 快进10 / 关闭 =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.cycleRepeatMode() }) {
                val (icon, tint) = when (nowPlaying.repeatMode) {
                    1 -> Icons.Filled.RepeatOne to Accent
                    2 -> Icons.Filled.Repeat to Accent
                    else -> Icons.Filled.Repeat to SubText
                }
                Icon(icon, contentDescription = "循环", tint = tint)
            }
            IconButton(onClick = { vm.seekBack() }) {
                Icon(Icons.Filled.Replay10, contentDescription = "快退10秒", tint = MainText, modifier = Modifier.size(34.dp))
            }
            Surface(
                shape = CircleShape,
                color = MainText,
                modifier = Modifier.size(68.dp),
                onClick = { vm.togglePlay() },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (nowPlaying.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (nowPlaying.isPlaying) "暂停" else "播放",
                        tint = PlayerBg,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            IconButton(onClick = { vm.seekForward() }) {
                Icon(Icons.Filled.Forward10, contentDescription = "快进10秒", tint = MainText, modifier = Modifier.size(34.dp))
            }
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.Close, contentDescription = "关闭", tint = SubText)
            }
        }

        Spacer(Modifier.height(10.dp))

        // ===== 进度条 + 时间 =====
        Slider(
            value = sliderValue,
            onValueChange = { dragValue = it },
            onValueChangeFinished = {
                dragValue?.let { vm.seekTo((it * durationMs).toLong()) }
                dragValue = null
            },
            enabled = durationMs > 0L,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                MusicUtils.formatDuration(if (dragValue != null) (dragValue!! * durationMs).toLong() else positionMs),
                style = MaterialTheme.typography.bodySmall, color = SubText,
            )
            Text(MusicUtils.formatDuration(durationMs), style = MaterialTheme.typography.bodySmall, color = SubText)
        }

        Spacer(Modifier.height(12.dp))

        // ===== 倍速 + 音量（紧凑行） =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { menuOpen = true }) {
                    Text(
                        "${trimSpeed(nowPlaying.playbackSpeed)}x",
                        color = if (nowPlaying.playbackSpeed != 1f) Accent else SubText,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    speedOptions.forEach { s ->
                        DropdownMenuItem(
                            text = { Text("${trimSpeed(s)}x") },
                            onClick = { vm.setPlaybackSpeed(s); menuOpen = false },
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.VolumeUp, contentDescription = "音量", tint = SubText)
            Slider(
                value = nowPlaying.volume.coerceIn(0f, 2f),
                onValueChange = { vm.setVolume(it) },
                valueRange = 0f..2f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            )
            Text(
                "${(nowPlaying.volume * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = if (nowPlaying.volume > 1f) Accent else SubText,
                modifier = Modifier.width(44.dp),
                textAlign = TextAlign.End,
            )
        }

        Spacer(Modifier.height(16.dp))

        // ===== 内嵌播放列表（卡片） =====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(CardBg)
                .padding(vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("播放列表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MainText)
                TextButton(onClick = onOpenQueue) { Text("全部", color = Accent) }
            }
            queue.forEachIndexed { index, item ->
                QueueRow(
                    index = index + 1,
                    title = item.title,
                    artist = item.artist,
                    durationMs = item.durationMs,
                    isCurrent = item.isCurrent,
                    onClick = { vm.playQueueItem(index) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** 内嵌播放列表的一行：序号 + 标题/歌手 + 时长。 */
@Composable
private fun QueueRow(
    index: Int,
    title: String,
    artist: String,
    durationMs: Long,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$index",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCurrent) Accent else SubText,
            modifier = Modifier.width(28.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) Accent else MainText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (artist.isNotBlank()) {
                Text(
                    artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = SubText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (durationMs > 0L) {
            Text(
                MusicUtils.formatDuration(durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = SubText,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** 把倍速显示成 "1.0"/"1.5" 文本。 */
private fun trimSpeed(speed: Float): String {
    val v = (speed * 100).toInt()
    val whole = v / 100
    val frac = (v % 100)
    return if (frac == 0) whole.toString() else "$whole.${(frac / 10)}"
}
