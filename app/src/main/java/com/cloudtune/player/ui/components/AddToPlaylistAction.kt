package com.cloudtune.player.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.core.model.MusicTrack
import com.cloudtune.player.core.util.MusicUtils
import com.cloudtune.player.data.MusicRepository
import kotlinx.coroutines.launch

/**
 * 多选工具栏里的“加入歌单”按钮。
 *
 * @param repository 数据仓库
 * @param files 选中的文件（加入歌单）
 * @param enabled 是否可用（有选中项）
 */
@Composable
fun AddToPlaylistAction(
    repository: MusicRepository,
    files: List<CloudFile>,
    enabled: Boolean,
) {
    var showDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // 收集歌单列表（用于弹窗展示）
    val playlists by repository.observePlaylists().collectAsState(initial = emptyList())

    IconButton(onClick = { showDialog = true }, enabled = enabled) {
        Icon(Icons.Filled.PlaylistAdd, contentDescription = "加入歌单")
    }

    if (showDialog) {
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { showDialog = false },
            onPick = { playlistId ->
                showDialog = false
                scope.launch {
                    files.forEach { f ->
                        repository.addToPlaylist(
                            playlistId,
                            MusicTrack(file = f, title = MusicUtils.baseName(f.name)),
                        )
                    }
                }
            },
            onCreate = { name ->
                showDialog = false
                scope.launch {
                    val id = repository.createPlaylist(name)
                    files.forEach { f ->
                        repository.addToPlaylist(
                            id,
                            MusicTrack(file = f, title = MusicUtils.baseName(f.name)),
                        )
                    }
                }
            },
        )
    }
}
