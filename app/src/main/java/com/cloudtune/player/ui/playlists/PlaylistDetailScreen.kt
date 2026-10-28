package com.cloudtune.player.ui.playlists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudtune.player.ui.components.MusicFileList
import com.cloudtune.player.ui.components.SearchField
import com.cloudtune.player.ui.components.SelectAllCheckbox

/**
 * 歌单详情页：展示歌单内歌曲，支持搜索、多选移除、删除 / 重命名歌单、播放。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    playlistName: String,
    vm: PlaylistDetailViewModel = viewModel(),
) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val state = vm.uiState
    val visible = state.visibleTracks(tracks)

    var showRename by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf(playlistName) }
    var showDelete by remember { mutableStateOf(false) }

    if (state.selectionMode) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { vm.clearSelection() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "退出多选")
                        }
                    },
                    title = { Text("已选 ${state.selectedIds.size} 项") },
                    actions = {
                        SelectAllCheckbox(
                            allSelected = visible.isNotEmpty() && state.selectedIds.size == visible.size,
                            selectedCount = state.selectedIds.size,
                            totalCount = visible.size,
                            onClick = { vm.selectAll() },
                        )
                        IconButton(
                            onClick = { vm.playSelected() },
                            enabled = state.selectedIds.isNotEmpty(),
                        ) { Icon(Icons.Filled.PlayArrow, contentDescription = "播放选中") }
                        IconButton(
                            onClick = { vm.removeSelected() },
                            enabled = state.selectedIds.isNotEmpty(),
                        ) { Icon(Icons.Filled.Delete, contentDescription = "从歌单移除") }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                MusicFileList(
                    files = visible.map { it.file },
                    selectionMode = true,
                    selectedIds = state.selectedIds,
                    onFolderClick = {},
                    onMusicClick = {},
                    onLongPressMusic = { vm.toggleSelect(it) },
                    onToggleSelect = { vm.toggleSelect(it) },
                )
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    title = { Text(playlistName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    actions = {
                        IconButton(onClick = { vm.playAll() }, enabled = visible.isNotEmpty()) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "播放全部")
                        }
                        IconButton(onClick = { showRename = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "重命名")
                        }
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除歌单")
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                SearchField(query = state.query, onQueryChange = { vm.setQuery(it) })
                if (visible.isEmpty()) {
                    com.cloudtune.player.ui.components.MessageView(
                        message = if (state.query.isNotBlank()) "无匹配结果" else "歌单为空"
                    )
                } else {
                    MusicFileList(
                        files = visible.map { it.file },
                        selectionMode = false,
                        selectedIds = emptySet(),
                        onFolderClick = {},
                        onMusicClick = { vm.playMusic(it) },
                        onLongPressMusic = { vm.enterSelection(it) },
                        onToggleSelect = { vm.toggleSelect(it) },
                    )
                }
            }
        }
    }

    // 重命名弹窗
    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("重命名歌单") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = renameValue.trim()
                    if (n.isNotEmpty()) vm.rename(n)
                    showRename = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("取消") }
            },
        )
    }

    // 删除确认弹窗
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除歌单") },
            text = { Text("确定删除歌单“$playlistName”吗？歌曲不会从云盘/本地移除。") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    vm.delete(onDone = onDeleted)
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("取消") }
            },
        )
    }
}
