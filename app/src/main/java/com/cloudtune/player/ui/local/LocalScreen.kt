package com.cloudtune.player.ui.local

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudtune.player.CloudTuneApp
import com.cloudtune.player.ui.components.AddToPlaylistAction
import com.cloudtune.player.ui.components.MessageView
import com.cloudtune.player.ui.components.MusicFileList
import com.cloudtune.player.ui.components.SearchField
import com.cloudtune.player.ui.components.SelectAllCheckbox

/**
 * 本地音乐页：首次进入申请音频权限，授权后扫描并展示，支持搜索/多选/全选。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalScreen(
    onOpenPlayer: () -> Unit,
    viewModel: LocalViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state = viewModel.uiState

    // 权限请求
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.load() }

    // 首次组合：已授权则直接加载，否则记录需要授权
    LaunchedEffect(Unit) {
        val granted = context.checkSelfPermission(viewModel.audioPermission) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.load()
    }

    val hasPermission = context.checkSelfPermission(viewModel.audioPermission) ==
        PackageManager.PERMISSION_GRANTED

    if (state.selectionMode) {
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
                    title = { Text("已选 ${state.selectedIds.size} 项") },
                    actions = {
                        SelectAllCheckbox(
                            allSelected = state.allSelected,
                            selectedCount = state.selectedIds.size,
                            totalCount = state.visibleFiles.size,
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
            Box(Modifier.fillMaxSize().padding(padding)) { Body(viewModel, hasPermission, permLauncher) }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("本地音乐") },
                    actions = {
                        if (state.files.isNotEmpty()) {
                            IconButton(onClick = { viewModel.playAll() }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "播放全部")
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (hasPermission) {
                    SearchField(query = state.query, onQueryChange = { viewModel.setQuery(it) })
                }
                Box(Modifier.fillMaxSize()) { Body(viewModel, hasPermission, permLauncher) }
            }
        }
    }
}

@Composable
private fun Body(
    viewModel: LocalViewModel,
    hasPermission: Boolean,
    permLauncher: androidx.activity.result.ActivityResultLauncher<String>,
) {
    val state = viewModel.uiState
    val context = androidx.compose.ui.platform.LocalContext.current
    when {
        !hasPermission -> MessageView(
            message = "需要音频读取权限以扫描本地音乐",
            actionText = "授权",
            onAction = { permLauncher.launch(viewModel.audioPermission) },
        )
        state.loading && state.files.isEmpty() ->
            com.cloudtune.player.ui.components.LoadingBox()
        state.error != null -> MessageView(
            message = state.error!!,
            actionText = "重试",
            onAction = { viewModel.load() },
        )
        state.visibleFiles.isEmpty() ->
            MessageView(message = if (state.query.isNotBlank()) "无匹配结果" else "未找到本地音乐")
        else -> MusicFileList(
            files = state.visibleFiles,
            selectionMode = state.selectionMode,
            selectedIds = state.selectedIds,
            onFolderClick = {},
            onMusicClick = { viewModel.playMusic(it) },
            onLongPressMusic = { viewModel.enterSelection(it) },
            onToggleSelect = { viewModel.toggleSelect(it) },
            onVideoClick = { com.cloudtune.player.ui.video.VideoActivity.start(context, it) },
            onRingtoneClick = { com.cloudtune.player.ringtone.RingtoneEditorActivity.start(context, it) },
        )
    }
}
