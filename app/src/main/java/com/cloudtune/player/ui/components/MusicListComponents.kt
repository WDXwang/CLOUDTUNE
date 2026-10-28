package com.cloudtune.player.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.core.util.MusicUtils

/**
 * 搜索框：输入关键词实时过滤。
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索文件名…",
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        singleLine = true,
        placeholder = { Text(placeholder) },
    )
}

/**
 * 全选复选框（支持三态：全选 / 部分 / 未选）。
 */
@Composable
fun SelectAllCheckbox(
    allSelected: Boolean,
    selectedCount: Int,
    totalCount: Int,
    onClick: () -> Unit,
) {
    val state = when {
        totalCount == 0 -> ToggleableState.Off
        allSelected -> ToggleableState.On
        selectedCount > 0 -> ToggleableState.Indeterminate
        else -> ToggleableState.Off
    }
    Row(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .combinedClickableCompat(onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriStateCheckbox(state = state, onClick = onClick)
        Text("全选", style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * 通用音乐 / 文件夹列表，支持多选。
 *
 * @param files 已过滤后的可见条目
 * @param selectionMode 是否多选模式
 * @param selectedIds 已选 id 集合
 * @param onFolderClick 点击文件夹
 * @param onMusicClick 点击音乐（带该音乐在可见音乐中的下标）
 * @param onLongPressMusic 长按音乐进入多选
 * @param onToggleSelect 多选模式下切换选中
 */
@Composable
fun MusicFileList(
    files: List<CloudFile>,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onFolderClick: (CloudFile) -> Unit,
    onMusicClick: (musicIndex: Int) -> Unit,
    onLongPressMusic: (CloudFile) -> Unit,
    onToggleSelect: (CloudFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        itemsIndexed(files, key = { _, f -> f.id }) { index, file ->
            if (file.isDir) {
                ListRow(
                    icon = Icons.Filled.Folder,
                    title = file.name,
                    subtitle = "",
                    tint = MaterialTheme.colorScheme.primary,
                    showCheckbox = false,
                    checked = false,
                    onClick = { onFolderClick(file) },
                    onLongClick = null,
                    onCheckboxClick = null,
                )
            } else if (file.isMusic()) {
                val musicIdx = files.subList(0, index + 1).count { it.isMusic() } - 1
                ListRow(
                    icon = Icons.Filled.MusicNote,
                    title = MusicUtils.baseName(file.name),
                    subtitle = MusicUtils.formatSize(file.size),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    showCheckbox = selectionMode,
                    checked = file.id in selectedIds,
                    onClick = {
                        if (selectionMode) onToggleSelect(file)
                        else onMusicClick(musicIdx)
                    },
                    onLongClick = { onLongPressMusic(file) },
                    onCheckboxClick = { onToggleSelect(file) },
                )
            }
        }
    }
}

/** 单行：图标 + 标题/副标题 + 可选复选框。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    tint: androidx.compose.ui.graphics.Color,
    showCheckbox: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onCheckboxClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showCheckbox) {
            Checkbox(checked = checked, onCheckedChange = { onCheckboxClick?.invoke() })
            Spacer(Modifier.width(8.dp))
        }
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 让带点击的容器复用 combinedClickable（简单封装）。 */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick)
