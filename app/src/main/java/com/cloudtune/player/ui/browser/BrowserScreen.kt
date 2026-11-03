package com.cloudtune.player.ui.browser

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudtune.player.CloudTuneApp
import com.cloudtune.player.core.model.StorageAccount
import com.cloudtune.player.core.model.displayName
import com.cloudtune.player.data.source.SourceBoard
import com.cloudtune.player.data.source.SourceSong
import com.cloudtune.player.ui.components.AddToPlaylistAction
import com.cloudtune.player.ui.components.LoadingBox
import com.cloudtune.player.ui.components.MessageView
import com.cloudtune.player.ui.components.MusicFileList
import com.cloudtune.player.ui.components.SearchField
import com.cloudtune.player.ui.components.SelectAllCheckbox
import com.cloudtune.player.ui.theme.NovaTheme
import kotlinx.coroutines.launch

/**
 * 云盘文件浏览页（多网盘聚合首页 + 存储内浏览）。
 *
 * 聚合首页：展示第三方音源排行榜/推荐 + 全部已启用存储，支持新增 / 管理（[onOpenStorages]），
 * 搜索框执行跨网盘搜索。进入存储后：文件夹点击进入，音乐点击即播，
 * 支持多选（长按音乐进入）与全选，右上角“播放全部”。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onOpenPlayer: () -> Unit,
    onOpenStorages: () -> Unit = {},
    drawerState: DrawerState? = null,
    viewModel: BrowserViewModel = viewModel(),
) {
    val state = viewModel.uiState
    val sourceState = viewModel.sourceUiState
    val scope = rememberCoroutineScope()

    if (state.selectionMode) {
        // 多选模式：顶部显示已选数量、全选、加入歌单、播放选中
        val context = LocalContext.current
        val repository = (context.applicationContext as CloudTuneApp).container.repository
        val selectedFiles = state.visibleFiles.filter { it.id in state.selectedIds }
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "退出多选")
                        }
                    },
                    title = { Text("已选 ${state.selectedIds.size} 项", maxLines = 1) },
                    actions = {
                        SelectAllCheckbox(
                            allSelected = state.allSelected,
                            selectedCount = state.selectedIds.size,
                            totalCount = state.visibleMusicCount,
                            onClick = { viewModel.selectAll() },
                        )
                        AddToPlaylistAction(
                            repository = repository,
                            files = selectedFiles,
                            enabled = selectedFiles.isNotEmpty(),
                        )
                        IconButton(
                            onClick = { viewModel.playSelected() },
                            enabled = state.selectedIds.isNotEmpty(),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "播放选中")
                        }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                MusicListBody(viewModel)
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        if (state.atHome) {
                            IconButton(
                                onClick = { scope.launch { drawerState?.open() } },
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = "打开侧边栏")
                            }
                        } else {
                            IconButton(onClick = { viewModel.goBack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回上级")
                            }
                        }
                    },
                    title = { Text(state.pathLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    actions = {
                        if (state.atHome) {
                            IconButton(onClick = { viewModel.loadSourceData() }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "刷新音源")
                            }
                        } else {
                            if (state.musicCount > 0) {
                                IconButton(onClick = { viewModel.playAll() }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "播放全部")
                                }
                            }
                            // 当前目录有视频时，提供「列表 / 电影墙」切换
                            if (state.videoCount > 0) {
                                IconButton(onClick = { viewModel.toggleWallMode() }) {
                                    Icon(
                                        if (state.wallMode) Icons.Filled.ViewList else Icons.Filled.GridView,
                                        contentDescription = if (state.wallMode) "切换为列表" else "切换为电影墙",
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.refresh() }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                            }
                        }
                        IconButton(onClick = onOpenStorages) {
                            Icon(Icons.Filled.Tune, contentDescription = "网盘管理")
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                SearchField(
                    query = state.query,
                    onQueryChange = { viewModel.setQuery(it) },
                    placeholder = if (state.atHome) "搜索网盘音乐 / 第三方音源…" else "搜索当前文件夹…",
                )
                Box(Modifier.fillMaxSize()) {
                    if (state.showAccountCards) {
                        HomeContent(
                            sourceState = sourceState,
                            onSourceSongClick = { viewModel.playSourceSong(it); onOpenPlayer() },
                            onBoardClick = { viewModel.selectBoard(it) },
                            onCategoryClick = { viewModel.selectCategory(it) },
                            onRefreshSource = { viewModel.loadSourceData() },
                            accounts = state.accounts,
                            onOpenAccount = { viewModel.openAccount(it) },
                            onAddAccount = onOpenStorages,
                        )
                    } else {
                        MusicListBody(viewModel, showAccountName = state.atHome)
                    }
                }
            }
        }
    }
}

/** 聚合首页：第三方音源模块 + 存储卡片列表 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    sourceState: SourceUiState,
    onSourceSongClick: (SourceSong) -> Unit,
    onBoardClick: (SourceBoard) -> Unit,
    onCategoryClick: (String) -> Unit,
    onRefreshSource: () -> Unit,
    accounts: List<StorageAccount>,
    onOpenAccount: (StorageAccount) -> Unit,
    onAddAccount: () -> Unit,
) {
    val colors = NovaTheme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // 第三方音源模块
        if (sourceState.loading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("加载第三方音源中…", style = MaterialTheme.typography.bodySmall, color = colors.sub)
            }
        } else if (sourceState.error != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Extension, contentDescription = null, tint = colors.sub, modifier = Modifier.size(20.dp))
                Text(sourceState.error!!, style = MaterialTheme.typography.bodySmall, color = colors.sub, modifier = Modifier.weight(1f))
                TextButton(onClick = onRefreshSource) {
                    Text("重试", color = colors.accent)
                }
            }
        } else if (sourceState.sources.isNotEmpty()) {
            // 排行榜模块
            if (sourceState.boards.isNotEmpty() || sourceState.boardSongs.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "排行榜",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text,
                    )
                    Spacer(Modifier.height(8.dp))
                    // 分类行（全部 / 网易云 / 酷狗 / 汽水 …）
                    if (sourceState.categories.size > 2) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(sourceState.categories, key = { it }) { category ->
                                FilterChip(
                                    selected = (sourceState.selectedCategory ?: "全部") == category,
                                    onClick = { onCategoryClick(category) },
                                    label = { Text(category, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.text,
                                        selectedLabelColor = colors.bg,
                                    ),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    // 榜单切换 chip（按当前分类过滤）
                    val visibleBoards = sourceState.selectedCategory?.let { selected ->
                        sourceState.boards.filter { sourceState.boardCategories[it.id] == selected }
                    } ?: sourceState.boards
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(visibleBoards, key = { it.id }) { board ->
                            FilterChip(
                                selected = board.id == sourceState.currentBoard?.id,
                                onClick = { onBoardClick(board) },
                                label = { Text(board.name, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.accent,
                                    selectedLabelColor = colors.onAccent,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // 榜单歌曲列表（横向）
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(sourceState.boardSongs, key = { it.id }) { song ->
                            SongCard(
                                song = song,
                                onClick = { onSourceSongClick(song) },
                                colors = colors,
                            )
                        }
                    }
                }
            }
            // 推荐歌曲模块
            if (sourceState.recommendedSongs.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "为你推荐",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text,
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(sourceState.recommendedSongs, key = { it.id }) { song ->
                            SongCard(
                                song = song,
                                onClick = { onSourceSongClick(song) },
                                colors = colors,
                            )
                        }
                    }
                }
            }
        }

        // 存储卡片列表
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "我的网盘",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
            )
            accounts.forEach { account ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    onClick = { onOpenAccount(account) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                account.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                account.config.backend.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "›",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                onClick = onAddAccount,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        if (accounts.isEmpty()) "还没有网盘，点击添加" else "＋ 添加网盘",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 第三方音源歌曲卡片（简洁版） */
