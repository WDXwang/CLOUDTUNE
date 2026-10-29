package com.cloudtune.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.cloudtune.player.ui.navigation.Routes
import com.cloudtune.player.ui.navigation.ShellBottomBar

// 播放页强制深色 One UI 9 风格（纯黑 + 柔蓝紫 + 大圆角）
private val PlayerBg = Color(0xFF000000)
private val CardBg = Color(0xFF16181D)
private val MainText = Color(0xFFF3F4F6)
private val SubText = Color(0xFF9CA1AB)
private val Accent = Color(0xFF8B9CFF)
private val Accent2 = Color(0xFF5A7CFF)
private val NeonBorder = Color(0xFF2A2D35)

/**
 * 主界面播放页：以播放器为主页，底部为功能导航，播放列表收纳在右下角隐藏式浮层。
 *
 * @param onNavigate  底部导航切换
 * @param onOpenLyrics 打开完整歌词
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onNavigate: (String) -> Unit,
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

    val lyricIdx = if (lyrics.isEmpty()) -1
    else lyrics.indexOfLast { it.timeMs <= positionMs }

    // 右下角隐藏式播放列表
    var showQueue by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PlayerBg),
    ) {
        // 顶部柔和光晕（One UI 氛围）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Accent.copy(alpha = 0.14f), Color.Transparent),
                    )
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
        ) {
            // ===== 顶部栏：标题 + 更多 =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "正在播放",
                    style = MaterialTheme.typography.titleMedium,
                    color = SubText,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { /* 更多菜单预留 */ }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多", tint = MainText)
                }
            }

            Spacer(Modifier.height(6.dp))

            // ===== 封面（居中大卡片） =====
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF232A4A), Color(0xFF4A5FBF), Color(0xFF8B9CFF))))
                    .border(1.dp, NeonBorder, RoundedCornerShape(30.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(90.dp),
                    tint = Color.White.copy(alpha = 0.92f),
                )
            }

            Spacer(Modifier.height(22.dp))

            // ===== 标题 / 艺术家（居中） =====
            Text(
                text = nowPlaying.title.ifBlank { "未知曲目" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MainText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = nowPlaying.artist.ifBlank { "未知艺术家" },
                style = MaterialTheme.typography.bodyLarge,
                color = SubText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // ===== 歌词预览（当前行高亮 + 后两行） =====
            if (lyricIdx >= 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(CardBg.copy(alpha = 0.6f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = lyrics[lyricIdx].text,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
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
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 3.dp),
                            )
                        }
                    }
                    TextButton(
                        onClick = onOpenLyrics,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text("完整歌词", color = Accent, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ===== 控制行：随机 / 快退10 / 播放暂停 / 快进10 / 循环 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.toggleShuffle() }) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "随机",
                        tint = if (nowPlaying.shuffleEnabled) Accent else SubText,
                    )
                }
                IconButton(onClick = { vm.seekBack() }) {
                    Icon(Icons.Filled.Replay10, contentDescription = "快退10秒", tint = MainText, modifier = Modifier.size(32.dp))
                }
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Accent, Accent2)))
                        .clickable { vm.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (nowPlaying.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (nowPlaying.isPlaying) "暂停" else "播放",
                        tint = Color(0xFF0B1030),
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = { vm.seekForward() }) {
                    Icon(Icons.Filled.Forward10, contentDescription = "快进10秒", tint = MainText, modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = { vm.cycleRepeatMode() }) {
                    val (icon, tint) = when (nowPlaying.repeatMode) {
                        1 -> Icons.Filled.RepeatOne to Accent
                        2 -> Icons.Filled.Repeat to Accent
                        else -> Icons.Filled.Repeat to SubText
                    }
                    Icon(icon, contentDescription = "循环", tint = tint)
                }
            }

            Spacer(Modifier.height(8.dp))

            // ===== 进度条 + 时间 =====
            Slider(
                value = sliderValue,
                onValueChange = { dragValue = it },
                onValueChangeFinished = {
                    dragValue?.let { vm.seekTo((it * durationMs).toLong()) }
                    dragValue = null
                },
                enabled = durationMs > 0L,
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = NeonBorder,
                ),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    MusicUtils.formatDuration(if (dragValue != null) (dragValue!! * durationMs).toLong() else positionMs),
                    style = MaterialTheme.typography.bodySmall, color = SubText,
                )
                Text(MusicUtils.formatDuration(durationMs), style = MaterialTheme.typography.bodySmall, color = SubText)
            }

            Spacer(Modifier.height(8.dp))

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
                    colors = SliderDefaults.colors(
                        thumbColor = Accent,
                        activeTrackColor = Accent,
                        inactiveTrackColor = NeonBorder,
                    ),
                )
                Text(
                    "${(nowPlaying.volume * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (nowPlaying.volume > 1f) Accent else SubText,
                    modifier = Modifier.width(44.dp),
                    textAlign = TextAlign.End,
                )
            }

            Spacer(Modifier.height(96.dp)) // 给底部导航和右下角按钮留白
        }

        // ===== 右下角隐藏式播放列表按钮 =====
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 92.dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Accent, Accent2)))
                .clickable { showQueue = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.QueueMusic, contentDescription = "播放列表", tint = Color(0xFF0B1030))
        }

        // 底部功能导航
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(PlayerBg),
        ) {
            ShellBottomBar(onNavigate = onNavigate, currentRoute = Routes.PLAYER)
        }
    }

    // 隐藏式播放列表浮层
    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            sheetState = sheetState,
            containerColor = Color(0xFF16181D),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "播放队列",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MainText,
                    )
                    Text(
                        "${queue.size} 首",
                        style = MaterialTheme.typography.bodySmall,
                        color = SubText,
                    )
                }
                Spacer(Modifier.height(8.dp))
                queue.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (item.isCurrent) Accent.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable { vm.playQueueItem(index); showQueue = false }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (item.isCurrent) Accent else SubText,
                            modifier = Modifier.width(26.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (item.isCurrent) Accent else MainText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (item.isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            if (item.artist.isNotBlank()) {
                                Text(
                                    item.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SubText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (item.durationMs > 0L) {
                            Text(
                                MusicUtils.formatDuration(item.durationMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = SubText,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
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
