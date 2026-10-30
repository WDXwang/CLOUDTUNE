package com.cloudtune.player.ui.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudtune.player.CloudTuneApp
import com.cloudtune.player.ui.components.AddToPlaylistAction
import com.cloudtune.player.ui.components.LoadingBox
import com.cloudtune.player.ui.components.MessageView
import com.cloudtune.player.ui.components.MusicFileList
import com.cloudtune.player.ui.components.SearchField
import com.cloudtune.player.ui.components.SelectAllCheckbox

/**
 * 云盘文件浏览页。
 *
 * 支持搜索（按文件名实时过滤）、多选（长按音乐进入）与全选；
 * 文件夹点击进入，音乐点击即播；非多选时右上角“播放全部”。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onOpenPlayer: () -> Unit,
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
        // 普通模式：搜索框 + 列表
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        if (state.canGoBack) {
                            IconButton(onClick = { viewModel.goBack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回上级")
                            }
                        }
                    },
                    title = { Text(state.pathLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    actions = {
                        if (state.musicCount > 0) {
                            IconButton(onClick = { viewModel.playAll() }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "播放全部")
                            }
                        }
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                SearchField(query = state.query, onQueryChange = { viewModel.setQuery(it) })
                Box(Modifier.fillMaxSize()) {
                    MusicListBody(viewModel)
                }
            }
        }
    }
}

/** 列表主体：处理加载 / 错误 / 空态。 */
@Composable
private fun MusicListBody(viewModel: BrowserViewModel) {
    val state = viewModel.uiState
    val context = androidx.compose.ui.platform.LocalContext.current
    when {
        state.loading && state.files.isEmpty() -> LoadingBox()
        state.error != null -> MessageView(
            message = state.error!!,
            actionText = "重试",
            onAction = { viewModel.refresh() },
        )
        state.visibleFiles.isEmpty() ->
            MessageView(message = if (state.query.isNotBlank()) "无匹配结果" else "此文件夹为空")
        else -> MusicFileList(
            files = state.visibleFiles,
            selectionMode = state.selectionMode,
            selectedIds = state.selectedIds,
            onFolderClick = { viewModel.enterFolder(it) },
            onMusicClick = { viewModel.playMusic(it) },
            onLongPressMusic = { viewModel.enterSelection(it) },
            onToggleSelect = { viewModel.toggleSelect(it) },
            onVideoClick = { com.cloudtune.player.ui.video.VideoActivity.start(context, it) },
        )
    }
}
