package com.cloudtune.player.ui.browser

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudtune.player.CloudTuneApp
import com.cloudtune.player.core.model.StorageAccount
import com.cloudtune.player.core.model.displayName
import com.cloudtune.player.ui.components.AddToPlaylistAction
import com.cloudtune.player.ui.components.LoadingBox
import com.cloudtune.player.ui.components.MessageView
import com.cloudtune.player.ui.components.MusicFileList
import com.cloudtune.player.ui.components.SearchField
import com.cloudtune.player.ui.components.SelectAllCheckbox

/**
 * 云盘文件浏览页（多网盘聚合首页 + 存储内浏览）。
 *
 * 聚合首页：展示全部已启用存储，支持新增 / 管理（[onOpenStorages]），
 * 搜索框执行跨网盘搜索。进入存储后：文件夹点击进入，音乐点击即播，
 * 支持多选（长按音乐进入）与全选，右上角“播放全部”。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onOpenPlayer: () -> Unit,
    onOpenStorages: () -> Unit = {},
    viewModel: BrowserViewModel = viewModel(),
) {
    val state = viewModel.uiState

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
                        if (!state.atHome) {
                            IconButton(onClick = { viewModel.goBack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回上级")
                            }
                        }
                    },
                    title = { Text(state.pathLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    actions = {
                        if (!state.atHome && state.musicCount > 0) {
                            IconButton(onClick = { viewModel.playAll() }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "播放全部")
                            }
                        }
                        // 当前目录有视频时，提供「列表 / 电影墙」切换
                        if (!state.atHome && state.videoCount > 0) {
                            IconButton(onClick = { viewModel.toggleWallMode() }) {
                                Icon(
                                    if (state.wallMode) Icons.Filled.ViewList else Icons.Filled.GridView,
                                    contentDescription = if (state.wallMode) "切换为列表" else "切换为电影墙",
                                )
                            }
                        }
                        if (!state.atHome) {
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
                    placeholder = if (state.atHome) "在所有网盘中搜索…" else "搜索当前文件夹…",
                )
                Box(Modifier.fillMaxSize()) {
                    if (state.showAccountCards) {
                        AccountCards(
                            accounts = state.accounts,
                            onOpenAccount = { viewModel.openAccount(it) },
                            onAdd = onOpenStorages,
                        )
                    } else {
                        MusicListBody(viewModel, showAccountName = state.atHome)
                    }
                }
            }
        }
    }
}

/** 聚合首页：存储卡片 + 添加网盘入口 */
@Composable
private fun AccountCards(
    accounts: List<StorageAccount>,
    onOpenAccount: (StorageAccount) -> Unit,
    onAdd: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(accounts, key = { it.id }) { account ->
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
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                onClick = onAdd,
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
        }
        item { Spacer(Modifier.height(8.dp)) }
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
