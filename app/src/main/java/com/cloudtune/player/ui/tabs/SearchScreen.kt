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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudtune.player.CloudTuneApp
import com.cloudtune.player.core.provider.PlaybackResolution
import com.cloudtune.player.core.provider.ProviderTrack
import com.cloudtune.player.core.provider.MusicProviderRegistry
import com.cloudtune.player.core.provider.SearchCapability
import com.cloudtune.player.ui.theme.NovaTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Search Tab — 跨源聚合搜索。
 *
 * 对所有支持 SearchCapability 的 Provider 并行发起搜索请求，
 * 结果合并去重后展示。
 */
@Composable
fun SearchScreen(
    onOpenPlayer: () -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as CloudTuneApp).container
    val registry = container.providerRegistry

    var query by remember { mutableStateOf(TextFieldValue("")) }
    var results by remember { mutableStateOf<List<ProviderTrack>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(query.text) {
        val q = query.text.trim()
        if (q.isBlank()) {
            results = emptyList()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        // 防抖 400ms
        delay(400)
        val searchers = registry.all().filterIsInstance<SearchCapability>()
        if (searchers.isEmpty()) {
            loading = false
            return@LaunchedEffect
        }
        val allResults = searchers.map { provider ->
            async { runCatching { provider.searchSongs(q, pageSize = 30) }.getOrDefault(emptyList()) }
        }
        results = allResults.map { it.await() }
            .flatten()
            .distinctBy { "${it.source}:${it.id}" }
            .take(60)
        loading = false
    }

    val colors = NovaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = "搜索",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = colors.text,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))

        // 搜索框
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = colors.sub,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(8.dp))
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("跨网盘 · 第三方音源 · 本地", color = colors.sub) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = colors.accent,
                ),
            )
        }

        Spacer(Modifier.height(8.dp))

        Box(Modifier.fillMaxSize()) {
            when {
                loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    strokeWidth = 2.dp,
                )
                query.text.isBlank() -> SearchHint()
                results.isEmpty() -> Text(
                    text = "没有找到「${query.text}」",
                    color = colors.sub,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 20.dp, end = 20.dp, bottom = 100.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(results, key = { "${it.source}:${it.id}" }) { track ->
                        SearchResultItem(
                            track = track,
                            onPlay = {
                                scope.launch { playTrack(track, registry, onOpenPlayer) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHint() {
    val colors = NovaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = colors.sub,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "输入关键词开始搜索",
            color = colors.sub,
            fontSize = 14.sp,
        )
        Text(
            text = "搜索范围：网盘文件名 / 第三方音源 / 本地音乐",
            color = colors.sub,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SearchResultItem(
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
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colors.text,
                maxLines = 1,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (track.artist.isNotBlank()) {
                    Text(
                        text = track.artist,
                        fontSize = 12.sp,
                        color = colors.sub,
                        maxLines = 1,
                    )
                }
                Text(
                    text = "· ${track.source.displayName}",
                    fontSize = 11.sp,
                    color = colors.accent,
                    fontWeight = FontWeight.Medium,
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
