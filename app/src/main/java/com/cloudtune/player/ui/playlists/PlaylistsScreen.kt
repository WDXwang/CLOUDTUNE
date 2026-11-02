package com.cloudtune.player.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudtune.player.core.model.PlaylistInfo
import com.cloudtune.player.core.model.displayName
import com.cloudtune.player.ui.components.MessageView

/**
 * 歌单列表页：卡片展示所有歌单，可搜索、可新建；点击进入歌单详情。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onOpenPlaylist: (Long, String) -> Unit,
    vm: PlaylistsViewModel = viewModel(),
) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("歌单") },
                actions = {
                    if (vm.generating) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(12.dp).size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = { vm.openPicker() }) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = "按歌手生成歌单")
                        }
                    }
                    IconButton(onClick = { showCreate = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "新建歌单")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 搜索框
            OutlinedTextField(
                value = vm.searchQuery,
                onValueChange = { vm.searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索歌单") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )

            val q = vm.searchQuery.trim()
            val filtered = if (q.isEmpty()) playlists
                else playlists.filter { it.name.contains(q, ignoreCase = true) }

            if (filtered.isEmpty()) {
                MessageView(
                    modifier = Modifier.fillMaxSize(),
                    message = if (playlists.isEmpty()) "还没有歌单，点右上角 + 新建" else "没有匹配的歌单",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filtered, key = { it.id }) { p ->
                        PlaylistCard(playlist = p, onClick = { onOpenPlaylist(p.id, p.name) })
                    }
                }
            }
        }
    }

    // 文件夹选择器
    if (vm.pickerOpen) {
        FolderPickerDialog(
            vm = vm,
            onDismiss = { vm.closePicker() },
            onConfirm = { selected ->
                vm.closePicker()
                vm.generateByArtist(selected)
            },
        )
    }

    // 生成结果提示
    vm.generateMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.clearGenerateMessage() },
            title = { Text("按歌手生成歌单") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { vm.clearGenerateMessage() }) { Text("知道了") }
            },
        )
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建歌单") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("歌单名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newName.trim()
                        if (name.isNotEmpty()) {
                            vm.create(name)
                            newName = ""
                            showCreate = false
                        }
                    }
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("取消") }
            },
        )
    }
}

/**
 * 文件夹选择弹窗：
 * 第一步：列出已启用的网盘；
 * 第二步：进入某网盘后逐级浏览文件夹，可多选；
 * 确认后仅扫描选中的文件夹（未选任何文件夹则扫描全部已启用网盘）。
 */
@Composable
private fun FolderPickerDialog(
    vm: PlaylistsViewModel,
    onDismiss: () -> Unit,
    onConfirm: (List<FolderRef>) -> Unit,
) {
    val browseAccountId = vm.browseAccountId
    val browseStack = vm.browseStack

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (browseAccountId != null) {
                    IconButton(onClick = { vm.backOneLevel() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
                Text(
                    text = if (browseAccountId == null) "选择要扫描的文件夹"
                    else browseStack.joinToString(" / ") { it.name }.ifBlank { "根目录" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            if (browseAccountId == null) {
                // 账号列表
                Column {
                    Text(
                        "选择一个网盘浏览文件夹，勾选要扫描的文件夹后点确认。\n" +
                            "不勾选任何文件夹将扫描全部已启用网盘。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        items(vm.pickerAccounts, key = { it.id }) { acc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.openAccount(acc) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Folder, contentDescription = null)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(acc.name, fontWeight = FontWeight.Medium)
                                    Text(
                                        acc.config.backend.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // 文件夹列表
                if (vm.pickerLoading && vm.currentFolders.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (vm.currentFolders.isEmpty()) {
                    Text("该目录下没有子文件夹", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        items(vm.currentFolders, key = { it.id }) { folder ->
                            val key = "${folder.accountId ?: browseAccountId}\n${folder.id}"
                            val checked = vm.selectedFolders.containsKey(key)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.toggleFolder(folder) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Folder, contentDescription = null)
                                Spacer(Modifier.width(10.dp))
                                Text(folder.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (checked) {
                                    Icon(Icons.Filled.Check, contentDescription = "已选", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            // 进入子目录按钮
                            IconButton(onClick = { vm.enterFolder(folder) }) {
                                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "进入")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(vm.selectedFolders.values.toList()) }
            ) {
                val n = vm.selectedFolders.size
                Text(if (n == 0) "扫描全部" else "扫描选中（$n）")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { vm.clearSelection() }) { Text("清空选择") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

/** 单个歌单卡片：渐变封面 + 名称 + 曲数 */
@Composable
private fun PlaylistCard(playlist: PlaylistInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFFF5F6D), Color(0xFFFF8A5C), Color(0xFFFFC15C))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = Color.White,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${playlist.trackCount} 首",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Filled.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
