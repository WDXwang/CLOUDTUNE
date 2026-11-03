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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudtune.player.CloudTuneApp
import com.cloudtune.player.core.provider.HomeFeedCapability
import com.cloudtune.player.core.provider.MusicSource
import com.cloudtune.player.core.provider.MusicProviderRegistry
import com.cloudtune.player.core.provider.PlaybackResolution
import com.cloudtune.player.core.provider.ProviderHomeFeed
import com.cloudtune.player.core.provider.ProviderTrack
import com.cloudtune.player.ui.theme.NovaTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * Home Tab — 各 Provider 的 HomeFeed 聚合展示。
 *
 * 首页内容：
 * 1. Provider 首屏推荐块（网盘音乐 / 第三方源榜单预览）
 * 2. 最近听过（从历史表取最新 N 首）
 */
@Composable
fun HomeScreen(
    onOpenPlayer: () -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as CloudTuneApp).container
    val registry = container.providerRegistry

    var feeds by remember { mutableStateOf<List<Pair<MusicSource, ProviderHomeFeed>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loading = true
        // 串行拉所有 Provider 的 HomeFeed（Provider 数量不多，无需 async 并行）
        val homeFeeders = registry.all().filter { it is HomeFeedCapability }
        val collected = mutableListOf<Pair<MusicSource, ProviderHomeFeed>>()
        for (p in homeFeeders) {
            val hf = p as HomeFeedCapability
            val feed = runCatching { hf.homeFeed() }.getOrDefault(ProviderHomeFeed())
            collected += p.source to feed
        }
        feeds = collected.filter { it.second.recommendedSongs.isNotEmpty() || it.second.rankings.isNotEmpty() }
        loading = false
    }

    val colors = NovaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 100.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = "推荐",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.text,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "CloudTune · 你的音乐",
            fontSize = 14.sp,
            color = colors.sub,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        if (loading) {
            Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    strokeWidth = 2.dp,
                )
            }
        } else if (feeds.isEmpty()) {
            EmptyHomeHint()
        } else {
            feeds.forEach { (source, feed) ->
                if (feed.recommendedSongs.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    SectionHeader(
                        title = source.displayName,
                        subtitle = "为你推荐",
                    )
                    Spacer(Modifier.height(8.dp))
                    SongRow(
                        songs = feed.recommendedSongs,
                        onPlay = { track ->
                            scope.launch { playTrack(track, registry, onOpenPlayer) }
                        },
                    )
                }
                if (feed.rankings.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    SectionHeader(
                        title = source.displayName,
                        subtitle = "热门榜单",
                    )
                    Spacer(Modifier.height(8.dp))
                    RankingRow(rankings = feed.rankings)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EmptyHomeHint() {
    val colors = NovaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Cloud,
            contentDescription = null,
            tint = colors.sub,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "还没有推荐内容",
            color = colors.sub,
            fontSize = 14.sp,
        )
        Text(
            text = "请先在「我的」页添加网盘或导入第三方音源",
            color = colors.sub,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    val colors = NovaTheme.colors
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            text = subtitle,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = colors.text,
        )
        Text(
            text = "来自 $title",
            fontSize = 12.sp,
            color = colors.sub,
        )
    }
}

@Composable
private fun SongRow(
    songs: List<ProviderTrack>,
    onPlay: (ProviderTrack) -> Unit,
) {
    val colors = NovaTheme.colors
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(songs) { song ->
            Column(
                modifier = Modifier
                    .width(140.dp)
                    .clickable { onPlay(song) },
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .background(colors.accentSoft, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(36.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.text,
                )
                if (song.artist.isNotBlank()) {
                    Text(
                        text = song.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                        color = colors.sub,
                    )
                }
            }
        }
    }
}

@Composable
private fun RankingRow(
    rankings: List<com.cloudtune.player.core.provider.ProviderRanking>,
) {
    val colors = NovaTheme.colors
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(rankings) { ranking ->
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(80.dp)
                    .background(colors.accentSoft, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = ranking.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private suspend fun playTrack(
    track: ProviderTrack,
    registry: MusicProviderRegistry,
    onOpenPlayer: () -> Unit,
) {
    val container = CloudTuneApp.instance.container
    val provider = registry.bySource(track.source)
    if (provider is com.cloudtune.player.core.provider.PlaybackCapability) {
        val resolution = provider.resolvePlayback(track)
        when (resolution) {
            is PlaybackResolution.Playable -> {
                container.playerManager.playUrl(resolution.url, track.title, track.artist)
                onOpenPlayer()
            }
            is PlaybackResolution.NeedsCloudResolve -> {
                val file = com.cloudtune.player.core.model.CloudFile(
                    id = resolution.fileId, name = track.title, path = "",
                    isDir = false, accountId = resolution.accountId,
                )
                container.playerManager.playFiles(listOf(file), 0)
                onOpenPlayer()
            }
            is PlaybackResolution.Failed -> { /* ignore */ }
        }
    }
}