@Composable
private fun SongCard(
    song: SourceSong,
    onClick: () -> Unit,
    colors: com.cloudtune.player.ui.theme.NovaPalette,
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
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
            text = song.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = colors.sub,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 列表主体：处理加载 / 错误 / 空态。 */
@Composable
private fun MusicListBody(viewModel: BrowserViewModel, showAccountName: Boolean = false) {
    val state = viewModel.uiState
    val context = androidx.compose.ui.platform.LocalContext.current
    when {
        state.loading && state.visibleFiles.isEmpty() -> LoadingBox()
        state.error != null -> MessageView(
            message = state.error!!,
            actionText = "重试",
            onAction = { viewModel.refresh() },
        )
        state.visibleFiles.isEmpty() ->
            MessageView(
                message = when {
                    state.query.isNotBlank() -> "无匹配结果"
                    state.atHome -> "还没有网盘，点击右上角「管理」添加"
                    else -> "此文件夹为空"
                }
            )
        else -> if (state.wallMode && !state.atHome && !state.selectionMode) {
            // 电影墙：视频海报网格（含文件夹 / 音乐紧凑卡片）
            MediaWallGrid(
                files = state.visibleFiles,
                onFolderClick = { viewModel.enterFolder(it) },
                onVideoClick = { com.cloudtune.player.ui.video.VideoActivity.start(context, it) },
                onMusicClick = { viewModel.playMusic(it) },
            )
        } else {
            MusicFileList(
                files = state.visibleFiles,
                selectionMode = state.selectionMode,
                selectedIds = state.selectedIds,
                onFolderClick = { viewModel.enterFolder(it) },
                onMusicClick = { viewModel.playMusic(it) },
                onLongPressMusic = { viewModel.enterSelection(it) },
                onToggleSelect = { viewModel.toggleSelect(it) },
                onVideoClick = { com.cloudtune.player.ui.video.VideoActivity.start(context, it) },
                onRingtoneClick = { com.cloudtune.player.ringtone.RingtoneEditorActivity.start(context, it) },
                showAccountName = showAccountName,
            )
        }
    }
}
