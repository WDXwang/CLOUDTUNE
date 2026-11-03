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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.cloudtune.player.core.provider.MusicProviderRegistry
import com.cloudtune.player.core.provider.PlaybackResolution
import com.cloudtune.player.core.provider.ProviderRanking
import com.cloudtune.player.core.provider.ProviderTrack
import com.cloudtune.player.core.provider.RankingsCapability
import com.cloudtune.player.ui.theme.NovaTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * Explore Tab — 第三方音源榜单聚合。
 *
 * 从所有支持 RankingsCapability 的 Provider 拉榜单目录，
 * 横向 Chip 展示分类，选中榜单后展示歌曲列表。
 */
@Composable
fun ExploreScreen(
    onOpenPlayer: () -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as CloudTuneApp).container
    val registry = container.providerRegistry

    var rankings by remember { mutableStateOf<List<ProviderRanking>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var currentRanking by remember { mutableStateOf<ProviderRanking?>(null) }
    var rankingSongs by remember { mutableStateOf<List<ProviderTrack>>(emptyList()) }
    var loadingSongs by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loading = true
        val rankers = registry.all().filterIsInstance<RankingsCapability>()
        val results = rankers.map { provider ->
            async { runCatching { provider.rankingList() }.getOrDefault(emptyList()) }
        }
        rankings = results.map { it.await() }.flatten()
        currentRanking = rankings.firstOrNull()
        loading = false
        // 自动加载第一个榜单的歌曲
        currentRanking?.let { loadRankingSongs(it, registry) { songs -> rankingSongs = songs } }
    }

    val colors = NovaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = "探索",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.text,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "第三方音源榜单 · 新歌速递",
            fontSize = 14.sp,
            color = colors.sub,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(16.dp))

        if (loading) {
            Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    strokeWidth = 2.dp,
                )
            }
        } else if (rankings.isEmpty()) {
            Box(Modifier.fillMaxSize()) {
                Text(
                    text = "暂无榜单数据，请先导入第三方音源",
                    color = colors.sub,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        } else {
            // 榜单 Chip 行
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rankings) { ranking ->
                    FilterChip(
                        selected = currentRanking?.id == ranking.id,
                        onClick = {
                            currentRanking = ranking
                            rankingSongs = emptyList()
                            scope.launch {
                                loadingSongs = true
                                loadRankingSongs(ranking, registry) { songs ->
                                    rankingSongs = songs
                                    loadingSongs = false
                                }
                            }
                        },
                        label = {
                            Text(
                                ranking.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accent,
                            selectedLabelColor = colors.onAccent,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (loadingSongs) {
                Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        strokeWidth = 2.dp,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 20.dp, end = 20.dp, bottom = 100.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(rankingSongs, key = { _, it -> "${it.source}:${it.id}" }) { index, song ->
                        SongListItem(
                            rank = index + 1,
                            track = song,
                            onPlay = {
                                scope.launch { playTrack(song, registry, onOpenPlayer) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SongListItem(
    rank: Int,
    track: ProviderTrack,
    onPlay: () -> Unit,
) {
    val colors = NovaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = rank.toString(),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (rank <= 3) colors.accent else colors.sub,
            modifier = Modifier.width(28.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (track.artist.isNotBlank()) {
                Text(
                    text = track.artist,
                    fontSize = 12.sp,
                    color = colors.sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(20.dp),
        )
    }
}

private suspend fun loadRankingSongs(
    ranking: ProviderRanking,
    registry: MusicProviderRegistry,
    callback: (List<ProviderTrack>) -> Unit,
) {
    val provider = registry.bySource(ranking.source) as? RankingsCapability ?: run {
        // 第三方音源的榜单统一走 SourceProvider（source=ThirdParty）
        registry.bySource(com.cloudtune.player.core.provider.MusicSource.ThirdParty) as? RankingsCapability
    }
    val songs = provider?.let { runCatching { it.rankingTracks(ranking.id) }.getOrDefault(emptyList()) }
        ?: emptyList()
    callback(songs)
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
