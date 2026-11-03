package com.cloudtune.player.ui.lyrics

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudtune.player.data.lyrics.WordLyricLine

/**
 * 歌词页：随播放进度滚动，当前行高亮居中；支持逐字高亮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(
    onBack: () -> Unit,
    vm: LyricsViewModel = viewModel(),
) {
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val lines by vm.lines.collectAsStateWithLifecycle()
    val positionMs by vm.positionFlow().collectAsStateWithLifecycle(0L)

    val listState = rememberLazyListState()

    // 当前歌词行索引（按播放位置）
    val currentIndex = remember(lines, positionMs) {
        if (lines.isEmpty()) 0
        else lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
    }

    // 自动滚动让当前行居中
    LaunchedEffect(currentIndex) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(currentIndex.coerceAtLeast(0))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text(nowPlaying.title.ifBlank { "歌词" }, maxLines = 1) },
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val half = (constraints.maxHeight / 2f).dp

            when (status) {
                LyricsStatus.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                LyricsStatus.NoLyrics, LyricsStatus.Error, LyricsStatus.Idle -> {
                    Text(
                        text = if (status == LyricsStatus.Error) "歌词获取失败，请检查网络"
                        else "暂无歌词",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                LyricsStatus.Loaded -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item { Spacer(Modifier.height(half)) }
                        itemsIndexed(lines, key = { _, it -> it.timeMs to it.text }) { index, line ->
                            val isCurrent = index == currentIndex
                            LyricRow(
                                line = line,
                                active = isCurrent,
                                positionMs = if (isCurrent) positionMs else -1L,
                            )
                        }
                        item { Spacer(Modifier.height(half)) }
                    }
                }
            }
        }
    }
}

/**
 * 渲染一行歌词：
 * - 非当前行：普通文本
 * - 当前行 + 有逐字时间戳：逐字高亮（找到当前播放到的字）
 * - 当前行 + 无逐字：整行高亮
 */
@Composable
private fun LyricRow(
    line: WordLyricLine,
    active: Boolean,
    positionMs: Long,
) {
    val baseColor = if (active) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    val activeFontSize = 20.sp
    val normalFontSize = 16.sp

    if (active && line.isWordLevel) {
        // 找到当前字索引：最后一个 timeMs <= positionMs 的字
        val currentWordIdx = line.words.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
        val text = buildAnnotatedString {
            line.words.forEachIndexed { idx, word ->
                withStyle(
                    SpanStyle(
                        color = if (idx <= currentWordIdx) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontWeight = if (idx == currentWordIdx) FontWeight.Bold else FontWeight.Normal,
                    )
                ) { append(word.text) }
            }
        }
        Text(
            text = text,
            fontSize = activeFontSize,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 10.dp),
        )
    } else {
        Text(
            text = line.text,
            color = baseColor,
            fontSize = if (active) activeFontSize else normalFontSize,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 10.dp),
        )
    }
}
