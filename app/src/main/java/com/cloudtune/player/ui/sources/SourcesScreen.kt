package com.cloudtune.player.ui.sources

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudtune.player.core.util.MusicUtils
import com.cloudtune.player.data.source.MusicSourceInfo
import com.cloudtune.player.data.source.SourceBoard
import com.cloudtune.player.data.source.SourceSong
import com.cloudtune.player.ui.theme.NovaTheme

/**
 * 音源管理页：导入洛雪自定义源脚本、选择第三方源、获取排行榜并直接播放。
 */
@Composable
fun SourcesScreen(
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    vm: SourcesViewModel = viewModel(),
) {
    val colors = NovaTheme.colors
    val sources by vm.sources.collectAsStateWithLifecycle()
    var showUrlImport by remember { mutableStateOf(false) }
    var urlText by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.import(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        // 顶部栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 6.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (vm.selected != null) vm.backToList() else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = colors.text)
            }
            Text(
                text = vm.selected?.name ?: "音源",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (vm.selected == null) {
                TextButton(onClick = { launcher.launch("*/*") }) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("文件", color = colors.accent)
                }
                TextButton(onClick = { showUrlImport = true }) {
                    Text("链接", color = colors.accent)
                }
            }
        }

        vm.message?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (text.startsWith("播放") || text.contains("失败")) colors.warn else colors.sub,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        if (vm.importing) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.accent)
                Spacer(Modifier.width(8.dp))
                Text("正在解析音源脚本…", style = MaterialTheme.typography.bodySmall, color = colors.sub)
            }
        }

        val selected = vm.selected
        if (selected == null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { IntroCard() }
                if (sources.isEmpty()) {
                    item {
                        Text(
                            text = "还没有导入音源，点击右上角「导入」选择 .js 音源脚本。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.sub,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                items(sources, key = { it.id }) { info ->
                    SourceCard(info = info, onOpen = { vm.select(info) }, onDelete = { vm.delete(info) })
                }
            }
        } else {
            SourceDetail(
                info = selected,
                boards = vm.boards,
                songs = vm.songs,
                loading = vm.loading,
                loadingSongId = vm.loadingSongId,
                onRefresh = { vm.loadLeaderboard() },
                onBoard = { vm.loadSongs(it) },
                onPlay = { song ->
                    vm.play(song)
                    onOpenPlayer()
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    // 网络地址导入弹窗
    if (showUrlImport) {
        AlertDialog(
            onDismissRequest = { showUrlImport = false },
            title = { Text("从网络地址导入音源") },
            text = {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("音源脚本 URL（.js 直链）") },
                    placeholder = { Text("https://.../source.js") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val u = urlText.trim()
                        if (u.isNotEmpty()) {
                            vm.importUrl(u)
                            urlText = ""
                            showUrlImport = false
                        }
                    }
                ) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { showUrlImport = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun IntroCard() {
    val colors = NovaTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = colors.surface,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("第三方音源", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.accent)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "兼容洛雪音乐自定义源（globalThis.lx）。导入网友分享的 .js 脚本后，" +
                    "可直接播放第三方音乐，并读取脚本提供的排行榜（扩展 action：leaderboard）。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.sub,
            )
        }
    }
}

@Composable
private fun SourceCard(info: MusicSourceInfo, onOpen: () -> Unit, onDelete: () -> Unit) {
    val colors = NovaTheme.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(22.dp),
        color = colors.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(colors.accentSoft, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = colors.accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = info.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        if (info.author.isNotBlank()) append(info.author)
                        if (info.version.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append("v").append(info.version)
                        }
                        if (info.sources.isNotEmpty()) {
                            if (isNotEmpty()) append(" · ")
                            append(info.sources.joinToString("/"))
                        }
                    }.ifBlank { "未提供作者信息" },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除音源", tint = colors.sub)
            }
        }
    }
}

@Composable
private fun SourceDetail(
    info: MusicSourceInfo,
    boards: List<SourceBoard>,
    songs: List<SourceSong>,
    loading: Boolean,
    loadingSongId: String?,
    onRefresh: () -> Unit,
    onBoard: (SourceBoard) -> Unit,
    onPlay: (SourceSong) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NovaTheme.colors
    var activeBoardId by remember(info.id) { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "第三方源 · ${info.sources.joinToString("/").ifBlank { "未知平台" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.sub,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("刷新榜单", color = colors.accent)
                }
            }
        }

        if (boards.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    boards.forEach { board ->
                        val active = board.id == activeBoardId
                        Surface(
                            modifier = Modifier.clickable {
                                activeBoardId = board.id
                                onBoard(board)
                            },
                            shape = RoundedCornerShape(18.dp),
                            color = if (active) colors.accent else colors.surface2,
                        ) {
                            Text(
                                text = board.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                color = if (active) colors.onAccent else colors.text,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        if (loading) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.accent)
                    Spacer(Modifier.width(10.dp))
                    Text("正在获取榜单…", style = MaterialTheme.typography.bodySmall, color = colors.sub)
                }
            }
        }

        if (!loading && songs.isEmpty()) {
            item {
                Text(
                    text = "暂无歌曲。若音源脚本未实现 leaderboard，请尝试其他音源，" +
                        "或直接通过「乐库」播放网盘 / 本地音乐。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.sub,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            }
        }

        items(songs) { song ->
            SongRow(
                song = song,
                loading = loadingSongId == song.id,
                onClick = { onPlay(song) },
            )
        }
    }
}

@Composable
private fun SongRow(song: SourceSong, loading: Boolean, onClick: () -> Unit) {
    val colors = NovaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !loading, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(colors.surface2, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = colors.accent)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.name,
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (song.artist.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (song.durationMs > 0) {
            Text(
                text = MusicUtils.formatDuration(song.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = colors.sub,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.accent)
        } else {
            Icon(Icons.Filled.PlayArrow, contentDescription = "播放", tint = colors.text)
        }
    }
}
