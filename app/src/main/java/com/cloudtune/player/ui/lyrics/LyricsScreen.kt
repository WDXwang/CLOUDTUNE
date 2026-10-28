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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudtune.player.data.lyrics.LyricLine

/**
 * 歌词页：随播放进度滚动，当前行高亮居中。
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
        lines.indexOfLast { it.timeMs <= positionMs }
            .coerceAtLeast(0)
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
                        itemsIndexed(lines, key = { _, it -> it.timeMs to it.text }) { index, line: LyricLine ->
                            LyricRow(
                                text = line.text,
                                active = index == currentIndex,
                            )
                        }
                        item { Spacer(Modifier.height(half)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricRow(text: String, active: Boolean) {
    Text(
        text = text,
        color = if (active) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = if (active) 20.sp else 16.sp,
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 10.dp),
    )
}
